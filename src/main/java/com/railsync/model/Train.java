package com.railsync.model;

import com.railsync.exception.InvalidTrainException;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A train that wants to use a platform at the station.
 *
 * <p>Why it exists: every train shares the same data (number, timetable,
 * priority, assigned platform), but different kinds of train behave
 * differently (priority bonus, platform length needed, minimum stop time,
 * clearance needed). So the shared part lives here and the different part
 * is left to the subclasses.</p>
 *
 * <p>OS analogy: a train is like a <b>process</b>. Its stay at the platform
 * (departure minus arrival) is its <b>burst time</b>, and a platform is the
 * <b>CPU</b> it needs.</p>
 *
 * <p>OOP concepts shown:</p>
 * <ul>
 *   <li>Abstraction: abstract class, cannot be created directly.</li>
 *   <li>Encapsulation: all fields private; times change only through
 *       validated methods updateTimes() and applyDelay().</li>
 *   <li>Inheritance: ExpressTrain, PassengerTrain, FreightTrain extend it.</li>
 *   <li>Polymorphism: canUsePlatform() calls abstract methods whose answer
 *       depends on the real subclass at runtime.</li>
 *   <li>Association: a train references the Platform it is assigned to.</li>
 * </ul>
 *
 * <p>Works with: Platform, Schedule (which assigns platforms),
 * PlatformReservation.</p>
 */
public abstract class Train {

    /** Lowest allowed base priority. */
    public static final int MIN_BASE_PRIORITY = 1;
    /** Highest allowed base priority. */
    public static final int MAX_BASE_PRIORITY = 5;
    /** Upper limit for coaches or wagons, to catch typing mistakes. */
    public static final int MAX_CARRIAGES = 60;
    /** A single delay may not be longer than one day. */
    public static final int MAX_DELAY_MINUTES = 24 * 60;

    // [ENCAPSULATION] every field is private; outside code must use methods.
    private final String id;
    private final String number;
    private String name;
    private LocalDateTime scheduledArrival;
    private LocalDateTime scheduledDeparture;
    private int basePriority;
    private final int carriageCount;
    private int delayMinutes;
    private TrainStatus status;

    // [ASSOCIATION] the train knows the platform it uses, but does not own it.
    // Null means "no platform assigned yet".
    private Platform assignedPlatform;

    /**
     * Creates a train after checking every value.
     *
     * @param id               unique internal id, e.g. "T1"
     * @param number           public train number, e.g. "12951"
     * @param name             display name, e.g. "Rajdhani Express"
     * @param scheduledArrival timetable arrival time at the platform
     * @param scheduledDeparture timetable departure time from the platform
     * @param basePriority     1 (lowest) to 5 (highest), set by the user
     * @param carriageCount    number of coaches or wagons (1 to 60)
     * @throws InvalidTrainException if any value is missing or out of range
     */
    protected Train(String id, String number, String name,
                    LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                    int basePriority, int carriageCount) {
        // Step 1: check the simple text and number fields first.
        this.id = requireText(id, "Train id");
        this.number = requireText(number, "Train number");
        this.name = requireText(name, "Train name");
        if (carriageCount < 1 || carriageCount > MAX_CARRIAGES) {
            throw new InvalidTrainException("Carriage count must be between 1 and "
                    + MAX_CARRIAGES + ", but was " + carriageCount + ".");
        }
        this.carriageCount = carriageCount;
        setBasePriority(basePriority);

        // Step 2: check the timetable. This calls getMinimumDwellMinutes(),
        // an overridable method, from the constructor. That is safe here only
        // because every subclass returns a constant and never reads its own
        // fields (they would not be set yet while the parent constructor runs).
        validateTimes(scheduledArrival, scheduledDeparture);
        this.scheduledArrival = scheduledArrival;
        this.scheduledDeparture = scheduledDeparture;

        // Step 3: a new train starts with no delay and no platform.
        this.delayMinutes = 0;
        this.status = TrainStatus.SCHEDULED;
        this.assignedPlatform = null;
    }

    // ------------------------------------------------------------------
    // Abstract methods: each subclass MUST answer these differently.
    // [ABSTRACTION] Train says WHAT every train can answer; each subclass says HOW.
    // ------------------------------------------------------------------

    /**
     * Returns the category label used for platform compatibility.
     *
     * @return EXPRESS, PASSENGER or FREIGHT
     */
    public abstract TrainType getType();

    /**
     * Returns the effective priority used by the Priority strategy.
     * Higher number means more important. It is the base priority plus a
     * bonus that depends on the kind of train.
     *
     * @return effective priority
     */
    public abstract int getPriority();

    /**
     * Returns how long (in metres) a platform must be for this train to fit.
     *
     * @return required platform length in metres
     */
    public abstract int getRequiredPlatformLength();

    /**
     * Returns the shortest stop this kind of train is allowed to make.
     *
     * @return minimum dwell time in minutes
     */
    public abstract int getMinimumDwellMinutes();

    // ------------------------------------------------------------------
    // Concrete methods with a default that a subclass MAY override.
    // ------------------------------------------------------------------

    /**
     * Returns the gap (in minutes) that must be kept free on the platform
     * before and after this train. Most trains just use the station-wide
     * value; FreightTrain overrides this to ask for more.
     *
     * @param baseBufferMinutes the station-wide clearance buffer from Schedule
     * @return clearance buffer for this train in minutes
     */
    public int getClearanceBufferMinutes(int baseBufferMinutes) {
        return baseBufferMinutes;
    }

    /**
     * Explains why this train cannot use the given platform.
     * This one method holds the compatibility rules so they are never
     * duplicated anywhere else.
     *
     * @param platform the platform to check
     * @return a plain-English reason, or null if the train CAN use it
     */
    public String getIncompatibilityReason(Platform platform) {
        // Step 1: a failed platform cannot accept any train.
        if (!platform.isOperational()) {
            return "Platform " + platform.getNumber() + " is not operational.";
        }
        // Step 2: the platform must accept this category of train.
        // [POLYMORPHISM] getType() is answered by the real subclass at runtime.
        if (!platform.supports(getType())) {
            return "Platform " + platform.getNumber() + " does not accept "
                    + getType() + " trains.";
        }
        // Step 3: the train must physically fit.
        // [POLYMORPHISM] each subclass computes its own required length.
        int needed = getRequiredPlatformLength();
        if (platform.getLengthMeters() < needed) {
            return "Platform " + platform.getNumber() + " is " + platform.getLengthMeters()
                    + " m long but train " + number + " needs " + needed + " m.";
        }
        return null;
    }

    /**
     * Checks whether this train is allowed to use the platform
     * (operational, right category, long enough). Time conflicts are
     * checked separately by Schedule.
     *
     * @param platform the platform to check
     * @return true if the train can use it
     */
    public boolean canUsePlatform(Platform platform) {
        return getIncompatibilityReason(platform) == null;
    }

    // ------------------------------------------------------------------
    // Validated "setters": the only ways to change the train's state.
    // ------------------------------------------------------------------

    /**
     * Changes the timetable of the train after validating the new times.
     *
     * @param newArrival   new timetable arrival
     * @param newDeparture new timetable departure
     * @throws InvalidTrainException if the times are missing, in the wrong
     *                               order, or the stop is too short
     */
    public void updateTimes(LocalDateTime newArrival, LocalDateTime newDeparture) {
        // [ENCAPSULATION] arrival/departure can only change through this validated method.
        validateTimes(newArrival, newDeparture);
        this.scheduledArrival = newArrival;
        this.scheduledDeparture = newDeparture;
    }

    /**
     * Adds a delay. The whole stay at the platform shifts later by this many
     * minutes; the length of the stay does not change. Delays add up.
     *
     * @param minutes extra delay in minutes (must be positive)
     * @throws InvalidTrainException if minutes is not positive or the total
     *                               delay would exceed one day
     */
    public void applyDelay(int minutes) {
        if (minutes <= 0) {
            throw new InvalidTrainException("Delay must be a positive number of minutes.");
        }
        if (delayMinutes + minutes > MAX_DELAY_MINUTES) {
            throw new InvalidTrainException("Total delay cannot exceed "
                    + MAX_DELAY_MINUTES + " minutes.");
        }
        delayMinutes = delayMinutes + minutes;
    }

    /**
     * Takes back part or all of a delay (used by Undo).
     *
     * @param minutes how many minutes to remove (1 to the current delay)
     * @throws InvalidTrainException if minutes is not positive or larger than the current delay
     */
    public void reduceDelay(int minutes) {
        // [ENCAPSULATION] the delay can only shrink through this validated method, never below zero.
        if (minutes <= 0 || minutes > delayMinutes) {
            throw new InvalidTrainException("Can only remove between 1 and " + delayMinutes + " minutes of delay.");
        }
        delayMinutes = delayMinutes - minutes;
    }

    /**
     * Renames the train.
     *
     * @param newName the new display name (not blank)
     * @throws InvalidTrainException if the name is blank
     */
    public void setName(String newName) {
        this.name = requireText(newName, "Train name");
    }

    /**
     * Changes the base priority chosen by the user. It is final so a
     * subclass cannot weaken the range check (it is also called from the
     * constructor, where overridable methods would be risky).
     *
     * @param newBasePriority value from 1 to 5
     * @throws InvalidTrainException if the value is out of range
     */
    public final void setBasePriority(int newBasePriority) {
        if (newBasePriority < MIN_BASE_PRIORITY || newBasePriority > MAX_BASE_PRIORITY) {
            throw new InvalidTrainException("Base priority must be between "
                    + MIN_BASE_PRIORITY + " and " + MAX_BASE_PRIORITY
                    + ", but was " + newBasePriority + ".");
        }
        this.basePriority = newBasePriority;
    }

    /**
     * Records that the allocator tried and found no safe platform.
     * Only allowed when the train holds no platform, so the status can never
     * contradict the assigned platform.
     */
    public void markUnallocated() {
        if (assignedPlatform == null) {
            status = TrainStatus.UNALLOCATED;
        }
    }

    /**
     * Puts an unallocated train back to "waiting to be processed", for
     * example before re-running the allocator with another strategy.
     * Ignored if the train holds a platform.
     */
    public void markScheduled() {
        if (assignedPlatform == null) {
            status = TrainStatus.SCHEDULED;
        }
    }

    /**
     * Links the train to a platform. Package-private on purpose: only
     * Schedule (same package) may call it, at the moment it creates a
     * reservation, so the two can never disagree.
     */
    void assignPlatform(Platform platform) {
        // [ASSOCIATION] the train now references the platform it is assigned to.
        this.assignedPlatform = platform;
        this.status = TrainStatus.ALLOCATED;
    }

    /**
     * Removes the platform link. Package-private: only Schedule calls it,
     * when it deletes the train's reservation.
     */
    void clearPlatform() {
        this.assignedPlatform = null;
        this.status = TrainStatus.SCHEDULED;
    }

    // ------------------------------------------------------------------
    // Getters and derived values.
    // ------------------------------------------------------------------

    /** @return unique internal id */
    public String getId() {
        return id;
    }

    /** @return public train number */
    public String getNumber() {
        return number;
    }

    /** @return display name */
    public String getName() {
        return name;
    }

    /** @return timetable arrival (without delay) */
    public LocalDateTime getScheduledArrival() {
        return scheduledArrival;
    }

    /** @return timetable departure (without delay) */
    public LocalDateTime getScheduledDeparture() {
        return scheduledDeparture;
    }

    /** @return arrival time including any delay */
    public LocalDateTime getExpectedArrival() {
        return scheduledArrival.plusMinutes(delayMinutes);
    }

    /** @return departure time including any delay */
    public LocalDateTime getExpectedDeparture() {
        return scheduledDeparture.plusMinutes(delayMinutes);
    }

    /**
     * Returns how long the train stays at the platform ("burst time" in the
     * OS analogy). A delay shifts the stay but does not change its length.
     *
     * @return dwell time as a Duration
     */
    public Duration getDwellTime() {
        return Duration.between(scheduledArrival, scheduledDeparture);
    }

    /** @return the user-chosen base priority (1 to 5) */
    public int getBasePriority() {
        return basePriority;
    }

    /** @return number of coaches or wagons */
    public int getCarriageCount() {
        return carriageCount;
    }

    /** @return total delay in minutes (0 if on time) */
    public int getDelayMinutes() {
        return delayMinutes;
    }

    /** @return true if the train has any delay */
    public boolean isDelayed() {
        return delayMinutes > 0;
    }

    /** @return current allocation status */
    public TrainStatus getStatus() {
        return status;
    }

    /** @return the assigned platform, or null if none */
    public Platform getAssignedPlatform() {
        return assignedPlatform;
    }

    /** @return short label such as "EXPRESS 12951 Rajdhani" */
    @Override
    public String toString() {
        return getType() + " " + number + " " + name;
    }

    // ------------------------------------------------------------------
    // Private helpers.
    // ------------------------------------------------------------------

    /**
     * Shared time rules, used by both the constructor and updateTimes()
     * so the rules are written only once.
     */
    private void validateTimes(LocalDateTime arrival, LocalDateTime departure) {
        // Step 1: both times must be given.
        if (arrival == null || departure == null) {
            throw new InvalidTrainException("Arrival and departure times are required.");
        }
        // Step 2: the train must leave after it arrives.
        if (!departure.isAfter(arrival)) {
            throw new InvalidTrainException("Departure must be after arrival.");
        }
        // Step 3: the stop must be at least as long as this kind of train needs.
        // [POLYMORPHISM] the subclass decides the minimum dwell.
        long dwell = Duration.between(arrival, departure).toMinutes();
        if (dwell < getMinimumDwellMinutes()) {
            throw new InvalidTrainException("A " + getType() + " train must stop for at least "
                    + getMinimumDwellMinutes() + " minutes, but this stop is " + dwell + ".");
        }
    }

    /** Returns the trimmed text, or throws if it is null or blank. */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrainException(fieldName + " must not be empty.");
        }
        return value.trim();
    }
}
