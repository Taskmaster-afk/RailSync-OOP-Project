package com.railsync.strategy;

import com.railsync.model.ExpressTrain;
import com.railsync.model.FreightTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Train;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the queue order produced by each strategy on its own (no platforms).
 */
class StrategyOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 30);

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    private List<Train> sampleQueue() {
        List<Train> queue = new ArrayList<>();
        queue.add(new FreightTrain("T1", "F1", "Coal", at(10, 0), at(10, 30), 2, 20));          // priority 2
        queue.add(new PassengerTrain("T2", "P2", "Local", at(10, 5), at(10, 15), 3, 8));        // priority 4
        queue.add(new ExpressTrain("T3", "E3", "Rajdhani", at(10, 10), at(10, 20), 4, 12));      // priority 7
        return queue;
    }

    // Scenario: FCFS on a mixed queue. Expected: order by arrival F1, P2, E3.
    @Test
    void fcfsOrdersByArrival() {
        List<Train> order = new FcfsStrategy().orderReadyQueue(sampleQueue(), NOW);
        assertEquals("F1", order.get(0).getNumber());
        assertEquals("P2", order.get(1).getNumber());
        assertEquals("E3", order.get(2).getNumber());
    }

    // Scenario: Priority on the same queue. Expected: E3 (7), P2 (4), F1 (2).
    @Test
    void priorityOrdersByEffectivePriority() {
        List<Train> order = new PriorityStrategy().orderReadyQueue(sampleQueue(), NOW);
        assertEquals("E3", order.get(0).getNumber());
        assertEquals("P2", order.get(1).getNumber());
        assertEquals("F1", order.get(2).getNumber());
    }

    // Scenario: two trains with equal priority. Expected: the earlier arrival goes first (tie-break).
    @Test
    void priorityTieIsBrokenByArrival() {
        List<Train> queue = new ArrayList<>();
        queue.add(new PassengerTrain("T1", "LATE", "L", at(10, 20), at(10, 30), 3, 8));
        queue.add(new PassengerTrain("T2", "EARLY", "E", at(10, 0), at(10, 10), 3, 8));
        List<Train> order = new PriorityStrategy().orderReadyQueue(queue, NOW);
        assertEquals("EARLY", order.get(0).getNumber());
    }

    // Scenario: strategy is given a list. Expected: the original list is not reordered.
    @Test
    void originalQueueIsNotModified() {
        List<Train> queue = sampleQueue();
        new PriorityStrategy().orderReadyQueue(queue, NOW);
        assertEquals("F1", queue.get(0).getNumber());
    }

    // Scenario: strategies used through the interface type. Expected: runtime dispatch gives different first trains.
    @Test
    void strategiesAreInterchangeableThroughInterface() {
        PlatformAllocationStrategy strategy = new FcfsStrategy();
        assertEquals("F1", strategy.orderReadyQueue(sampleQueue(), NOW).get(0).getNumber());
        strategy = new PriorityStrategy();
        assertEquals("E3", strategy.orderReadyQueue(sampleQueue(), NOW).get(0).getNumber());
    }
}
