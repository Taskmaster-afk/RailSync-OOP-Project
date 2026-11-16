package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.Train;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The outcome of trying to place one train: either "placed on platform P
 * from time S" or "not placed, because ...".
 *
 * <p>Why it exists: failing to find a platform is a normal outcome, not an
 * error, so it is returned as a value instead of thrown as an exception.
 * The GUI and reports read these results to show what happened and why.</p>
 *
 * <p>It also records whether a <b>conflict</b> was detected: the train
 * could not get a platform at its arrival time because every compatible
 * platform was busy (so it had to wait, displace another train, or be
 * refused). A train that simply fits no platform is not a conflict.</p>
 *
 * <p>OOP concepts: Encapsulation and immutability (final fields, no
 * setters). The constructor is private; the static factory methods make it
 * impossible to build a half-filled result.</p>
 *
 * <p>Works with: PlatformAllocator (creates them), MetricsCalculator
 * (counts conflicts), Train, Platform.</p>
 */
public final class AllocationResult {

    private final Train train;
    private final boolean allocated;
    private final Platform platform;
    private final LocalDateTime start;
    private final boolean conflictAtArrival;
    private final String message;

    private AllocationResult(Train train, boolean allocated, Platform platform, LocalDateTime start,
                             boolean conflictAtArrival, String message) {
        this.train = train;
        this.allocated = allocated;
        this.platform = platform;
        this.start = start;
        this.conflictAtArrival = conflictAtArrival;
        this.message = message;
    }

    /**
     * Builds a "placed" result. If the train had to wait, that counts as a conflict.
     *
     * @param train    the train
     * @param platform the platform it got
     * @param start    when it starts occupying the platform
     * @return the result
     */
    public static AllocationResult success(Train train, Platform platform, LocalDateTime start) {
        long wait = Duration.between(train.getExpectedArrival(), start).toMinutes();
        String text = "Train " + train.getNumber() + " -> " + platform + " at " + start.toLocalTime()
                + (wait > 0 ? " (waited " + wait + " min at the outer signal)" : " (on arrival)");
        return new AllocationResult(train, true, platform, start, wait > 0, text);
    }

    /**
     * Builds a "placed by preemption" result: the train took a platform from
     * a lower-priority train, which was safely moved to another platform.
     *
     * @param train         the train that got the platform
     * @param platform      the platform it got
     * @param start         when it starts occupying it
     * @param displaced     the lower-priority train that was moved
     * @param movedTo       where the displaced train now goes
     * @return the result
     */
    public static AllocationResult successByPreemption(Train train, Platform platform, LocalDateTime start,
                                                       Train displaced, Platform movedTo) {
        String text = "Train " + train.getNumber() + " -> " + platform + " at " + start.toLocalTime()
                + " (PREEMPTED lower-priority " + displaced.getNumber() + ", which was moved to " + movedTo + ")";
        return new AllocationResult(train, true, platform, start, true, text);
    }

    /**
     * Builds a "not placed" result.
     *
     * @param train             the train
     * @param reason            plain-English explanation
     * @param conflictAtArrival true if the cause was busy platforms (a conflict),
     *                          false if the train fits no platform at all
     * @return the result
     */
    public static AllocationResult failure(Train train, String reason, boolean conflictAtArrival) {
        return new AllocationResult(train, false, null, null, conflictAtArrival,
                "Train " + train.getNumber() + " NOT allocated: " + reason);
    }

    /** @return the train this result is about */
    public Train getTrain() {
        return train;
    }

    /** @return true if a platform was found */
    public boolean isAllocated() {
        return allocated;
    }

    /** @return the platform, or null if not allocated */
    public Platform getPlatform() {
        return platform;
    }

    /** @return the start time, or null if not allocated */
    public LocalDateTime getStart() {
        return start;
    }

    /** @return true if every compatible platform was busy when the train arrived */
    public boolean hadConflictAtArrival() {
        return conflictAtArrival;
    }

    /**
     * Minutes the train waited in the queue after its expected arrival.
     *
     * @return waiting minutes (0 if not allocated or served on arrival)
     */
    public long getQueueWaitMinutes() {
        if (!allocated) {
            return 0;
        }
        return Duration.between(train.getExpectedArrival(), start).toMinutes();
    }

    /** @return a readable one-line explanation */
    public String getMessage() {
        return message;
    }

    /** @return same as getMessage() */
    @Override
    public String toString() {
        return message;
    }
}
