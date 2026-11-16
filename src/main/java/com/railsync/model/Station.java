package com.railsync.model;

import com.railsync.exception.InvalidPlatformException;
import com.railsync.exception.RailSyncException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A railway station: a name and the platforms it has.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Aggregation: the station <i>holds</i> platforms, but the platforms
 *       are created outside and passed in, and they can exist on their own.
 *       Removing a platform from the station does not destroy it.</li>
 *   <li>Encapsulation: the platform list is private; outside code gets a
 *       read-only view and must use addPlatform()/removePlatform().</li>
 * </ul>
 *
 * <p>Collections: an ArrayList keeps the platforms in ascending platform
 * number. Strategies try platforms in this order, so First-Fit is
 * deterministic and easy to trace by hand. A station has only a handful of
 * platforms, so a linear search by id is fast enough and simple to read.</p>
 *
 * <p>Works with: Platform, PlatformManager (later stage).</p>
 */
public class Station {

    private final String name;

    // [AGGREGATION] Station holds platforms but does not own their lifecycle.
    private final List<Platform> platforms = new ArrayList<>();

    /**
     * Creates an empty station.
     *
     * @param name station name, not blank
     * @throws RailSyncException if the name is blank
     */
    public Station(String name) {
        if (name == null || name.isBlank()) {
            throw new RailSyncException("Station name must not be empty.");
        }
        this.name = name.trim();
    }

    /**
     * Adds a platform, keeping the list sorted by platform number.
     *
     * @param platform the platform to add
     * @throws InvalidPlatformException if its id or number is already used
     */
    public void addPlatform(Platform platform) {
        // Step 1: refuse duplicates, so ids and numbers stay unique.
        for (Platform existing : platforms) {
            if (existing.getId().equals(platform.getId())) {
                throw new InvalidPlatformException("A platform with id " + platform.getId() + " already exists.");
            }
            if (existing.getNumber() == platform.getNumber()) {
                throw new InvalidPlatformException("Platform number " + platform.getNumber() + " already exists.");
            }
        }
        // Step 2: find the first platform with a bigger number and insert before it.
        // Keeping the list sorted here means no one else ever has to sort it.
        int insertAt = platforms.size();
        for (int i = 0; i < platforms.size(); i++) {
            if (platforms.get(i).getNumber() > platform.getNumber()) {
                insertAt = i;
                break;
            }
        }
        platforms.add(insertAt, platform);
    }

    /**
     * Removes a platform from the station. The Platform object itself still
     * exists (aggregation). Checking for active reservations is the job of
     * PlatformManager, which knows the schedule.
     *
     * @param platform the platform to remove
     * @return true if it was in the station
     */
    public boolean removePlatform(Platform platform) {
        return platforms.remove(platform);
    }

    /**
     * Finds a platform by its internal id.
     *
     * @param id platform id
     * @return the platform, or null if not found (an ordinary result, not an error)
     */
    public Platform findPlatformById(String id) {
        for (Platform platform : platforms) {
            if (platform.getId().equals(id)) {
                return platform;
            }
        }
        return null;
    }

    /**
     * Returns all platforms in ascending number order, read-only.
     *
     * @return unmodifiable list of platforms
     */
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    /** @return number of platforms */
    public int getPlatformCount() {
        return platforms.size();
    }

    /** @return station name */
    public String getName() {
        return name;
    }
}
