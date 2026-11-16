package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainType;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.observer.ScheduleEventType;
import com.railsync.strategy.PlatformAllocationStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The single entry point the GUI talks to. It coordinates the managers,
 * the allocator and the delay manager, and announces every change to the
 * observers.
 *
 * <p>Why it exists: Swing panels should not know how allocation, delays or
 * metrics work. They call one method here, e.g. delayTrain(), and get a
 * report back; all panels then refresh through the Observer pattern.
 * This class never imports Swing.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>[STRATEGY] / [ABSTRACTION] / SOLID-D: the allocation strategy is
 *       received through the constructor and can be swapped with
 *       setStrategy(); both take the PlatformAllocationStrategy interface.</li>
 *   <li>[OBSERVER]: every change ends with publish(...).</li>
 *   <li>SOLID-S: it coordinates; the real work is done by TrainManager,
 *       PlatformManager, PlatformAllocator, DelayManager, MetricsCalculator.</li>
 *   <li>Composition: it creates and owns its helper managers.</li>
 * </ul>
 *
 * <p>Works with: every service class, Station, Schedule, ScheduleEventManager.</p>
 */
public class SchedulingService {

    private final Station station;
    private final Schedule schedule;
    private final ScheduleEventManager events;
    private final TrainManager trainManager;
    private final PlatformManager platformManager;
    private final PlatformAllocator allocator;
    private final DelayManager delayManager;
    private final MetricsCalculator metricsCalculator = new MetricsCalculator();
    private PlatformAllocationStrategy strategy;
    private int conflictsDetected;
    private List<AllocationResult> lastResults = new ArrayList<>();
    // [COMMAND] the undo stack of allocations and delays.
    private final CommandHistory history = new CommandHistory();

    /**
     * Creates the service. The strategy is injected, so the service never
     * depends on a concrete algorithm.
     *
     * @param station  the station
     * @param schedule its bookings
     * @param strategy the starting allocation strategy
     * @param events   where change notifications are published
     */
    public SchedulingService(Station station, Schedule schedule,
                             PlatformAllocationStrategy strategy, ScheduleEventManager events) {
        this.station = station;
        this.schedule = schedule;
        this.events = events;
        // [COMPOSITION] the service creates and owns its helpers.
        this.trainManager = new TrainManager(schedule);
        this.platformManager = new PlatformManager(station, schedule);
        this.allocator = new PlatformAllocator(station, schedule, PlatformAllocator.DEFAULT_MAX_WAIT_MINUTES);
        this.delayManager = new DelayManager(schedule, allocator);
        // [SOLID-D] the strategy arrives from outside as an interface (constructor injection).
        setStrategy(strategy);
    }

    // ------------------------------------------------------------------
    // Strategy and settings.
    // ------------------------------------------------------------------

    /**
     * Swaps the allocation strategy at runtime.
     *
     * @param newStrategy any implementation of the interface
     * @throws RailSyncException if it is null
     */
    public void setStrategy(PlatformAllocationStrategy newStrategy) {
        if (newStrategy == null) {
            throw new RailSyncException("A strategy must be chosen.");
        }
        // [STRATEGY] algorithm chosen at runtime through the interface.
        this.strategy = newStrategy;
        events.publish(new ScheduleEvent(ScheduleEventType.SETTINGS_CHANGED, "Strategy set to " + newStrategy.getName()));
    }

    /** @return the current strategy */
    public PlatformAllocationStrategy getStrategy() {
        return strategy;
    }

    /**
     * Changes the station-wide clearance buffer.
     *
     * @param minutes 0 to 60
     */
    public void setClearanceBufferMinutes(int minutes) {
        schedule.setClearanceBufferMinutes(minutes);
        history.clear(); // old snapshots may break the new buffer rule
        events.publish(new ScheduleEvent(ScheduleEventType.SETTINGS_CHANGED, "Clearance buffer set to " + minutes + " min"));
    }

    /**
     * Changes how long trains may wait at the outer signal.
     *
     * @param minutes 0 to 180
     */
    public void setMaxWaitMinutes(int minutes) {
        allocator.setMaxWaitMinutes(minutes);
        events.publish(new ScheduleEvent(ScheduleEventType.SETTINGS_CHANGED, "Maximum wait set to " + minutes + " min"));
    }

    /**
     * Switches optional preemption on or off.
     *
     * @param enabled true to allow safe preemption
     */
    public void setPreemptionEnabled(boolean enabled) {
        allocator.setPreemptionEnabled(enabled);
        events.publish(new ScheduleEvent(ScheduleEventType.SETTINGS_CHANGED,
                "Preemption " + (enabled ? "enabled" : "disabled")));
    }

    // ------------------------------------------------------------------
    // Trains (Module A).
    // ------------------------------------------------------------------

    /**
     * Adds a train.
     *
     * @param train the new train
     */
    public void addTrain(Train train) {
        trainManager.addTrain(train);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.TRAINS_CHANGED, "Added train " + train));
    }

    /**
     * Removes a train that has no platform.
     *
     * @param train the train
     */
    public void removeTrain(Train train) {
        trainManager.removeTrain(train);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.TRAINS_CHANGED, "Removed train " + train));
    }

    /**
     * Changes a train's name, base priority and timetable. If it held a
     * platform, the booking is released and the train is placed again.
     *
     * @param train        the train
     * @param name         new name
     * @param basePriority new base priority (1-5)
     * @param arrival      new timetable arrival
     * @param departure    new timetable departure
     * @return the new placement result, or null if the train had no platform
     */
    public AllocationResult updateTrain(Train train, String name, int basePriority,
                                        LocalDateTime arrival, LocalDateTime departure) {
        // Step 1: change the values. Each setter validates its own value and throws before
        // changing it, and the times go last, so a bad value can never leave a stale booking.
        train.setName(name);
        train.setBasePriority(basePriority);
        boolean hadPlatform = schedule.findReservation(train) != null;
        train.updateTimes(arrival, departure);
        history.clear();
        // Step 2: an old booking no longer matches the new times, so place the train again.
        AllocationResult result = null;
        if (hadPlatform) {
            schedule.release(train);
            result = allocator.allocateSingle(train);
        }
        events.publish(new ScheduleEvent(ScheduleEventType.TRAINS_CHANGED, "Updated train " + train));
        return result;
    }

    // ------------------------------------------------------------------
    // Platforms (Module B).
    // ------------------------------------------------------------------

    /**
     * Adds a platform.
     *
     * @param platform the new platform
     */
    public void addPlatform(Platform platform) {
        platformManager.addPlatform(platform);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.PLATFORMS_CHANGED, "Added " + platform));
    }

    /**
     * Removes a platform that has no bookings.
     *
     * @param platform the platform
     */
    public void removePlatform(Platform platform) {
        platformManager.removePlatform(platform);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.PLATFORMS_CHANGED, "Removed " + platform));
    }

    /**
     * Changes a platform's length and categories (refused if a booking would break).
     *
     * @param platform  the platform
     * @param newLength new length in metres
     * @param newTypes  new accepted categories
     */
    public void updatePlatform(Platform platform, int newLength, Set<TrainType> newTypes) {
        platformManager.updatePlatform(platform, newLength, newTypes);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.PLATFORMS_CHANGED, "Updated " + platform));
    }

    /**
     * Marks a failed platform as working again.
     *
     * @param platform the platform
     */
    public void repairPlatform(Platform platform) {
        platformManager.repairPlatform(platform);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.PLATFORMS_CHANGED, platform + " repaired"));
    }

    // ------------------------------------------------------------------
    // Allocation (Modules C and D).
    // ------------------------------------------------------------------

    /**
     * Allocates every train that has no platform, using the current strategy.
     *
     * @return one result per train processed
     */
    public List<AllocationResult> allocatePendingTrains() {
        return runAllocation(trainManager.getTrainsWithoutPlatform());
    }

    /**
     * Allocates one train (Module C: allocate a single arriving train).
     *
     * @param train the train
     * @return the result
     */
    public AllocationResult allocateTrain(Train train) {
        if (schedule.findReservation(train) != null) {
            return allocator.allocateSingle(train); // already placed: just describe its booking
        }
        List<Train> single = new ArrayList<>();
        single.add(train);
        return runAllocation(single).get(0);
    }

    /**
     * Allocates trains as an undoable command and records the results.
     */
    private List<AllocationResult> runAllocation(List<Train> trains) {
        // [COMMAND] wrap the action so it can be undone later.
        // [ABSTRACTION] the allocator receives the interface, not a concrete strategy.
        AllocateCommand command = new AllocateCommand(schedule, trainManager.getAllTrains(), allocator, trains, strategy);
        command.execute();
        history.push(command);
        recordResults(command.getResults());
        return command.getResults();
    }

    /**
     * Frees a train's platform.
     *
     * @param train the train
     */
    public void releaseTrain(Train train) {
        if (schedule.release(train)) {
            history.clear();
            events.publish(new ScheduleEvent(ScheduleEventType.ALLOCATION, "Released train " + train.getNumber()));
        }
    }

    /**
     * Clears every booking so the trains can be allocated again (for example
     * with a different strategy). Delays and failed platforms stay as they are.
     */
    public void resetAllocations() {
        for (Train train : trainManager.getAllTrains()) {
            schedule.release(train);
            train.markScheduled();
        }
        conflictsDetected = 0;
        lastResults = new ArrayList<>();
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.SCHEDULE_RESET, "All allocations cleared"));
    }

    /**
     * Removes every train, booking and platform (before loading a new scenario).
     */
    public void clearEverything() {
        resetAllocations();
        trainManager.clear();
        platformManager.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.SCHEDULE_RESET, "Scenario cleared"));
    }

    /**
     * Replaces the live data with a copy of a scenario (for example one made
     * by ScenarioGenerator). Nothing is allocated.
     *
     * @param scenario the scenario to load (a copy is used, so it stays unchanged)
     */
    public void loadScenario(Scenario scenario) {
        clearEverything();
        Scenario copy = scenario.copy();
        for (Platform platform : copy.getPlatforms()) {
            platformManager.addPlatform(platform);
        }
        for (Train train : copy.getTrains()) {
            trainManager.addTrain(train);
        }
        events.publish(new ScheduleEvent(ScheduleEventType.SCHEDULE_RESET, "Loaded a scenario with "
                + copy.getTrains().size() + " trains and " + copy.getPlatforms().size() + " platforms"));
    }

    /**
     * Re-plans from a moment onwards with the current strategy, without
     * changing the past: bookings that have already started stay; bookings
     * that start later are released and decided again, and no new booking
     * may start before {@code now}. Used by the yard view so that switching
     * strategy while the clock runs changes what happens next.
     *
     * @param now the current simulated time
     * @return the new placement results
     */
    public List<AllocationResult> reallocateFrom(LocalDateTime now) {
        // Step 1: release every booking that has not started yet (those trains are still on the way or queuing).
        List<PlatformReservation> notStarted = new ArrayList<>();
        for (PlatformReservation booking : schedule.getAllReservations()) {
            if (booking.getStart().isAfter(now)) {
                notStarted.add(booking);
            }
        }
        for (PlatformReservation booking : notStarted) {
            schedule.release(booking.getTrain());
        }
        // Step 2: trains given up earlier get another chance (the allocator drops them again if it is too late).
        for (Train train : trainManager.getAllTrains()) {
            train.markScheduled();
        }
        // Step 3: decide again with the current strategy, starting no earlier than now.
        List<AllocationResult> results = allocator.allocateAll(trainManager.getTrainsWithoutPlatform(), strategy, now);
        history.clear();
        recordResults(results);
        return results;
    }

    // ------------------------------------------------------------------
    // Disruptions (Modules E and E2).
    // ------------------------------------------------------------------

    /**
     * Delays a train and repairs the schedule (see DelayManager for the order of attempts).
     *
     * @param train   the late train
     * @param minutes extra delay in minutes
     * @return what happened, including any ripple cascade
     */
    public DelayReport delayTrain(Train train, int minutes) {
        // [COMMAND] the delay (with any ripple or reassignment) can be undone as one step.
        DelayCommand command = new DelayCommand(schedule, trainManager.getAllTrains(), delayManager, train, minutes);
        command.execute();
        history.push(command);
        DelayReport report = command.getReport();
        // Step 1: the delay itself (carries the moved bookings for the animation).
        events.publish(new ScheduleEvent(ScheduleEventType.DELAY, report.getMessage(), report.getChanges()));
        // Step 2: a new clash was found; count it and tell observers (status bar, dashboard).
        if (report.isConflict()) {
            conflictsDetected++;
            ScheduleEventType type = report.getOutcome() == DelayReport.Outcome.FAILED
                    ? ScheduleEventType.CONFLICT : ScheduleEventType.REASSIGNMENT;
            String summary = "Delay of " + report.getTrain().getNumber() + " caused a conflict, resolved as "
                    + report.getOutcome() + " (" + report.getChanges().size() + " booking(s) changed)";
            events.publish(new ScheduleEvent(type, summary, report.getChanges()));
        }
        return report;
    }

    /**
     * Marks a platform failed and reassigns its trains.
     *
     * @param platform the platform that broke down
     * @return who moved, who could not, and why
     */
    public BreakdownReport reportBreakdown(Platform platform) {
        BreakdownReport report = delayManager.handleBreakdown(platform);
        history.clear();
        events.publish(new ScheduleEvent(ScheduleEventType.BREAKDOWN, report.toString(), report.getChanges()));
        return report;
    }

    // ------------------------------------------------------------------
    // Undo (Command pattern).
    // ------------------------------------------------------------------

    /**
     * Takes back the most recent allocation or delay, restoring every booking
     * (including trains moved by a ripple or preemption) exactly as before.
     *
     * @return a description of what was undone, or null if there was nothing to undo
     */
    public String undoLast() {
        ScheduleCommand command = history.pop();
        if (command == null) {
            return null;
        }
        // [COMMAND] [POLYMORPHISM] the command object knows how to reverse itself.
        command.undo();
        lastResults = new ArrayList<>();
        events.publish(new ScheduleEvent(ScheduleEventType.UNDO, "Undid the " + command.getDescription()));
        return command.getDescription();
    }

    /** @return true if there is an allocation or delay to undo */
    public boolean canUndo() {
        return history.size() > 0;
    }

    /** @return what Undo would take back, e.g. "delay of 12951 by 5 min", or null */
    public String getUndoDescription() {
        ScheduleCommand next = history.peek();
        return next == null ? null : next.getDescription();
    }

    // ------------------------------------------------------------------
    // Metrics (Module F) and read access for the GUI.
    // ------------------------------------------------------------------

    /** @return metrics of the live schedule */
    public ScheduleMetrics calculateMetrics() {
        return metricsCalculator.calculate(station, schedule, trainManager.getAllTrains(), conflictsDetected);
    }

    /** @return the results of the latest allocation run, read-only */
    public List<AllocationResult> getLastResults() {
        return Collections.unmodifiableList(lastResults);
    }

    /**
     * Finds a train's booking.
     *
     * @param train the train
     * @return the booking, or null
     */
    public PlatformReservation findReservation(Train train) {
        return schedule.findReservation(train);
    }

    /** @return the station */
    public Station getStation() {
        return station;
    }

    /** @return the schedule (read it; change it only through this service) */
    public Schedule getSchedule() {
        return schedule;
    }

    /** @return the train register */
    public TrainManager getTrainManager() {
        return trainManager;
    }

    /** @return the platform manager */
    public PlatformManager getPlatformManager() {
        return platformManager;
    }

    /** @return the event manager, so panels can subscribe */
    public ScheduleEventManager getEvents() {
        return events;
    }

    /** @return conflicts detected since the last reset */
    public int getConflictsDetected() {
        return conflictsDetected;
    }

    /** @return the maximum wait at the outer signal */
    public int getMaxWaitMinutes() {
        return allocator.getMaxWaitMinutes();
    }

    /** @return true if preemption is enabled */
    public boolean isPreemptionEnabled() {
        return allocator.isPreemptionEnabled();
    }

    /** Saves results, counts conflicts and notifies observers. */
    private void recordResults(List<AllocationResult> results) {
        lastResults = new ArrayList<>(results);
        int newConflicts = metricsCalculator.countConflicts(results);
        conflictsDetected += newConflicts;
        int placed = 0;
        for (AllocationResult result : results) {
            if (result.isAllocated()) {
                placed++;
            }
        }
        String summary = strategy.getName() + ": placed " + placed + " of " + results.size()
                + " train(s), " + newConflicts + " conflict(s) detected";
        // [OBSERVER] fire an event so subscribed panels refresh.
        events.publish(new ScheduleEvent(ScheduleEventType.ALLOCATION, summary));
        if (newConflicts > 0) {
            events.publish(new ScheduleEvent(ScheduleEventType.CONFLICT, summary));
        }
    }
}
