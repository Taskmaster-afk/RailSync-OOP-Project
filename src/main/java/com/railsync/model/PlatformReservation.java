package com.railsync.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One booking: "train T stands on platform P from start until end".
 *
 * <p>The time range is <b>half-open</b>: [start, end). The train occupies
 * the platform at the start minute but has already left at the end minute.
 * Example: a reservation 10:00-10:30 is busy at 10:29 and free at 10:30.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Composition: only Schedule can create a reservation (the constructor
 *       is package-private) and only Schedule deletes it. A reservation has
 *       no meaning without the schedule that owns it.</li>
 *   <li>Encapsulation / immutability: every field is final and there are
 *       no setters. To "move" a train, Schedule deletes the old reservation
 *       and creates a new one, so a half-updated booking can never exist.</li>
 *   <li>Association: it references a Train and a Platform.</li>
 * </ul>
 *
 * <p>Works with: Schedule (owner), Train, Platform.</p>
 */
public class PlatformReservation {

    private final Train train;
    private final Platform platform;
    private final LocalDateTime start;
    private final LocalDateTime end;

    /**
     * Package-private: only Schedule creates reservations.
     * Schedule has already validated all values before calling this.
     */
    PlatformReservation(Train train, Platform platform, LocalDateTime start, LocalDateTime end) {
        // [COMPOSITION] created only by Schedule, which owns this object.
        this.train = train;
        this.platform = platform;
        this.start = start;
        this.end = end;
    }

    /** @return the train that holds this booking */
    public Train getTrain() {
        return train;
    }

    /** @return the booked platform */
    public Platform getPlatform() {
        return platform;
    }

    /** @return the minute the train starts occupying the platform (inclusive) */
    public LocalDateTime getStart() {
        return start;
    }

    /** @return the minute the platform becomes free again (exclusive) */
    public LocalDateTime getEnd() {
        return end;
    }

    /** @return how long the platform is occupied */
    public Duration getDuration() {
        return Duration.between(start, end);
    }

    /** @return label such as "12951 on PF-2 [10:00, 10:30)" */
    @Override
    public String toString() {
        return train.getNumber() + " on " + platform
                + " [" + start.toLocalTime() + ", " + end.toLocalTime() + ")";
    }
}
