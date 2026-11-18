package com.railsync.persistence;

import com.railsync.service.StationSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the {@code bookings} table: which train stands at which
 * platform from when. The train id is the primary key, so the database itself
 * refuses two bookings for one train, and the foreign keys refuse a booking
 * for a train or platform that does not exist.
 *
 * <p>OOP concepts: [SOLID-S] one table, one class. Design pattern: Data Access Object.</p>
 *
 * <p>Works with: SqliteStationRepository.</p>
 */
final class BookingDao {

    /**
     * Writes every booking (the table must be empty).
     *
     * @param connection the open connection
     * @param bookings   the bookings
     * @throws SQLException on a database error (for example a missing train)
     */
    void insertAll(Connection connection, List<StationSnapshot.Booking> bookings) throws SQLException {
        String sql = "INSERT INTO bookings (train_id, platform_id, start_time) VALUES (?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (StationSnapshot.Booking booking : bookings) {
                insert.setString(1, booking.getTrainId());
                insert.setString(2, booking.getPlatformId());
                insert.setString(3, booking.getStart().toString());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /**
     * Reads every booking in start order.
     *
     * @param connection the open connection
     * @return the bookings
     * @throws SQLException on a database error
     */
    List<StationSnapshot.Booking> findAll(Connection connection) throws SQLException {
        List<StationSnapshot.Booking> bookings = new ArrayList<>();
        try (Statement query = connection.createStatement();
             ResultSet rows = query.executeQuery(
                     "SELECT train_id, platform_id, start_time FROM bookings ORDER BY start_time, train_id")) {
            while (rows.next()) {
                bookings.add(new StationSnapshot.Booking(rows.getString("train_id"), rows.getString("platform_id"),
                        LocalDateTime.parse(rows.getString("start_time"))));
            }
        }
        return bookings;
    }

    /**
     * Empties the table.
     *
     * @param connection the open connection
     * @throws SQLException on a database error
     */
    void deleteAll(Connection connection) throws SQLException {
        try (Statement delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM bookings");
        }
    }
}
