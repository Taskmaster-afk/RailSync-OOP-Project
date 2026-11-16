package com.railsync.strategy;

import com.railsync.exception.RailSyncException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Train;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the queue order of Shortest Dwell First and Priority with Aging.
 */
class AgingAndSjfTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 16, hour, minute);
    }

    // L: passenger priority 2, waiting since 10:01, 12-minute stop.
    private final Train lowPriority = new PassengerTrain("T1", "L", "Long stop", at(10, 1), at(10, 13), 1, 8);
    // E2: express priority 7, arrived 10:27, 10-minute stop.
    private final Train express = new ExpressTrain("T2", "E2", "Express", at(10, 27), at(10, 37), 4, 12);

    private List<Train> queue() {
        List<Train> list = new ArrayList<>();
        list.add(lowPriority);
        list.add(express);
        return list;
    }

    // Scenario: SJF with a 12-min and a 10-min stop. Expected: the 10-min train first.
    @Test
    void sjfServesShortestDwellFirst() {
        List<Train> order = new ShortestDwellFirstStrategy().orderReadyQueue(queue(), at(10, 30));
        assertEquals("E2", order.get(0).getNumber());
    }

    // Scenario: aging rate 4, at 10:30 L waited 29 min. Expected: L effective 2 + 7 = 9, E2 7 + 0 = 7.
    @Test
    void effectivePriorityGrowsWithWaiting() {
        PriorityAgingStrategy aging = new PriorityAgingStrategy(4);
        assertEquals(9, aging.getEffectivePriority(lowPriority, at(10, 30)));
        assertEquals(7, aging.getEffectivePriority(express, at(10, 30)));
    }

    // Scenario: same queue at 10:30. Expected: Aging serves L first, while plain Priority serves E2 first.
    @Test
    void agingOvertakesPlainPriority() {
        assertEquals("L", new PriorityAgingStrategy(4).orderReadyQueue(queue(), at(10, 30)).get(0).getNumber());
        assertEquals("E2", new PriorityStrategy().orderReadyQueue(queue(), at(10, 30)).get(0).getNumber());
    }

    // Scenario: very slow aging (120 min per point). Expected: behaves like plain Priority, E2 first.
    @Test
    void slowAgingBehavesLikePriority() {
        assertEquals("E2", new PriorityAgingStrategy(120).orderReadyQueue(queue(), at(10, 30)).get(0).getNumber());
    }

    // Scenario: "now" is before the train's arrival. Expected: no negative bonus, effective = plain priority.
    @Test
    void noBonusBeforeArrival() {
        assertEquals(2, new PriorityAgingStrategy(4).getEffectivePriority(lowPriority, at(9, 0)));
    }

    // Scenario: default constructor and invalid rates 0 and 121. Expected: default 4, invalid rejected.
    @Test
    void agingRateIsConfigurableAndValidated() {
        assertEquals(PriorityAgingStrategy.DEFAULT_MINUTES_PER_POINT, new PriorityAgingStrategy().getMinutesPerPriorityPoint());
        assertThrows(RailSyncException.class, () -> new PriorityAgingStrategy(0));
        assertThrows(RailSyncException.class, () -> new PriorityAgingStrategy(121));
    }
}
