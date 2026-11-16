package com.railsync.service;

import com.railsync.model.ReservationChange;
import com.railsync.model.Train;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened when a train was delayed: the outcome, a readable message,
 * every booking that moved, and the ripple cascade (other trains pushed later).
 *
 * <p>OOP concepts: Encapsulation and immutability. The nested enum Outcome
 * lists every possible result, so the GUI can react to each one clearly.</p>
 *
 * <p>Works with: DelayManager (creates it), SchedulingService, ReservationChange.</p>
 */
public final class DelayReport {

    /** Every possible way a delay can be resolved. */
    public enum Outcome {
        /** The train had no platform; only its times changed. */
        NOT_ALLOCATED,
        /** The existing booking still works unchanged (the train was waiting anyway). */
        STILL_VALID,
        /** Same platform, later time; nobody else affected. */
        KEPT_PLATFORM,
        /** Moved to another platform, or to a later slot after waiting at the outer signal. */
        REASSIGNED,
        /** Kept its platform and pushed the following trains later (delay ripple). */
        RIPPLE,
        /** No safe placement existed; the train lost its platform. */
        FAILED
    }

    private final Train train;
    private final int delayMinutes;
    private final Outcome outcome;
    private final String message;
    private final List<ReservationChange> changes;
    private final List<ReservationChange> cascade;

    /**
     * Creates a report.
     *
     * @param train        the delayed train
     * @param delayMinutes the delay just added
     * @param outcome      how it was resolved
     * @param message      plain-English explanation
     * @param changes      every booking that moved (the delayed train first)
     * @param cascade      only the OTHER trains pushed by a ripple (empty otherwise)
     */
    DelayReport(Train train, int delayMinutes, Outcome outcome, String message,
                List<ReservationChange> changes, List<ReservationChange> cascade) {
        this.train = train;
        this.delayMinutes = delayMinutes;
        this.outcome = outcome;
        this.message = message;
        this.changes = new ArrayList<>(changes);
        this.cascade = new ArrayList<>(cascade);
    }

    /** @return the delayed train */
    public Train getTrain() {
        return train;
    }

    /** @return minutes of delay added */
    public int getDelayMinutes() {
        return delayMinutes;
    }

    /** @return how the delay was resolved */
    public Outcome getOutcome() {
        return outcome;
    }

    /** @return readable explanation */
    public String getMessage() {
        return message;
    }

    /** @return every moved booking, read-only */
    public List<ReservationChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    /** @return the other trains pushed by the ripple, in chain order, read-only */
    public List<ReservationChange> getCascade() {
        return Collections.unmodifiableList(cascade);
    }

    /** @return how many other trains the ripple pushed */
    public int getCascadeCount() {
        return cascade.size();
    }

    /** @return true if a clash appeared that had to be resolved (or could not be) */
    public boolean isConflict() {
        return outcome == Outcome.REASSIGNED || outcome == Outcome.RIPPLE || outcome == Outcome.FAILED;
    }

    /** @return the message */
    @Override
    public String toString() {
        return message;
    }
}
