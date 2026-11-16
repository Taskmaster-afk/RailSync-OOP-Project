package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the performance numbers of a schedule, using exactly these
 * definitions (the same ones used in the docs and the viva guide):
 *
 * <ul>
 *   <li><b>Waiting time</b> of a train = its platform start minus its
 *       ORIGINAL timetable arrival. 0 if it got the platform on time.
 *       A delay or time spent in the queue both count as waiting.</li>
 *   <li><b>Turnaround time</b> = waiting time + dwell time.</li>
 *   <li><b>Averages</b> are over allocated trains only (an unallocated
 *       train has no platform start).</li>
 *   <li><b>Utilization</b> of a platform = minutes it is occupied / length
 *       of the observed window (first booking start to last booking end).</li>
 *   <li><b>Minimum platforms needed</b> = the most trains present at the
 *       same moment, found with a sweep line over arrival/departure events.</li>
 * </ul>
 *
 * <p>OS analogy: these are the classic CPU-scheduling measures (waiting
 * time, turnaround time, CPU utilization) applied to platforms.</p>
 *
 * <p>OOP concepts: SOLID-S (it only calculates; it never changes the
 * schedule and never formats text, which is ReportGenerator's job).</p>
 *
 * <p>Works with: Schedule, Station, Train, AllocationResult, ScheduleMetrics.</p>
 */
public class MetricsCalculator {

    /**
     * Calculates all metrics for a set of trains and the schedule they were placed in.
     *
     * @param station           the station (for the list of platforms)
     * @param schedule          the bookings
     * @param trains            every train that should have been served
     * @param conflictsDetected how many conflicts the allocator reported
     * @return a filled ScheduleMetrics
     */
    public ScheduleMetrics calculate(Station station, Schedule schedule, List<Train> trains, int conflictsDetected) {
        // [SOLID-S] this class only calculates; turning numbers into text is ReportGenerator's job.
        // Step 1: count trains and add up waiting and turnaround of the allocated ones.
        int allocated = 0;
        int delayed = 0;
        long totalWaiting = 0;
        long totalTurnaround = 0;
        for (Train train : trains) {
            if (train.isDelayed()) {
                delayed++;
            }
            PlatformReservation reservation = schedule.findReservation(train);
            if (reservation != null) {
                allocated++;
                totalWaiting += waitingMinutes(train, reservation);
                totalTurnaround += turnaroundMinutes(train, reservation);
            }
        }
        int unallocated = trains.size() - allocated;

        // Step 2: averages (guard against dividing by zero when nothing is allocated).
        double averageWaiting = allocated == 0 ? 0.0 : (double) totalWaiting / allocated;
        double averageTurnaround = allocated == 0 ? 0.0 : (double) totalTurnaround / allocated;

        // Step 3: the observed window runs from the first booking start to the last booking end.
        LocalDateTime windowStart = null;
        LocalDateTime windowEnd = null;
        for (PlatformReservation reservation : schedule.getAllReservations()) {
            if (windowStart == null || reservation.getStart().isBefore(windowStart)) {
                windowStart = reservation.getStart();
            }
            if (windowEnd == null || reservation.getEnd().isAfter(windowEnd)) {
                windowEnd = reservation.getEnd();
            }
        }

        // Step 4: utilization of every platform, and the average over all platforms.
        Map<Platform, Double> utilization = new LinkedHashMap<>();
        double utilizationSum = 0.0;
        for (Platform platform : station.getPlatforms()) {
            double value = platformUtilization(schedule, platform, windowStart, windowEnd);
            utilization.put(platform, value);
            utilizationSum += value;
        }
        double averageUtilization = station.getPlatformCount() == 0 ? 0.0 : utilizationSum / station.getPlatformCount();

        // Step 5: theoretical minimum (sweep line) and what the strategy actually used.
        int minimumNeeded = minimumPlatformsNeeded(trains, schedule.getClearanceBufferMinutes());
        int used = platformsUsed(schedule);

        return new ScheduleMetrics(trains.size(), allocated, unallocated, delayed, conflictsDetected,
                averageWaiting, averageTurnaround, utilization, averageUtilization,
                minimumNeeded, used, windowStart, windowEnd);
    }

    /**
     * Waiting time = platform start minus ORIGINAL timetable arrival (never negative).
     * Example: timetable 10:00, delayed 10 min, waited 5 more in the queue,
     * platform start 10:15 -> waiting time 15 min.
     *
     * @param train       the train
     * @param reservation its booking
     * @return waiting minutes
     */
    public long waitingMinutes(Train train, PlatformReservation reservation) {
        long minutes = Duration.between(train.getScheduledArrival(), reservation.getStart()).toMinutes();
        return Math.max(0, minutes);
    }

    /**
     * Turnaround time = waiting time + dwell time.
     * Example: waited 15 min, stops 10 min -> turnaround 25 min.
     *
     * @param train       the train
     * @param reservation its booking
     * @return turnaround minutes
     */
    public long turnaroundMinutes(Train train, PlatformReservation reservation) {
        return waitingMinutes(train, reservation) + train.getDwellTime().toMinutes();
    }

    /**
     * Fraction of the window during which the platform is occupied.
     * Example: window 10:00-12:00 (120 min), platform busy 30 + 30 min -> 0.5.
     *
     * @param schedule    the bookings
     * @param platform    the platform
     * @param windowStart start of the window (null means nothing booked)
     * @param windowEnd   end of the window
     * @return a value from 0.0 to 1.0
     */
    public double platformUtilization(Schedule schedule, Platform platform,
                                      LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (windowStart == null || !windowEnd.isAfter(windowStart)) {
            return 0.0;
        }
        long windowMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        long occupied = 0;
        for (PlatformReservation reservation : schedule.getReservationsOn(platform)) {
            // Only count the part of each booking that falls inside the window.
            LocalDateTime from = reservation.getStart().isBefore(windowStart) ? windowStart : reservation.getStart();
            LocalDateTime to = reservation.getEnd().isAfter(windowEnd) ? windowEnd : reservation.getEnd();
            if (to.isAfter(from)) {
                occupied += Duration.between(from, to).toMinutes();
            }
        }
        return (double) occupied / windowMinutes;
    }

    /**
     * The most trains that want to be at the station at the same moment,
     * using their expected times plus their clearance buffer. This many
     * platforms would let every train in with zero waiting (if every platform
     * suited every train). It ignores compatibility, so it is a theoretical
     * figure, not a guarantee.
     *
     * <p>Sweep line, step by step:</p>
     * <ol>
     *   <li>Turn each train into two events: +1 at arrival, -1 at
     *       departure + buffer.</li>
     *   <li>Sort the events by time. When times are equal, put -1 first,
     *       because the intervals are half-open: a train leaving at 10:30 has
     *       freed the platform for one arriving at 10:30.</li>
     *   <li>Walk through the events keeping a running count; the highest
     *       count seen is the answer.</li>
     * </ol>
     *
     * <p>Example (buffer 0): A 10:00-10:30, B 10:10-10:40, C 10:30-10:50.
     * Events: 10:00 +1 (1), 10:10 +1 (2), 10:30 -1 (1), 10:30 +1 (2),
     * 10:40 -1 (1), 10:50 -1 (0). Maximum = 2 platforms.</p>
     *
     * @param trains             the trains
     * @param baseBufferMinutes  the station-wide clearance buffer
     * @return the maximum number of trains present at once
     */
    public int minimumPlatformsNeeded(List<Train> trains, int baseBufferMinutes) {
        // Step 1: two events per train.
        List<SweepEvent> events = new ArrayList<>();
        for (Train train : trains) {
            // [POLYMORPHISM] FreightTrain adds its own extra buffer here.
            int buffer = train.getClearanceBufferMinutes(baseBufferMinutes);
            events.add(new SweepEvent(train.getExpectedArrival(), +1));
            events.add(new SweepEvent(train.getExpectedDeparture().plusMinutes(buffer), -1));
        }
        // Step 2: sort by time; on a tie the -1 (leaving) event comes first.
        events.sort((a, b) -> {
            int byTime = a.time.compareTo(b.time);
            return byTime != 0 ? byTime : Integer.compare(a.change, b.change);
        });
        // Step 3: running count; remember the peak.
        int current = 0;
        int peak = 0;
        for (SweepEvent event : events) {
            current += event.change;
            if (current > peak) {
                peak = current;
            }
        }
        return peak;
    }

    /**
     * Counts the different platforms that hold at least one booking.
     *
     * @param schedule the bookings
     * @return number of platforms used
     */
    public int platformsUsed(Schedule schedule) {
        // A HashSet ignores duplicates, so each platform is counted once.
        Set<Platform> used = new HashSet<>();
        for (PlatformReservation reservation : schedule.getAllReservations()) {
            used.add(reservation.getPlatform());
        }
        return used.size();
    }

    /**
     * Counts results where every compatible platform was busy when the train needed one.
     *
     * @param results allocation results
     * @return number of conflicts
     */
    public int countConflicts(List<AllocationResult> results) {
        int count = 0;
        for (AllocationResult result : results) {
            if (result.hadConflictAtArrival()) {
                count++;
            }
        }
        return count;
    }

    /** One point on the sweep line: at this time the number of trains present changes by +1 or -1. */
    private static final class SweepEvent {
        private final LocalDateTime time;
        private final int change;

        private SweepEvent(LocalDateTime time, int change) {
            this.time = time;
            this.change = change;
        }
    }
}
