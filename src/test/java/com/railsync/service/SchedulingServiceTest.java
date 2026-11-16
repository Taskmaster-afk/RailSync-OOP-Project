package com.railsync.service;

import com.railsync.exception.InvalidTrainException;
import com.railsync.exception.RailSyncException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.observer.ScheduleEventType;
import com.railsync.observer.ScheduleObserver;
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
 * Tests the SchedulingService facade: strategy injection and switching,
 * observer notifications, and the rules it coordinates.
 */
class SchedulingServiceTest {

    private SchedulingService service;
    private final List<ScheduleEventType> received = new ArrayList<>();
    private Platform p1;

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    @BeforeEach
    void setUp() {
        ScheduleEventManager events = new ScheduleEventManager();
        events.subscribe(new ScheduleObserver() {
            @Override
            public void onScheduleEvent(ScheduleEvent event) {
                received.add(event.getType());
            }
        });
        service = new SchedulingService(new Station("S"), new Schedule(5), new FcfsStrategy(), events);
        p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER));
        service.addPlatform(p1);
        received.clear();
    }

    /** The Stage 2 FCFS-vs-Priority trio: one platform busy until 10:20. */
    private Train[] addContentionTrains() {
        Train p1Train = new PassengerTrain("T1", "P1", "Local 1", at(10, 0), at(10, 20), 3, 8);
        Train p2Train = new PassengerTrain("T2", "P2", "Local 2", at(10, 5), at(10, 15), 3, 8);
        Train e1 = new ExpressTrain("T3", "E1", "Express", at(10, 10), at(10, 20), 4, 12);
        service.addTrain(p1Train);
        service.addTrain(p2Train);
        service.addTrain(e1);
        return new Train[]{p1Train, p2Train, e1};
    }

    // Scenario: service built with FCFS, then switched to Priority. Expected: getStrategy follows; null refused.
    @Test
    void strategyIsInjectedAndSwappable() {
        assertEquals("FCFS (First-Fit)", service.getStrategy().getName());
        service.setStrategy(new PriorityStrategy());
        assertEquals("Priority", service.getStrategy().getName());
        assertThrows(RailSyncException.class, () -> service.setStrategy(null));
    }

    // Scenario: allocate with FCFS, reset, allocate with Priority. Expected: different train wins the 10:25 slot.
    @Test
    void switchingStrategyChangesLiveResult() {
        Train[] t = addContentionTrains();
        service.allocatePendingTrains();
        assertEquals(TrainStatus.ALLOCATED, t[1].getStatus());
        assertEquals(TrainStatus.UNALLOCATED, t[2].getStatus());

        service.resetAllocations();
        assertEquals(TrainStatus.SCHEDULED, t[2].getStatus());
        service.setStrategy(new PriorityStrategy());
        service.allocatePendingTrains();
        assertEquals(TrainStatus.ALLOCATED, t[2].getStatus());
        assertEquals(TrainStatus.UNALLOCATED, t[1].getStatus());
    }

    // Scenario: add train, allocate with a conflict, delay, breakdown. Expected: matching events reach the observer.
    @Test
    void observersAreNotifiedOfEveryChange() {
        Train[] t = addContentionTrains();
        assertTrue(received.contains(ScheduleEventType.TRAINS_CHANGED));
        service.allocatePendingTrains();
        assertTrue(received.contains(ScheduleEventType.ALLOCATION));
        assertTrue(received.contains(ScheduleEventType.CONFLICT));
        service.delayTrain(t[0], 5);
        assertTrue(received.contains(ScheduleEventType.DELAY));
        service.reportBreakdown(p1);
        assertTrue(received.contains(ScheduleEventType.BREAKDOWN));
    }

    // Scenario: remove an allocated train. Expected: refused with a friendly message; allowed after release.
    @Test
    void allocatedTrainCannotBeRemovedThroughService() {
        Train[] t = addContentionTrains();
        service.allocatePendingTrains();
        assertThrows(InvalidTrainException.class, () -> service.removeTrain(t[0]));
        service.releaseTrain(t[0]);
        service.removeTrain(t[0]);
        assertNull(service.getTrainManager().findByNumber("P1"));
    }

    // Scenario: an allocated train's times are moved one hour later. Expected: re-placed at the new time.
    @Test
    void updatingAllocatedTrainReplacesIt() {
        Train train = new PassengerTrain("T1", "100", "Local", at(10, 0), at(10, 10), 3, 8);
        service.addTrain(train);
        service.allocatePendingTrains();
        AllocationResult result = service.updateTrain(train, "Renamed", 4, at(11, 0), at(11, 10));
        assertTrue(result.isAllocated());
        assertEquals(at(11, 0), service.findReservation(train).getStart());
        assertEquals("Renamed", train.getName());
    }

    // Scenario: allocation where one train waits and one is refused. Expected: metrics show 2 conflicts and 1 unallocated.
    @Test
    void metricsReflectLiveSchedule() {
        addContentionTrains();
        service.allocatePendingTrains();
        ScheduleMetrics metrics = service.calculateMetrics();
        assertEquals(3, metrics.getTotalTrains());
        assertEquals(1, metrics.getUnallocatedTrains());
        assertEquals(2, metrics.getConflictsDetected());
    }

    // Scenario: settings changed through the service. Expected: values applied and SETTINGS_CHANGED events sent.
    @Test
    void settingsAreAppliedAndAnnounced() {
        service.setMaxWaitMinutes(10);
        service.setClearanceBufferMinutes(3);
        service.setPreemptionEnabled(true);
        assertEquals(10, service.getMaxWaitMinutes());
        assertEquals(3, service.getSchedule().getClearanceBufferMinutes());
        assertTrue(service.isPreemptionEnabled());
        assertEquals(3, received.size());
    }

    // Scenario: clear everything. Expected: no trains, no platforms, no bookings.
    @Test
    void clearEverythingEmptiesTheScenario() {
        addContentionTrains();
        service.allocatePendingTrains();
        service.clearEverything();
        assertEquals(0, service.getTrainManager().getAllTrains().size());
        assertEquals(0, service.getStation().getPlatformCount());
        assertEquals(0, service.getSchedule().size());
    }
}
