package com.railsync.service;

import com.railsync.model.Platform;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot of the performance numbers of one schedule (one strategy run).
 *
 * <p>Why it exists: MetricsCalculator does the maths once and puts the
 * answers here. ReportGenerator and the GUI then only read them, so the
 * formulas are never repeated in display code.</p>
 *
 * <p>OOP concepts: Encapsulation and immutability (all fields final, no
 * setters, read-only map). The constructor is package-private, so only
 * MetricsCalculator (same package) can create one.</p>
 *
 * <p>Collections: a LinkedHashMap (a kind of HashMap) maps each platform to
 * its utilization. It keeps insertion order, which is platform-number order,
 * so reports list platforms neatly.</p>
 *
 * <p>Works with: MetricsCalculator (creates it), ReportGenerator (reads it).</p>
 */
public final class ScheduleMetrics {

    private final int totalTrains;
    private final int allocatedTrains;
    private final int unallocatedTrains;
    private final int delayedTrains;
    private final int conflictsDetected;
    private final double averageWaitingMinutes;
    private final double averageTurnaroundMinutes;
    private final Map<Platform, Double> utilizationByPlatform;
    private final double averageUtilization;
    private final int minimumPlatformsNeeded;
    private final int platformsUsed;
    private final LocalDateTime windowStart;
    private final LocalDateTime windowEnd;

    ScheduleMetrics(int totalTrains, int allocatedTrains, int unallocatedTrains, int delayedTrains,
                    int conflictsDetected, double averageWaitingMinutes, double averageTurnaroundMinutes,
                    Map<Platform, Double> utilizationByPlatform, double averageUtilization,
                    int minimumPlatformsNeeded, int platformsUsed,
                    LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.totalTrains = totalTrains;
        this.allocatedTrains = allocatedTrains;
        this.unallocatedTrains = unallocatedTrains;
        this.delayedTrains = delayedTrains;
        this.conflictsDetected = conflictsDetected;
        this.averageWaitingMinutes = averageWaitingMinutes;
        this.averageTurnaroundMinutes = averageTurnaroundMinutes;
        // Copy into our own map so nobody outside can change it later.
        this.utilizationByPlatform = new LinkedHashMap<>(utilizationByPlatform);
        this.averageUtilization = averageUtilization;
        this.minimumPlatformsNeeded = minimumPlatformsNeeded;
        this.platformsUsed = platformsUsed;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    /** @return number of trains considered */
    public int getTotalTrains() {
        return totalTrains;
    }

    /** @return trains holding a platform */
    public int getAllocatedTrains() {
        return allocatedTrains;
    }

    /** @return trains without a platform */
    public int getUnallocatedTrains() {
        return unallocatedTrains;
    }

    /** @return trains with a delay greater than zero */
    public int getDelayedTrains() {
        return delayedTrains;
    }

    /** @return number of times every compatible platform was busy when a train needed one */
    public int getConflictsDetected() {
        return conflictsDetected;
    }

    /** @return average waiting time of allocated trains, in minutes */
    public double getAverageWaitingMinutes() {
        return averageWaitingMinutes;
    }

    /** @return average turnaround time (waiting + dwell) of allocated trains, in minutes */
    public double getAverageTurnaroundMinutes() {
        return averageTurnaroundMinutes;
    }

    /** @return read-only map of platform to utilization (0.0 to 1.0) */
    public Map<Platform, Double> getUtilizationByPlatform() {
        return Collections.unmodifiableMap(utilizationByPlatform);
    }

    /** @return average utilization over all platforms (0.0 to 1.0) */
    public double getAverageUtilization() {
        return averageUtilization;
    }

    /** @return most trains present at once (sweep line): platforms needed for zero waiting */
    public int getMinimumPlatformsNeeded() {
        return minimumPlatformsNeeded;
    }

    /** @return number of different platforms that hold at least one booking */
    public int getPlatformsUsed() {
        return platformsUsed;
    }

    /** @return start of the observed time window, or null if nothing is booked */
    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    /** @return end of the observed time window, or null if nothing is booked */
    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }
}
