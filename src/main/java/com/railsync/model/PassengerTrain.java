package com.railsync.model;

import java.time.LocalDateTime;

/**
 * A local or suburban passenger train (an EMU/MEMU set): medium priority,
 * shorter self-propelled coaches with no separate engine.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Inheritance: extends Train.</li>
 *   <li>Polymorphism: overrides getPriority(), getRequiredPlatformLength()
 *       and getMinimumDwellMinutes(). It keeps the inherited default
 *       getClearanceBufferMinutes().</li>
 * </ul>
 *
 * <p>Works with: Train (parent), Platform (compatibility check).</p>
 */
public class PassengerTrain extends Train {

    /** Small bonus: above freight, below express with the same base priority. */
    public static final int PRIORITY_BONUS = 1;
    /** Suburban coaches are shorter than long-distance coaches. */
    public static final int COACH_LENGTH_METERS = 20;
    /** Passengers need a little time to get on and off. */
    public static final int MIN_DWELL_MINUTES = 3;

    /**
     * Creates a passenger train. All checks are done by the Train constructor.
     *
     * @param id                 unique internal id
     * @param number             public train number
     * @param name               display name
     * @param scheduledArrival   timetable arrival
     * @param scheduledDeparture timetable departure
     * @param basePriority       1 to 5
     * @param coachCount         number of coaches
     */
    public PassengerTrain(String id, String number, String name,
                          LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                          int basePriority, int coachCount) {
        super(id, number, name, scheduledArrival, scheduledDeparture, basePriority, coachCount);
    }

    /** @return {@link TrainType#PASSENGER} */
    @Override
    public TrainType getType() {
        return TrainType.PASSENGER;
    }

    /** @return base priority + 1 */
    @Override
    public int getPriority() {
        return getBasePriority() + PRIORITY_BONUS;
    }

    /**
     * No separate locomotive, so only the coaches count.
     *
     * @return coaches x 20 m
     */
    @Override
    public int getRequiredPlatformLength() {
        return getCarriageCount() * COACH_LENGTH_METERS;
    }

    /** @return 3 minutes */
    @Override
    public int getMinimumDwellMinutes() {
        return MIN_DWELL_MINUTES;
    }
}
