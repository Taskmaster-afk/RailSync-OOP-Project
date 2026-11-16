package com.railsync.exception;

/**
 * Thrown when platform data is invalid, for example a non-positive length, no supported train categories, or a duplicate platform number in a Station. <p>Thrown by: Platform constructor and setters, Station.addPlatform().</p>
 *
 * <p>OOP concepts: Inheritance (extends RailSyncException, so the GUI can
 * catch all RailSync errors with one catch block).</p>
 */
public class InvalidPlatformException extends RailSyncException {

    /**
     * Creates the exception with a user-friendly message.
     *
     * @param message plain-English explanation shown to the user
     */
    public InvalidPlatformException(String message) {
        super(message);
    }
}
