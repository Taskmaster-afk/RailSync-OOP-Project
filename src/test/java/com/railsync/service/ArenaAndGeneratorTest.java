package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.strategy.PriorityAgingStrategy;
import com.railsync.strategy.PriorityStrategy;
import com.railsync.strategy.ShortestDwellFirstStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the seeded generator, scenario copying, the comparison arena and
 * re-planning "from now on".
 */
class ArenaAndGeneratorTest {

    private static List<PlatformAllocationStrategy> allStrategies() {
        List<PlatformAllocationStrategy> list = new ArrayList<>();
        list.add(new FcfsStrategy());
        list.add(new PriorityStrategy());
        list.add(new ShortestDwellFirstStrategy());
        list.add(new PriorityAgingStrategy());
        return list;
    }

    private static SchedulingService demoService() {
        SchedulingService service = new SchedulingService(new Station("Demo"), new Schedule(), new FcfsStrategy(),
                new ScheduleEventManager());
        DemoScenarioLoader.load(service);
        return service;
    }

    // Scenario: generate twice with seed 42. Expected: identical trains and platforms (reproducible).
    @Test
    void sameSeedGivesSameScenario() {
        Scenario a = ScenarioGenerator.generate(42, 60);
        Scenario b = ScenarioGenerator.generate(42, 60);
        assertEquals(a.getPlatforms().size(), b.getPlatforms().size());
        for (int i = 0; i < a.getTrains().size(); i++) {
            assertEquals(a.getTrains().get(i).getNumber(), b.getTrains().get(i).getNumber());
            assertEquals(a.getTrains().get(i).getType(), b.getTrains().get(i).getType());
            assertEquals(a.getTrains().get(i).getScheduledArrival(), b.getTrains().get(i).getScheduledArrival());
        }
    }

    // Scenario: seeds 1 and 2. Expected: different arrival times somewhere.
    @Test
    void differentSeedGivesDifferentScenario() {
        Scenario a = ScenarioGenerator.generate(1, 60);
        Scenario b = ScenarioGenerator.generate(2, 60);
        boolean anyDifferent = false;
        for (int i = 0; i < 60; i++) {
            if (!a.getTrains().get(i).getScheduledArrival().equals(b.getTrains().get(i).getScheduledArrival())) {
                anyDifferent = true;
            }
        }
        assertTrue(anyDifferent);
    }

    // Scenario: 9 and 501 trains requested. Expected: refused; 10 and 500 accepted.
    @Test
    void trainCountIsValidated() {
        assertThrows(RailSyncException.class, () -> ScenarioGenerator.generate(1, 9));
        assertThrows(RailSyncException.class, () -> ScenarioGenerator.generate(1, 501));
        assertEquals(10, ScenarioGenerator.generate(1, 10).getTrains().size());
        assertEquals(500, ScenarioGenerator.generate(1, 500).getTrains().size());
    }

    // Scenario: delay a copied train. Expected: the original is unchanged (deep copy), and copies keep delays.
    @Test
    void copyIsIndependent() {
        Scenario original = ScenarioGenerator.generate(7, 20);
        original.getTrains().get(0).applyDelay(5);
        Scenario copy = original.copy();
        assertEquals(5, copy.getTrains().get(0).getDelayMinutes());
        copy.getTrains().get(0).applyDelay(10);
        assertEquals(5, original.getTrains().get(0).getDelayMinutes());
        assertNotSame(original.getPlatforms().get(0), copy.getPlatforms().get(0));
    }

    // Scenario: arena on the live demo. Expected: 4 entries, FCFS/Priority differ, live schedule untouched.
    @Test
    void arenaComparesWithoutTouchingLiveData() {
        SchedulingService service = demoService();
        service.allocatePendingTrains();
        int liveBookings = service.getSchedule().size();
        Train l102 = service.getTrainManager().findByNumber("L-102");
        PlatformReservation liveBooking = service.findReservation(l102);

        List<StrategyArena.Entry> entries = new StrategyArena().compare(Scenario.fromLive(service), allStrategies(),
                5, 20, false);

        assertEquals(4, entries.size());
        assertEquals(1, entries.get(0).getMetrics().getUnallocatedTrains());     // FCFS: only F-999
        assertEquals(2, entries.get(1).getMetrics().getUnallocatedTrains());     // Priority: F-999 and L-102
        assertEquals(liveBookings, service.getSchedule().size());
        assertSame(liveBooking, service.findReservation(l102));
        // The sweep-line figure depends only on the trains, so it is the same for every strategy.
        assertEquals(entries.get(0).getMetrics().getMinimumPlatformsNeeded(),
                entries.get(3).getMetrics().getMinimumPlatformsNeeded());
    }

    // Scenario: 500 generated trains through all 4 strategies. Expected: finishes within 10 s and never double-books.
    @Test
    void arenaHandles500TrainsQuickly() {
        Scenario big = ScenarioGenerator.generate(2026, 500);
        long start = System.currentTimeMillis();
        List<StrategyArena.Entry> entries = new StrategyArena().compare(big, allStrategies(), 5, 20, false);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10_000, "took " + elapsed + " ms");
        for (StrategyArena.Entry entry : entries) {
            assertEquals(500, entry.getMetrics().getTotalTrains());
            assertTrue(entry.getMetrics().getAllocatedTrains() > 300);
        }
    }

    // Scenario: demo allocated with FCFS, then re-planned from 08:12 with Priority. Expected: started bookings unchanged, nothing new before 08:12, 12002 wins the slot.
    @Test
    void reallocateFromNowKeepsThePast() {
        SchedulingService service = demoService();
        service.allocatePendingTrains();
        LocalDateTime now = DemoScenarioLoader.SERVICE_DAY.atTime(8, 12);
        PlatformReservation started = service.findReservation(service.getTrainManager().findByNumber("S-201"));

        service.setStrategy(new PriorityStrategy());
        List<AllocationResult> results = service.reallocateFrom(now);

        assertSame(started, service.findReservation(service.getTrainManager().findByNumber("S-201")));
        for (AllocationResult result : results) {
            if (result.isAllocated()) {
                assertFalse(result.getStart().isBefore(now));
            }
        }
        assertEquals("08:15", service.findReservation(service.getTrainManager().findByNumber("12002"))
                .getStart().toLocalTime().toString());
    }

    // Scenario: load a generated scenario into the live service. Expected: same counts, nothing allocated.
    @Test
    void generatedScenarioCanBeLoaded() {
        SchedulingService service = demoService();
        Scenario generated = ScenarioGenerator.generate(5, 40);
        service.loadScenario(generated);
        assertEquals(40, service.getTrainManager().getAllTrains().size());
        assertEquals(generated.getPlatforms().size(), service.getStation().getPlatformCount());
        assertEquals(0, service.getSchedule().size());
    }
}
