package com.railsync.strategy;

import com.railsync.model.Train;

import java.time.LocalDateTime;

/**
 * Priority scheduling: among the waiting trains, the one with the highest
 * effective priority is served first; ties go to the earlier arrival.
 *
 * <p>OS analogy: non-preemptive priority scheduling. Important trains wait
 * less, but a low-priority train can be passed over again and again
 * ("starvation"). PriorityAgingStrategy (Stage 3) fixes that.</p>
 *
 * <p>Priority only changes the ORDER of service. It never lets a train use
 * an incompatible platform or break the clearance gap; those rules are
 * enforced by Train and Schedule for every strategy.</p>
 *
 * <p>Worked example (same as FcfsStrategy): P2 (priority 4) arrives 10:05,
 * E1 (priority 7) arrives 10:10. When the platform frees at 10:25, Priority
 * serves E1 first and P2 waits.</p>
 *
 * <p>OOP concepts: Inheritance, Polymorphism (calls Train.getPriority(),
 * which each Train subclass answers differently), Strategy pattern.</p>
 */
public class PriorityStrategy extends QueueOrderingStrategy {

    /** @return "Priority" */
    @Override
    public String getName() {
        return "Priority";
    }

    /** @return the explanation shown on the Allocation screen */
    @Override
    public String getDescription() {
        return "OS analogy: non-preemptive priority scheduling. The waiting train with the highest priority goes first (express > passenger > freight, plus base priority). Low-priority trains can starve.";
    }

    /**
     * Higher priority comes first.
     *
     * @return negative if a has the higher priority
     */
    @Override
    protected int compareByPolicy(Train a, Train b, LocalDateTime now) {
        // [POLYMORPHISM] getPriority() runs the ExpressTrain / PassengerTrain / FreightTrain version.
        // b before a in the subtraction gives descending order (highest first).
        return Integer.compare(b.getPriority(), a.getPriority());
    }
}
