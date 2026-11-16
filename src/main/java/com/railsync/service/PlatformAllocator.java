package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PlatformAllocationStrategy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The allocation engine: places trains on platforms, letting the chosen
 * strategy decide who goes first whenever several trains are waiting.
 *
 * <p>How it works (a small event-driven simulation, like an OS dispatcher):</p>
 * <ol>
 *   <li>Trains that fit no platform at all are rejected straight away, with
 *       the reason for every platform.</li>
 *   <li>A simulated clock jumps from event to event. An event is "a train
 *       arrives" or "a platform becomes free for a waiting train".</li>
 *   <li>At each event, arrived trains join the <b>ready queue</b> (they wait
 *       at the outer signal). The strategy orders the queue and each train,
 *       in that order, takes the first free compatible platform (First-Fit).</li>
 *   <li>Optional preemption: if no platform is free, a train may take the
 *       booking of ONE lower-priority train that has not reached the
 *       platform yet, but only if that train can be moved to another
 *       platform at the same time. Otherwise nothing changes.</li>
 *   <li>A train that cannot start within the maximum wait is marked
 *       unallocated, with the reason.</li>
 * </ol>
 *
 * <p>This is a heuristic (greedy, one decision at a time). It is NOT
 * guaranteed to use the fewest platforms or to minimise waiting.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>[ABSTRACTION] / SOLID-D: depends on the PlatformAllocationStrategy
 *       interface, never on FcfsStrategy or PriorityStrategy directly.</li>
 *   <li>SOLID-S: it only decides placements. Storing bookings is Schedule's
 *       job; compatibility rules are Train's job.</li>
 *   <li>Dependency injection: Station and Schedule are passed into the
 *       constructor, so tests and the comparison arena can give it copies.</li>
 * </ul>
 *
 * <p>Works with: Station, Schedule, Train, PlatformAllocationStrategy,
 * AllocationResult.</p>
 */
public class PlatformAllocator {

    /** Default time a train may wait at the outer signal for a platform. */
    public static final int DEFAULT_MAX_WAIT_MINUTES = 20;
    /** Largest allowed maximum wait. */
    public static final int MAX_ALLOWED_WAIT_MINUTES = 180;

    private final Station station;
    private final Schedule schedule;
    private int maxWaitMinutes;
    private boolean preemptionEnabled;

    /**
     * Creates an allocator for one station and its schedule.
     *
     * @param station        the station whose platforms may be used
     * @param schedule       where reservations are recorded
     * @param maxWaitMinutes how long a train may wait for a platform (0 to 180)
     * @throws RailSyncException if maxWaitMinutes is out of range
     */
    public PlatformAllocator(Station station, Schedule schedule, int maxWaitMinutes) {
        this.station = station;
        this.schedule = schedule;
        setMaxWaitMinutes(maxWaitMinutes);
        this.preemptionEnabled = false;
    }

    /**
     * Allocates a group of trains using the given strategy. Trains that
     * already hold a reservation are skipped. Existing reservations in the
     * schedule are respected.
     *
     * @param trains   trains to place
     * @param strategy decides the service order of waiting trains
     * @return one result per train processed, in the order decisions were made
     */
    public List<AllocationResult> allocateAll(List<Train> trains, PlatformAllocationStrategy strategy) {
        return allocateAll(trains, strategy, null);
    }

    /**
     * Same as allocateAll(trains, strategy), but no train may start before
     * {@code notBefore}. Used to re-plan "from now on" while the simulation
     * clock is running: decisions in the past are never changed.
     *
     * @param trains    trains to place
     * @param strategy  decides the service order of waiting trains
     * @param notBefore earliest allowed start, or null for no limit
     * @return one result per train processed, in the order decisions were made
     */
    public List<AllocationResult> allocateAll(List<Train> trains, PlatformAllocationStrategy strategy,
                                              LocalDateTime notBefore) {
        List<AllocationResult> results = new ArrayList<>();

        // Step 1: reject trains that fit no platform at all; keep the rest as "not arrived yet".
        List<Train> notArrived = new ArrayList<>();
        for (Train train : trains) {
            if (schedule.findReservation(train) != null) {
                continue;
            }
            String reason = explainNoCompatiblePlatform(train);
            if (reason != null) {
                train.markUnallocated();
                results.add(AllocationResult.failure(train, reason, false));
            } else {
                notArrived.add(train);
            }
        }

        // Step 2: sort by arrival so arrivals can be taken from the front of the list.
        notArrived.sort((a, b) -> a.getExpectedArrival().compareTo(b.getExpectedArrival()));

        // Step 3: run the event loop. The clock starts at the first arrival (but never before notBefore).
        List<Train> readyQueue = new ArrayList<>();
        LocalDateTime clock = notArrived.isEmpty() ? null : notArrived.get(0).getExpectedArrival();
        if (clock != null && notBefore != null && clock.isBefore(notBefore)) {
            clock = notBefore;
        }
        while (clock != null) {
            // Step 3a: trains that have arrived by now join the ready queue (OS: ready queue).
            moveArrivedTrains(notArrived, readyQueue, clock);
            // Step 3b: the strategy picks the order; each train tries to get a platform now.
            dispatch(readyQueue, strategy, clock, results);
            // Step 3c: drop trains that can no longer start in time; find the next free moment.
            LocalDateTime nextFreeStart = dropHopelessTrains(readyQueue, clock, results);
            // Step 3d: jump the clock to the next event (arrival or platform freeing up).
            clock = earlierOf(nextFreeStart, nextArrival(notArrived));
        }
        return results;
    }

    /**
     * Places one train that holds no platform: on arrival if a platform is
     * free, otherwise after waiting up to the maximum wait. DelayManager uses
     * this for every reassignment, so delays and breakdowns share one rule.
     * With a single train the service order does not matter, so FCFS is used.
     *
     * @param train a train with no booking
     * @return the result; if the train already had a booking, a success
     *         result describing that booking
     */
    public AllocationResult allocateSingle(Train train) {
        PlatformReservation existing = schedule.findReservation(train);
        if (existing != null) {
            return AllocationResult.success(train, existing.getPlatform(), existing.getStart());
        }
        List<Train> justOne = new ArrayList<>();
        justOne.add(train);
        return allocateAll(justOne, new FcfsStrategy()).get(0);
    }

    /**
     * Returns the lowest-numbered platform the train can use with no clash
     * if it starts at the given time (First-Fit).
     *
     * @param train the train
     * @param start the start time to test
     * @return a free compatible platform, or null if there is none
     */
    public Platform findFirstFreePlatform(Train train, LocalDateTime start) {
        // Station keeps platforms sorted by number, so First-Fit is deterministic and easy to trace.
        for (Platform platform : station.getPlatforms()) {
            if (train.canUsePlatform(platform) && schedule.findConflict(train, platform, start) == null) {
                return platform;
            }
        }
        return null;
    }

    /**
     * Explains why the train fits no platform at all (ignoring time).
     *
     * @param train the train
     * @return a reason listing every platform, or null if at least one platform fits
     */
    public String explainNoCompatiblePlatform(Train train) {
        if (station.getPlatformCount() == 0) {
            return "the station has no platforms.";
        }
        StringBuilder reasons = new StringBuilder("no compatible platform.");
        for (Platform platform : station.getPlatforms()) {
            String reason = train.getIncompatibilityReason(platform);
            if (reason == null) {
                return null;
            }
            reasons.append(' ').append(reason);
        }
        return reasons.toString();
    }

    /** @return true if higher-priority trains may displace lower-priority bookings */
    public boolean isPreemptionEnabled() {
        return preemptionEnabled;
    }

    /**
     * Switches optional preemption on or off (off by default).
     *
     * @param enabled true to allow safe preemption
     */
    public void setPreemptionEnabled(boolean enabled) {
        this.preemptionEnabled = enabled;
    }

    /** @return the maximum wait in minutes */
    public int getMaxWaitMinutes() {
        return maxWaitMinutes;
    }

    /**
     * Changes how long a train may wait for a platform.
     *
     * @param minutes 0 to 180
     * @throws RailSyncException if out of range
     */
    public final void setMaxWaitMinutes(int minutes) {
        if (minutes < 0 || minutes > MAX_ALLOWED_WAIT_MINUTES) {
            throw new RailSyncException("Maximum wait must be between 0 and "
                    + MAX_ALLOWED_WAIT_MINUTES + " minutes, but was " + minutes + ".");
        }
        this.maxWaitMinutes = minutes;
    }

    // ------------------------------------------------------------------
    // Private steps of the event loop.
    // ------------------------------------------------------------------

    /** Moves every train whose expected arrival is at or before the clock into the ready queue. */
    private void moveArrivedTrains(List<Train> notArrived, List<Train> readyQueue, LocalDateTime clock) {
        // notArrived is sorted, so we only ever look at the front.
        while (!notArrived.isEmpty() && !notArrived.get(0).getExpectedArrival().isAfter(clock)) {
            readyQueue.add(notArrived.remove(0));
        }
    }

    /** Lets the strategy order the queue, then gives each train the first free platform, if any. */
    private void dispatch(List<Train> readyQueue, PlatformAllocationStrategy strategy,
                          LocalDateTime clock, List<AllocationResult> results) {
        // [STRATEGY] the algorithm chosen at runtime decides the order of service.
        // [SOLID-L] any PlatformAllocationStrategy can be substituted here and the loop still works.
        List<Train> serviceOrder = strategy.orderReadyQueue(readyQueue, clock);
        for (Train train : serviceOrder) {
            Platform platform = findFirstFreePlatform(train, clock);
            if (platform != null) {
                schedule.reserve(train, platform, clock);
                results.add(AllocationResult.success(train, platform, clock));
                readyQueue.remove(train);
            } else if (preemptionEnabled) {
                AllocationResult preempted = tryPreemption(train, clock);
                if (preempted != null) {
                    results.add(preempted);
                    readyQueue.remove(train);
                }
            }
        }
    }

    /**
     * Tries to give the train a platform by moving ONE lower-priority train
     * that is booked there but has not arrived at the platform yet.
     *
     * <p>OS analogy: preemption, where a higher-priority process takes the
     * CPU. Unlike an OS, a train already standing at a platform is never
     * pushed out, and a displaced train must keep its time slot on another
     * platform. If it cannot, nothing is changed at all.</p>
     *
     * @param train the higher-priority train that needs a platform now
     * @param clock the current simulated time
     * @return a preemption result, or null if no safe preemption exists
     */
    private AllocationResult tryPreemption(Train train, LocalDateTime clock) {
        for (Platform platform : station.getPlatforms()) {
            if (!train.canUsePlatform(platform)) {
                continue;
            }
            // Step 1: preempt only when exactly one booking is in the way.
            // Keeping it to one victim keeps the rule (and the undo) simple.
            List<PlatformReservation> clashes = schedule.findAllConflicts(train, platform, clock);
            if (clashes.size() != 1) {
                continue;
            }
            PlatformReservation victimBooking = clashes.get(0);
            Train victim = victimBooking.getTrain();
            // Step 2: the victim must have LOWER priority and must not have reached the platform yet.
            // [POLYMORPHISM] getPriority() is decided by each Train subclass.
            if (victim.getPriority() >= train.getPriority() || !victimBooking.getStart().isAfter(clock)) {
                continue;
            }
            // Step 3: try the swap: free the victim's slot and give it to the new train.
            LocalDateTime victimStart = victimBooking.getStart();
            schedule.release(victim);
            schedule.reserve(train, platform, clock);
            // Step 4: the victim must find another platform at the SAME time (no extra waiting).
            Platform newHome = findFirstFreePlatform(victim, victimStart);
            if (newHome != null) {
                schedule.reserve(victim, newHome, victimStart);
                return AllocationResult.successByPreemption(train, platform, clock, victim, newHome);
            }
            // Step 5: the victim cannot move safely, so undo everything exactly as it was.
            schedule.release(train);
            schedule.reserve(victim, platform, victimStart);
        }
        return null;
    }

    /**
     * For every train still waiting, works out the earliest time any
     * compatible platform could take it. If that is later than its maximum
     * wait, the train is given up as unallocated. Bookings only ever get
     * added, so a train's earliest start can only move later: once it is too
     * late, it stays too late.
     *
     * @return the earliest start among the trains still waiting, or null if none
     */
    private LocalDateTime dropHopelessTrains(List<Train> readyQueue, LocalDateTime clock,
                                             List<AllocationResult> results) {
        LocalDateTime earliestOverall = null;
        List<Train> stillWaiting = new ArrayList<>();
        for (Train train : readyQueue) {
            // Step 1: earliest moment any compatible platform is free for this train.
            LocalDateTime earliest = earliestPossibleStart(train, clock);
            LocalDateTime deadline = train.getExpectedArrival().plusMinutes(maxWaitMinutes);
            // Step 2: too late? Then give up on this train and say why.
            if (earliest == null) {
                train.markUnallocated();
                results.add(AllocationResult.failure(train, "no compatible platform is available any more.", false));
            } else if (earliest.isAfter(deadline)) {
                train.markUnallocated();
                long wouldWait = Duration.between(train.getExpectedArrival(), earliest).toMinutes();
                results.add(AllocationResult.failure(train, "all compatible platforms are busy. "
                        + "The earliest free slot is " + earliest.toLocalTime() + " (a " + wouldWait
                        + " min wait), but the maximum wait is " + maxWaitMinutes + " min.", true));
            } else {
                // Step 3: still hopeful; remember the earliest start among the waiting trains.
                stillWaiting.add(train);
                earliestOverall = earlierOf(earliestOverall, earliest);
            }
        }
        readyQueue.clear();
        readyQueue.addAll(stillWaiting);
        return earliestOverall;
    }

    /** Earliest clash-free start for the train on any compatible platform, at or after notBefore. */
    private LocalDateTime earliestPossibleStart(Train train, LocalDateTime notBefore) {
        LocalDateTime best = null;
        for (Platform platform : station.getPlatforms()) {
            if (train.canUsePlatform(platform)) {
                best = earlierOf(best, schedule.earliestFreeStart(train, platform, notBefore));
            }
        }
        return best;
    }

    /** @return arrival of the next train not yet in the queue, or null if none */
    private static LocalDateTime nextArrival(List<Train> notArrived) {
        return notArrived.isEmpty() ? null : notArrived.get(0).getExpectedArrival();
    }

    /** @return the earlier of two times, treating null as "no time" */
    private static LocalDateTime earlierOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }
}
