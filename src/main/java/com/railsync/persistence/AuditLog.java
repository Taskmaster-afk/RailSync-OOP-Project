package com.railsync.persistence;

import java.util.List;

/**
 * A permanent, append-only record of every change made to the schedule:
 * when, what kind, and what happened.
 *
 * <p>Why it exists: in a real control room every decision must be traceable
 * afterwards ("who moved train 12951 to PF-3, and when?"). Entries are only
 * ever added, never edited or deleted by the application.</p>
 *
 * <p>OOP concepts: [ABSTRACTION] / [SOLID-D] the screens and the auto-saver use
 * this interface, not SQLite directly. Design pattern: Repository.</p>
 *
 * <p>Works with: SqliteAuditLog (the implementation), AutoSaver, the Reports screen.</p>
 */
public interface AuditLog {

    /**
     * Appends one entry, timestamped now.
     *
     * @param eventType the kind of change, e.g. "DELAY"
     * @param message   what happened
     */
    void record(String eventType, String message);

    /**
     * Reads the newest entries.
     *
     * @param limit how many at most
     * @return newest first
     */
    List<AuditEntry> recent(int limit);

    /** @return how many entries there are in total */
    int count();
}
