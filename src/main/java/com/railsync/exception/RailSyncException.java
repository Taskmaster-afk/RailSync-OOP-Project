package com.railsync.exception;

/**
 * Base class for every RailSync-specific error.
 *
 * <p>Why it exists: the GUI can catch this one type and show its friendly
 * message, instead of catching four different exception classes everywhere.</p>
 *
 * <p>OOP concepts: Inheritance (extends RuntimeException, and is itself the
 * parent of the four specific exceptions below).</p>
 *
 * <p>Design decision: it is an <b>unchecked</b> exception (RuntimeException).
 * These errors mean "the user gave invalid input" or "this request breaks a
 * scheduling rule". Making them unchecked keeps method signatures short,
 * while the GUI still catches them in one place. Ordinary situations such as
 * "no free platform found" are NOT exceptions; they are returned as normal
 * results and handled with if-checks.</p>
 *
 * <p>Works with: InvalidTrainException, InvalidPlatformException,
 * PlatformUnavailableException, SchedulingConflictException.</p>
 */
public class RailSyncException extends RuntimeException {

    /**
     * Creates an exception with a message that is safe to show to the user.
     *
     * @param message plain-English explanation of what went wrong
     */
    public RailSyncException(String message) {
        super(message);
    }

    /**
     * Creates an exception that keeps the technical cause (for example a
     * database error) behind a friendly message.
     *
     * @param message plain-English explanation of what went wrong
     * @param cause   the underlying exception
     */
    public RailSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
