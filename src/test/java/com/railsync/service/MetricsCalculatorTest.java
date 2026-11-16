package com.railsync.service;

import com.railsync.model.FreightTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainType;
import com.railsync.strategy.FcfsStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the metric definitions, the sweep line and the report text.
 */
class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();
    private Station station;
    private Schedule schedule;
    private Platform p1;
    private Platform p2;

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    private static Train passenger(String number, int h, int m, int dwell) {
        return new PassengerTrain("T-" + number, number, "Local", at(h, m), at(h, m).plusMinutes(dwell), 3, 8);
    }

    @BeforeEach
    void setUp() {
        station = new Station("S");
        p1 = new Platform("P1", 1, 500, EnumSet.allOf(TrainType.class));
        p2 = new Platform("P2", 2, 500, EnumSet.allOf(TrainType.class));
        station.addPlatform(p1);
        station.addPlatform(p2);
        schedule = new Schedule(0);
    }

    // Scenario: timetable 10:00, delayed 10, then waits 5 more (start 10:15), stop 10 min. Expected: waiting 15, turnaround 25.
    @Test
    void waitingCountsDelayAndQueueTime() {
        Train train = passenger("A", 10, 0, 10);
        train.applyDelay(10);
        PlatformReservation booking = schedule.reserve(train, p1, at(10, 15));
        assertEquals(15, calculator.waitingMinutes(train, booking));
        assertEquals(25, calculator.turnaroundMinutes(train, booking));
    }

    // Scenario: train placed exactly on time. Expected: waiting 0, turnaround = dwell.
    @Test
    void onTimeTrainHasZeroWaiting() {
        Train train = passenger("A", 10, 0, 10);
        PlatformReservation booking = schedule.reserve(train, p1, at(10, 0));
        assertEquals(0, calculator.waitingMinutes(train, booking));
        assertEquals(10, calculator.turnaroundMinutes(train, booking));
    }

    // Scenario: A on time (wait 0), B waits 20, C unallocated. Expected: averages over A and B only: wait 10, turnaround 20.
    @Test
    void averagesIgnoreUnallocatedTrains() {
        Train a = passenger("A", 10, 0, 10);
        Train b = passenger("B", 10, 0, 10);
        Train c = passenger("C", 10, 0, 10);
        schedule.reserve(a, p1, at(10, 0));
        schedule.reserve(b, p2, at(10, 20));
        List<Train> trains = new ArrayList<>();
        trains.add(a);
        trains.add(b);
        trains.add(c);
        ScheduleMetrics m = calculator.calculate(station, schedule, trains, 1);
        assertEquals(2, m.getAllocatedTrains());
        assertEquals(1, m.getUnallocatedTrains());
        assertEquals(10.0, m.getAverageWaitingMinutes(), 0.001);
        assertEquals(20.0, m.getAverageTurnaroundMinutes(), 0.001);
        assertEquals(1, m.getConflictsDetected());
    }

    // Scenario: window 10:00-12:00, PF-1 busy 30+30 min, PF-2 busy 120 min. Expected: 0.5, 1.0, average 0.75.
    @Test
    void utilizationIsOccupiedOverWindow() {
        schedule.reserve(passenger("A", 10, 0, 30), p1, at(10, 0));
        schedule.reserve(passenger("B", 11, 0, 30), p1, at(11, 0));
        schedule.reserve(passenger("C", 10, 0, 120), p2, at(10, 0));
        List<Train> none = new ArrayList<>();
        ScheduleMetrics m = calculator.calculate(station, schedule, none, 0);
        assertEquals(0.5, m.getUtilizationByPlatform().get(p1), 0.001);
        assertEquals(1.0, m.getUtilizationByPlatform().get(p2), 0.001);
        assertEquals(0.75, m.getAverageUtilization(), 0.001);
        assertEquals(at(10, 0), m.getWindowStart());
        assertEquals(at(12, 0), m.getWindowEnd());
    }

    // Scenario: nothing booked. Expected: utilization 0 and no division by zero.
    @Test
    void emptyScheduleGivesZeroMetrics() {
        ScheduleMetrics m = calculator.calculate(station, schedule, new ArrayList<>(), 0);
        assertEquals(0.0, m.getAverageUtilization(), 0.001);
        assertEquals(0.0, m.getAverageWaitingMinutes(), 0.001);
        assertEquals(0, m.getPlatformsUsed());
    }

    // Scenario: doc example A 10:00-10:30, B 10:10-10:40, C 10:30-10:50, buffer 0. Expected: 2 platforms.
    @Test
    void sweepLineFindsPeak() {
        List<Train> trains = new ArrayList<>();
        trains.add(passenger("A", 10, 0, 30));
        trains.add(passenger("B", 10, 10, 30));
        trains.add(passenger("C", 10, 30, 20));
        assertEquals(2, calculator.minimumPlatformsNeeded(trains, 0));
    }

    // Scenario: back-to-back trains, buffer 0 then buffer 5. Expected: 1 platform, then 2 (buffer makes them overlap).
    @Test
    void sweepLineHalfOpenAndBuffer() {
        List<Train> trains = new ArrayList<>();
        trains.add(passenger("A", 10, 0, 30));
        trains.add(passenger("B", 10, 30, 30));
        assertEquals(1, calculator.minimumPlatformsNeeded(trains, 0));
        assertEquals(2, calculator.minimumPlatformsNeeded(trains, 5));
    }

    // Scenario: freight leaves 10:30, passenger arrives 10:33, base buffer 0. Expected: 2 (freight adds 5 extra minutes).
    @Test
    void sweepLineUsesFreightExtraBuffer() {
        List<Train> trains = new ArrayList<>();
        trains.add(new FreightTrain("F", "F", "Coal", at(10, 0), at(10, 30), 1, 10));
        trains.add(passenger("B", 10, 33, 10));
        assertEquals(2, calculator.minimumPlatformsNeeded(trains, 0));
    }

    // Scenario: two trains on PF-1 only. Expected: platforms used = 1.
    @Test
    void platformsUsedCountsDistinctPlatforms() {
        schedule.reserve(passenger("A", 10, 0, 10), p1, at(10, 0));
        schedule.reserve(passenger("B", 11, 0, 10), p1, at(11, 0));
        assertEquals(1, calculator.platformsUsed(schedule));
    }

    // Scenario: one platform, second train must wait. Expected: 1 conflict counted from allocator results.
    @Test
    void conflictsAreCountedFromResults() {
        Station single = new Station("One");
        single.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.PASSENGER)));
        Schedule s = new Schedule(5);
        List<Train> trains = new ArrayList<>();
        trains.add(passenger("A", 10, 0, 10));
        trains.add(passenger("B", 10, 5, 10));
        List<AllocationResult> results = new PlatformAllocator(single, s, 20).allocateAll(trains, new FcfsStrategy());
        assertEquals(1, calculator.countConflicts(results));
    }

    // Scenario: report and comparison table are built. Expected: they contain the titles and key labels.
    @Test
    void reportGeneratorProducesReadableText() {
        schedule.reserve(passenger("A", 10, 0, 30), p1, at(10, 0));
        List<Train> trains = new ArrayList<>();
        trains.add(schedule.getAllReservations().get(0).getTrain());
        ScheduleMetrics m = calculator.calculate(station, schedule, trains, 0);
        ReportGenerator reports = new ReportGenerator();
        assertTrue(reports.buildSummaryReport("FCFS", m).contains("Avg waiting time"));
        List<String> names = new ArrayList<>();
        names.add("FCFS");
        List<ScheduleMetrics> all = new ArrayList<>();
        all.add(m);
        assertTrue(reports.buildComparisonTable(names, all).contains("FCFS"));
        assertTrue(reports.buildOccupancyListing(station, schedule).contains("(free all day)"));
    }
}
