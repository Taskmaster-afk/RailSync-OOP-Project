package com.railsync.persistence;

import com.railsync.exception.RailSyncException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens the SQLite database and keeps its schema up to date.
 *
 * <p>Why it exists: every repository needs a connection and the tables, but
 * none of them should know where the file is or how the schema evolves. This
 * class owns both. The schema version is stored inside the database file
 * ({@code PRAGMA user_version}), so a newer RailSync can upgrade an older file
 * step by step (see {@link #migrate()}).</p>
 *
 * <p>SQLite is embedded: the whole database is one file and there is no server
 * to install. Tests use an in-memory database ({@link #inMemory()}), so they
 * never touch the user's real data.</p>
 *
 * <p>OOP concepts: [ENCAPSULATION] the connection is private; [SOLID-S] it only
 * connects and migrates, the repositories do the reading and writing.</p>
 *
 * <p>Works with: SqliteStationRepository, SqliteAuditLog, Main.</p>
 */
public final class Database implements AutoCloseable {

    /** The schema version this build writes. Raise it and add a step in migrate() to change the schema. */
    public static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final String location;

    private Database(String jdbcUrl, String location) {
        this.location = location;
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new RailSyncException("Could not open the database at " + location + ": " + e.getMessage(), e);
        }
        try {
            try (Statement statement = connection.createStatement()) {
                // Foreign keys are off by default in SQLite; switch them on for this connection.
                statement.execute("PRAGMA foreign_keys = ON");
            }
            migrate();
        } catch (SQLException | RuntimeException e) {
            // A refused file must not stay open (and locked) behind the error.
            close();
            if (e instanceof RailSyncException) {
                throw (RailSyncException) e;
            }
            throw new RailSyncException("Could not prepare the database at " + location + ": " + e.getMessage(), e);
        }
    }

    /**
     * Opens (or creates) a database file, creating its folder if needed.
     *
     * @param file the database file, e.g. ~/.railsync/railsync.db
     * @return the open database
     */
    public static Database open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException e) {
            throw new RailSyncException("Could not create the folder for " + file + ": " + e.getMessage(), e);
        }
        return new Database("jdbc:sqlite:" + file.toAbsolutePath(), file.toAbsolutePath().toString());
    }

    /**
     * Opens a private in-memory database (gone when closed). Used by the tests.
     *
     * @return the open database
     */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:", "memory");
    }

    /** @return the default file: the folder in the system property "railsync.db", else ~/.railsync/railsync.db */
    public static Path defaultFile() {
        String override = System.getProperty("railsync.db");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".railsync", "railsync.db");
    }

    /** @return the shared connection (used by the repositories in this package) */
    Connection connection() {
        return connection;
    }

    /** @return where the database lives (a file path, or "memory") */
    public String getLocation() {
        return location;
    }

    /** @return the schema version stored in the file */
    public int getSchemaVersion() {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RailSyncException("Could not read the schema version: " + e.getMessage(), e);
        }
    }

    /**
     * Brings the schema up to {@link #SCHEMA_VERSION}, one version at a time.
     * A new file starts at version 0 and gets every step.
     */
    private void migrate() throws SQLException {
        int version = getSchemaVersion();
        if (version > SCHEMA_VERSION) {
            throw new RailSyncException("The database was written by a newer RailSync (schema " + version
                    + "); this version understands schema " + SCHEMA_VERSION + ".");
        }
        try (Statement statement = connection.createStatement()) {
            // Step 1 (version 0 -> 1): the first schema.
            if (version < 1) {
                statement.execute("CREATE TABLE platforms ("
                        + " id TEXT PRIMARY KEY,"
                        + " number INTEGER NOT NULL UNIQUE CHECK (number > 0),"
                        + " length_m INTEGER NOT NULL CHECK (length_m > 0),"
                        + " train_types TEXT NOT NULL,"
                        + " status TEXT NOT NULL CHECK (status IN ('OPERATIONAL', 'FAILED')))");
                statement.execute("CREATE TABLE trains ("
                        + " id TEXT PRIMARY KEY,"
                        + " number TEXT NOT NULL UNIQUE,"
                        + " name TEXT NOT NULL,"
                        + " type TEXT NOT NULL CHECK (type IN ('EXPRESS', 'PASSENGER', 'FREIGHT')),"
                        + " arrival TEXT NOT NULL,"
                        + " departure TEXT NOT NULL,"
                        + " base_priority INTEGER NOT NULL CHECK (base_priority BETWEEN 1 AND 5),"
                        + " carriages INTEGER NOT NULL CHECK (carriages > 0),"
                        + " delay_min INTEGER NOT NULL DEFAULT 0 CHECK (delay_min >= 0),"
                        + " given_up INTEGER NOT NULL DEFAULT 0 CHECK (given_up IN (0, 1)))");
                statement.execute("CREATE TABLE bookings ("
                        + " train_id TEXT PRIMARY KEY REFERENCES trains(id) ON DELETE CASCADE,"
                        + " platform_id TEXT NOT NULL REFERENCES platforms(id),"
                        + " start_time TEXT NOT NULL)");
                statement.execute("CREATE TABLE settings ("
                        + " key TEXT PRIMARY KEY,"
                        + " value TEXT NOT NULL)");
                statement.execute("CREATE TABLE audit_log ("
                        + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " logged_at TEXT NOT NULL,"
                        + " event_type TEXT NOT NULL,"
                        + " message TEXT NOT NULL)");
                statement.execute("CREATE INDEX idx_bookings_platform ON bookings(platform_id)");
                statement.execute("PRAGMA user_version = 1");
            }
            // Future steps go here, e.g. "if (version < 2) { ALTER TABLE ...; PRAGMA user_version = 2; }".
        }
    }

    /**
     * Runs work inside one transaction: either everything is saved or nothing is.
     *
     * @param work the statements to run
     */
    void inTransaction(SqlWork work) {
        try {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RailSyncException("Database error: " + e.getMessage(), e);
        }
    }

    /** Closes the connection. */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // Nothing useful to do while shutting down.
        }
    }

    /** A block of database work that may throw SQLException. */
    @FunctionalInterface
    interface SqlWork {
        /**
         * @param connection the open connection
         * @throws SQLException on any database error
         */
        void run(Connection connection) throws SQLException;
    }
}
