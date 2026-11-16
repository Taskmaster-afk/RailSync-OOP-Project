package com.railsync.model;

import java.time.LocalDateTime;

/**
 * A goods train: lowest priority, many short wagons with two locomotives,
 * long loading stops, and slow to clear the platform afterwards.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Inheritance: extends Train.</li>
 *   <li>Polymorphism: overrides getPriority(), getRequiredPlatformLength(),
 *       getMinimumDwellMinutes() AND the concrete method
 *       getClearanceBufferMinutes(), which the other two subclasses inherit
 *       unchanged.</li>
 * </ul>
 *
 * <p>Works with: Train (parent), Platform (compatibility check),
 * Schedule (uses the larger buffer when checking overlaps).</p>
 */
public class FreightTrain extends Train {

    /** Freight trains get no priority bonus. */
    public static final int PRIORITY_BONUS = 0;
    /** One goods wagon in metres. */
    public static final int WAGON_LENGTH_METERS = 15;
    /** Heavy freight is hauled by two locomotives. */
    public static final int LOCOMOTIVE_COUNT = 2;
    /** Length of one locomotive in metres. */
    public static final int LOCOMOTIVE_LENGTH_METERS = 20;
    /** Loading and unloading goods takes time. */
    public static final int MIN_DWELL_MINUTES = 20;
    /** A long, slow freight train needs extra minutes to clear the platform. */
    public static final int EXTRA_CLEARANCE_MINUTES = 5;

    /**
     * Creates a freight train. All checks are done by the Train constructor.
     *
     * @param id                 unique internal id
     * @param number             public train number
     * @param name               display name
     * @param scheduledArrival   timetable arrival
     * @param scheduledDeparture timetable departure
     * @param basePriority       1 to 5
     * @param wagonCount         number of wagons
     */
    public FreightTrain(String id, String number, String name,
                        LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                        int basePriority, int wagonCount) {
        super(id, number, name, scheduledArrival, scheduledDeparture, basePriority, wagonCount);
    }

    /** @return {@link TrainType#FREIGHT} */
    @Override
    public TrainType getType() {
        return TrainType.FREIGHT;
    }

    /** @return base priority (no bonus) */
    @Override
    public int getPriority() {
        return getBasePriority() + PRIORITY_BONUS;
    }

    /**
     * Wagons plus two locomotives.
     *
     * @return wagons x 15 m + 2 x 20 m
     */
    @Override
    public int getRequiredPlatformLength() {
        return getCarriageCount() * WAGON_LENGTH_METERS
                + LOCOMOTIVE_COUNT * LOCOMOTIVE_LENGTH_METERS;
    }

    /** @return 20 minutes */
    @Override
    public int getMinimumDwellMinutes() {
        return MIN_DWELL_MINUTES;
    }

    /**
     * Freight trains need the station buffer plus 5 extra minutes.
     *
     * @param baseBufferMinutes the station-wide buffer
     * @return base buffer + 5
     */
    @Override
    public int getClearanceBufferMinutes(int baseBufferMinutes) {
        // [POLYMORPHISM] overrides a concrete parent method, not just an abstract one.
        return baseBufferMinutes + EXTRA_CLEARANCE_MINUTES;
    }
}
