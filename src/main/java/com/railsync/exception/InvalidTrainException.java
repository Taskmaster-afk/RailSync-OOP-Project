package com.railsync.exception;

/**
 * Thrown when train data is invalid, for example a departure before the arrival, a blank train number, or a dwell time shorter than the train type allows. <p>Thrown by: Train constructor and Train.updateTimes(), Train.applyDelay().</p>
 *
 * <p>OOP concepts: Inheritance (extends RailSyncException, so the GUI can
 * catch all RailSync errors with one catch block).</p>
 */
public class InvalidTrainException extends RailSyncException {

    /**
     * Creates the exception with a user-friendly message.
     *
     * @param message plain-English explanation shown to the user
     */
    public InvalidTrainException(String message) {
        super(message);
    }
}
