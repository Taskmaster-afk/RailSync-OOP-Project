package com.railsync.observer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the Observer pattern publisher.
 */
class ScheduleEventManagerTest {

    /** A test observer that just remembers what it received. */
    private static class RecordingObserver implements ScheduleObserver {
        private final List<ScheduleEvent> received = new ArrayList<>();

        @Override
        public void onScheduleEvent(ScheduleEvent event) {
            received.add(event);
        }
    }

    // Scenario: two observers subscribed, one event published. Expected: both receive it.
    @Test
    void everyObserverReceivesEvent() {
        ScheduleEventManager manager = new ScheduleEventManager();
        RecordingObserver first = new RecordingObserver();
        RecordingObserver second = new RecordingObserver();
        manager.subscribe(first);
        manager.subscribe(second);
        manager.publish(new ScheduleEvent(ScheduleEventType.DELAY, "late"));
        assertEquals(1, first.received.size());
        assertEquals(ScheduleEventType.DELAY, second.received.get(0).getType());
    }

    // Scenario: same observer subscribed twice. Expected: registered once, notified once.
    @Test
    void duplicateSubscriptionIgnored() {
        ScheduleEventManager manager = new ScheduleEventManager();
        RecordingObserver observer = new RecordingObserver();
        manager.subscribe(observer);
        manager.subscribe(observer);
        manager.publish(new ScheduleEvent(ScheduleEventType.ALLOCATION, "x"));
        assertEquals(1, manager.getObserverCount());
        assertEquals(1, observer.received.size());
    }

    // Scenario: observer unsubscribes. Expected: it receives nothing afterwards.
    @Test
    void unsubscribedObserverIsNotNotified() {
        ScheduleEventManager manager = new ScheduleEventManager();
        RecordingObserver observer = new RecordingObserver();
        manager.subscribe(observer);
        manager.unsubscribe(observer);
        manager.publish(new ScheduleEvent(ScheduleEventType.ALLOCATION, "x"));
        assertTrue(observer.received.isEmpty());
    }

    // Scenario: an observer unsubscribes itself while being notified. Expected: no error, the others still notified.
    @Test
    void observerMayUnsubscribeDuringNotification() {
        ScheduleEventManager manager = new ScheduleEventManager();
        RecordingObserver other = new RecordingObserver();
        ScheduleObserver selfRemoving = new ScheduleObserver() {
            @Override
            public void onScheduleEvent(ScheduleEvent event) {
                manager.unsubscribe(this);
            }
        };
        manager.subscribe(selfRemoving);
        manager.subscribe(other);
        assertDoesNotThrow(() -> manager.publish(new ScheduleEvent(ScheduleEventType.BREAKDOWN, "x")));
        assertEquals(1, other.received.size());
        assertEquals(1, manager.getObserverCount());
    }
}
