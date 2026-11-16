package com.railsync.strategy;

import com.railsync.model.Train;

import java.time.LocalDateTime;

/**
 * Shortest Dwell First: among the waiting trains, the one with the shortest
 * stay at the platform is served first.
 *
 * <p>OS analogy: SJF (Shortest Job First). Serving short jobs first keeps
 * the average waiting time low, because many short stays are not stuck
 * behind one long one. The cost: a long job (for example a slow-loading
 * train) can be passed over again and again and <b>starve</b>.</p>
 *
 * <p>Worked example (one platform, frees at 10:30): L (12-minute stop) has
 * waited since 10:01 and E2 (10-minute stop) arrived at 10:27. SJF serves
 * E2 first because 10 &lt; 12, even though L has waited much longer.</p>
 *
 * <p>Note: we know each train's dwell time exactly from the timetable,
 * unlike an OS, which has to guess a process's burst time.</p>
 *
 * <p>OOP concepts: Inheritance (extends QueueOrderingStrategy),
 * Polymorphism (overrides compareByPolicy), Strategy pattern.</p>
 */
public class ShortestDwellFirstStrategy extends QueueOrderingStrategy {

    /** @return "Shortest Dwell First (SJF)" */
    @Override
    public String getName() {
        return "Shortest Dwell First (SJF)";
    }

    /** @return the explanation shown on the Allocation screen */
    @Override
    public String getDescription() {
        return "OS analogy: SJF (Shortest Job First). The waiting train with the shortest platform stay goes first. Keeps average waiting low, but long stays can starve.";
    }

    /**
     * Shorter dwell time comes first.
     *
     * @return negative if a stays for less time than b
     */
    @Override
    protected int compareByPolicy(Train a, Train b, LocalDateTime now) {
        return a.getDwellTime().compareTo(b.getDwellTime());
    }
}
