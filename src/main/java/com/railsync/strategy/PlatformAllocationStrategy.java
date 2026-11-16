package com.railsync.strategy;

import com.railsync.model.Train;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The "scheduling policy" of the station: given the trains waiting for a
 * platform right now, in which order should they be served?
 *
 * <p>OS analogy: this is the CPU scheduler. The list it receives is the
 * <b>ready queue</b> (trains that have arrived but hold no platform), and
 * the order it returns decides who gets the next free platform (CPU).</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Abstraction / interface: PlatformAllocator only knows this interface,
 *       never a concrete strategy.</li>
 *   <li>Strategy pattern: FCFS, Priority (and later SJF, Aging) are
 *       interchangeable implementations picked at runtime.</li>
 *   <li>SOLID-O (open/closed): a new policy is a new class; the allocator
 *       does not change.</li>
 *   <li>SOLID-I (interface segregation): only two small methods.</li>
 * </ul>
 *
 * <p>Why an interface and not an abstract class: strategies share no
 * state, and any class should be free to become a strategy. Shared sorting
 * code that most strategies want is offered separately by the abstract
 * class QueueOrderingStrategy.</p>
 *
 * <p>Works with: PlatformAllocator (caller), QueueOrderingStrategy,
 * FcfsStrategy, PriorityStrategy.</p>
 */
public interface PlatformAllocationStrategy {

    /**
     * Returns a short name for menus and reports.
     *
     * @return e.g. "FCFS (First-Fit)"
     */
    String getName();

    /**
     * Returns a short plain-English explanation, including the OS analogy,
     * shown on the Allocation screen.
     *
     * @return a one- or two-sentence description
     */
    String getDescription();

    /**
     * Puts the waiting trains in the order they should be served. The train
     * at index 0 is tried first. The given list must not be changed; a new
     * list is returned.
     *
     * @param readyQueue trains that have arrived and are waiting for a platform
     * @param now        the current simulated time (used by time-aware
     *                   policies such as aging)
     * @return a new list in service order
     */
    List<Train> orderReadyQueue(List<Train> readyQueue, LocalDateTime now);
}
