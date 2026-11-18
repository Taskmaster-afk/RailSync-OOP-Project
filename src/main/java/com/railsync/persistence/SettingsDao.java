package com.railsync.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes the {@code settings} table, a simple key/value store
 * (strategy, clearance buffer, maximum wait, ...). New settings need no
 * schema change: they are just new keys.
 *
 * <p>OOP concepts: [SOLID-S] one table, one class. Design pattern: Data Access Object.</p>
 *
 * <p>Works with: SqliteStationRepository.</p>
 */
final class SettingsDao {

    /**
     * Writes every setting, replacing old values with the same key.
     *
     * @param connection the open connection
     * @param values     key to value
     * @throws SQLException on a database error
     */
    void saveAll(Connection connection, Map<String, String> values) throws SQLException {
        String sql = "INSERT INTO settings (key, value) VALUES (?, ?)"
                + " ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement upsert = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                upsert.setString(1, entry.getKey());
                upsert.setString(2, entry.getValue());
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }

    /**
     * Reads every setting.
     *
     * @param connection the open connection
     * @return key to value (empty if nothing was saved)
     * @throws SQLException on a database error
     */
    Map<String, String> findAll(Connection connection) throws SQLException {
        Map<String, String> values = new HashMap<>();
        try (Statement query = connection.createStatement();
             ResultSet rows = query.executeQuery("SELECT key, value FROM settings")) {
            while (rows.next()) {
                values.put(rows.getString("key"), rows.getString("value"));
            }
        }
        return values;
    }
}
