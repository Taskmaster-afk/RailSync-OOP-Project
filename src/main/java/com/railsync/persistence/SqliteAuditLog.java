package com.railsync.persistence;

import com.railsync.exception.RailSyncException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The audit log stored in SQLite's {@code audit_log} table.
 *
 * <p>Why it exists: the SQLite implementation of AuditLog. It only ever
 * INSERTs and SELECTs; there is deliberately no update or delete.</p>
 *
 * <p>OOP concepts: [POLYMORPHISM] used through the AuditLog interface;
 * PreparedStatement parameters only (safe against SQL injection).</p>
 *
 * <p>Works with: Database, AutoSaver, the Reports screen.</p>
 */
public final class SqliteAuditLog implements AuditLog {

    /** Longest message stored; longer ones are shortened (keeps the file small). */
    static final int MAX_MESSAGE_LENGTH = 2000;

    private final Database database;

    /**
     * @param database the open database
     */
    public SqliteAuditLog(Database database) {
        this.database = database;
    }

    /** {@inheritDoc} */
    @Override
    public void record(String eventType, String message) {
        String text = message == null ? "" : message;
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        String sql = "INSERT INTO audit_log (logged_at, event_type, message) VALUES (?, ?, ?)";
        try (PreparedStatement insert = database.connection().prepareStatement(sql)) {
            insert.setString(1, LocalDateTime.now().withNano(0).toString());
            insert.setString(2, eventType);
            insert.setString(3, text);
            insert.executeUpdate();
        } catch (SQLException e) {
            throw new RailSyncException("Could not write to the audit log: " + e.getMessage(), e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<AuditEntry> recent(int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        String sql = "SELECT id, logged_at, event_type, message FROM audit_log ORDER BY id DESC LIMIT ?";
        try (PreparedStatement query = database.connection().prepareStatement(sql)) {
            query.setInt(1, Math.max(0, limit));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    entries.add(new AuditEntry(rows.getLong("id"), LocalDateTime.parse(rows.getString("logged_at")),
                            rows.getString("event_type"), rows.getString("message")));
                }
            }
        } catch (SQLException e) {
            throw new RailSyncException("Could not read the audit log: " + e.getMessage(), e);
        }
        return entries;
    }

    /** {@inheritDoc} */
    @Override
    public int count() {
        try (Statement query = database.connection().createStatement();
             ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM audit_log")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RailSyncException("Could not count the audit log: " + e.getMessage(), e);
        }
    }
}
