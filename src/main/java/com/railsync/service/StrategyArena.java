package com.railsync.service;

import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.strategy.PlatformAllocationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * The strategy comparison arena (Module F2): runs the same scenario through
 * several strategies and collects the metrics of each run side by side.
 *
 * <p>Safety rule: every run works on its own deep copy of the scenario, with
 * its own Station, Schedule and PlatformAllocator. The live schedule is never
 * read for writing and never changed.</p>
 *
 * <p>OOP concepts: [STRATEGY] / Polymorphism (the same loop runs every
 * strategy through the interface), SOLID-S (it only orchestrates; allocating
 * is PlatformAllocator's job and measuring is MetricsCalculator's job).</p>
 *
 * <p>Works with: Scenario, PlatformAllocator, MetricsCalculator, ScheduleMetrics.</p>
 */
public class StrategyArena {

    /** The outcome of one strategy in the arena. */
    public static final class Entry {
        private final String strategyName;
        private final ScheduleMetrics metrics;
        private final long runMillis;

        Entry(String strategyName, ScheduleMetrics metrics, long runMillis) {
            this.strategyName = strategyName;
            this.metrics = metrics;
            this.runMillis = runMillis;
        }

        /** @return the strategy's name */
        public String getStrategyName() {
            return strategyName;
        }

        /** @return the metrics of this strategy's run */
        public ScheduleMetrics getMetrics() {
            return metrics;
        }

        /** @return how long the allocation took, in milliseconds */
        public long getRunMillis() {
            return runMillis;
        }
    }

    private final MetricsCalculator calculator = new MetricsCalculator();

    /**
     * Runs every strategy on its own copy of the scenario.
     *
     * @param scenario          the trains and platforms to use (not changed)
     * @param strategies        the strategies to compare
     * @param bufferMinutes     clearance buffer for every run
     * @param maxWaitMinutes    maximum wait for every run
     * @param preemptionEnabled whether safe preemption is allowed in every run
     * @return one entry per strategy, in the given order
     */
    public List<Entry> compare(Scenario scenario, List<PlatformAllocationStrategy> strategies,
                               int bufferMinutes, int maxWaitMinutes, boolean preemptionEnabled) {
        List<Entry> entries = new ArrayList<>();
        for (PlatformAllocationStrategy strategy : strategies) {
            // Step 1: a fresh, independent copy of the scenario for this strategy only.
            Scenario copy = scenario.copy();
            Station station = copy.buildStation("Arena copy");
            Schedule schedule = new Schedule(bufferMinutes);
            PlatformAllocator allocator = new PlatformAllocator(station, schedule, maxWaitMinutes);
            allocator.setPreemptionEnabled(preemptionEnabled);

            // Step 2: allocate the same trains with this strategy and time it.
            long startNanos = System.nanoTime();
            // [STRATEGY] same call for every strategy; the object decides the behaviour.
            List<AllocationResult> results = allocator.allocateAll(copy.getTrains(), strategy);
            long runMillis = (System.nanoTime() - startNanos) / 1_000_000;

            // Step 3: measure the result with the same definitions as everywhere else.
            ScheduleMetrics metrics = calculator.calculate(station, schedule, copy.getTrains(),
                    calculator.countConflicts(results));
            entries.add(new Entry(strategy.getName(), metrics, runMillis));
        }
        return entries;
    }
}
