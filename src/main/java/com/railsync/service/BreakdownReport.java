package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.ReservationChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened when a platform broke down: for every train that was booked
 * on it, whether it moved (where and when) or could not (and why).
 *
 * <p>OOP concepts: Encapsulation and immutability.</p>
 *
 * <p>Works with: DelayManager (creates it), AllocationResult, ReservationChange.</p>
 */
public final class BreakdownReport {

    private final Platform platform;
    private final List<AllocationResult> outcomes;
    private final List<ReservationChange> changes;

    BreakdownReport(Platform platform, List<AllocationResult> outcomes, List<ReservationChange> changes) {
        this.platform = platform;
        this.outcomes = new ArrayList<>(outcomes);
        this.changes = new ArrayList<>(changes);
    }

    /** @return the failed platform */
    public Platform getPlatform() {
        return platform;
    }

    /** @return one result per affected train, read-only */
    public List<AllocationResult> getOutcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    /** @return before/after of every affected booking, read-only */
    public List<ReservationChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    /** @return how many trains were moved successfully */
    public int getMovedCount() {
        int count = 0;
        for (AllocationResult outcome : outcomes) {
            if (outcome.isAllocated()) {
                count++;
            }
        }
        return count;
    }

    /** @return how many trains could not be moved */
    public int getStrandedCount() {
        return outcomes.size() - getMovedCount();
    }

    /** @return a multi-line summary */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Breakdown of " + platform + ": " + outcomes.size()
                + " train(s) affected, " + getMovedCount() + " moved, " + getStrandedCount() + " could not be moved.");
        for (AllocationResult outcome : outcomes) {
            text.append("\n  ").append(outcome.getMessage());
        }
        return text.toString();
    }
}
