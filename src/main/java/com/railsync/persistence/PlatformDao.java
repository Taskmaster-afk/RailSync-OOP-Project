package com.railsync.persistence;

import com.railsync.model.Platform;
import com.railsync.model.PlatformStatus;
import com.railsync.model.TrainType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the {@code platforms} table.
 *
 * <p>Why it exists: one small class per table keeps every SQL statement in one
 * obvious place. All values go through PreparedStatement parameters, never by
 * gluing text into SQL, so no input can change the query (no SQL injection).</p>
 *
 * <p>OOP concepts: [SOLID-S] one table, one class. Design pattern: Data Access Object.</p>
 *
 * <p>Works with: SqliteStationRepository.</p>
 */
final class PlatformDao {

    /**
     * Writes every platform (the table must be empty).
     *
     * @param connection the open connection
     * @param platforms  the platforms
     * @throws SQLException on a database error
     */
    void insertAll(Connection connection, List<Platform> platforms) throws SQLException {
        String sql = "INSERT INTO platforms (id, number, length_m, train_types, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Platform platform : platforms) {
                insert.setString(1, platform.getId());
                insert.setInt(2, platform.getNumber());
                insert.setInt(3, platform.getLengthMeters());
                insert.setString(4, typesToText(platform.getSupportedTypes()));
                insert.setString(5, platform.getStatus().name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /**
     * Reads every platform, in number order. Each row passes through the
     * Platform constructor, so invalid saved values are rejected.
     *
     * @param connection the open connection
     * @return the platforms
     * @throws SQLException on a database error
     */
    List<Platform> findAll(Connection connection) throws SQLException {
        List<Platform> platforms = new ArrayList<>();
        try (Statement query = connection.createStatement();
             ResultSet rows = query.executeQuery(
                     "SELECT id, number, length_m, train_types, status FROM platforms ORDER BY number")) {
            while (rows.next()) {
                platforms.add(new Platform(rows.getString("id"), rows.getInt("number"), rows.getInt("length_m"),
                        textToTypes(rows.getString("train_types")), PlatformStatus.valueOf(rows.getString("status"))));
            }
        }
        return platforms;
    }

    /**
     * Empties the table.
     *
     * @param connection the open connection
     * @throws SQLException on a database error
     */
    void deleteAll(Connection connection) throws SQLException {
        try (Statement delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM platforms");
        }
    }

    /** @return e.g. "EXPRESS,PASSENGER" (in enum order, so the text is stable) */
    static String typesToText(Set<TrainType> types) {
        StringBuilder text = new StringBuilder();
        for (TrainType type : TrainType.values()) {
            if (types.contains(type)) {
                text.append(text.length() == 0 ? "" : ",").append(type.name());
            }
        }
        return text.toString();
    }

    /** @return the set written by typesToText */
    static Set<TrainType> textToTypes(String text) {
        Set<TrainType> types = EnumSet.noneOf(TrainType.class);
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                types.add(TrainType.valueOf(part.trim()));
            }
        }
        return types;
    }
}
