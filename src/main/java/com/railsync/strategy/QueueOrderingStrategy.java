package com.railsync.strategy;

import com.railsync.model.Train;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for strategies that work by sorting the ready queue with one
 * rule. It does the copying, sorting and tie-breaking once, so each concrete
 * strategy only writes its own comparison rule.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Abstract class: holds real shared code (the sort and the tie-break)
 *       that the interface cannot hold. This is the RailSync example of
 *       "interface = what you can do, abstract class = shared how".</li>
 *   <li>Template Method idea: orderReadyQueue() is final and fixed; the one
 *       varying step, compareByPolicy(), is left to subclasses.</li>
 *   <li>Polymorphism: compareByPolicy() is dispatched to the real subclass.</li>
 * </ul>
 *
 * <p>Works with: FcfsStrategy, PriorityStrategy (subclasses).</p>
 */
public abstract class QueueOrderingStrategy implements PlatformAllocationStrategy {

    /**
     * Copies the queue and sorts it: policy rule first, then the fixed
     * tie-break (earlier expected arrival, then train number). The tie-break
     * makes every run deterministic, so results are reproducible in a viva.
     *
     * @param readyQueue waiting trains (not changed)
     * @param now        current simulated time
     * @return a new, sorted list
     */
    @Override
    public final List<Train> orderReadyQueue(List<Train> readyQueue, LocalDateTime now) {
        // Step 1: copy, so the caller's list is never modified.
        List<Train> ordered = new ArrayList<>(readyQueue);
        // Step 2: sort using compareTrains (a small lambda that just forwards to it).
        ordered.sort((a, b) -> compareTrains(a, b, now));
        return ordered;
    }

    /**
     * The policy rule. Return a negative number if {@code a} should be
     * served before {@code b}, positive if after, 0 if this rule cannot decide.
     *
     * @param a   one train
     * @param b   another train
     * @param now current simulated time
     * @return comparison result
     */
    protected abstract int compareByPolicy(Train a, Train b, LocalDateTime now);

    /** Policy rule first; if it ties, earlier arrival wins; then lower train number. */
    private int compareTrains(Train a, Train b, LocalDateTime now) {
        // [POLYMORPHISM] the real subclass supplies the main rule.
        int result = compareByPolicy(a, b, now);
        if (result != 0) {
            return result;
        }
        result = a.getExpectedArrival().compareTo(b.getExpectedArrival());
        if (result != 0) {
            return result;
        }
        return a.getNumber().compareTo(b.getNumber());
    }
}
