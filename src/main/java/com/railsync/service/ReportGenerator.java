package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;

import java.util.List;
import java.util.Map;

/**
 * Turns metrics and schedules into plain-text reports for the console,
 * the Reports screen and the Strategy Comparison screen.
 *
 * <p>Why it exists: formatting is kept apart from calculating. If the
 * layout of a report changes, MetricsCalculator does not change, and the
 * other way round.</p>
 *
 * <p>OOP concepts: SOLID-S (single responsibility: text output only). It
 * only reads ScheduleMetrics and Schedule, never changes them.</p>
 *
 * <p>Works with: ScheduleMetrics, Schedule, Station, AllocationResult.</p>
 */
public class ReportGenerator {

    /**
     * Builds a multi-line summary of one schedule's metrics.
     *
     * @param title   heading, e.g. the strategy name
     * @param metrics the numbers to show
     * @return the report text
     */
    public String buildSummaryReport(String title, ScheduleMetrics metrics) {
        // [SOLID-S] formatting only: every number was already computed by MetricsCalculator.
        StringBuilder text = new StringBuilder();
        text.append("Report: ").append(title).append('\n');
        if (metrics.getWindowStart() != null) {
            text.append("  Observed window      : ").append(metrics.getWindowStart().toLocalTime())
                    .append(" - ").append(metrics.getWindowEnd().toLocalTime()).append('\n');
        }
        text.append("  Trains               : ").append(metrics.getTotalTrains())
                .append(" (allocated ").append(metrics.getAllocatedTrains())
                .append(", unallocated ").append(metrics.getUnallocatedTrains())
                .append(", delayed ").append(metrics.getDelayedTrains()).append(")\n");
        text.append("  Conflicts detected   : ").append(metrics.getConflictsDetected()).append('\n');
        text.append(String.format("  Avg waiting time     : %.1f min%n", metrics.getAverageWaitingMinutes()));
        text.append(String.format("  Avg turnaround time  : %.1f min%n", metrics.getAverageTurnaroundMinutes()));
        text.append(String.format("  Avg utilization      : %.0f%%%n", metrics.getAverageUtilization() * 100));
        for (Map.Entry<Platform, Double> entry : metrics.getUtilizationByPlatform().entrySet()) {
            text.append(String.format("    %-14s %5.0f%%%n", entry.getKey(), entry.getValue() * 100));
        }
        text.append("  Platforms used       : ").append(metrics.getPlatformsUsed()).append('\n');
        text.append("  Needed for zero wait : ").append(metrics.getMinimumPlatformsNeeded())
                .append(" (sweep line, ignores compatibility)\n");
        return text.toString();
    }

    /**
     * Builds a side-by-side table, one row per strategy.
     *
     * @param strategyNames names, same order as metrics
     * @param metrics       one ScheduleMetrics per strategy
     * @return the table text
     */
    public String buildComparisonTable(List<String> strategyNames, List<ScheduleMetrics> metrics) {
        StringBuilder text = new StringBuilder();
        String header = String.format("%-28s %6s %8s %8s %9s %10s %7s %6s %7s%n",
                "Strategy", "Alloc", "Unalloc", "Confl.", "Avg wait", "Avg turn.", "Util.", "Used", "Needed");
        text.append(header);
        text.append("-".repeat(header.length() - 1)).append('\n');
        for (int i = 0; i < metrics.size(); i++) {
            ScheduleMetrics m = metrics.get(i);
            text.append(String.format("%-28s %6d %8d %8d %8.1fm %9.1fm %6.0f%% %6d %7d%n",
                    strategyNames.get(i), m.getAllocatedTrains(), m.getUnallocatedTrains(),
                    m.getConflictsDetected(), m.getAverageWaitingMinutes(), m.getAverageTurnaroundMinutes(),
                    m.getAverageUtilization() * 100, m.getPlatformsUsed(), m.getMinimumPlatformsNeeded()));
        }
        return text.toString();
    }

    /**
     * Lists every platform with its bookings in time order (a text Gantt chart).
     *
     * @param station  the station
     * @param schedule the bookings
     * @return the listing text
     */
    public String buildOccupancyListing(Station station, Schedule schedule) {
        StringBuilder text = new StringBuilder();
        for (Platform platform : station.getPlatforms()) {
            text.append("  ").append(platform).append(platform.isOperational() ? "" : " [FAILED]").append(": ");
            List<PlatformReservation> bookings = schedule.getReservationsOn(platform);
            if (bookings.isEmpty()) {
                text.append("(free all day)");
            }
            for (PlatformReservation booking : bookings) {
                text.append(booking.getTrain().getNumber()).append(" [")
                        .append(booking.getStart().toLocalTime()).append("-")
                        .append(booking.getEnd().toLocalTime()).append(")  ");
            }
            text.append('\n');
        }
        return text.toString();
    }
}
