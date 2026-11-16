package com.railsync.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A record of one booking moving: "train T went from (old platform, old time)
 * to (new platform, new time)". If the train lost its platform completely,
 * the "new" values are null.
 *
 * <p>Why it exists: delays, ripples and breakdowns must be reported, never
 * applied silently. The reports and the GUI animation (old trajectory as a
 * faded "ghost", new one sliding into place) both need the before-and-after
 * values, and a PlatformReservation is gone once Schedule deletes it.</p>
 *
 * <p>OOP concepts: Encapsulation and immutability (final fields, no setters).</p>
 *
 * <p>Works with: DelayManager, DelayReport, BreakdownReport, ScheduleEvent.</p>
 */
public final class ReservationChange {

    private final Train train;
    private final LocalDateTime oldArrival;
    private final Platform oldPlatform;
    private final LocalDateTime oldStart;
    private final LocalDateTime oldEnd;
    private final Platform newPlatform;
    private final LocalDateTime newStart;
    private final LocalDateTime newEnd;

    /**
     * Creates a change record for a train whose arrival time did not change
     * (for example a train pushed by a ripple, or moved by a breakdown).
     *
     * @param before the booking before the change (must not be null)
     * @param after  the booking after the change, or null if the train lost its platform
     */
    public ReservationChange(PlatformReservation before, PlatformReservation after) {
        this(before, after, before.getTrain().getExpectedArrival());
    }

    /**
     * Creates a change record for a train whose arrival time DID change
     * (the delayed train itself), remembering when it used to arrive.
     * The animation needs this to draw the old trajectory correctly.
     *
     * @param before     the booking before the change (must not be null)
     * @param after      the booking after the change, or null if the train lost its platform
     * @param oldArrival the train's expected arrival before the change
     */
    public ReservationChange(PlatformReservation before, PlatformReservation after, LocalDateTime oldArrival) {
        this.train = before.getTrain();
        this.oldArrival = oldArrival;
        this.oldPlatform = before.getPlatform();
        this.oldStart = before.getStart();
        this.oldEnd = before.getEnd();
        this.newPlatform = after == null ? null : after.getPlatform();
        this.newStart = after == null ? null : after.getStart();
        this.newEnd = after == null ? null : after.getEnd();
    }

    /** @return the train whose booking changed */
    public Train getTrain() {
        return train;
    }

    /** @return the train's expected arrival before the change */
    public LocalDateTime getOldArrival() {
        return oldArrival;
    }

    /** @return platform before the change */
    public Platform getOldPlatform() {
        return oldPlatform;
    }

    /** @return start before the change */
    public LocalDateTime getOldStart() {
        return oldStart;
    }

    /** @return end before the change */
    public LocalDateTime getOldEnd() {
        return oldEnd;
    }

    /** @return platform after the change, or null if the train lost its platform */
    public Platform getNewPlatform() {
        return newPlatform;
    }

    /** @return start after the change, or null */
    public LocalDateTime getNewStart() {
        return newStart;
    }

    /** @return end after the change, or null */
    public LocalDateTime getNewEnd() {
        return newEnd;
    }

    /** @return true if the train still has a platform after the change */
    public boolean hasNewBooking() {
        return newPlatform != null;
    }

    /** @return true if the train ended up on a different platform */
    public boolean changedPlatform() {
        return newPlatform != null && newPlatform != oldPlatform;
    }

    /** @return how many minutes later the train now starts (0 if it lost its booking) */
    public long getShiftMinutes() {
        if (newStart == null) {
            return 0;
        }
        return Duration.between(oldStart, newStart).toMinutes();
    }

    /** @return text such as "12951: PF-2 10:00 -> PF-2 10:10 (+10 min)" */
    @Override
    public String toString() {
        String before = train.getNumber() + ": " + oldPlatform + " " + oldStart.toLocalTime();
        if (newPlatform == null) {
            return before + " -> no platform";
        }
        return before + " -> " + newPlatform + " " + newStart.toLocalTime() + " (+" + getShiftMinutes() + " min)";
    }
}
