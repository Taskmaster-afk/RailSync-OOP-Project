package com.railsync.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the positionAt-style movement calculation used by the diagrams.
 */
class TrainMovementTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    // Doc example: expected 08:05, slot 08:15-08:25.
    private final TrainMovement waiting = new TrainMovement(at(8, 5), at(8, 15), at(8, 25));

    // Scenario: doc example. Expected: outer 08:01, leaves 08:11, home 08:13, exit 08:28, held 10 min.
    @Test
    void keyTimesMatchWorkedExample() {
        assertEquals(at(8, 1), waiting.getReachesOuterSignal());
        assertEquals(at(8, 11), waiting.getLeavesOuterSignal());
        assertEquals(at(8, 13), waiting.getPassesHomeSignal());
        assertEquals(at(8, 28), waiting.getClearsExit());
        assertEquals(10, waiting.getHoldMinutes());
    }

    // Scenario: ask the phase at moments through the visit. Expected: each phase in order, half-open boundaries.
    @Test
    void phasesFollowEachOther() {
        assertEquals(TrainMovement.Phase.NOT_YET_ARRIVED, waiting.phaseAt(at(8, 0)));
        assertEquals(TrainMovement.Phase.HOLDING_AT_OUTER_SIGNAL, waiting.phaseAt(at(8, 1)));
        assertEquals(TrainMovement.Phase.APPROACHING, waiting.phaseAt(at(8, 11)));
        assertEquals(TrainMovement.Phase.AT_PLATFORM, waiting.phaseAt(at(8, 15)));
        assertEquals(TrainMovement.Phase.DEPARTING, waiting.phaseAt(at(8, 25)));
        assertEquals(TrainMovement.Phase.GONE, waiting.phaseAt(at(8, 28)));
    }

    // Scenario: halfway through the 10-minute platform stay (08:20). Expected: progress 0.5.
    @Test
    void progressIsLinearWithinPhase() {
        assertEquals(0.5, waiting.progressAt(at(8, 20)), 0.001);
        assertEquals(1.0, waiting.progressAt(at(9, 0)), 0.001);
        assertEquals(0.0, waiting.progressAt(at(7, 0)), 0.001);
    }

    // Scenario: platform slot starts exactly at arrival. Expected: no holding at the outer signal.
    @Test
    void onTimeTrainDoesNotHold() {
        TrainMovement onTime = new TrainMovement(at(9, 0), at(9, 0), at(9, 10));
        assertEquals(0, onTime.getHoldMinutes());
        assertEquals(TrainMovement.Phase.APPROACHING, onTime.phaseAt(at(8, 56)));
    }
}
