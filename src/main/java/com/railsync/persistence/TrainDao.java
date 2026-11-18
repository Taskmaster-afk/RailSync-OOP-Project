package com.railsync.persistence;

import com.railsync.model.Train;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the {@code trains} table.
 *
 * <p>Why it exists: keeps the train SQL in one place. Only the timetable and
 * the delay are stored; the expected times are worked out again by Train.
 * Loading uses TrainFactory, so each row becomes the right subclass
 * (Express, Passenger or Freight) with its own behaviour.</p>
 *
 * <p>OOP concepts: [SOLID-S] one table, one class; Factory pattern on load.
 * Design pattern: Data Access Object.</p>
 *
 * <p>Works with: SqliteStationRepository, TrainFactory.</p>
 */
final class TrainDao {

    /**
     * Writes every train (the table must be empty).
     *
     * @param connection the open connection
     * @param trains     the trains
     * @param givenUpIds ids of trains with status UNALLOCATED
     * @throws SQLException on a database error
     */
    void insertAll(Connection connection, List<Train> trains, Set<String> givenUpIds) throws SQLException {
        String sql = "INSERT INTO trains (id, number, name, type, arrival, departure, base_priority, carriages,"
                + " delay_min, given_up) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Train train : trains) {
                insert.setString(1, train.getId());
                insert.setString(2, train.getNumber());
                insert.setString(3, train.getName());
                insert.setString(4, train.getType().name());
                insert.setString(5, train.getScheduledArrival().toString());
                insert.setString(6, train.getScheduledDeparture().toString());
                insert.setInt(7, train.getBasePriority());
                insert.setInt(8, train.getCarriageCount());
                insert.setInt(9, train.getDelayMinutes());
                insert.setInt(10, givenUpIds.contains(train.getId()) ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /**
     * Reads every train in timetable order, rebuilding the right subclass.
     *
     * @param connection the open connection
     * @param givenUpIds filled with the ids of trains that were given up
     * @return the trains
     * @throws SQLException on a database error
     */
    List<Train> findAll(Connection connection, Set<String> givenUpIds) throws SQLException {
        List<Train> trains = new ArrayList<>();
        String sql = "SELECT id, number, name, type, arrival, departure, base_priority, carriages, delay_min, given_up"
                + " FROM trains ORDER BY arrival, number";
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(sql)) {
            while (rows.next()) {
                // [FACTORY] the saved type decides which subclass is created.
                Train train = TrainFactory.create(TrainType.valueOf(rows.getString("type")), rows.getString("id"),
                        rows.getString("number"), rows.getString("name"),
                        LocalDateTime.parse(rows.getString("arrival")), LocalDateTime.parse(rows.getString("departure")),
                        rows.getInt("base_priority"), rows.getInt("carriages"));
                int delay = rows.getInt("delay_min");
                if (delay > 0) {
                    train.applyDelay(delay);
                }
                if (rows.getInt("given_up") == 1) {
                    givenUpIds.add(train.getId());
                }
                trains.add(train);
            }
        }
        return trains;
    }

    /**
     * Empties the table (bookings go with it, through ON DELETE CASCADE).
     *
     * @param connection the open connection
     * @throws SQLException on a database error
     */
    void deleteAll(Connection connection) throws SQLException {
        try (Statement delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM trains");
        }
    }
}
