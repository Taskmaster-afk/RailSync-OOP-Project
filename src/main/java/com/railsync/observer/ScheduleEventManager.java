package com.railsync.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the list of observers and sends every event to all of them.
 *
 * <p>Observer pattern: this is the "subject" (publisher) side.
 * SchedulingService calls publish(); every subscribed panel's
 * onScheduleEvent() runs.</p>
 *
 * <p>OOP concepts: [OBSERVER] pattern, Encapsulation (the observer list is
 * private), SOLID-O (adding a new screen = subscribing a new observer; the
 * services do not change).</p>
 *
 * <p>Collections: an ArrayList keeps observers in the order they subscribed,
 * so notification order is predictable.</p>
 *
 * <p>Works with: ScheduleObserver, ScheduleEvent, SchedulingService.</p>
 */
public class ScheduleEventManager {

    private final List<ScheduleObserver> observers = new ArrayList<>();

    /**
     * Registers an observer. Subscribing the same observer twice has no effect.
     *
     * @param observer the observer to add
     */
    public void subscribe(ScheduleObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Removes an observer.
     *
     * @param observer the observer to remove
     */
    public void unsubscribe(ScheduleObserver observer) {
        observers.remove(observer);
    }

    /**
     * Sends an event to every observer.
     *
     * @param event the event to send
     */
    public void publish(ScheduleEvent event) {
        // Loop over a copy, so an observer may safely unsubscribe while being notified.
        List<ScheduleObserver> snapshot = new ArrayList<>(observers);
        for (ScheduleObserver observer : snapshot) {
            // [OBSERVER] each subscribed panel refreshes itself.
            observer.onScheduleEvent(event);
        }
    }

    /** @return how many observers are subscribed */
    public int getObserverCount() {
        return observers.size();
    }
}
