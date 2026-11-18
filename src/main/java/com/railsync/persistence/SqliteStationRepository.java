package com.railsync.persistence;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.Train;
import com.railsync.service.StationSnapshot;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores the whole session in SQLite.
 *
 * <p>Why it exists: it is the SQLite implementation of StationRepository. A
 * save replaces every table inside ONE transaction, so a crash half-way can
 * never leave a mix of old and new data: the previous save stays intact.</p>
 *
 * <p>Loading only rebuilds objects; the service then re-checks every booking
 * through the normal rules (SchedulingService.restore).</p>
 *
 * <p>OOP concepts: [POLYMORPHISM] used through the StationRepository interface;
 * [COMPOSITION] owns one DAO per table; [SOLID-S] it coordinates, the DAOs hold the SQL.</p>
 *
 * <p>Works with: Database, PlatformDao, TrainDao, BookingDao, SettingsDao.</p>
 */
public final class SqliteStationRepository implements StationRepository {

    static final String KEY_SAVED_AT = "saved_at";
    static final String KEY_STRATEGY = "strategy";
    static final String KEY_AGING = "aging_minutes_per_point";
    static final String KEY_BUFFER = "clearance_buffer_minutes";
    static final String KEY_MAX_WAIT = "max_wait_minutes";
    static final String KEY_PREEMPTION = "preemption_enabled";
    static final String KEY_CONFLICTS = "conflicts_detected";

    private final Database database;
    private final PlatformDao platformDao = new PlatformDao();
    private final TrainDao trainDao = new TrainDao();
    private final BookingDao bookingDao = new BookingDao();
    private final SettingsDao settingsDao = new SettingsDao();

    /**
     * @param database the open database
     */
    public SqliteStationRepository(Database database) {
        this.database = database;
    }

    /** {@inheritDoc} */
    @Override
    public void save(StationSnapshot snapshot) {
        // Step 1: everything below runs in one transaction (all or nothing).
        database.inTransaction(connection -> {
            // Step 2: clear the old session. Bookings first, because they point at trains and platforms.
            bookingDao.deleteAll(connection);
            trainDao.deleteAll(connection);
            platformDao.deleteAll(connection);
            // Step 3: write the new one.
            platformDao.insertAll(connection, snapshot.getPlatforms());
            trainDao.insertAll(connection, snapshot.getTrains(), snapshot.getUnallocatedTrainIds());
            bookingDao.insertAll(connection, snapshot.getBookings());
            settingsDao.saveAll(connection, settingsToMap(snapshot.getSettings()));
        });
    }

    /** {@inheritDoc} */
    @Override
    public Optional<StationSnapshot> load() {
        try {
            Map<String, String> settings = settingsDao.findAll(database.connection());
            if (!settings.containsKey(KEY_SAVED_AT)) {
                return Optional.empty(); // a new, empty database
            }
            List<Platform> platforms = platformDao.findAll(database.connection());
            Set<String> givenUp = new HashSet<>();
            List<Train> trains = trainDao.findAll(database.connection(), givenUp);
            List<StationSnapshot.Booking> bookings = bookingDao.findAll(database.connection());
            return Optional.of(new StationSnapshot(platforms, trains, bookings, givenUp, mapToSettings(settings)));
        } catch (SQLException e) {
            throw new RailSyncException("Could not read the saved session: " + e.getMessage(), e);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // A damaged value (unknown type, bad date): refuse the data rather than guess.
            throw new RailSyncException("The saved session contains an invalid value: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> settingsToMap(StationSnapshot.Settings settings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_SAVED_AT, LocalDateTime.now().withNano(0).toString());
        values.put(KEY_STRATEGY, settings.getStrategyName());
        values.put(KEY_AGING, String.valueOf(settings.getAgingMinutesPerPoint()));
        values.put(KEY_BUFFER, String.valueOf(settings.getClearanceBufferMinutes()));
        values.put(KEY_MAX_WAIT, String.valueOf(settings.getMaxWaitMinutes()));
        values.put(KEY_PREEMPTION, String.valueOf(settings.isPreemptionEnabled()));
        values.put(KEY_CONFLICTS, String.valueOf(settings.getConflictsDetected()));
        return values;
    }

    private static StationSnapshot.Settings mapToSettings(Map<String, String> values) {
        return new StationSnapshot.Settings(
                values.getOrDefault(KEY_STRATEGY, ""),
                Integer.parseInt(values.getOrDefault(KEY_AGING, "4")),
                Integer.parseInt(values.getOrDefault(KEY_BUFFER, "5")),
                Integer.parseInt(values.getOrDefault(KEY_MAX_WAIT, "20")),
                Boolean.parseBoolean(values.getOrDefault(KEY_PREEMPTION, "false")),
                Integer.parseInt(values.getOrDefault(KEY_CONFLICTS, "0")));
    }
}
