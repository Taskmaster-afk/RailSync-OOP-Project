package com.railsync.observer;

/**
 * The kinds of things that can happen to the schedule. Observers can use the
 * type to decide how to react (for example, the diagram animates only on
 * DELAY, REASSIGNMENT and BREAKDOWN).
 */
public enum ScheduleEventType {
    /** Trains were added, edited or removed. */
    TRAINS_CHANGED,
    /** Platforms were added, edited, removed or repaired. */
    PLATFORMS_CHANGED,
    /** The allocator placed (or failed to place) trains. */
    ALLOCATION,
    /** A train was delayed; its booking may have moved. */
    DELAY,
    /** A train was moved to another platform or time. */
    REASSIGNMENT,
    /** A clash was found: a train could not get a platform when it needed one. */
    CONFLICT,
    /** A platform broke down and its trains were reassigned. */
    BREAKDOWN,
    /** All bookings were cleared, or a whole new scenario was loaded. */
    SCHEDULE_RESET,
    /** The allocation strategy or a setting was changed. */
    SETTINGS_CHANGED,
    /** The last allocation or delay was undone. */
    UNDO
}
