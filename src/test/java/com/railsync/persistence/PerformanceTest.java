package com.railsync.persistence;

import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.service.ScenarioGenerator;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.FcfsStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance checks for the non-functional requirements (NFR-P2, NFR-P3):
 * the largest supported scenario (500 trains) must allocate, save to a real
 * database file and load back quickly enough that the user never notices.
 * Timings are printed so they can be quoted in the test report.
 */
class PerformanceTest {

    // Scenario: 500 generated trains, allocated with FCFS, saved to a file and restored into a new service.
    // Expected: allocation, save and restore each finish well under one second, and nothing is lost.
    @Test
    void fiveHundredTrainsAllocateSaveAndRestoreQuickly(@TempDir Path folder) {
        SchedulingService service = new SchedulingService(new Station("Perf"), new Schedule(), new FcfsStrategy(),
                new ScheduleEventManager());
        service.loadScenario(ScenarioGenerator.generate(2026, 500));

        long t0 = System.nanoTime();
        service.allocatePendingTrains();
        long t1 = System.nanoTime();
        try (Database database = Database.open(folder.resolve("perf.db"))) {
            SqliteStationRepository repository = new SqliteStationRepository(database);
            repository.save(service.takeSnapshot());
            long t2 = System.nanoTime();
            SchedulingService restored = new SchedulingService(new Station("Perf"), new Schedule(), new FcfsStrategy(),
                    new ScheduleEventManager());
            restored.restore(repository.load().orElseThrow());
            long t3 = System.nanoTime();

            long allocateMs = (t1 - t0) / 1_000_000;
            long saveMs = (t2 - t1) / 1_000_000;
            long restoreMs = (t3 - t2) / 1_000_000;
            System.out.println("PERF 500 trains: allocate " + allocateMs + " ms, save " + saveMs
                    + " ms, restore " + restoreMs + " ms, bookings " + service.getSchedule().size());

            assertEquals(service.getSchedule().size(), restored.getSchedule().size());
            assertTrue(allocateMs < 1000, "allocation took " + allocateMs + " ms");
            assertTrue(saveMs < 1000, "save took " + saveMs + " ms");
            assertTrue(restoreMs < 1000, "restore took " + restoreMs + " ms");
        }
    }
}
