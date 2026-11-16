package com.railsync.strategy;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Train;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Priority with Aging: like PriorityStrategy, but a train's priority goes up
 * the longer it waits, so a low-priority train cannot be passed over forever.
 *
 * <p>OS analogy: aging. Same idea as OS aging: it raises a waiting process's
 * priority over time to prevent starvation.</p>
 *
 * <p>Rule: effective priority = train priority + (minutes waited / N),
 * where N = "minutes per priority point" (configurable, default 4).
 * Integer division, so the bonus grows in whole steps.</p>
 *
 * <p>Worked example (N = 4, one platform frees at 10:30):
 * L is a passenger train with priority 2 that has waited since 10:01, so
 * 29 minutes: 2 + 29/4 = 2 + 7 = 9. E2 is an express with priority 7 that
 * arrived at 10:27, so 3 minutes: 7 + 3/4 = 7 + 0 = 7. L now wins (9 &gt; 7).
 * Plain Priority would have chosen E2 and L would have starved.</p>
 *
 * <p>OOP concepts: Inheritance (extends QueueOrderingStrategy),
 * Polymorphism (overrides compareByPolicy and uses Train.getPriority()),
 * Encapsulation (validated aging rate), constructor chaining,
 * Strategy pattern.</p>
 */
public class PriorityAgingStrategy extends QueueOrderingStrategy {

    /** Default: every 4 minutes of waiting adds 1 priority point. */
    public static final int DEFAULT_MINUTES_PER_POINT = 4;
    /** Slowest allowed aging. */
    public static final int MAX_MINUTES_PER_POINT = 120;

    private int minutesPerPriorityPoint;

    /** Creates the strategy with the default aging rate (4 minutes per point). */
    public PriorityAgingStrategy() {
        this(DEFAULT_MINUTES_PER_POINT);
    }

    /**
     * Creates the strategy with a chosen aging rate.
     *
     * @param minutesPerPriorityPoint waiting minutes needed to gain one point (1 to 120)
     * @throws RailSyncException if out of range
     */
    public PriorityAgingStrategy(int minutesPerPriorityPoint) {
        setMinutesPerPriorityPoint(minutesPerPriorityPoint);
    }

    /** @return "Priority + Aging" */
    @Override
    public String getName() {
        return "Priority + Aging";
    }

    /**
     * Works out the priority of a train once its waiting time is counted.
     *
     * @param train the train
     * @param now   current simulated time
     * @return train priority plus the aging bonus
     */
    public int getEffectivePriority(Train train, LocalDateTime now) {
        // Step 1: how long has the train been waiting since it arrived? Never negative.
        long waited = Duration.between(train.getExpectedArrival(), now).toMinutes();
        if (waited < 0) {
            waited = 0;
        }
        // Step 2: one bonus point for every full block of N minutes.
        int agingBonus = (int) (waited / minutesPerPriorityPoint);
        // [POLYMORPHISM] getPriority() comes from the real Train subclass.
        return train.getPriority() + agingBonus;
    }

    /** @return the explanation shown on the Allocation screen */
    @Override
    public String getDescription() {
        return "OS analogy: priority with aging. Like Priority, but every N minutes of waiting adds 1 point, so a passed-over train eventually wins. Prevents starvation.";
    }

    /**
     * Higher effective priority comes first.
     *
     * @return negative if a should be served before b
     */
    @Override
    protected int compareByPolicy(Train a, Train b, LocalDateTime now) {
        return Integer.compare(getEffectivePriority(b, now), getEffectivePriority(a, now));
    }

    /** @return waiting minutes needed for one extra priority point */
    public int getMinutesPerPriorityPoint() {
        return minutesPerPriorityPoint;
    }

    /**
     * Changes the aging rate. A smaller number means faster aging.
     *
     * @param minutes 1 to 120
     * @throws RailSyncException if out of range
     */
    public final void setMinutesPerPriorityPoint(int minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES_PER_POINT) {
            throw new RailSyncException("Aging rate must be between 1 and "
                    + MAX_MINUTES_PER_POINT + " minutes per point, but was " + minutes + ".");
        }
        this.minutesPerPriorityPoint = minutes;
    }
}
