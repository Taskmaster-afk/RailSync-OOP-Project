package com.railsync.model;

import com.railsync.exception.SchedulingConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the half-open interval rule and the clearance buffer in Schedule.
 */
class OverlapTest {

    private Schedule schedule;
    private Platform platform1;
    private Platform platform2;

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    private static Train passenger(String id, int fromHour, int fromMinute, int dwellMinutes) {
        LocalDateTime arrival = at(fromHour, fromMinute);
        return new PassengerTrain(id, "N" + id, "Local " + id, arrival, arrival.plusMinutes(dwellMinutes), 3, 8);
    }

    @BeforeEach
    void setUp() {
        schedule = new Schedule(5);
        platform1 = new Platform("P1", 1, 600, EnumSet.allOf(TrainType.class));
        platform2 = new Platform("P2", 2, 600, EnumSet.allOf(TrainType.class));
    }

    // Scenario: [10:00,10:30) vs [10:30,11:00). Expected: no overlap (half-open, touching ends).
    @Test
    void touchingIntervalsDoNotOverlap() {
        assertFalse(Schedule.overlaps(at(10, 0), at(10, 30), at(10, 30), at(11, 0)));
    }

    // Scenario: [10:00,10:30) vs [10:29,11:00). Expected: overlap at 10:29.
    @Test
    void oneMinuteOverlapIsDetected() {
        assertTrue(Schedule.overlaps(at(10, 0), at(10, 30), at(10, 29), at(11, 0)));
    }

    // Scenario: [10:00,11:00) contains [10:15,10:20). Expected: overlap.
    @Test
    void containedIntervalOverlaps() {
        assertTrue(Schedule.overlaps(at(10, 0), at(11, 0), at(10, 15), at(10, 20)));
    }

    // Scenario: A 10:00-10:30, B 10:30-11:00, buffer 5. Expected: conflict (B must start 10:35 or later).
    @Test
    void backToBackConflictsBecauseOfBuffer() {
        Train a = passenger("A", 10, 0, 30);
        Train b = passenger("B", 10, 30, 30);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        assertSame(schedule.findReservation(a), schedule.findConflict(b, platform1, at(10, 30)));
        assertNull(schedule.findConflict(b, platform1, at(10, 35)));
    }

    // Scenario: same back-to-back trains with buffer 0. Expected: allowed (pure half-open rule).
    @Test
    void backToBackIsFineWithZeroBuffer() {
        schedule.setClearanceBufferMinutes(0);
        Train a = passenger("A", 10, 0, 30);
        Train b = passenger("B", 10, 30, 30);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        assertNull(schedule.findConflict(b, platform1, at(10, 30)));
    }

    // Scenario: new train ends 2 min before an existing one starts, buffer 5. Expected: conflict (gap applies on both sides).
    @Test
    void bufferAppliesBeforeAnExistingBooking() {
        Train later = passenger("L", 10, 0, 30);
        Train earlier = passenger("E", 9, 48, 10);
        schedule.reserve(later, platform1, later.getExpectedArrival());
        assertNotNull(schedule.findConflict(earlier, platform1, at(9, 48)));
    }

    // Scenario: freight (needs 10) leaves 10:30, passenger starts 10:35. Expected: conflict; 10:40 is fine.
    @Test
    void freightNeedsLargerGap() {
        Train freight = new FreightTrain("F", "F1", "Coal", at(10, 0), at(10, 30), 1, 20);
        Train next = passenger("B", 10, 35, 10);
        schedule.reserve(freight, platform1, freight.getExpectedArrival());
        assertEquals(10, schedule.requiredGapMinutes(freight, next));
        assertNotNull(schedule.findConflict(next, platform1, at(10, 35)));
        assertNull(schedule.findConflict(next, platform1, at(10, 40)));
    }

    // Scenario: overlapping times on different platforms. Expected: no conflict.
    @Test
    void differentPlatformsNeverConflict() {
        Train a = passenger("A", 10, 0, 30);
        Train b = passenger("B", 10, 10, 30);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        assertNull(schedule.findConflict(b, platform2, at(10, 10)));
    }

    // Scenario: reserve() asked to double-book overlapping trains. Expected: SchedulingConflictException, nothing stored.
    @Test
    void reserveRefusesOverlap() {
        Train a = passenger("A", 10, 0, 30);
        Train b = passenger("B", 10, 10, 30);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        assertThrows(SchedulingConflictException.class, () -> schedule.reserve(b, platform1, at(10, 10)));
        assertEquals(1, schedule.size());
        assertNull(b.getAssignedPlatform());
    }

    // Scenario: a train is checked against its own booking (e.g. after a delay). Expected: ignored, no conflict.
    @Test
    void trainDoesNotConflictWithItself() {
        Train a = passenger("A", 10, 0, 30);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        assertNull(schedule.findConflict(a, platform1, at(10, 5)));
    }

    // Scenario: bookings [10:00,10:30) and [10:40,11:00), 10-min train from 10:10. Expected: earliest start 11:05.
    @Test
    void earliestFreeStartJumpsOverChainOfBookings() {
        Train a = passenger("A", 10, 0, 30);
        Train c = passenger("C", 10, 40, 20);
        Train b = passenger("B", 10, 10, 10);
        schedule.reserve(a, platform1, a.getExpectedArrival());
        schedule.reserve(c, platform1, c.getExpectedArrival());
        assertEquals(at(11, 5), schedule.earliestFreeStart(b, platform1, at(10, 10)));
    }

    // Scenario: empty platform. Expected: earliest start is the requested time itself.
    @Test
    void earliestFreeStartOnEmptyPlatformIsNow() {
        Train b = passenger("B", 10, 10, 10);
        assertEquals(at(10, 10), schedule.earliestFreeStart(b, platform1, at(10, 10)));
    }
}
