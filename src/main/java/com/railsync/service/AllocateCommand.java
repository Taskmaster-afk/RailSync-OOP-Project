package com.railsync.service;

import com.railsync.model.Schedule;
import com.railsync.model.Train;
import com.railsync.strategy.PlatformAllocationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Undoable "allocate these trains with this strategy".
 *
 * <p>OOP concepts: [COMMAND] pattern, Inheritance (extends SnapshotCommand,
 * which provides undo()).</p>
 *
 * <p>Works with: PlatformAllocator, SchedulingService (creates and runs it).</p>
 */
public class AllocateCommand extends SnapshotCommand {

    private final PlatformAllocator allocator;
    private final List<Train> toAllocate;
    private final PlatformAllocationStrategy strategy;
    private List<AllocationResult> results = new ArrayList<>();

    /**
     * Creates the command (nothing happens until execute()).
     *
     * @param schedule   the schedule
     * @param allTrains  every train (for the status snapshot)
     * @param allocator  the allocation engine
     * @param toAllocate the trains to place
     * @param strategy   the strategy to use
     */
    public AllocateCommand(Schedule schedule, List<Train> allTrains, PlatformAllocator allocator,
                           List<Train> toAllocate, PlatformAllocationStrategy strategy) {
        super(schedule, allTrains);
        this.allocator = allocator;
        this.toAllocate = new ArrayList<>(toAllocate);
        this.strategy = strategy;
    }

    /** Saves the current state, then allocates. */
    @Override
    public void execute() {
        takeSnapshot();
        results = allocator.allocateAll(toAllocate, strategy);
    }

    /** @return the results of execute() */
    public List<AllocationResult> getResults() {
        return results;
    }

    /** @return e.g. "allocation (FCFS (First-Fit))" */
    @Override
    public String getDescription() {
        return "allocation (" + strategy.getName() + ")";
    }
}
