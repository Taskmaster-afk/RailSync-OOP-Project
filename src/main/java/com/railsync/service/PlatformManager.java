package com.railsync.service;

import com.railsync.exception.InvalidPlatformException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainType;

import java.util.List;
import java.util.Set;

/**
 * Manages the station's platforms: add, update, remove, repair (Module B).
 *
 * <p>Rules it enforces: a platform with bookings cannot be removed, and an
 * update may not make an existing booking invalid (for example, shortening
 * a platform below the length of a train already booked on it).</p>
 *
 * <p>OOP concepts: Encapsulation, SOLID-S (platform rules only). It works
 * through Station (aggregation) and asks Schedule about bookings.</p>
 *
 * <p>Works with: Station, Platform, Schedule, SchedulingService.</p>
 */
public class PlatformManager {

    private final Station station;
    private final Schedule schedule;

    /**
     * Creates a manager for one station.
     *
     * @param station  the station whose platforms are managed
     * @param schedule used to check for bookings
     */
    public PlatformManager(Station station, Schedule schedule) {
        this.station = station;
        this.schedule = schedule;
    }

    /**
     * Adds a platform to the station.
     *
     * @param platform the new platform
     * @throws InvalidPlatformException if its id or number is already used
     */
    public void addPlatform(Platform platform) {
        station.addPlatform(platform);
    }

    /**
     * Removes a platform that has no bookings.
     *
     * @param platform the platform to remove
     * @throws InvalidPlatformException if trains are still booked on it
     */
    public void removePlatform(Platform platform) {
        if (schedule.hasReservations(platform)) {
            throw new InvalidPlatformException("Platform " + platform.getNumber()
                    + " still has trains booked. Move or release them first.");
        }
        station.removePlatform(platform);
    }

    /**
     * Changes a platform's length and accepted categories, but only if every
     * train already booked on it would still fit.
     *
     * @param platform  the platform
     * @param newLength new length in metres
     * @param newTypes  new accepted categories
     * @throws InvalidPlatformException if a booked train would no longer fit,
     *                                  or the new values are invalid
     */
    public void updatePlatform(Platform platform, int newLength, Set<TrainType> newTypes) {
        // Step 1: check every booked train against the NEW values before changing anything.
        for (PlatformReservation booking : schedule.getReservationsOn(platform)) {
            Train train = booking.getTrain();
            if (newTypes == null || !newTypes.contains(train.getType())) {
                throw new InvalidPlatformException("Train " + train.getNumber() + " (" + train.getType()
                        + ") is booked on platform " + platform.getNumber() + ", so that category must stay allowed.");
            }
            if (train.getRequiredPlatformLength() > newLength) {
                throw new InvalidPlatformException("Train " + train.getNumber() + " needs "
                        + train.getRequiredPlatformLength() + " m and is booked on platform "
                        + platform.getNumber() + ", so it cannot be shortened to " + newLength + " m.");
            }
        }
        // Step 2: safe, so apply (the setters still validate the values themselves).
        platform.setLengthMeters(newLength);
        platform.setSupportedTypes(newTypes);
    }

    /**
     * Marks a failed platform as working again.
     *
     * @param platform the platform
     */
    public void repairPlatform(Platform platform) {
        platform.markOperational();
    }

    /**
     * Finds a platform by id.
     *
     * @param id e.g. "P2"
     * @return the platform, or null
     */
    public Platform findById(String id) {
        return station.findPlatformById(id);
    }

    /** @return all platforms in number order, read-only */
    public List<Platform> getPlatforms() {
        return station.getPlatforms();
    }

    /** Removes every platform (used when a new scenario is loaded; bookings must be cleared first). */
    public void clear() {
        while (station.getPlatformCount() > 0) {
            station.removePlatform(station.getPlatforms().get(0));
        }
    }

    /**
     * Suggests the next free platform id ("P1", "P2", ...).
     *
     * @return an id no platform uses yet
     */
    public String nextPlatformId() {
        int number = 1;
        while (station.findPlatformById("P" + number) != null) {
            number++;
        }
        return "P" + number;
    }

    /**
     * Suggests the next free platform number.
     *
     * @return one more than the highest number in use (1 if none)
     */
    public int nextPlatformNumber() {
        int highest = 0;
        for (Platform platform : station.getPlatforms()) {
            if (platform.getNumber() > highest) {
                highest = platform.getNumber();
            }
        }
        return highest + 1;
    }
}
