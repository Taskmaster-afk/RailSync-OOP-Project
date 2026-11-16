package com.railsync.service;

import com.railsync.exception.InvalidTrainException;
import com.railsync.exception.RailSyncException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.PlatformStatus;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.strategy.FcfsStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CSV save/load and Undo (Command pattern) for allocations and delays.
 */
class CsvAndUndoTest {

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(new Station("Demo"), new Schedule(), new FcfsStrategy(), new ScheduleEventManager());
        DemoScenarioLoader.load(service);
    }

    private Train train(String number) {
        return service.getTrainManager().findByNumber(number);
    }

    /** Records every booking as "number@platform@start" so two states can be compared. */
    private List<String> bookings() {
        List<String> list = new ArrayList<>();
        for (PlatformReservation booking : service.getSchedule().getAllReservations()) {
            list.add(booking.getTrain().getNumber() + "@" + booking.getPlatform().getNumber() + "@" + booking.getStart());
        }
        list.sort(null);
        return list;
    }

    // ---------- CSV ----------

    // Scenario: save the demo (with a delay and a failed platform) and load it back. Expected: identical values.
    @Test
    void csvRoundTripKeepsEverything(@TempDir Path folder) {
        train("12951").applyDelay(7);
        service.getPlatformManager().findById("P4").markFailed();
        Path file = folder.resolve("demo.csv");
        ScenarioCsv.save(Scenario.fromLive(service), file);

        Scenario loaded = ScenarioCsv.load(file);
        assertEquals(6, loaded.getPlatforms().size());
        assertEquals(12, loaded.getTrains().size());
        Train rajdhani = loaded.getTrains().get(0);
        assertEquals("12951", rajdhani.getNumber());
        assertTrue(rajdhani instanceof ExpressTrain);
        assertEquals(7, rajdhani.getDelayMinutes());
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 0), rajdhani.getScheduledArrival());
        assertEquals(PlatformStatus.FAILED, loaded.getPlatforms().get(3).getStatus());
        assertTrue(loaded.getPlatforms().get(0).supports(TrainType.EXPRESS));
        assertFalse(loaded.getPlatforms().get(0).supports(TrainType.FREIGHT));
    }

    // Scenario: a train name containing a comma and a quote. Expected: quoted on save, read back exactly.
    @Test
    void namesWithCommasAndQuotesSurvive() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 16, 9, 0);
        List<Train> trains = new ArrayList<>();
        trains.add(new PassengerTrain("T1", "100", "Mumbai, \"Fast\" Local", t, t.plusMinutes(5), 3, 8));
        List<Platform> platforms = new ArrayList<>();
        platforms.add(new Platform("P1", 1, 300, EnumSet.of(TrainType.PASSENGER)));
        Scenario back = ScenarioCsv.fromLines(ScenarioCsv.toLines(new Scenario(platforms, trains)));
        assertEquals("Mumbai, \"Fast\" Local", back.getTrains().get(0).getName());
    }

    // Scenario: line 3 has departure before arrival. Expected: error names line 3 and the reason.
    @Test
    void badLineIsReportedWithItsNumber() {
        List<String> lines = new ArrayList<>();
        lines.add("# RailSync scenario v1");
        lines.add("PLATFORM,P1,1,300,OPERATIONAL,PASSENGER");
        lines.add("TRAIN,T1,PASSENGER,100,Local,2026-11-16T09:00,2026-11-16T08:00,3,8,0");
        RailSyncException error = assertThrows(RailSyncException.class, () -> ScenarioCsv.fromLines(lines));
        assertTrue(error.getMessage().startsWith("Line 3:"));
        assertTrue(error.getMessage().contains("Departure must be after arrival"));
    }

    // Scenario: unknown type, wrong field count, bad number, duplicate platform. Expected: each refused.
    @Test
    void malformedFilesAreRefused() {
        assertThrows(RailSyncException.class, () -> ScenarioCsv.fromLines(List.of("TRAIN,T1,ROCKET,1,x,2026-11-16T09:00,2026-11-16T09:10,3,8,0")));
        assertThrows(RailSyncException.class, () -> ScenarioCsv.fromLines(List.of("PLATFORM,P1,1,300")));
        assertThrows(RailSyncException.class, () -> ScenarioCsv.fromLines(List.of("PLATFORM,P1,one,300,OPERATIONAL,PASSENGER")));
        assertThrows(RailSyncException.class, () -> ScenarioCsv.fromLines(List.of(
                "PLATFORM,P1,1,300,OPERATIONAL,PASSENGER", "PLATFORM,P2,1,300,OPERATIONAL,PASSENGER")));
    }

    // Scenario: open a file that does not exist. Expected: friendly RailSyncException, not an IOException.
    @Test
    void missingFileGivesFriendlyError(@TempDir Path folder) {
        assertThrows(RailSyncException.class, () -> ScenarioCsv.load(folder.resolve("nope.csv")));
    }

    // ---------- Undo ----------

    // Scenario: fresh demo. Expected: nothing to undo.
    @Test
    void nothingToUndoAtStart() {
        assertFalse(service.canUndo());
        assertNull(service.undoLast());
    }

    // Scenario: allocate with FCFS, then undo. Expected: no bookings, every train SCHEDULED again (F-999 too).
    @Test
    void undoAllocation() {
        service.allocatePendingTrains();
        assertEquals("allocation (FCFS (First-Fit))", service.getUndoDescription());
        service.undoLast();
        assertEquals(0, service.getSchedule().size());
        for (Train t : service.getTrainManager().getAllTrains()) {
            assertEquals(TrainStatus.SCHEDULED, t.getStatus());
        }
        assertFalse(service.canUndo());
    }

    // Scenario: allocate, then the ripple delay, then undo once. Expected: delay gone, every booking back where it was.
    @Test
    void undoDelayRestoresRippleExactly() {
        service.allocatePendingTrains();
        List<String> before = bookings();
        DelayReport report = service.delayTrain(train("12951"), 5);
        assertEquals(DelayReport.Outcome.RIPPLE, report.getOutcome());
        assertNotEquals(before, bookings());

        service.undoLast();
        assertEquals(before, bookings());
        assertEquals(0, train("12951").getDelayMinutes());
        assertEquals("allocation (FCFS (First-Fit))", service.getUndoDescription());
    }

    // Scenario: allocate, delay, undo twice. Expected: back to the untouched demo.
    @Test
    void undoTwiceGoesBackTwoSteps() {
        service.allocatePendingTrains();
        service.delayTrain(train("12951"), 5);
        service.undoLast();
        service.undoLast();
        assertEquals(0, service.getSchedule().size());
        assertEquals(0, train("12951").getDelayMinutes());
    }

    // Scenario: allocate, then a breakdown (not undoable). Expected: history cleared, Undo no longer offered.
    @Test
    void nonUndoableChangeClearsHistory() {
        service.allocatePendingTrains();
        service.reportBreakdown(service.getPlatformManager().findById("P2"));
        assertFalse(service.canUndo());
    }

    // Scenario: max wait 5; 12951 delayed 20 clashes with 12909 on PF-1 (only platform long enough), the ripple
    // would make 12909 wait 10 > 5 and waiting would take 25 > 5, so the delay FAILS. Then undo. Expected: booking back.
    @Test
    void undoFailedDelayGivesPlatformBack() {
        service.setMaxWaitMinutes(5);
        service.allocatePendingTrains();
        List<String> before = bookings();
        DelayReport report = service.delayTrain(train("12951"), 20);
        assertEquals(DelayReport.Outcome.FAILED, report.getOutcome());
        service.undoLast();
        assertEquals(before, bookings());
        assertEquals(TrainStatus.ALLOCATED, train("12951").getStatus());
    }

    // Scenario: invalid delay of 0. Expected: exception and nothing put on the undo stack.
    @Test
    void invalidDelayIsNotRecorded() {
        service.allocatePendingTrains();
        assertThrows(InvalidTrainException.class, () -> service.delayTrain(train("12951"), 0));
        assertEquals("allocation (FCFS (First-Fit))", service.getUndoDescription());
    }

    // Scenario: reduceDelay with more than the current delay. Expected: refused (delay never below zero).
    @Test
    void reduceDelayIsValidated() {
        Train rajdhani = train("12951");
        rajdhani.applyDelay(5);
        assertThrows(InvalidTrainException.class, () -> rajdhani.reduceDelay(6));
        rajdhani.reduceDelay(5);
        assertEquals(0, rajdhani.getDelayMinutes());
    }
}
