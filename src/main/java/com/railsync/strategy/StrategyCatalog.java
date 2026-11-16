package com.railsync.strategy;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of every allocation strategy RailSync offers, in menu order.
 *
 * <p>Why it exists: the comparison arena and the yard view both need "one of
 * each strategy". Keeping the list here means a new strategy is added in one
 * place only (SOLID-O for the screens that use it).</p>
 */
public final class StrategyCatalog {

    private StrategyCatalog() {
        // Only static methods.
    }

    /**
     * Creates a fresh instance of every strategy (aging uses its default rate).
     *
     * @return FCFS, Priority, Shortest Dwell First, Priority + Aging
     */
    public static List<PlatformAllocationStrategy> createAll() {
        // [SOLID-O] a new strategy = one new class + one line here; the allocator and screens stay unchanged.
        List<PlatformAllocationStrategy> list = new ArrayList<>();
        list.add(new FcfsStrategy());
        list.add(new PriorityStrategy());
        list.add(new ShortestDwellFirstStrategy());
        list.add(new PriorityAgingStrategy());
        return list;
    }
}
