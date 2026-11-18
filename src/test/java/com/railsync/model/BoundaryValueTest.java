package com.railsync.model;

import com.railsync.exception.RailSyncException;
import com.railsync.service.PlatformAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Black-box boundary value analysis of every numeric input rule. For each valid range [min, max] the values
 * min-1, min, max and max+1 are tried (see the boundary tables in docs/se/TEST_PLAN.md). "true" means the value
 * must be accepted, "false" that it must be rejected with a RailSyncException.
 */
class BoundaryValueTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 16, 8, 0);

    private static Train train(TrainType type, int dwellMinutes, int priority, int carriages) {
        return TrainFactory.create(type, "T1", "100", "Test", T0, T0.plusMinutes(dwellMinutes), priority, carriages);
    }

    private static void check(boolean valid, Runnable action) {
        if (valid) {
            assertDoesNotThrow(action::run);
        } else {
            assertThrows(RailSyncException.class, action::run);
        }
    }

    // Scenario: base priority, valid range 1..5. Expected: 0 and 6 rejected, 1 and 5 accepted.
    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "5,true", "6,false"})
    void basePriority(int priority, boolean valid) {
        check(valid, () -> train(TrainType.EXPRESS, 10, priority, 10));
    }

    // Scenario: coach or wagon count, valid range 1..60. Expected: 0 and 61 rejected, 1 and 60 accepted.
    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "60,true", "61,false"})
    void carriageCount(int carriages, boolean valid) {
        check(valid, () -> train(TrainType.PASSENGER, 10, 3, carriages));
    }

    // Scenario: minimum stop per type (express 5, passenger 3, freight 20 min). Expected: one minute less is
    // rejected, exactly the minimum is accepted.
    @ParameterizedTest
    @CsvSource({"EXPRESS,4,false", "EXPRESS,5,true", "PASSENGER,2,false", "PASSENGER,3,true",
        "FREIGHT,19,false", "FREIGHT,20,true"})
    void minimumDwellPerType(TrainType type, int dwellMinutes, boolean valid) {
        check(valid, () -> train(type, dwellMinutes, 3, 10));
    }

    // Scenario: platform length, valid range 1..1500 m. Expected: 0 and 1501 rejected, 1 and 1500 accepted.
    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "1500,true", "1501,false"})
    void platformLength(int length, boolean valid) {
        check(valid, () -> new Platform("P1", 1, length, EnumSet.of(TrainType.EXPRESS)));
    }

    // Scenario: clearance buffer, valid range 0..60 min. Expected: -1 and 61 rejected, 0 and 60 accepted.
    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "60,true", "61,false"})
    void clearanceBuffer(int minutes, boolean valid) {
        check(valid, () -> new Schedule().setClearanceBufferMinutes(minutes));
    }

    // Scenario: maximum wait at the outer signal, valid range 0..180 min. Expected: -1 and 181 rejected.
    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "180,true", "181,false"})
    void maximumWait(int minutes, boolean valid) {
        PlatformAllocator allocator = new PlatformAllocator(new Station("S"), new Schedule(), 20);
        check(valid, () -> allocator.setMaxWaitMinutes(minutes));
    }

    // Scenario: one delay, valid range 1..1440 min (one day). Expected: 0 and 1441 rejected, 1 and 1440 accepted.
    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "1440,true", "1441,false"})
    void delayMinutes(int minutes, boolean valid) {
        check(valid, () -> train(TrainType.EXPRESS, 10, 3, 10).applyDelay(minutes));
    }

    // Scenario: a platform exactly as long as the train needs, and one metre shorter (express, 10 coaches = 270 m).
    // Expected: 270 m fits, 269 m does not.
    @ParameterizedTest
    @CsvSource({"269,false", "270,true"})
    void platformJustLongEnough(int length, boolean fits) {
        Train express = train(TrainType.EXPRESS, 10, 3, 10);
        Platform platform = new Platform("P1", 1, length, EnumSet.of(TrainType.EXPRESS));
        assertEquals(fits, express.canUsePlatform(platform));
    }

    // Scenario: second train starts exactly at the end of the first plus the 5-minute gap, and one minute earlier.
    // Expected: 10:15 is accepted (half-open interval plus gap), 10:14 is a conflict.
    @ParameterizedTest
    @CsvSource({"14,false", "15,true"})
    void clearanceGapEdge(int startMinute, boolean free) {
        Schedule schedule = new Schedule(5);
        Platform platform = new Platform("P1", 1, 500, EnumSet.of(TrainType.PASSENGER));
        Train first = TrainFactory.create(TrainType.PASSENGER, "A", "A", "A", T0.plusMinutes(0), T0.plusMinutes(10), 3, 5);
        schedule.reserve(first, platform, T0);
        Train second = TrainFactory.create(TrainType.PASSENGER, "B", "B", "B", T0.plusMinutes(startMinute),
                T0.plusMinutes(startMinute + 5), 3, 5);
        assertEquals(free, schedule.findConflict(second, platform, T0.plusMinutes(startMinute)) == null);
    }
}
