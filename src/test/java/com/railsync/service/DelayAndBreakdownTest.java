package com.railsync.service;

import com.railsync.exception.InvalidTrainException;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.PlatformStatus;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DelayManager: every delay outcome including the ripple cascade, and
 * platform breakdown handling.
 */
class DelayAndBreakdownTest {

    private Station station;
    private Schedule schedule;
    private PlatformAllocator allocator;
    private DelayManager delays;
    private Platform p1;

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    private static Train local(String number, int h, int m, int dwell) {
        return new PassengerTrain("T-" + number, number, "Local " + number, at(h, m), at(h, m).plusMinutes(dwell), 3, 8);
    }

    @BeforeEach
    void setUp() {
        station = new Station("S");
        p1 = addPlatform(1);
        schedule = new Schedule(5);
        allocator = new PlatformAllocator(station, schedule, 20);
        delays = new DelayManager(schedule, allocator);
    }

    private Platform addPlatform(int number) {
        Platform platform = new Platform("P" + number, number, 500, EnumSet.of(TrainType.PASSENGER));
        station.addPlatform(platform);
        return platform;
    }

    private Train book(Train train, Platform platform) {
        schedule.reserve(train, platform, train.getExpectedArrival());
        return train;
    }

    private LocalDateTime startOf(Train train) {
        return schedule.findReservation(train).getStart();
    }

    // Scenario: delay a train that has no platform. Expected: NOT_ALLOCATED, delay recorded, no booking.
    @Test
    void delayWithoutBookingOnlyChangesTimes() {
        Train a = local("A", 10, 0, 10);
        DelayReport report = delays.delayTrain(a, 10);
        assertEquals(DelayReport.Outcome.NOT_ALLOCATED, report.getOutcome());
        assertEquals(10, a.getDelayMinutes());
        assertNull(schedule.findReservation(a));
    }

    // Scenario: train arrives 10:00 but its slot is 10:20 (it was queued); delayed 10 min. Expected: STILL_VALID, slot unchanged.
    @Test
    void bookingStillValidWhenTrainWasWaitingAnyway() {
        Train a = local("A", 10, 0, 10);
        schedule.reserve(a, p1, at(10, 20));
        DelayReport report = delays.delayTrain(a, 10);
        assertEquals(DelayReport.Outcome.STILL_VALID, report.getOutcome());
        assertEquals(at(10, 20), startOf(a));
        assertFalse(report.isConflict());
    }

    // Scenario: A alone on PF-1, delayed 15. Expected: KEPT_PLATFORM, new start 10:15.
    @Test
    void delayKeepsPlatformWhenFree() {
        Train a = book(local("A", 10, 0, 10), p1);
        DelayReport report = delays.delayTrain(a, 15);
        assertEquals(DelayReport.Outcome.KEPT_PLATFORM, report.getOutcome());
        assertEquals(at(10, 15), startOf(a));
        assertSame(p1, a.getAssignedPlatform());
        assertEquals(15, report.getChanges().get(0).getShiftMinutes());
    }

    // Scenario: delay makes A clash with B on PF-1; PF-2 is free. Expected: REASSIGNED to PF-2 at 10:10, B untouched.
    @Test
    void delayMovesToAnotherFreePlatform() {
        Platform p2 = addPlatform(2);
        Train a = book(local("A", 10, 0, 10), p1);
        Train b = book(local("B", 10, 15, 10), p1);
        DelayReport report = delays.delayTrain(a, 10);
        assertEquals(DelayReport.Outcome.REASSIGNED, report.getOutcome());
        assertSame(p2, a.getAssignedPlatform());
        assertEquals(at(10, 10), startOf(a));
        assertEquals(at(10, 15), startOf(b));
        assertTrue(report.getChanges().get(0).changedPlatform());
    }

    private Train[] buildRippleChain() {
        Train a = book(local("A", 10, 0, 10), p1);
        Train b = book(local("B", 10, 15, 10), p1);
        Train c = book(local("C", 10, 30, 10), p1);
        Train d = book(local("D", 11, 0, 10), p1);
        return new Train[]{a, b, c, d};
    }

    // Scenario: only PF-1 with A 10:00, B 10:15, C 10:30, D 11:00; A delayed 10. Expected: RIPPLE pushes B and C +10, D unchanged.
    @Test
    void delayRipplePushesFollowingTrains() {
        Train[] t = buildRippleChain();
        DelayReport report = delays.delayTrain(t[0], 10);
        assertEquals(DelayReport.Outcome.RIPPLE, report.getOutcome());
        assertEquals(2, report.getCascadeCount());
        assertEquals("B", report.getCascade().get(0).getTrain().getNumber());
        assertEquals("C", report.getCascade().get(1).getTrain().getNumber());
        assertEquals(at(10, 10), startOf(t[0]));
        assertEquals(at(10, 25), startOf(t[1]));
        assertEquals(at(10, 40), startOf(t[2]));
        assertEquals(at(11, 0), startOf(t[3]));
        // Pushed trains are not "delayed" themselves; they wait at the outer signal.
        assertEquals(0, t[1].getDelayMinutes());
        assertNoOverlaps(p1);
    }

    // Scenario: same chain, A delayed 30, max wait 20 (B would wait 30). Expected: no ripple; A waits for a later slot (10:45), B-D untouched.
    @Test
    void rippleRefusedWhenPushTooLargeButTrainCanWait() {
        Train[] t = buildRippleChain();
        DelayReport report = delays.delayTrain(t[0], 30);
        assertEquals(DelayReport.Outcome.REASSIGNED, report.getOutcome());
        assertEquals(0, report.getCascadeCount());
        assertEquals(at(10, 45), startOf(t[0]));
        assertEquals(at(10, 15), startOf(t[1]));
        assertEquals(at(10, 30), startOf(t[2]));
        assertNoOverlaps(p1);
    }

    // Scenario: same chain, max wait 5, A delayed 10. Expected: FAILED, A unallocated with reason; other trains untouched.
    @Test
    void delayFailsSafelyWhenNoOptionWorks() {
        allocator.setMaxWaitMinutes(5);
        Train[] t = buildRippleChain();
        DelayReport report = delays.delayTrain(t[0], 10);
        assertEquals(DelayReport.Outcome.FAILED, report.getOutcome());
        assertEquals(TrainStatus.UNALLOCATED, t[0].getStatus());
        assertNull(schedule.findReservation(t[0]));
        assertTrue(report.getMessage().contains("would wait"));
        assertEquals(at(10, 15), startOf(t[1]));
        assertFalse(report.getChanges().get(0).hasNewBooking());
    }

    // Scenario: delay of 0 minutes. Expected: InvalidTrainException and booking unchanged.
    @Test
    void invalidDelayChangesNothing() {
        Train a = book(local("A", 10, 0, 10), p1);
        assertThrows(InvalidTrainException.class, () -> delays.delayTrain(a, 0));
        assertEquals(at(10, 0), startOf(a));
    }

    // Scenario: PF-1 fails with A (10:00) and B (11:00); PF-2 busy 10:00-10:30, PF-3 busy 11:00-11:30. Expected: A -> PF-3, B -> PF-2.
    @Test
    void breakdownMovesTrainsToOtherPlatforms() {
        Platform p2 = addPlatform(2);
        Platform p3 = addPlatform(3);
        Train a = book(local("A", 10, 0, 10), p1);
        Train b = book(local("B", 11, 0, 10), p1);
        book(local("X", 10, 0, 30), p2);
        book(local("Y", 11, 0, 30), p3);
        BreakdownReport report = delays.handleBreakdown(p1);
        assertEquals(PlatformStatus.FAILED, p1.getStatus());
        assertEquals(2, report.getMovedCount());
        assertSame(p3, a.getAssignedPlatform());
        assertSame(p2, b.getAssignedPlatform());
        assertFalse(schedule.hasReservations(p1));
    }

    // Scenario: PF-1 fails; PF-2 busy all morning. Expected: train stranded, UNALLOCATED, reason given.
    @Test
    void breakdownReportsTrainsThatCannotMove() {
        Platform p2 = addPlatform(2);
        book(local("LONG", 9, 0, 180), p2);
        Train a = book(local("A", 10, 0, 10), p1);
        BreakdownReport report = delays.handleBreakdown(p1);
        assertEquals(1, report.getStrandedCount());
        assertEquals(TrainStatus.UNALLOCATED, a.getStatus());
        assertTrue(report.getOutcomes().get(0).getMessage().contains("NOT allocated"));
    }

    // Scenario: breakdown of a platform with no bookings. Expected: platform failed, empty report.
    @Test
    void breakdownOfEmptyPlatform() {
        BreakdownReport report = delays.handleBreakdown(p1);
        assertEquals(0, report.getOutcomes().size());
        assertFalse(p1.isOperational());
    }

    private void assertNoOverlaps(Platform platform) {
        List<PlatformReservation> list = schedule.getReservationsOn(platform);
        for (int i = 1; i < list.size(); i++) {
            int gap = schedule.requiredGapMinutes(list.get(i - 1).getTrain(), list.get(i).getTrain());
            assertFalse(list.get(i).getStart().isBefore(list.get(i - 1).getEnd().plusMinutes(gap)));
        }
    }
}
