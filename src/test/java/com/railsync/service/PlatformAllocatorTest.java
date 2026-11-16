package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.FreightTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PriorityStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the allocation engine: First-Fit placement, waiting in the
 * ready queue, giving up after the maximum wait, and strategy differences.
 */
class PlatformAllocatorTest {

    private Station station;
    private Schedule schedule;
    private PlatformAllocator allocator;

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    private static Train passenger(String number, int h, int m, int dwell) {
        return new PassengerTrain("T-" + number, number, "Local " + number, at(h, m), at(h, m).plusMinutes(dwell), 3, 8);
    }

    private static Train express(String number, int h, int m, int dwell) {
        return new ExpressTrain("T-" + number, number, "Express " + number, at(h, m), at(h, m).plusMinutes(dwell), 4, 12);
    }

    private static List<Train> listOf(Train... trains) {
        List<Train> list = new ArrayList<>();
        for (Train train : trains) {
            list.add(train);
        }
        return list;
    }

    @BeforeEach
    void setUp() {
        station = new Station("Test");
        schedule = new Schedule(5);
        allocator = new PlatformAllocator(station, schedule, 20);
    }

    private Platform addPlatform(int number, TrainType... types) {
        EnumSet<TrainType> set = EnumSet.noneOf(TrainType.class);
        for (TrainType type : types) {
            set.add(type);
        }
        Platform platform = new Platform("P" + number, number, 500, set);
        station.addPlatform(platform);
        return platform;
    }

    // Scenario: one train, two suitable platforms. Expected: placed on arrival on the lowest number (First-Fit).
    @Test
    void firstFitChoosesLowestNumberedPlatform() {
        Platform p2 = addPlatform(2, TrainType.PASSENGER);
        Platform p1 = addPlatform(1, TrainType.PASSENGER);
        Train train = passenger("A", 10, 0, 10);
        List<AllocationResult> results = allocator.allocateAll(listOf(train), new FcfsStrategy());
        assertTrue(results.get(0).isAllocated());
        assertSame(p1, train.getAssignedPlatform());
        assertNotSame(p2, train.getAssignedPlatform());
        assertEquals(0, results.get(0).getQueueWaitMinutes());
    }

    // Scenario: two overlapping trains, two platforms. Expected: second goes to platform 2.
    @Test
    void overlappingTrainUsesNextPlatform() {
        addPlatform(1, TrainType.PASSENGER);
        Platform p2 = addPlatform(2, TrainType.PASSENGER);
        Train a = passenger("A", 10, 0, 30);
        Train b = passenger("B", 10, 10, 10);
        allocator.allocateAll(listOf(a, b), new FcfsStrategy());
        assertSame(p2, b.getAssignedPlatform());
    }

    // Scenario: all platforms occupied, free again within max wait. Expected: train waits at outer signal, then is placed.
    @Test
    void trainWaitsWhenAllPlatformsOccupied() {
        addPlatform(1, TrainType.PASSENGER);
        Train a = passenger("A", 10, 0, 20);
        Train b = passenger("B", 10, 10, 10);
        List<AllocationResult> results = allocator.allocateAll(listOf(a, b), new FcfsStrategy());
        AllocationResult forB = results.get(1);
        assertTrue(forB.isAllocated());
        assertEquals(at(10, 25), forB.getStart());          // 10:20 + 5 min buffer
        assertEquals(15, forB.getQueueWaitMinutes());
    }

    // Scenario: platform busy longer than the max wait. Expected: UNALLOCATED with a reason about the maximum wait.
    @Test
    void trainIsUnallocatedWhenWaitTooLong() {
        addPlatform(1, TrainType.PASSENGER);
        Train a = passenger("A", 10, 0, 60);
        Train b = passenger("B", 10, 5, 10);
        List<AllocationResult> results = allocator.allocateAll(listOf(a, b), new FcfsStrategy());
        assertFalse(results.get(1).isAllocated());
        assertEquals(TrainStatus.UNALLOCATED, b.getStatus());
        assertTrue(results.get(1).getMessage().contains("maximum wait"));
    }

    // Scenario: max wait set to 0 and the platform is busy. Expected: no waiting allowed, train unallocated.
    @Test
    void zeroMaxWaitMeansNoQueueing() {
        allocator.setMaxWaitMinutes(0);
        addPlatform(1, TrainType.PASSENGER);
        Train a = passenger("A", 10, 0, 10);
        Train b = passenger("B", 10, 5, 10);
        allocator.allocateAll(listOf(a, b), new FcfsStrategy());
        assertEquals(TrainStatus.UNALLOCATED, b.getStatus());
    }

    // Scenario: freight train but only passenger platforms. Expected: rejected at once, reason names each platform.
    @Test
    void trainIncompatibleWithEveryPlatformIsRejectedWithReasons() {
        addPlatform(1, TrainType.PASSENGER);
        addPlatform(2, TrainType.EXPRESS);
        Train freight = new FreightTrain("F", "F1", "Coal", at(10, 0), at(10, 30), 1, 20);
        List<AllocationResult> results = allocator.allocateAll(listOf(freight), new FcfsStrategy());
        assertFalse(results.get(0).isAllocated());
        assertTrue(results.get(0).getMessage().contains("Platform 1"));
        assertTrue(results.get(0).getMessage().contains("Platform 2"));
    }

    // Scenario: platform 1 has failed. Expected: train is placed on platform 2 instead (disabled platform skipped).
    @Test
    void failedPlatformIsSkipped() {
        Platform p1 = addPlatform(1, TrainType.PASSENGER);
        Platform p2 = addPlatform(2, TrainType.PASSENGER);
        p1.markFailed();
        Train train = passenger("A", 10, 0, 10);
        allocator.allocateAll(listOf(train), new FcfsStrategy());
        assertSame(p2, train.getAssignedPlatform());
    }

    // Scenario: station with no platforms. Expected: failure, not an exception.
    @Test
    void stationWithNoPlatformsGivesFailure() {
        Train train = passenger("A", 10, 0, 10);
        List<AllocationResult> results = allocator.allocateAll(listOf(train), new FcfsStrategy());
        assertFalse(results.get(0).isAllocated());
    }

    // Scenario: one platform busy till 10:20; P2 arrives 10:05, express E1 10:10. Expected: FCFS serves P2, Priority serves E1.
    @Test
    void fcfsAndPriorityChooseDifferentTrains() {
        addPlatform(1, TrainType.EXPRESS, TrainType.PASSENGER);
        Train p1 = passenger("P1", 10, 0, 20);
        Train p2 = passenger("P2", 10, 5, 10);
        Train e1 = express("E1", 10, 10, 10);
        allocator.allocateAll(listOf(p1, p2, e1), new FcfsStrategy());
        assertEquals(TrainStatus.ALLOCATED, p2.getStatus());
        assertEquals(TrainStatus.UNALLOCATED, e1.getStatus());

        // Fresh copy of the same scenario, run with Priority.
        setUp();
        addPlatform(1, TrainType.EXPRESS, TrainType.PASSENGER);
        Train q1 = passenger("P1", 10, 0, 20);
        Train q2 = passenger("P2", 10, 5, 10);
        Train f1 = express("E1", 10, 10, 10);
        allocator.allocateAll(listOf(q1, q2, f1), new PriorityStrategy());
        assertEquals(TrainStatus.ALLOCATED, f1.getStatus());
        assertEquals(at(10, 25), schedule.findReservation(f1).getStart());
        assertEquals(TrainStatus.UNALLOCATED, q2.getStatus());
    }

    // Scenario: high-priority express, only a freight platform is free. Expected: priority never overrides compatibility.
    @Test
    void priorityNeverOverridesCompatibility() {
        allocator.setMaxWaitMinutes(0);
        Platform freightOnly = addPlatform(1, TrainType.FREIGHT);
        addPlatform(2, TrainType.EXPRESS, TrainType.PASSENGER);
        Train blocker = passenger("A", 10, 0, 30);
        Train vip = express("VIP", 10, 5, 10);
        allocator.allocateAll(listOf(blocker, vip), new PriorityStrategy());
        assertFalse(schedule.hasReservations(freightOnly));
        assertEquals(TrainStatus.UNALLOCATED, vip.getStatus());
    }

    // Scenario: schedule already holds a booking from an earlier run. Expected: new train respects it and waits.
    @Test
    void existingReservationsAreRespected() {
        Platform p1 = addPlatform(1, TrainType.PASSENGER);
        Train old = passenger("OLD", 10, 0, 20);
        schedule.reserve(old, p1, old.getExpectedArrival());
        Train fresh = passenger("NEW", 10, 10, 5);
        List<AllocationResult> results = allocator.allocateAll(listOf(fresh), new FcfsStrategy());
        assertEquals(at(10, 25), results.get(0).getStart());
    }

    // Scenario: list contains a train that already has a booking. Expected: skipped, its booking unchanged.
    @Test
    void alreadyAllocatedTrainIsSkipped() {
        Platform p1 = addPlatform(1, TrainType.PASSENGER);
        Train old = passenger("OLD", 10, 0, 20);
        schedule.reserve(old, p1, old.getExpectedArrival());
        List<AllocationResult> results = allocator.allocateAll(listOf(old), new FcfsStrategy());
        assertTrue(results.isEmpty());
        assertEquals(1, schedule.size());
    }

    // Scenario: many trains on two platforms. Expected: no two bookings on a platform ever break the buffer rule.
    @Test
    void neverCreatesOverlappingBookings() {
        Platform p1 = addPlatform(1, TrainType.PASSENGER);
        Platform p2 = addPlatform(2, TrainType.PASSENGER);
        List<Train> trains = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            trains.add(passenger("X" + i, 10, i * 4, 12));
        }
        allocator.allocateAll(trains, new PriorityStrategy());
        assertNoOverlaps(p1);
        assertNoOverlaps(p2);
    }

    private void assertNoOverlaps(Platform platform) {
        List<PlatformReservation> list = schedule.getReservationsOn(platform);
        for (int i = 1; i < list.size(); i++) {
            int gap = schedule.requiredGapMinutes(list.get(i - 1).getTrain(), list.get(i).getTrain());
            assertFalse(list.get(i).getStart().isBefore(list.get(i - 1).getEnd().plusMinutes(gap)));
        }
    }

    // Scenario: max wait of -1 or 999. Expected: RailSyncException.
    @Test
    void maxWaitIsValidated() {
        assertThrows(RailSyncException.class, () -> allocator.setMaxWaitMinutes(-1));
        assertThrows(RailSyncException.class, () -> allocator.setMaxWaitMinutes(999));
    }
}
