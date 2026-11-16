package com.railsync.service;

import com.railsync.model.ExpressTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.strategy.PriorityAgingStrategy;
import com.railsync.strategy.PriorityStrategy;
import com.railsync.strategy.ShortestDwellFirstStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole-allocator tests for all four strategies on a starvation scenario,
 * plus the optional preemption rules.
 */
class StarvationAndPreemptionTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    /**
     * One platform, max wait 30. Expresses keep arriving while low-priority L
     * (12-min stop) waits from 10:01. Returns L after running the strategy.
     */
    private Train runStarvationScenario(PlatformAllocationStrategy strategy) {
        Station station = new Station("S");
        station.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        Schedule schedule = new Schedule(5);
        PlatformAllocator allocator = new PlatformAllocator(station, schedule, 30);
        Train low = new PassengerTrain("L", "L", "Slow local", at(10, 1), at(10, 13), 1, 8);
        List<Train> trains = new ArrayList<>();
        trains.add(new ExpressTrain("E0", "E0", "Express 0", at(10, 0), at(10, 10), 4, 12));
        trains.add(low);
        trains.add(new ExpressTrain("E1", "E1", "Express 1", at(10, 12), at(10, 22), 4, 12));
        trains.add(new ExpressTrain("E2", "E2", "Express 2", at(10, 27), at(10, 37), 4, 12));
        allocator.allocateAll(trains, strategy);
        return low;
    }

    // Scenario: starvation scenario with FCFS. Expected: L is served (it arrived early).
    @Test
    void fcfsServesLowPriorityTrain() {
        assertEquals(TrainStatus.ALLOCATED, runStarvationScenario(new FcfsStrategy()).getStatus());
    }

    // Scenario: same with plain Priority. Expected: L starves (expresses always go first).
    @Test
    void priorityStarvesLowPriorityTrain() {
        assertEquals(TrainStatus.UNALLOCATED, runStarvationScenario(new PriorityStrategy()).getStatus());
    }

    // Scenario: same with SJF. Expected: L (longest stop) starves.
    @Test
    void sjfStarvesLongDwellTrain() {
        assertEquals(TrainStatus.UNALLOCATED, runStarvationScenario(new ShortestDwellFirstStrategy()).getStatus());
    }

    // Scenario: same with Priority + Aging (4 min per point). Expected: L ages past E2 and is served at 10:30.
    @Test
    void agingPreventsStarvation() {
        Train low = runStarvationScenario(new PriorityAgingStrategy(4));
        assertEquals(TrainStatus.ALLOCATED, low.getStatus());
    }

    // ---------- Preemption ----------

    private Station station;
    private Schedule schedule;
    private PlatformAllocator allocator;
    private Platform mixed;
    private Platform passengerOnly;

    private void buildPreemptionStation() {
        station = new Station("S");
        mixed = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER));
        passengerOnly = new Platform("P2", 2, 500, EnumSet.of(TrainType.PASSENGER));
        station.addPlatform(mixed);
        station.addPlatform(passengerOnly);
        schedule = new Schedule(5);
        allocator = new PlatformAllocator(station, schedule, 0);
        allocator.setPreemptionEnabled(true);
    }

    private Train bookLocalOnMixedAt1030() {
        Train local = new PassengerTrain("L", "L", "Local", at(10, 30), at(10, 40), 1, 8);
        schedule.reserve(local, mixed, local.getExpectedArrival());
        return local;
    }

    private Train expressArriving1028() {
        return new ExpressTrain("X", "X", "Express", at(10, 28), at(10, 38), 4, 12);
    }

    private List<Train> listOf(Train train) {
        List<Train> list = new ArrayList<>();
        list.add(train);
        return list;
    }

    // Scenario: express needs PF-1, which a lower-priority local has booked from 10:30; PF-2 is free. Expected: local moved to PF-2, express gets PF-1.
    @Test
    void preemptionMovesLowerPriorityTrainSafely() {
        buildPreemptionStation();
        Train local = bookLocalOnMixedAt1030();
        Train express = expressArriving1028();
        List<AllocationResult> results = allocator.allocateAll(listOf(express), new PriorityStrategy());
        assertSame(mixed, express.getAssignedPlatform());
        assertSame(passengerOnly, local.getAssignedPlatform());
        assertEquals(at(10, 30), schedule.findReservation(local).getStart());
        assertTrue(results.get(0).getMessage().contains("PREEMPTED"));
    }

    // Scenario: same, but PF-2 is also busy so the local cannot move. Expected: no displacement, local keeps PF-1 at 10:30.
    @Test
    void preemptionRefusedWhenDisplacedTrainCannotMove() {
        buildPreemptionStation();
        Train local = bookLocalOnMixedAt1030();
        Train blocker = new PassengerTrain("B", "B", "Blocker", at(10, 25), at(10, 45), 3, 8);
        schedule.reserve(blocker, passengerOnly, blocker.getExpectedArrival());
        Train express = expressArriving1028();
        allocator.allocateAll(listOf(express), new PriorityStrategy());
        PlatformReservation kept = schedule.findReservation(local);
        assertSame(mixed, kept.getPlatform());
        assertEquals(at(10, 30), kept.getStart());
        assertEquals(TrainStatus.UNALLOCATED, express.getStatus());
        assertEquals(2, schedule.size());
    }

    // Scenario: the booked train has HIGHER priority than the newcomer. Expected: no preemption.
    @Test
    void preemptionNeverDisplacesHigherPriority() {
        buildPreemptionStation();
        Train vip = new ExpressTrain("V", "V", "VIP", at(10, 30), at(10, 40), 5, 12);
        schedule.reserve(vip, mixed, vip.getExpectedArrival());
        Train express = expressArriving1028();
        allocator.allocateAll(listOf(express), new PriorityStrategy());
        assertSame(mixed, vip.getAssignedPlatform());
        assertEquals(TrainStatus.UNALLOCATED, express.getStatus());
    }

    // Scenario: preemption switched off (the default). Expected: local stays, express unallocated.
    @Test
    void preemptionIsOffByDefault() {
        buildPreemptionStation();
        allocator = new PlatformAllocator(station, schedule, 0);
        assertFalse(allocator.isPreemptionEnabled());
        Train local = bookLocalOnMixedAt1030();
        Train express = expressArriving1028();
        allocator.allocateAll(listOf(express), new PriorityStrategy());
        assertSame(mixed, local.getAssignedPlatform());
        assertEquals(TrainStatus.UNALLOCATED, express.getStatus());
    }

    // Scenario: the local is already standing on PF-1 (started 10:25) when the express arrives. Expected: never pushed out.
    @Test
    void trainAlreadyAtPlatformIsNeverDisplaced() {
        buildPreemptionStation();
        Train local = new PassengerTrain("L", "L", "Local", at(10, 25), at(10, 35), 1, 8);
        schedule.reserve(local, mixed, local.getExpectedArrival());
        Train express = expressArriving1028();
        allocator.allocateAll(listOf(express), new PriorityStrategy());
        assertSame(mixed, local.getAssignedPlatform());
        assertEquals(TrainStatus.UNALLOCATED, express.getStatus());
    }
}
