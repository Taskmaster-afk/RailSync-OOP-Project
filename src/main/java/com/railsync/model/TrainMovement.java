package com.railsync.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Where a train is at any moment, worked out from its booking. No physics:
 * just five fixed moments and straight lines between them.
 *
 * <pre>
 *  reaches outer signal  -> (waits there if no platform yet) -> leaves outer signal
 *  -> passes home signal (half way) -> stops at the platform -> leaves the platform
 *  -> clears the exit signal
 * </pre>
 *
 * <p>Fixed assumptions: the run from the outer signal to the platform takes
 * {@value #APPROACH_MINUTES} minutes, and clearing the exit signal after
 * leaving takes {@value #EXIT_MINUTES} minutes.</p>
 *
 * <p>Worked example: a train expected at 08:05 whose platform slot starts at
 * 08:15 and ends at 08:25. It reaches the outer signal at 08:01, waits
 * there (ready queue!) until 08:11, runs in, stops at 08:15, leaves at 08:25
 * and is gone at 08:28.</p>
 *
 * <p>OS analogy: holding at the outer signal is a process waiting in the
 * ready queue; standing at the platform is running on the CPU.</p>
 *
 * <p>OOP concepts: Encapsulation and immutability; the nested enum Phase
 * names every state. Used only for reading; it never changes a booking.</p>
 *
 * <p>Works with: PlatformReservation, ReservationChange, the time-distance
 * diagram and the yard view (both only read it).</p>
 */
public final class TrainMovement {

    /** Minutes from the outer signal to the platform. */
    public static final int APPROACH_MINUTES = 4;
    /** Minutes from leaving the platform until the train has cleared the exit signal. */
    public static final int EXIT_MINUTES = 3;

    /** The stages of a train's visit to the station. */
    public enum Phase {
        /** Not yet at the outer signal. */
        NOT_YET_ARRIVED,
        /** Waiting at the outer signal for a platform (the ready queue). */
        HOLDING_AT_OUTER_SIGNAL,
        /** Running in from the outer signal to the platform. */
        APPROACHING,
        /** Standing at the platform. */
        AT_PLATFORM,
        /** Running out towards the exit signal. */
        DEPARTING,
        /** Has left the station area. */
        GONE
    }

    private final LocalDateTime reachesOuterSignal;
    private final LocalDateTime leavesOuterSignal;
    private final LocalDateTime platformStart;
    private final LocalDateTime platformEnd;
    private final LocalDateTime clearsExit;

    /**
     * Builds the movement from an arrival time and a platform slot.
     *
     * @param expectedArrival when the train could reach the platform without waiting
     * @param platformStart   when it actually starts occupying the platform
     * @param platformEnd     when it leaves the platform
     */
    public TrainMovement(LocalDateTime expectedArrival, LocalDateTime platformStart, LocalDateTime platformEnd) {
        // Step 1: the train reaches the outer signal one approach-run before it could be at the platform.
        this.reachesOuterSignal = expectedArrival.minusMinutes(APPROACH_MINUTES);
        // Step 2: it must leave the outer signal one approach-run before its platform slot.
        // If the slot is later than its arrival, the difference is time spent waiting at the signal.
        LocalDateTime leave = platformStart.minusMinutes(APPROACH_MINUTES);
        this.leavesOuterSignal = leave.isBefore(reachesOuterSignal) ? reachesOuterSignal : leave;
        this.platformStart = platformStart;
        this.platformEnd = platformEnd;
        this.clearsExit = platformEnd.plusMinutes(EXIT_MINUTES);
    }

    /**
     * Builds the movement of a booked train.
     *
     * @param booking the booking
     * @return its movement
     */
    public static TrainMovement of(PlatformReservation booking) {
        return new TrainMovement(booking.getTrain().getExpectedArrival(), booking.getStart(), booking.getEnd());
    }

    /**
     * Says what the train is doing at a given moment.
     *
     * @param time the moment
     * @return the phase
     */
    public Phase phaseAt(LocalDateTime time) {
        // Each phase is a half-open interval [from, to), checked in order.
        if (time.isBefore(reachesOuterSignal)) {
            return Phase.NOT_YET_ARRIVED;
        }
        if (time.isBefore(leavesOuterSignal)) {
            return Phase.HOLDING_AT_OUTER_SIGNAL;
        }
        if (time.isBefore(platformStart)) {
            return Phase.APPROACHING;
        }
        if (time.isBefore(platformEnd)) {
            return Phase.AT_PLATFORM;
        }
        if (time.isBefore(clearsExit)) {
            return Phase.DEPARTING;
        }
        return Phase.GONE;
    }

    /**
     * How far through its current phase the train is: 0.0 at the start, 1.0 at the end.
     * The yard view uses this to slide trains smoothly along the track.
     *
     * @param time the moment
     * @return a fraction from 0.0 to 1.0
     */
    public double progressAt(LocalDateTime time) {
        switch (phaseAt(time)) {
            case HOLDING_AT_OUTER_SIGNAL:
                return fraction(reachesOuterSignal, leavesOuterSignal, time);
            case APPROACHING:
                return fraction(leavesOuterSignal, platformStart, time);
            case AT_PLATFORM:
                return fraction(platformStart, platformEnd, time);
            case DEPARTING:
                return fraction(platformEnd, clearsExit, time);
            case GONE:
                return 1.0;
            default:
                return 0.0;
        }
    }

    /** @return when the train reaches the outer signal */
    public LocalDateTime getReachesOuterSignal() {
        return reachesOuterSignal;
    }

    /** @return when the train leaves the outer signal (later than arrival there if it had to wait) */
    public LocalDateTime getLeavesOuterSignal() {
        return leavesOuterSignal;
    }

    /** @return when the train passes the home signal (half way through the approach) */
    public LocalDateTime getPassesHomeSignal() {
        long halfSeconds = Duration.between(leavesOuterSignal, platformStart).getSeconds() / 2;
        return leavesOuterSignal.plusSeconds(halfSeconds);
    }

    /** @return when the train stops at the platform */
    public LocalDateTime getPlatformStart() {
        return platformStart;
    }

    /** @return when the train leaves the platform */
    public LocalDateTime getPlatformEnd() {
        return platformEnd;
    }

    /** @return when the train has cleared the exit signal */
    public LocalDateTime getClearsExit() {
        return clearsExit;
    }

    /** @return minutes spent waiting at the outer signal */
    public long getHoldMinutes() {
        return Duration.between(reachesOuterSignal, leavesOuterSignal).toMinutes();
    }

    /** Linear position of time between from and to, clamped to 0..1. */
    private static double fraction(LocalDateTime from, LocalDateTime to, LocalDateTime time) {
        double total = Duration.between(from, to).toMillis();
        if (total <= 0) {
            return 1.0;
        }
        double done = Duration.between(from, time).toMillis();
        return Math.max(0.0, Math.min(1.0, done / total));
    }
}
