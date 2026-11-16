package com.railsync.observer;

import com.railsync.model.ReservationChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One notification sent to every observer: what happened, a readable
 * message, and the bookings that moved (empty if none).
 *
 * <p>OOP concepts: Encapsulation and immutability (final fields, the
 * change list is copied in and handed out read-only).</p>
 *
 * <p>Works with: ScheduleEventManager (sends it), ScheduleObserver (receives
 * it), ReservationChange.</p>
 */
public final class ScheduleEvent {

    private final ScheduleEventType type;
    private final String message;
    private final List<ReservationChange> changes;

    /**
     * Creates an event with no booking changes.
     *
     * @param type    what happened
     * @param message plain-English description
     */
    public ScheduleEvent(ScheduleEventType type, String message) {
        this(type, message, new ArrayList<>());
    }

    /**
     * Creates an event that carries the bookings that moved.
     *
     * @param type    what happened
     * @param message plain-English description
     * @param changes bookings before and after (copied)
     */
    public ScheduleEvent(ScheduleEventType type, String message, List<ReservationChange> changes) {
        this.type = type;
        this.message = message;
        this.changes = new ArrayList<>(changes);
    }

    /** @return the kind of event */
    public ScheduleEventType getType() {
        return type;
    }

    /** @return a readable description */
    public String getMessage() {
        return message;
    }

    /** @return the moved bookings, read-only */
    public List<ReservationChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    /** @return "TYPE: message" */
    @Override
    public String toString() {
        return type + ": " + message;
    }
}
