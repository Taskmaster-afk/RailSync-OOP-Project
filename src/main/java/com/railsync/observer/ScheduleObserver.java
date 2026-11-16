package com.railsync.observer;

/**
 * Anything that wants to be told when the schedule changes, for example a
 * GUI panel that must refresh its table, or the diagram that animates a
 * delayed train.
 *
 * <p>Observer pattern: this is the "observer" side. Observers register with
 * ScheduleEventManager and are called back; the services never need to
 * know which panels exist.</p>
 *
 * <p>OOP concepts: Abstraction / interface, SOLID-D (services depend on this
 * interface, not on Swing classes), SOLID-I (one small method).</p>
 *
 * <p>Works with: ScheduleEventManager, ScheduleEvent.</p>
 */
public interface ScheduleObserver {

    // [SOLID-I] one small method: an observer is never forced to implement things it does not need.
    /**
     * Called after something in the schedule has changed.
     *
     * @param event what happened
     */
    void onScheduleEvent(ScheduleEvent event);
}
