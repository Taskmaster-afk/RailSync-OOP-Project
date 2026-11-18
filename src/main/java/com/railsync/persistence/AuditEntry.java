package com.railsync.persistence;

import java.time.LocalDateTime;

/**
 * One line of the audit log.
 *
 * <p>OOP concepts: [ENCAPSULATION] immutable value object (final fields, no setters).</p>
 *
 * <p>Works with: AuditLog, SqliteAuditLog, the Reports screen.</p>
 */
public final class AuditEntry {

    private final long id;
    private final LocalDateTime loggedAt;
    private final String eventType;
    private final String message;

    /**
     * @param id        the entry's number (grows with time)
     * @param loggedAt  when it was recorded (real clock time)
     * @param eventType the kind of change
     * @param message   what happened
     */
    public AuditEntry(long id, LocalDateTime loggedAt, String eventType, String message) {
        this.id = id;
        this.loggedAt = loggedAt;
        this.eventType = eventType;
        this.message = message;
    }

    /** @return the entry's number */
    public long getId() {
        return id;
    }

    /** @return when it was recorded */
    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    /** @return the kind of change */
    public String getEventType() {
        return eventType;
    }

    /** @return what happened */
    public String getMessage() {
        return message;
    }

    /** @return "2026-11-19 10:42:07  DELAY  ..." */
    @Override
    public String toString() {
        return loggedAt.toString().replace('T', ' ') + "  " + eventType + "  " + message;
    }
}
