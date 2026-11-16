package com.railsync.exception;

/**
 * Thrown when a reservation would overlap (including the clearance buffer) with another reservation on the same platform, or the train already holds a reservation. <p>Thrown by: Schedule.reserve().</p>
 *
 * <p>OOP concepts: Inheritance (extends RailSyncException, so the GUI can
 * catch all RailSync errors with one catch block).</p>
 */
public class SchedulingConflictException extends RailSyncException {

    /**
     * Creates the exception with a user-friendly message.
     *
     * @param message plain-English explanation shown to the user
     */
    public SchedulingConflictException(String message) {
        super(message);
    }
}
