package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.Train;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A complete, self-contained picture of the live session: platforms, trains,
 * bookings, which trains were given up, and the settings. It is what the
 * database stores and what the service restores at start-up.
 *
 * <p>Why it exists: the service must not know HOW it is stored (SQLite today,
 * maybe something else later), and the storage code must not reach into the
 * service's private fields. This plain object is the contract between the two.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>[ENCAPSULATION] immutable: every list is copied in and handed out read-only.</li>
 *   <li>[SOLID-D] SchedulingService and the persistence layer both depend on this
 *       simple data object instead of on each other's internals.</li>
 * </ul>
 *
 * <p>Works with: SchedulingService.takeSnapshot() / restore(), and
 * persistence.SqliteStationRepository.</p>
 */
public final class StationSnapshot {

    private final List<Platform> platforms;
    private final List<Train> trains;
    private final List<Booking> bookings;
    private final Set<String> unallocatedTrainIds;
    private final Settings settings;

    /**
     * Creates a snapshot (the lists are copied).
     *
     * @param platforms           every platform
     * @param trains              every train (delays included)
     * @param bookings            every booking, by train and platform id
     * @param unallocatedTrainIds ids of trains that were given up (status UNALLOCATED)
     * @param settings            strategy and allocation settings
     */
    public StationSnapshot(List<Platform> platforms, List<Train> trains, List<Booking> bookings,
                           Set<String> unallocatedTrainIds, Settings settings) {
        this.platforms = new ArrayList<>(platforms);
        this.trains = new ArrayList<>(trains);
        this.bookings = new ArrayList<>(bookings);
        this.unallocatedTrainIds = new HashSet<>(unallocatedTrainIds);
        this.settings = settings;
    }

    /** @return the platforms, read-only */
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    /** @return the trains, read-only */
    public List<Train> getTrains() {
        return Collections.unmodifiableList(trains);
    }

    /** @return the bookings, read-only */
    public List<Booking> getBookings() {
        return Collections.unmodifiableList(bookings);
    }

    /** @return ids of trains marked UNALLOCATED, read-only */
    public Set<String> getUnallocatedTrainIds() {
        return Collections.unmodifiableSet(unallocatedTrainIds);
    }

    /** @return the settings */
    public Settings getSettings() {
        return settings;
    }

    /**
     * One saved booking: which train stands at which platform from when.
     * The end is not stored, because the train's own times decide it.
     */
    public static final class Booking {
        private final String trainId;
        private final String platformId;
        private final LocalDateTime start;

        /**
         * @param trainId    the train's id
         * @param platformId the platform's id
         * @param start      when the train takes the platform
         */
        public Booking(String trainId, String platformId, LocalDateTime start) {
            this.trainId = trainId;
            this.platformId = platformId;
            this.start = start;
        }

        /** @return the train's id */
        public String getTrainId() {
            return trainId;
        }

        /** @return the platform's id */
        public String getPlatformId() {
            return platformId;
        }

        /** @return when the booking starts */
        public LocalDateTime getStart() {
            return start;
        }
    }

    /** The allocation settings in force when the snapshot was taken. */
    public static final class Settings {
        private final String strategyName;
        private final int agingMinutesPerPoint;
        private final int clearanceBufferMinutes;
        private final int maxWaitMinutes;
        private final boolean preemptionEnabled;
        private final int conflictsDetected;

        /**
         * @param strategyName           the strategy's display name, e.g. "FCFS (First-Come-First-Served)"
         * @param agingMinutesPerPoint   aging rate (used only by the aging strategy)
         * @param clearanceBufferMinutes station clearance buffer
         * @param maxWaitMinutes         maximum wait at the outer signal
         * @param preemptionEnabled      whether safe preemption is on
         * @param conflictsDetected      conflicts counted so far
         */
        public Settings(String strategyName, int agingMinutesPerPoint, int clearanceBufferMinutes,
                        int maxWaitMinutes, boolean preemptionEnabled, int conflictsDetected) {
            this.strategyName = strategyName;
            this.agingMinutesPerPoint = agingMinutesPerPoint;
            this.clearanceBufferMinutes = clearanceBufferMinutes;
            this.maxWaitMinutes = maxWaitMinutes;
            this.preemptionEnabled = preemptionEnabled;
            this.conflictsDetected = conflictsDetected;
        }

        /** @return the strategy's display name */
        public String getStrategyName() {
            return strategyName;
        }

        /** @return the aging rate in minutes per +1 priority */
        public int getAgingMinutesPerPoint() {
            return agingMinutesPerPoint;
        }

        /** @return the clearance buffer in minutes */
        public int getClearanceBufferMinutes() {
            return clearanceBufferMinutes;
        }

        /** @return the maximum wait in minutes */
        public int getMaxWaitMinutes() {
            return maxWaitMinutes;
        }

        /** @return true if preemption is enabled */
        public boolean isPreemptionEnabled() {
            return preemptionEnabled;
        }

        /** @return conflicts counted so far */
        public int getConflictsDetected() {
            return conflictsDetected;
        }
    }
}
