package com.railsync.strategy;

import com.railsync.model.Train;

import java.time.LocalDateTime;

/**
 * First-Come-First-Served: the train that arrived first is served first.
 *
 * <p>OS analogy: FCFS scheduling. Simple and fair in arrival order, but an
 * important train stuck behind earlier ones must wait (like a short
 * process behind a long one: the "convoy effect").</p>
 *
 * <p>Worked example (one platform, 5-minute gap, platform busy until 10:20):
 * Passenger P2 arrives 10:05, Express E1 arrives 10:10. At 10:25 the
 * platform is free; FCFS serves P2 (arrived first). E1 waits for P2 to
 * leave, even though it has higher priority.</p>
 *
 * <p>Platform choice is First-Fit (lowest-numbered free compatible platform),
 * done by PlatformAllocator for every strategy.</p>
 *
 * <p>OOP concepts: Inheritance (extends QueueOrderingStrategy),
 * Polymorphism (overrides compareByPolicy), Strategy pattern.</p>
 */
public class FcfsStrategy extends QueueOrderingStrategy {

    /** @return "FCFS (First-Fit)" */
    @Override
    public String getName() {
        return "FCFS (First-Fit)";
    }

    /** @return the explanation shown on the Allocation screen */
    @Override
    public String getDescription() {
        return "OS analogy: FCFS. Waiting trains are served strictly in arrival order. Fair and predictable, but an important train can be stuck behind earlier ones (convoy effect).";
    }

    /**
     * Earlier expected arrival comes first.
     *
     * @return negative if a arrived before b
     */
    @Override
    protected int compareByPolicy(Train a, Train b, LocalDateTime now) {
        return a.getExpectedArrival().compareTo(b.getExpectedArrival());
    }
}
