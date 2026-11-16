package com.railsync.model;

import com.railsync.exception.InvalidTrainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Train hierarchy: creation, validation and the overridden
 * behaviour of each subclass.
 */
class TrainTest {

    private static final LocalDateTime T10_00 = LocalDateTime.of(2026, 11, 16, 10, 0);

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    // Scenario: a valid express train is created. Expected: all fields stored, status SCHEDULED, no platform.
    @Test
    void validTrainIsCreatedWithCorrectFields() {
        Train train = new ExpressTrain("T1", "12951", "Rajdhani", T10_00, at(10, 10), 4, 16);
        assertEquals("T1", train.getId());
        assertEquals("12951", train.getNumber());
        assertEquals(TrainType.EXPRESS, train.getType());
        assertEquals(TrainStatus.SCHEDULED, train.getStatus());
        assertNull(train.getAssignedPlatform());
        assertEquals(Duration.ofMinutes(10), train.getDwellTime());
    }

    // Scenario: departure equals arrival. Expected: InvalidTrainException.
    @Test
    void departureMustBeAfterArrival() {
        assertThrows(InvalidTrainException.class,
                () -> new PassengerTrain("T1", "66001", "Local", T10_00, T10_00, 3, 8));
    }

    // Scenario: departure before arrival. Expected: InvalidTrainException.
    @Test
    void departureBeforeArrivalIsRejected() {
        assertThrows(InvalidTrainException.class,
                () -> new PassengerTrain("T1", "66001", "Local", at(10, 30), T10_00, 3, 8));
    }

    // Scenario: missing arrival time. Expected: InvalidTrainException.
    @Test
    void nullTimesAreRejected() {
        assertThrows(InvalidTrainException.class,
                () -> new ExpressTrain("T1", "12951", "Rajdhani", null, at(10, 10), 3, 10));
    }

    // Scenario: freight train stops 10 min but needs 20. Expected: InvalidTrainException (subclass rule).
    @Test
    void freightDwellShorterThanMinimumIsRejected() {
        assertThrows(InvalidTrainException.class,
                () -> new FreightTrain("T1", "F100", "Coal", T10_00, at(10, 10), 1, 30));
    }

    // Scenario: the same 10-minute stop is fine for an express. Expected: no exception.
    @Test
    void sameDwellIsFineForExpress() {
        assertDoesNotThrow(() -> new ExpressTrain("T1", "12951", "Rajdhani", T10_00, at(10, 10), 3, 10));
    }

    // Scenario: blank number, priority 0, priority 6, 0 coaches. Expected: each rejected.
    @Test
    void invalidFieldsAreRejected() {
        assertThrows(InvalidTrainException.class,
                () -> new ExpressTrain("T1", " ", "Rajdhani", T10_00, at(10, 10), 3, 10));
        assertThrows(InvalidTrainException.class,
                () -> new ExpressTrain("T1", "1", "Rajdhani", T10_00, at(10, 10), 0, 10));
        assertThrows(InvalidTrainException.class,
                () -> new ExpressTrain("T1", "1", "Rajdhani", T10_00, at(10, 10), 6, 10));
        assertThrows(InvalidTrainException.class,
                () -> new ExpressTrain("T1", "1", "Rajdhani", T10_00, at(10, 10), 3, 0));
    }

    // Scenario: updateTimes with valid then invalid times. Expected: valid applied; invalid rejected and old times kept.
    @Test
    void updateTimesValidatesAndKeepsOldValuesOnFailure() {
        Train train = new PassengerTrain("T1", "66001", "Local", T10_00, at(10, 5), 3, 8);
        train.updateTimes(at(11, 0), at(11, 10));
        assertEquals(at(11, 0), train.getScheduledArrival());

        assertThrows(InvalidTrainException.class, () -> train.updateTimes(at(12, 0), at(11, 0)));
        assertEquals(at(11, 0), train.getScheduledArrival());
        assertEquals(at(11, 10), train.getScheduledDeparture());
    }

    // Scenario: delays of 10 then 5 minutes. Expected: total 15, expected times shift, dwell unchanged.
    @Test
    void delaysAddUpAndShiftExpectedTimes() {
        Train train = new ExpressTrain("T1", "12951", "Rajdhani", T10_00, at(10, 10), 3, 10);
        train.applyDelay(10);
        train.applyDelay(5);
        assertEquals(15, train.getDelayMinutes());
        assertTrue(train.isDelayed());
        assertEquals(at(10, 15), train.getExpectedArrival());
        assertEquals(at(10, 25), train.getExpectedDeparture());
        assertEquals(Duration.ofMinutes(10), train.getDwellTime());
        assertEquals(T10_00, train.getScheduledArrival());
    }

    // Scenario: zero or negative delay. Expected: InvalidTrainException.
    @Test
    void nonPositiveDelayIsRejected() {
        Train train = new ExpressTrain("T1", "12951", "Rajdhani", T10_00, at(10, 10), 3, 10);
        assertThrows(InvalidTrainException.class, () -> train.applyDelay(0));
        assertThrows(InvalidTrainException.class, () -> train.applyDelay(-5));
    }

    // Scenario: same base priority 3 for all types. Expected: express 6 > passenger 4 > freight 3.
    @Test
    void priorityDependsOnSubclass() {
        Train express = new ExpressTrain("T1", "1", "E", T10_00, at(10, 10), 3, 10);
        Train passenger = new PassengerTrain("T2", "2", "P", T10_00, at(10, 10), 3, 10);
        Train freight = new FreightTrain("T3", "3", "F", T10_00, at(10, 30), 3, 10);
        assertEquals(6, express.getPriority());
        assertEquals(4, passenger.getPriority());
        assertEquals(3, freight.getPriority());
    }

    // Scenario: 10 carriages of each type. Expected: express 270 m, passenger 200 m, freight 190 m.
    @Test
    void requiredLengthDependsOnSubclass() {
        assertEquals(270, new ExpressTrain("T1", "1", "E", T10_00, at(10, 10), 3, 10).getRequiredPlatformLength());
        assertEquals(200, new PassengerTrain("T2", "2", "P", T10_00, at(10, 10), 3, 10).getRequiredPlatformLength());
        assertEquals(190, new FreightTrain("T3", "3", "F", T10_00, at(10, 30), 3, 10).getRequiredPlatformLength());
    }

    // Scenario: base buffer 5. Expected: freight asks for 10, express and passenger keep 5.
    @Test
    void freightOverridesClearanceBuffer() {
        assertEquals(10, new FreightTrain("T3", "3", "F", T10_00, at(10, 30), 3, 10).getClearanceBufferMinutes(5));
        assertEquals(5, new ExpressTrain("T1", "1", "E", T10_00, at(10, 10), 3, 10).getClearanceBufferMinutes(5));
        assertEquals(5, new PassengerTrain("T2", "2", "P", T10_00, at(10, 10), 3, 10).getClearanceBufferMinutes(5));
    }

    // Scenario: a list of Train references holding different subclasses. Expected: each call dispatches to the real class.
    @Test
    void polymorphicListDispatchesToRealSubclass() {
        List<Train> trains = new ArrayList<>();
        trains.add(new ExpressTrain("T1", "1", "E", T10_00, at(10, 10), 1, 4));
        trains.add(new FreightTrain("T2", "2", "F", T10_00, at(10, 30), 1, 4));
        int totalMinimumDwell = 0;
        for (Train train : trains) {
            totalMinimumDwell += train.getMinimumDwellMinutes();
        }
        assertEquals(5 + 20, totalMinimumDwell);
    }

    // Scenario: markUnallocated on a train with no platform. Expected: status UNALLOCATED.
    @Test
    void markUnallocatedChangesStatusWhenNoPlatform() {
        Train train = new ExpressTrain("T1", "1", "E", T10_00, at(10, 10), 1, 4);
        train.markUnallocated();
        assertEquals(TrainStatus.UNALLOCATED, train.getStatus());
    }
}
