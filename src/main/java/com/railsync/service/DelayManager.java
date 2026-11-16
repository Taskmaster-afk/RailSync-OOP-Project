package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.ReservationChange;
import com.railsync.model.Schedule;
import com.railsync.model.Train;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles disruptions: a train running late (Module E) and a platform
 * breaking down (Module E2).
 *
 * <p>Both kinds of disruption reuse the same two reassignment steps, so the
 * logic is never duplicated:</p>
 * <ol>
 *   <li>placeElsewhereAtSameTime(): another free platform at the same time;</li>
 *   <li>PlatformAllocator.allocateSingle(): any platform, waiting up to the
 *       maximum wait.</li>
 * </ol>
 *
 * <p>Safety rule: a booking is never changed silently. Every move is
 * recorded as a ReservationChange and returned in a report. If no safe
 * placement exists, the train is clearly marked unallocated with the reason.</p>
 *
 * <p>OOP concepts: SOLID-S (disruption handling only; storing bookings is
 * Schedule's job, finding platforms is PlatformAllocator's job),
 * Encapsulation (the ripple plan is a private helper class).</p>
 *
 * <p>Works with: Schedule, PlatformAllocator, Train, Platform, DelayReport,
 * BreakdownReport, ReservationChange.</p>
 */
public class DelayManager {

    private final Schedule schedule;
    private final PlatformAllocator allocator;

    /**
     * Creates a delay manager.
     *
     * @param schedule  the bookings to update
     * @param allocator used for reassignment and for the maximum wait setting
     */
    public DelayManager(Schedule schedule, PlatformAllocator allocator) {
        this.schedule = schedule;
        this.allocator = allocator;
    }

    /**
     * Delays a train and repairs its booking, trying the least disruptive
     * option first:
     * <ol>
     *   <li>booking still valid as it is (the train was waiting anyway);</li>
     *   <li>same platform at the new time;</li>
     *   <li>another platform at the new time;</li>
     *   <li>delay ripple: keep the platform and push the following trains,
     *       only if no pushed train waits longer than the maximum wait;</li>
     *   <li>any platform after waiting at the outer signal;</li>
     *   <li>otherwise: the train loses its platform and the report says why.</li>
     * </ol>
     *
     * @param train   the train that is running late
     * @param minutes extra delay in minutes (positive)
     * @return a report of what happened
     * @throws com.railsync.exception.InvalidTrainException if minutes is invalid
     *         (nothing is changed in that case)
     */
    public DelayReport delayTrain(Train train, int minutes) {
        List<ReservationChange> changes = new ArrayList<>();
        List<ReservationChange> noCascade = new ArrayList<>();

        // Step 1: remember the old booking, then record the delay (this validates minutes first).
        PlatformReservation oldBooking = schedule.findReservation(train);
        LocalDateTime oldArrival = train.getExpectedArrival();
        train.applyDelay(minutes);
        String header = "Train " + train.getNumber() + " delayed " + minutes + " min (now arrives "
                + train.getExpectedArrival().toLocalTime() + "). ";
        if (oldBooking == null) {
            return new DelayReport(train, minutes, DelayReport.Outcome.NOT_ALLOCATED,
                    header + "It had no platform, so only its times changed.", changes, noCascade);
        }

        // Step 2: the new start is the later of "old slot" and "new arrival".
        // Example: booked 10:25 because it was waiting; delayed to arrive 10:12 -> 10:25 still works.
        LocalDateTime newStart = later(oldBooking.getStart(), train.getExpectedArrival());
        if (newStart.equals(oldBooking.getStart())) {
            return new DelayReport(train, minutes, DelayReport.Outcome.STILL_VALID,
                    header + "Its booking " + oldBooking + " is still valid.", changes, noCascade);
        }

        // Step 3: free the old slot, then try the same platform at the new time.
        Platform home = oldBooking.getPlatform();
        schedule.release(train);
        if (train.canUsePlatform(home) && schedule.findConflict(train, home, newStart) == null) {
            PlatformReservation booking = schedule.reserve(train, home, newStart);
            changes.add(new ReservationChange(oldBooking, booking, oldArrival));
            return new DelayReport(train, minutes, DelayReport.Outcome.KEPT_PLATFORM,
                    header + "Keeps " + home + ", now " + describe(booking) + ".", changes, noCascade);
        }

        // Step 4: a new conflict exists. Try another platform at the new time (nobody else is disturbed).
        AllocationResult moved = placeElsewhereAtSameTime(train, newStart);
        if (moved != null) {
            changes.add(new ReservationChange(oldBooking, schedule.findReservation(train), oldArrival));
            return new DelayReport(train, minutes, DelayReport.Outcome.REASSIGNED,
                    header + "Conflict on " + home + "; moved to " + moved.getPlatform() + " at "
                            + newStart.toLocalTime() + ".", changes, noCascade);
        }

        // Step 5: delay ripple. Keep the platform and push the following trains later.
        String rippleProblem = "";
        if (train.canUsePlatform(home)) {
            List<RippleStep> plan = planRipple(train, home, oldBooking.getStart(), newStart);
            RippleStep tooLate = findStepOverLimit(plan);
            if (tooLate == null) {
                List<ReservationChange> cascade = applyRipple(train, home, newStart, plan, oldBooking, oldArrival, changes);
                return new DelayReport(train, minutes, DelayReport.Outcome.RIPPLE,
                        header + "Keeps " + home + " and pushes " + cascade.size()
                                + " following train(s) later: " + describeCascade(cascade) + ".", changes, cascade);
            }
            rippleProblem = " Keeping " + home + " would push " + plan.size() + " train(s); "
                    + tooLate.booking.getTrain().getNumber() + " would wait "
                    + waitMinutes(tooLate) + " min (limit " + allocator.getMaxWaitMinutes() + ").";
        }

        // Step 6: last try: any platform, waiting at the outer signal if needed.
        AllocationResult retry = allocator.allocateSingle(train);
        PlatformReservation newBooking = schedule.findReservation(train);
        changes.add(new ReservationChange(oldBooking, newBooking, oldArrival));
        if (retry.isAllocated()) {
            return new DelayReport(train, minutes, DelayReport.Outcome.REASSIGNED,
                    header + "Conflict on " + home + "." + rippleProblem + " " + retry.getMessage() + ".",
                    changes, noCascade);
        }

        // Step 7: no safe placement. The train is now unallocated and we say exactly why.
        return new DelayReport(train, minutes, DelayReport.Outcome.FAILED,
                header + "No safe platform." + rippleProblem + " " + retry.getMessage(), changes, noCascade);
    }

    /**
     * Handles a platform breakdown: marks it failed and tries to move every
     * train booked on it, first to another platform at the same time, then
     * anywhere after waiting.
     *
     * @param platform the platform that broke down
     * @return who moved, who could not, and why
     */
    public BreakdownReport handleBreakdown(Platform platform) {
        // Step 1: mark the platform failed, so no strategy can pick it any more.
        platform.markFailed();

        // Step 2: take a copy of its bookings, earliest first (the real list changes as we release).
        List<PlatformReservation> affected = schedule.getReservationsOn(platform);
        List<AllocationResult> outcomes = new ArrayList<>();
        List<ReservationChange> changes = new ArrayList<>();

        for (PlatformReservation oldBooking : affected) {
            Train train = oldBooking.getTrain();
            // Step 3: free the broken slot.
            schedule.release(train);
            // Step 4: same reassignment steps as for a delay: same time elsewhere, else wait.
            AllocationResult result = placeElsewhereAtSameTime(train, oldBooking.getStart());
            if (result == null) {
                result = allocator.allocateSingle(train);
            }
            // Step 5: record before/after for the report and the animation.
            outcomes.add(result);
            changes.add(new ReservationChange(oldBooking, schedule.findReservation(train)));
        }
        return new BreakdownReport(platform, outcomes, changes);
    }

    // ------------------------------------------------------------------
    // Shared reassignment step.
    // ------------------------------------------------------------------

    /**
     * Puts a train (with no booking) on the first other free compatible
     * platform at exactly the given start time.
     *
     * @return the success result, or null if no platform is free at that time
     */
    private AllocationResult placeElsewhereAtSameTime(Train train, LocalDateTime start) {
        Platform other = allocator.findFirstFreePlatform(train, start);
        if (other == null) {
            return null;
        }
        schedule.reserve(train, other, start);
        return AllocationResult.success(train, other, start);
    }

    // ------------------------------------------------------------------
    // Delay ripple.
    // ------------------------------------------------------------------

    /**
     * Works out which following trains on the platform would have to move
     * later if the delayed train kept its platform. Nothing is changed here.
     *
     * <p>Walk the platform's later bookings in time order. Each one must start
     * at least "previous train's end + gap". If it already does, the chain
     * stops. Otherwise it is pushed to exactly that time, and becomes the
     * "previous train" for the next booking.</p>
     *
     * <p>Example (gap 5): A 10:00-10:10, B 10:15-10:25, C 10:30-10:40,
     * D 11:00-11:10. A is delayed 10 min to 10:10-10:20.
     * B must start at 10:25 or later, so B is pushed to 10:25-10:35.
     * C must start at 10:40 or later, so C is pushed to 10:40-10:50.
     * D needs 10:55 or later and is at 11:00, so the chain stops.
     * Cascade = B, C (2 trains).</p>
     */
    private List<RippleStep> planRipple(Train delayed, Platform home, LocalDateTime oldStart, LocalDateTime newStart) {
        List<RippleStep> plan = new ArrayList<>();
        Train previous = delayed;
        LocalDateTime previousEnd = newStart.plus(delayed.getDwellTime());
        for (PlatformReservation next : schedule.getReservationsOn(home)) {
            // Step 1: trains before the delayed train's old slot are never affected.
            if (next.getStart().isBefore(oldStart)) {
                continue;
            }
            // Step 2: the earliest this booking may start, given the train in front of it.
            LocalDateTime earliestAllowed = previousEnd.plusMinutes(schedule.requiredGapMinutes(previous, next.getTrain()));
            // Step 3: enough room already? Then the ripple stops here.
            if (!next.getStart().isBefore(earliestAllowed)) {
                break;
            }
            // Step 4: push this booking and carry on down the chain.
            plan.add(new RippleStep(next, earliestAllowed));
            previous = next.getTrain();
            previousEnd = earliestAllowed.plus(next.getDuration());
        }
        return plan;
    }

    /** @return the first pushed train that would wait longer than the maximum wait, or null if all are fine */
    private RippleStep findStepOverLimit(List<RippleStep> plan) {
        for (RippleStep step : plan) {
            if (waitMinutes(step) > allocator.getMaxWaitMinutes()) {
                return step;
            }
        }
        return null;
    }

    /**
     * Carries out a ripple plan: frees every pushed booking, books the delayed
     * train at its new time, then books each pushed train at its new time.
     * Order matters: everything is released first so no booking clashes with
     * an old position.
     *
     * @return the cascade (other trains only)
     */
    private List<ReservationChange> applyRipple(Train delayed, Platform home, LocalDateTime newStart,
                                                List<RippleStep> plan, PlatformReservation oldBooking,
                                                LocalDateTime oldArrival, List<ReservationChange> changes) {
        // Step 1: release every pushed train.
        for (RippleStep step : plan) {
            schedule.release(step.booking.getTrain());
        }
        // Step 2: the delayed train takes its new slot on the same platform.
        PlatformReservation delayedBooking = schedule.reserve(delayed, home, newStart);
        changes.add(new ReservationChange(oldBooking, delayedBooking, oldArrival));
        // Step 3: each pushed train takes its later slot, in chain order.
        List<ReservationChange> cascade = new ArrayList<>();
        for (RippleStep step : plan) {
            PlatformReservation pushed = schedule.reserve(step.booking.getTrain(), home, step.newStart);
            ReservationChange change = new ReservationChange(step.booking, pushed);
            changes.add(change);
            cascade.add(change);
        }
        return cascade;
    }

    /** @return minutes a pushed train would wait after its own expected arrival */
    private long waitMinutes(RippleStep step) {
        return Duration.between(step.booking.getTrain().getExpectedArrival(), step.newStart).toMinutes();
    }

    private static String describeCascade(List<ReservationChange> cascade) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cascade.size(); i++) {
            if (i > 0) {
                text.append(" -> ");
            }
            ReservationChange change = cascade.get(i);
            text.append(change.getTrain().getNumber()).append(" +").append(change.getShiftMinutes()).append(" min");
        }
        return text.toString();
    }

    private static String describe(PlatformReservation booking) {
        return "[" + booking.getStart().toLocalTime() + ", " + booking.getEnd().toLocalTime() + ")";
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /** One planned push: this booking would move to newStart. */
    private static final class RippleStep {
        private final PlatformReservation booking;
        private final LocalDateTime newStart;

        private RippleStep(PlatformReservation booking, LocalDateTime newStart) {
            this.booking = booking;
            this.newStart = newStart;
        }
    }
}
