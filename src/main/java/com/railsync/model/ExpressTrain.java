package com.railsync.model;

import java.time.LocalDateTime;

/**
 * A long-distance express train: high priority, long coaches plus an engine,
 * and only a short stop at the station.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Inheritance: extends Train and reuses all its data and validation.</li>
 *   <li>Polymorphism: overrides getPriority(), getRequiredPlatformLength()
 *       and getMinimumDwellMinutes() with express-specific rules.</li>
 * </ul>
 *
 * <p>Works with: Train (parent), Platform (compatibility check).</p>
 */
public class ExpressTrain extends Train {

    /** Express trains rank above other trains with the same base priority. */
    public static final int PRIORITY_BONUS = 3;
    /** Length of one long-distance coach in metres. */
    public static final int COACH_LENGTH_METERS = 25;
    /** Express trains are hauled by one separate locomotive. */
    public static final int ENGINE_LENGTH_METERS = 20;
    /** Express trains make short stops. */
    public static final int MIN_DWELL_MINUTES = 5;

    /**
     * Creates an express train. All checks are done by the Train constructor.
     *
     * @param id                 unique internal id
     * @param number             public train number
     * @param name               display name
     * @param scheduledArrival   timetable arrival
     * @param scheduledDeparture timetable departure
     * @param basePriority       1 to 5
     * @param coachCount         number of coaches
     */
    public ExpressTrain(String id, String number, String name,
                        LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                        int basePriority, int coachCount) {
        // [INHERITANCE] constructor chaining: the parent validates everything.
        super(id, number, name, scheduledArrival, scheduledDeparture, basePriority, coachCount);
    }

    /** @return {@link TrainType#EXPRESS} */
    @Override
    public TrainType getType() {
        return TrainType.EXPRESS;
    }

    /**
     * Express trains get the biggest bonus so the Priority strategy serves
     * them first.
     *
     * @return base priority + 3
     */
    @Override
    public int getPriority() {
        // [INHERITANCE] ExpressTrain overrides this to rank itself higher.
        return getBasePriority() + PRIORITY_BONUS;
    }

    /**
     * Coaches plus one locomotive.
     *
     * @return coaches x 25 m + 20 m
     */
    @Override
    public int getRequiredPlatformLength() {
        // [INHERITANCE] ExpressTrain overrides this to require a longer platform.
        return getCarriageCount() * COACH_LENGTH_METERS + ENGINE_LENGTH_METERS;
    }

    /** @return 5 minutes */
    @Override
    public int getMinimumDwellMinutes() {
        return MIN_DWELL_MINUTES;
    }
}
