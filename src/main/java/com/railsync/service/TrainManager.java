package com.railsync.service;

import com.railsync.exception.InvalidTrainException;
import com.railsync.model.Schedule;
import com.railsync.model.Train;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The register of all trains: add, find, list and remove (Module A).
 *
 * <p>Rules it enforces: train ids and numbers are unique, and a train that
 * holds a platform cannot be removed (release it first).</p>
 *
 * <p>OOP concepts: Encapsulation (private collections, read-only views),
 * SOLID-S (keeps the train list; it does not allocate or format).</p>
 *
 * <p>Collections:</p>
 * <ul>
 *   <li>ArrayList - all trains in the order they were added (table order).</li>
 *   <li>HashMap (train number to train) - "search by number" in one step.</li>
 * </ul>
 *
 * <p>Works with: Train, Schedule (to check for active bookings),
 * SchedulingService (its only caller in the app).</p>
 */
public class TrainManager {

    private final List<Train> trains = new ArrayList<>();
    private final Map<String, Train> trainsByNumber = new HashMap<>();
    private final Schedule schedule;
    private int nextIdNumber = 1;

    /**
     * Creates an empty register.
     *
     * @param schedule the schedule used to check for active bookings
     */
    public TrainManager(Schedule schedule) {
        this.schedule = schedule;
    }

    /**
     * Adds a train.
     *
     * @param train the train to add
     * @throws InvalidTrainException if its id or number is already used
     */
    public void addTrain(Train train) {
        if (findById(train.getId()) != null) {
            throw new InvalidTrainException("A train with id " + train.getId() + " already exists.");
        }
        if (trainsByNumber.containsKey(train.getNumber())) {
            throw new InvalidTrainException("Train number " + train.getNumber() + " already exists.");
        }
        trains.add(train);
        trainsByNumber.put(train.getNumber(), train);
    }

    /**
     * Removes a train that holds no platform.
     *
     * @param train the train to remove
     * @throws InvalidTrainException if the train still holds a platform
     */
    public void removeTrain(Train train) {
        if (schedule.findReservation(train) != null) {
            throw new InvalidTrainException("Train " + train.getNumber()
                    + " is allocated to a platform. Release it before removing it.");
        }
        trains.remove(train);
        trainsByNumber.remove(train.getNumber());
    }

    /** Removes every train (used when a new scenario is loaded). */
    public void clear() {
        trains.clear();
        trainsByNumber.clear();
        nextIdNumber = 1;
    }

    /**
     * Finds a train by its public number.
     *
     * @param number e.g. "12951" (spaces around it are ignored)
     * @return the train, or null if there is none
     */
    public Train findByNumber(String number) {
        if (number == null) {
            return null;
        }
        return trainsByNumber.get(number.trim());
    }

    /**
     * Finds a train by its internal id.
     *
     * @param id e.g. "T3"
     * @return the train, or null if there is none
     */
    public Train findById(String id) {
        for (Train train : trains) {
            if (train.getId().equals(id)) {
                return train;
            }
        }
        return null;
    }

    /** @return all trains in the order they were added, read-only */
    public List<Train> getAllTrains() {
        return Collections.unmodifiableList(trains);
    }

    /** @return a new list of the trains that hold no platform */
    public List<Train> getTrainsWithoutPlatform() {
        List<Train> result = new ArrayList<>();
        for (Train train : trains) {
            if (schedule.findReservation(train) == null) {
                result.add(train);
            }
        }
        return result;
    }

    /**
     * Suggests the next free internal id ("T1", "T2", ...).
     *
     * @return an id that no train uses yet
     */
    public String nextTrainId() {
        String candidate = "T" + nextIdNumber;
        while (findById(candidate) != null) {
            nextIdNumber++;
            candidate = "T" + nextIdNumber;
        }
        return candidate;
    }
}
