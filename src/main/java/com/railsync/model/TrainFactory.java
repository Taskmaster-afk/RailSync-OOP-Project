package com.railsync.model;

import com.railsync.exception.InvalidTrainException;

import java.time.LocalDateTime;

/**
 * Creates the right Train subclass from a TrainType.
 *
 * <p>Why it exists (Factory pattern): three places need to turn "the user
 * picked EXPRESS in a drop-down" into an object: the Train form in the GUI,
 * the demo-scenario loader and the random scenario generator. Without this
 * class each of them would repeat the same switch statement. Now the switch
 * lives in exactly one place, and callers work only with the abstract type
 * Train.</p>
 *
 * <p>OOP concepts: Factory pattern, Abstraction (callers get a Train, not a
 * concrete subclass), SOLID-O for callers (they never change when a train
 * kind is added; only this class does).</p>
 *
 * <p>Works with: Train, ExpressTrain, PassengerTrain, FreightTrain, TrainType.</p>
 */
public final class TrainFactory {

    private TrainFactory() {
        // Only static methods; no objects needed.
    }

    /**
     * Creates a train of the requested kind. All validation happens in the
     * Train constructor.
     *
     * @param type               which kind of train
     * @param id                 unique internal id
     * @param number             public train number
     * @param name               display name
     * @param scheduledArrival   timetable arrival
     * @param scheduledDeparture timetable departure
     * @param basePriority       1 to 5
     * @param carriageCount      coaches or wagons
     * @return a new ExpressTrain, PassengerTrain or FreightTrain
     * @throws InvalidTrainException if the type is missing or any value is invalid
     */
    public static Train create(TrainType type, String id, String number, String name,
                               LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                               int basePriority, int carriageCount) {
        if (type == null) {
            throw new InvalidTrainException("Train type is required.");
        }
        switch (type) {
            case EXPRESS:
                return new ExpressTrain(id, number, name, scheduledArrival, scheduledDeparture, basePriority, carriageCount);
            case PASSENGER:
                return new PassengerTrain(id, number, name, scheduledArrival, scheduledDeparture, basePriority, carriageCount);
            default:
                return new FreightTrain(id, number, name, scheduledArrival, scheduledDeparture, basePriority, carriageCount);
        }
    }
}
