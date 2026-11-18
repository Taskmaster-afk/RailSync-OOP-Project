package com.railsync.persistence;

import com.railsync.service.StationSnapshot;

import java.util.Optional;

/**
 * Saves and loads the whole session (platforms, trains, bookings, settings).
 *
 * <p>Why it exists: the rest of the program depends on this small interface,
 * not on SQLite. Swapping the storage (a server database, a cloud store, a
 * fake for tests) means writing one new class; nothing else changes.</p>
 *
 * <p>OOP concepts: [ABSTRACTION] / [SOLID-D] callers depend on the interface;
 * [SOLID-I] two operations only. Design pattern: Repository.</p>
 *
 * <p>Works with: SqliteStationRepository (the implementation), AutoSaver, Main.</p>
 */
public interface StationRepository {

    /**
     * Replaces whatever was saved with this snapshot, all or nothing.
     *
     * @param snapshot the live session
     */
    void save(StationSnapshot snapshot);

    /**
     * Loads the saved session.
     *
     * @return the saved session, or empty if nothing has been saved yet
     */
    Optional<StationSnapshot> load();
}
