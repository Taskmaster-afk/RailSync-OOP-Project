package com.railsync.model;

import com.railsync.exception.InvalidPlatformException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A platform (track + passenger edge) at the station where one train at a
 * time can stand.
 *
 * <p>OS analogy: a platform is like a <b>CPU</b>. Only one train (process)
 * can use it at a time, and the allocator decides who gets it.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Encapsulation: private fields, validated setters, and a defensive
 *       copy of the supported-category set so no caller can change it
 *       behind the platform's back.</li>
 *   <li>Constructor chaining: the short constructor calls the full one with
 *       this(...).</li>
 *   <li>Aggregation: a Station holds platforms, but a Platform can exist
 *       without a Station (for example in tests or a copied scenario).</li>
 * </ul>
 *
 * <p>Collections: a HashSet stores the supported categories because the
 * only question ever asked is "does it contain X?", order does not matter,
 * and duplicates make no sense.</p>
 *
 * <p>Works with: Station, Train.getIncompatibilityReason(), Schedule.</p>
 */
public class Platform {

    /** Longest platform we accept, to catch typing mistakes. */
    public static final int MAX_LENGTH_METERS = 1500;

    private final String id;
    private final int number;
    private int lengthMeters;
    private PlatformStatus status;
    private final Set<TrainType> supportedTypes = new HashSet<>();

    /**
     * Creates an operational platform. Most platforms start working, so this
     * shorter constructor is the one normally used.
     *
     * @param id             unique internal id, e.g. "P1"
     * @param number         platform number shown to passengers (positive)
     * @param lengthMeters   usable length in metres
     * @param supportedTypes train categories this platform accepts (at least one)
     * @throws InvalidPlatformException if any value is invalid
     */
    public Platform(String id, int number, int lengthMeters, Set<TrainType> supportedTypes) {
        // Constructor chaining: reuse the full constructor instead of repeating checks.
        this(id, number, lengthMeters, supportedTypes, PlatformStatus.OPERATIONAL);
    }

    /**
     * Creates a platform with an explicit starting status.
     *
     * @param id             unique internal id
     * @param number         platform number (positive)
     * @param lengthMeters   usable length in metres
     * @param supportedTypes accepted train categories (at least one)
     * @param status         starting status
     * @throws InvalidPlatformException if any value is invalid
     */
    public Platform(String id, int number, int lengthMeters,
                    Set<TrainType> supportedTypes, PlatformStatus status) {
        if (id == null || id.isBlank()) {
            throw new InvalidPlatformException("Platform id must not be empty.");
        }
        if (number <= 0) {
            throw new InvalidPlatformException("Platform number must be positive, but was " + number + ".");
        }
        if (status == null) {
            throw new InvalidPlatformException("Platform status is required.");
        }
        this.id = id.trim();
        this.number = number;
        this.status = status;
        setLengthMeters(lengthMeters);
        setSupportedTypes(supportedTypes);
    }

    /**
     * Changes the usable length.
     *
     * @param newLengthMeters length from 1 to 1500 metres
     * @throws InvalidPlatformException if the length is out of range
     */
    public final void setLengthMeters(int newLengthMeters) {
        if (newLengthMeters <= 0 || newLengthMeters > MAX_LENGTH_METERS) {
            throw new InvalidPlatformException("Platform length must be between 1 and "
                    + MAX_LENGTH_METERS + " m, but was " + newLengthMeters + ".");
        }
        this.lengthMeters = newLengthMeters;
    }

    /**
     * Replaces the set of accepted train categories.
     *
     * @param newTypes at least one category
     * @throws InvalidPlatformException if the set is null or empty
     */
    public final void setSupportedTypes(Set<TrainType> newTypes) {
        if (newTypes == null || newTypes.isEmpty()) {
            throw new InvalidPlatformException("A platform must support at least one train category.");
        }
        // [ENCAPSULATION] copy the values in, so later changes to the caller's
        // set cannot secretly change this platform.
        supportedTypes.clear();
        supportedTypes.addAll(newTypes);
    }

    /**
     * Checks whether this platform accepts a category of train.
     *
     * @param type the category to check
     * @return true if accepted
     */
    public boolean supports(TrainType type) {
        return supportedTypes.contains(type);
    }

    /** @return true if the platform is working */
    public boolean isOperational() {
        return status == PlatformStatus.OPERATIONAL;
    }

    /** Marks the platform as broken down. Reassigning its trains is done by the services. */
    public void markFailed() {
        status = PlatformStatus.FAILED;
    }

    /** Marks the platform as repaired and working again. */
    public void markOperational() {
        status = PlatformStatus.OPERATIONAL;
    }

    /** @return unique internal id */
    public String getId() {
        return id;
    }

    /** @return platform number shown to passengers */
    public int getNumber() {
        return number;
    }

    /** @return usable length in metres */
    public int getLengthMeters() {
        return lengthMeters;
    }

    /** @return current status */
    public PlatformStatus getStatus() {
        return status;
    }

    /**
     * Returns the accepted categories as a read-only view.
     *
     * @return unmodifiable set of categories
     */
    public Set<TrainType> getSupportedTypes() {
        // [ENCAPSULATION] callers can read the set but not change it.
        return Collections.unmodifiableSet(supportedTypes);
    }

    /** @return short label such as "PF-3 (600 m)" */
    @Override
    public String toString() {
        return "PF-" + number + " (" + lengthMeters + " m)";
    }
}
