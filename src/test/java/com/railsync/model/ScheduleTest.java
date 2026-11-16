package com.railsync.model;

import com.railsync.exception.PlatformUnavailableException;
import com.railsync.exception.RailSyncException;
import com.railsync.exception.SchedulingConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Schedule and PlatformReservation (composition and the
 * train-platform link). Time-overlap tests come in Stage 2.
 */
class ScheduleTest {

    private static final LocalDateTime T10_00 = LocalDateTime.of(2026, 11, 16, 10, 0);

    private Schedule schedule;
    private Platform platform1;
    private Platform freightOnly;
    private Train express;

    @BeforeEach
    void setUp() {
        schedule = new Schedule();
        platform1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER));
        freightOnly = new Platform("P2", 2, 800, EnumSet.of(TrainType.FREIGHT));
        express = new ExpressTrain("T1", "12951", "Rajdhani", T10_00, T10_00.plusMinutes(10), 4, 12);
    }

    // Scenario: new schedule. Expected: default buffer is 5 minutes and it is configurable.
    @Test
    void bufferDefaultsToFiveAndIsConfigurable() {
        assertEquals(5, schedule.getClearanceBufferMinutes());
        assertEquals(8, new Schedule(8).getClearanceBufferMinutes());
        schedule.setClearanceBufferMinutes(0);
        assertEquals(0, schedule.getClearanceBufferMinutes());
        assertThrows(RailSyncException.class, () -> schedule.setClearanceBufferMinutes(-1));
    }

    // Scenario: reserve a compatible platform at arrival. Expected: reservation [10:00,10:10), train linked to platform.
    @Test
    void reserveCreatesReservationAndLinksTrain() {
        PlatformReservation reservation = schedule.reserve(express, platform1, T10_00);
        assertEquals(T10_00, reservation.getStart());
        assertEquals(T10_00.plusMinutes(10), reservation.getEnd());
        assertSame(platform1, express.getAssignedPlatform());
        assertEquals(TrainStatus.ALLOCATED, express.getStatus());
        assertSame(reservation, schedule.findReservation(express));
        assertTrue(schedule.hasReservations(platform1));
    }

    // Scenario: express on a freight-only platform. Expected: PlatformUnavailableException, nothing recorded.
    @Test
    void reserveRejectsIncompatiblePlatform() {
        assertThrows(PlatformUnavailableException.class, () -> schedule.reserve(express, freightOnly, T10_00));
        assertEquals(0, schedule.size());
        assertNull(express.getAssignedPlatform());
    }

    // Scenario: the same train is reserved twice. Expected: SchedulingConflictException.
    @Test
    void trainCannotHoldTwoReservations() {
        schedule.reserve(express, platform1, T10_00);
        Platform platform3 = new Platform("P3", 3, 500, EnumSet.of(TrainType.EXPRESS));
        assertThrows(SchedulingConflictException.class, () -> schedule.reserve(express, platform3, T10_00));
    }

    // Scenario: start time before the train arrives. Expected: SchedulingConflictException.
    @Test
    void reservationCannotStartBeforeArrival() {
        assertThrows(SchedulingConflictException.class,
                () -> schedule.reserve(express, platform1, T10_00.minusMinutes(1)));
    }

    // Scenario: a waiting train starts 15 min late. Expected: reservation still lasts its 10-minute dwell.
    @Test
    void lateStartKeepsDwellLength() {
        PlatformReservation reservation = schedule.reserve(express, platform1, T10_00.plusMinutes(15));
        assertEquals(10, reservation.getDuration().toMinutes());
        assertEquals(T10_00.plusMinutes(25), reservation.getEnd());
    }

    // Scenario: release a reserved train. Expected: reservation gone, train unlinked and SCHEDULED again.
    @Test
    void releaseRemovesReservationAndUnlinksTrain() {
        schedule.reserve(express, platform1, T10_00);
        assertTrue(schedule.release(express));
        assertEquals(0, schedule.size());
        assertNull(express.getAssignedPlatform());
        assertEquals(TrainStatus.SCHEDULED, express.getStatus());
        assertFalse(schedule.hasReservations(platform1));
    }

    // Scenario: release a train that has no reservation. Expected: returns false, no exception.
    @Test
    void releasingUnallocatedTrainReturnsFalse() {
        assertFalse(schedule.release(express));
    }

    // Scenario: two trains booked on platform 1 in reverse time order. Expected: getReservationsOn returns earliest first.
    @Test
    void reservationsOnPlatformAreSortedByStart() {
        Train later = new PassengerTrain("T2", "66001", "Local", T10_00.plusHours(1), T10_00.plusHours(1).plusMinutes(5), 2, 8);
        schedule.reserve(later, platform1, later.getExpectedArrival());
        schedule.reserve(express, platform1, T10_00);
        assertEquals("12951", schedule.getReservationsOn(platform1).get(0).getTrain().getNumber());
        assertEquals("66001", schedule.getReservationsOn(platform1).get(1).getTrain().getNumber());
    }

    // Scenario: caller tries to change the list returned by getAllReservations. Expected: UnsupportedOperationException.
    @Test
    void reservationListIsReadOnly() {
        schedule.reserve(express, platform1, T10_00);
        assertThrows(UnsupportedOperationException.class, () -> schedule.getAllReservations().clear());
    }
}
