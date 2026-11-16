package com.railsync.exception;

/**
 * Thrown when a train is asked to use a platform it cannot use: the platform has failed, is too short, or does not accept that train category. <p>Thrown by: Schedule.reserve() when compatibility rules are broken.</p>
 *
 * <p>OOP concepts: Inheritance (extends RailSyncException, so the GUI can
 * catch all RailSync errors with one catch block).</p>
 */
public class PlatformUnavailableException extends RailSyncException {

    /**
     * Creates the exception with a user-friendly message.
     *
     * @param message plain-English explanation shown to the user
     */
    public PlatformUnavailableException(String message) {
        super(message);
    }
}
