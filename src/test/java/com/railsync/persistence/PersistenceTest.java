package com.railsync.persistence;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;
import com.railsync.service.StationSnapshot;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PriorityAgingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the SQLite persistence layer: the schema, saving and
 * restoring a whole session, transactions, the audit log and the auto-saver.
 * Every test uses its own in-memory (or temporary-file) database, so the
 * user's real data is never touched.
 */
class PersistenceTest {

    private Database database;
    private SqliteStationRepository repository;
    private SqliteAuditLog auditLog;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        repository = new SqliteStationRepository(database);
        auditLog = new SqliteAuditLog(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static SchedulingService newService() {
        return new SchedulingService(new Station("Test"), new Schedule(), new FcfsStrategy(), new ScheduleEventManager());
    }

    private static SchedulingService demoService() {
        SchedulingService service = newService();
        DemoScenarioLoader.load(service);
        return service;
    }

    /** Describes every train, booking and platform as text, so two sessions can be compared exactly. */
    private static List<String> describe(SchedulingService service) {
        List<String> lines = new ArrayList<>();
        for (Train train : service.getTrainManager().getAllTrains()) {
            PlatformReservation booking = service.findReservation(train);
            lines.add(train.getType() + " " + train.getNumber() + " " + train.getName() + " " + train.getScheduledArrival()
                    + " " + train.getScheduledDeparture() + " p" + train.getBasePriority() + " c" + train.getCarriageCount()
                    + " d" + train.getDelayMinutes() + " " + train.getStatus()
                    + (booking == null ? "" : " @PF-" + booking.getPlatform().getNumber() + " " + booking.getStart()));
        }
        for (Platform platform : service.getStation().getPlatforms()) {
            lines.add(platform.getId() + " " + platform.getNumber() + " " + platform.getLengthMeters() + " "
                    + platform.getSupportedTypes() + " " + platform.getStatus());
        }
        lines.sort(null);
        return lines;
    }

    // Scenario: a brand-new database. Expected: schema version 1 and nothing to restore.
    @Test
    void newDatabaseHasSchemaAndNoSession() {
        assertEquals(Database.SCHEMA_VERSION, database.getSchemaVersion());
        assertTrue(repository.load().isEmpty());
    }

    // Scenario: allocate the demo, delay 12951 (ripple), fail PF-2, use aging (rate 6) with preemption, save, restore.
    // Expected: every train, booking, delay, status, platform and setting comes back identical.
    @Test
    void roundTripRestoresTheWholeSession() {
        SchedulingService original = demoService();
        PriorityAgingStrategy aging = new PriorityAgingStrategy(6);
        original.setStrategy(aging);
        original.setPreemptionEnabled(true);
        original.setMaxWaitMinutes(25);
        original.allocatePendingTrains();
        original.delayTrain(original.getTrainManager().findByNumber("12951"), 5);
        original.reportBreakdown(original.getStation().findPlatformById("P2"));
        repository.save(original.takeSnapshot());

        SchedulingService restored = newService();
        restored.restore(repository.load().orElseThrow());

        assertEquals(describe(original), describe(restored));
        assertEquals("Priority + Aging", restored.getStrategy().getName());
        assertEquals(6, ((PriorityAgingStrategy) restored.getStrategy()).getMinutesPerPriorityPoint());
        assertTrue(restored.isPreemptionEnabled());
        assertEquals(25, restored.getMaxWaitMinutes());
        assertEquals(original.getConflictsDetected(), restored.getConflictsDetected());
        assertEquals(original.calculateMetrics().getAverageWaitingMinutes(),
                restored.calculateMetrics().getAverageWaitingMinutes(), 1e-9);
    }

    // Scenario: a train the allocator gave up on (F-999, too long for every platform) is saved.
    // Expected: after restoring it is still UNALLOCATED, not silently back to SCHEDULED.
    @Test
    void givenUpTrainStaysGivenUp() {
        SchedulingService original = demoService();
        original.allocatePendingTrains();
        assertEquals(TrainStatus.UNALLOCATED, original.getTrainManager().findByNumber("F-999").getStatus());
        repository.save(original.takeSnapshot());

        SchedulingService restored = newService();
        restored.restore(repository.load().orElseThrow());
        assertEquals(TrainStatus.UNALLOCATED, restored.getTrainManager().findByNumber("F-999").getStatus());
    }

    // Scenario: save to a real file, close it, open it again (like restarting the app). Expected: the session is there.
    @Test
    void sessionSurvivesClosingAndReopeningTheFile(@TempDir Path folder) {
        Path file = folder.resolve("sub").resolve("railsync.db");
        SchedulingService original = demoService();
        original.allocatePendingTrains();
        try (Database fileDb = Database.open(file)) {
            new SqliteStationRepository(fileDb).save(original.takeSnapshot());
        }
        try (Database reopened = Database.open(file)) {
            SchedulingService restored = newService();
            restored.restore(new SqliteStationRepository(reopened).load().orElseThrow());
            assertEquals(describe(original), describe(restored));
        }
    }

    // Scenario: a second save fails half-way (a booking points at a train that does not exist).
    // Expected: the transaction rolls back and the first save is still complete.
    @Test
    void failedSaveLeavesThePreviousSaveIntact() {
        SchedulingService original = demoService();
        original.allocatePendingTrains();
        repository.save(original.takeSnapshot());

        StationSnapshot good = original.takeSnapshot();
        List<StationSnapshot.Booking> broken = new ArrayList<>(good.getBookings());
        broken.add(new StationSnapshot.Booking("NO-SUCH-TRAIN", "P1", good.getBookings().get(0).getStart()));
        StationSnapshot bad = new StationSnapshot(good.getPlatforms(), good.getTrains(), broken,
                good.getUnallocatedTrainIds(), good.getSettings());
        assertThrows(RailSyncException.class, () -> repository.save(bad));

        StationSnapshot reloaded = repository.load().orElseThrow();
        assertEquals(good.getBookings().size(), reloaded.getBookings().size());
        assertEquals(good.getTrains().size(), reloaded.getTrains().size());
    }

    // Scenario: saved bookings that clash (two trains on PF-1 at the same time), e.g. a hand-edited file.
    // Expected: restore refuses them through the normal clash rule instead of showing an impossible plan.
    @Test
    void restoreRejectsClashingBookings() {
        SchedulingService original = demoService();
        original.allocatePendingTrains();
        StationSnapshot good = original.takeSnapshot();
        List<StationSnapshot.Booking> clashing = new ArrayList<>();
        StationSnapshot.Booking first = good.getBookings().get(0);
        for (StationSnapshot.Booking booking : good.getBookings()) {
            clashing.add(new StationSnapshot.Booking(booking.getTrainId(), first.getPlatformId(), first.getStart()));
        }
        StationSnapshot bad = new StationSnapshot(good.getPlatforms(), good.getTrains(), clashing,
                good.getUnallocatedTrainIds(), good.getSettings());
        assertThrows(RailSyncException.class, () -> newService().restore(bad));
    }

    // Scenario: a train name written to look like an SQL attack. Expected: stored and read back as plain text;
    // the trains table still exists (PreparedStatement parameters keep data and SQL apart).
    @Test
    void sqlInjectionTextIsStoredAsPlainText() {
        SchedulingService original = demoService();
        Train train = original.getTrainManager().findByNumber("12951");
        String nasty = "Robert'); DROP TABLE trains; --";
        original.updateTrain(train, nasty, train.getBasePriority(), train.getScheduledArrival(), train.getScheduledDeparture());
        repository.save(original.takeSnapshot());

        StationSnapshot loaded = repository.load().orElseThrow();
        assertTrue(loaded.getTrains().stream().anyMatch(t -> nasty.equals(t.getName())));
        assertEquals(12, loaded.getTrains().size());
    }

    // Scenario: write a row that breaks a rule directly with SQL (priority 9). Expected: the database's own CHECK
    // constraint refuses it, a second line of defence behind the Java validation.
    @Test
    void databaseConstraintsRejectInvalidRows() {
        SQLException error = assertThrows(SQLException.class, () -> {
            try (Statement statement = database.connection().createStatement()) {
                statement.executeUpdate("INSERT INTO trains (id, number, name, type, arrival, departure, base_priority,"
                        + " carriages) VALUES ('X1', '999', 'Bad', 'EXPRESS', '2026-01-01T08:00', '2026-01-01T08:10', 9, 10)");
            }
        });
        assertTrue(error.getMessage().contains("CHECK"));
    }

    // Scenario: open a file written by a newer RailSync (schema 99). Expected: refused with a clear message, not damaged.
    @Test
    void newerSchemaIsRefused(@TempDir Path folder) throws SQLException {
        Path file = folder.resolve("future.db");
        try (Database fileDb = Database.open(file); Statement statement = fileDb.connection().createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }
        RailSyncException error = assertThrows(RailSyncException.class, () -> Database.open(file));
        assertTrue(error.getMessage().contains("newer RailSync"));
    }

    // Scenario: an unknown strategy name in the saved settings. Expected: falls back to FCFS instead of failing.
    @Test
    void unknownStrategyFallsBackToFcfs() {
        SchedulingService original = demoService();
        StationSnapshot good = original.takeSnapshot();
        StationSnapshot.Settings odd = new StationSnapshot.Settings("Quantum Annealing", 4, 5, 20, false, 0);
        SchedulingService restored = newService();
        restored.restore(new StationSnapshot(good.getPlatforms(), good.getTrains(), good.getBookings(),
                good.getUnallocatedTrainIds(), odd));
        assertEquals(new FcfsStrategy().getName(), restored.getStrategy().getName());
    }

    // Scenario: three audit entries, one very long. Expected: newest first, count 3, long text shortened.
    @Test
    void auditLogIsNewestFirstAndBounded() {
        auditLog.record("ALLOCATION", "first");
        auditLog.record("DELAY", "second");
        auditLog.record("BREAKDOWN", "x".repeat(5000));
        List<AuditEntry> entries = auditLog.recent(10);
        assertEquals(3, auditLog.count());
        assertEquals("BREAKDOWN", entries.get(0).getEventType());
        assertEquals("first", entries.get(2).getMessage());
        assertEquals(SqliteAuditLog.MAX_MESSAGE_LENGTH, entries.get(0).getMessage().length());
        assertEquals(2, auditLog.recent(2).size());
    }

    // Scenario: the auto-saver is subscribed, then the user allocates and delays.
    // Expected: without any "Save" click the database holds the latest bookings, and the audit log has the events.
    @Test
    void autoSaverSavesAfterEveryChange() {
        SchedulingService service = demoService();
        AutoSaver saver = new AutoSaver(service, repository, auditLog);
        List<Boolean> results = new ArrayList<>();
        saver.addSaveListener((ok, message) -> results.add(ok));
        service.getEvents().subscribe(saver);

        service.allocatePendingTrains();
        assertEquals(service.getSchedule().size(), repository.load().orElseThrow().getBookings().size());
        service.delayTrain(service.getTrainManager().findByNumber("12951"), 5);
        Optional<StationSnapshot> saved = repository.load();
        assertTrue(saved.orElseThrow().getTrains().stream().anyMatch(t -> t.getNumber().equals("12951") && t.getDelayMinutes() == 5));

        assertTrue(auditLog.count() >= 2);
        assertTrue(auditLog.recent(50).stream().anyMatch(e -> e.getEventType().equals("DELAY")));
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(ok -> ok));
        assertNotNull(saver.getLastSavedAt());
        assertNull(saver.getLastError());
    }

    // Scenario: the database is closed under the auto-saver (like a disk failure). Expected: the change in memory
    // still succeeds, and the failure is reported to listeners instead of crashing the screen.
    @Test
    void autoSaverReportsFailureWithoutThrowing() {
        SchedulingService service = demoService();
        AutoSaver saver = new AutoSaver(service, repository, auditLog);
        List<String> messages = new ArrayList<>();
        saver.addSaveListener((ok, message) -> messages.add(ok ? "ok" : "failed"));
        service.getEvents().subscribe(saver);
        database.close();

        assertDoesNotThrow(service::allocatePendingTrains);
        assertTrue(service.getSchedule().size() > 0);
        assertTrue(messages.contains("failed"));
        assertNotNull(saver.getLastError());
    }

    // Scenario: platform categories are written as text. Expected: the conversion works both ways for every combination.
    @Test
    void trainTypeTextConversionIsLossless() {
        for (int mask = 1; mask < 8; mask++) {
            java.util.Set<com.railsync.model.TrainType> types = java.util.EnumSet.noneOf(com.railsync.model.TrainType.class);
            for (com.railsync.model.TrainType type : com.railsync.model.TrainType.values()) {
                if ((mask & (1 << type.ordinal())) != 0) {
                    types.add(type);
                }
            }
            assertEquals(types, PlatformDao.textToTypes(PlatformDao.typesToText(types)));
        }
    }
}
