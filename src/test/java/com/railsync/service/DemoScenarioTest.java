package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PriorityStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves every promise of the demo scenario, so the live demo never depends on luck.
 */
class DemoScenarioTest {

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(new Station("Demo"), new Schedule(), new FcfsStrategy(), new ScheduleEventManager());
        DemoScenarioLoader.load(service);
    }

    private Train train(String number) {
        return service.getTrainManager().findByNumber(number);
    }

    // Scenario: load the demo. Expected: at least 6 platforms and about 12 trains of all three types.
    @Test
    void scenarioHasEnoughData() {
        assertEquals(6, service.getStation().getPlatformCount());
        assertEquals(12, service.getTrainManager().getAllTrains().size());
    }

    // Scenario: FCFS allocation. Expected: conflicts detected and F-999 unallocated because it fits nowhere.
    @Test
    void fcfsHasConflictsAndOneImpossibleTrain() {
        service.allocatePendingTrains();
        assertTrue(service.getConflictsDetected() >= 3);
        assertEquals(TrainStatus.UNALLOCATED, train("F-999").getStatus());
        assertEquals(1, service.calculateMetrics().getUnallocatedTrains());
    }

    // Scenario: FCFS vs Priority on the same data. Expected: different winners (L-101 vs 12002 at 08:15) and L-102 lost under Priority.
    @Test
    void fcfsAndPriorityGiveVisiblyDifferentResults() {
        service.allocatePendingTrains();
        assertEquals("08:15", service.findReservation(train("L-101")).getStart().toLocalTime().toString());
        assertEquals("08:30", service.findReservation(train("12002")).getStart().toLocalTime().toString());
        assertEquals(TrainStatus.ALLOCATED, train("L-102").getStatus());

        service.resetAllocations();
        service.setStrategy(new PriorityStrategy());
        service.allocatePendingTrains();
        assertEquals("08:15", service.findReservation(train("12002")).getStart().toLocalTime().toString());
        assertEquals(TrainStatus.UNALLOCATED, train("L-102").getStatus());
    }

    // Scenario: after FCFS, delay 12951 by 5 min. Expected: RIPPLE pushing L-102 and 12909 (cascade of 2).
    @Test
    void scriptedDelayCausesRipple() {
        service.allocatePendingTrains();
        DelayReport report = service.delayTrain(train(DemoScenarioLoader.RIPPLE_TRAIN_NUMBER),
                DemoScenarioLoader.RIPPLE_DELAY_MINUTES);
        assertEquals(DelayReport.Outcome.RIPPLE, report.getOutcome());
        assertEquals(2, report.getCascadeCount());
        assertEquals("L-102", report.getCascade().get(0).getTrain().getNumber());
        assertEquals("12909", report.getCascade().get(1).getTrain().getNumber());
    }

    // Scenario: after FCFS and the ripple, PF-2 breaks down. Expected: S-201 reassigned to PF-4; at least one train stranded.
    @Test
    void scriptedBreakdownMovesSomeAndStrandsOthers() {
        service.allocatePendingTrains();
        service.delayTrain(train(DemoScenarioLoader.RIPPLE_TRAIN_NUMBER), DemoScenarioLoader.RIPPLE_DELAY_MINUTES);
        Platform broken = service.getPlatformManager().findById(DemoScenarioLoader.BREAKDOWN_PLATFORM_ID);
        BreakdownReport report = service.reportBreakdown(broken);
        assertTrue(report.getMovedCount() >= 1);
        assertTrue(report.getStrandedCount() >= 1);
        assertEquals(4, train("S-201").getAssignedPlatform().getNumber());
    }

    // Scenario: load the demo twice. Expected: no duplicate errors; the second load replaces the first.
    @Test
    void loadingTwiceReplacesScenario() {
        service.allocatePendingTrains();
        assertDoesNotThrow(() -> DemoScenarioLoader.load(service));
        assertEquals(12, service.getTrainManager().getAllTrains().size());
        assertEquals(0, service.getSchedule().size());
    }
}
