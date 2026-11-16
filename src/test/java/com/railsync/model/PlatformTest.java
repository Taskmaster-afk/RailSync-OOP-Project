package com.railsync.model;

import com.railsync.exception.InvalidPlatformException;
import com.railsync.exception.RailSyncException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Platform validation, Station aggregation, and train-platform
 * compatibility.
 */
class PlatformTest {

    private static final LocalDateTime T10_00 = LocalDateTime.of(2026, 11, 16, 10, 0);

    private static Set<TrainType> types(TrainType... values) {
        Set<TrainType> set = new HashSet<>();
        for (TrainType value : values) {
            set.add(value);
        }
        return set;
    }

    // Scenario: valid platform via the short constructor. Expected: stored values, OPERATIONAL by default.
    @Test
    void validPlatformStartsOperational() {
        Platform platform = new Platform("P1", 1, 500, types(TrainType.EXPRESS));
        assertEquals(1, platform.getNumber());
        assertEquals(500, platform.getLengthMeters());
        assertTrue(platform.isOperational());
        assertTrue(platform.supports(TrainType.EXPRESS));
        assertFalse(platform.supports(TrainType.FREIGHT));
    }

    // Scenario: zero length, negative number, empty category set. Expected: InvalidPlatformException each time.
    @Test
    void invalidPlatformValuesAreRejected() {
        assertThrows(InvalidPlatformException.class, () -> new Platform("P1", 1, 0, types(TrainType.EXPRESS)));
        assertThrows(InvalidPlatformException.class, () -> new Platform("P1", -1, 300, types(TrainType.EXPRESS)));
        assertThrows(InvalidPlatformException.class, () -> new Platform("P1", 1, 300, types()));
        assertThrows(InvalidPlatformException.class, () -> new Platform(" ", 1, 300, types(TrainType.EXPRESS)));
    }

    // Scenario: caller changes its own set after creating the platform. Expected: platform is not affected.
    @Test
    void supportedTypesAreDefensivelyCopied() {
        Set<TrainType> callerSet = types(TrainType.PASSENGER);
        Platform platform = new Platform("P1", 1, 300, callerSet);
        callerSet.add(TrainType.FREIGHT);
        assertFalse(platform.supports(TrainType.FREIGHT));
        assertThrows(UnsupportedOperationException.class,
                () -> platform.getSupportedTypes().add(TrainType.FREIGHT));
    }

    // Scenario: platform fails then is repaired. Expected: status switches FAILED then OPERATIONAL.
    @Test
    void platformCanFailAndRecover() {
        Platform platform = new Platform("P1", 1, 300, types(TrainType.PASSENGER));
        platform.markFailed();
        assertEquals(PlatformStatus.FAILED, platform.getStatus());
        assertFalse(platform.isOperational());
        platform.markOperational();
        assertTrue(platform.isOperational());
    }

    // Scenario: express (270 m) on a long express platform. Expected: compatible, no reason.
    @Test
    void compatibleTrainCanUsePlatform() {
        Train express = new ExpressTrain("T1", "1", "E", T10_00, T10_00.plusMinutes(10), 3, 10);
        Platform platform = new Platform("P1", 1, 500, types(TrainType.EXPRESS, TrainType.PASSENGER));
        assertTrue(express.canUsePlatform(platform));
        assertNull(express.getIncompatibilityReason(platform));
    }

    // Scenario: freight train on a passenger-only platform. Expected: incompatible, reason names the category.
    @Test
    void wrongCategoryIsIncompatible() {
        Train freight = new FreightTrain("T1", "F1", "Coal", T10_00, T10_00.plusMinutes(30), 1, 10);
        Platform platform = new Platform("P1", 1, 800, types(TrainType.PASSENGER));
        assertFalse(freight.canUsePlatform(platform));
        assertTrue(freight.getIncompatibilityReason(platform).contains("FREIGHT"));
    }

    // Scenario: express needs 270 m but platform is 250 m. Expected: incompatible, reason mentions length.
    @Test
    void tooShortPlatformIsIncompatible() {
        Train express = new ExpressTrain("T1", "1", "E", T10_00, T10_00.plusMinutes(10), 3, 10);
        Platform platform = new Platform("P1", 1, 250, types(TrainType.EXPRESS));
        assertFalse(express.canUsePlatform(platform));
        assertTrue(express.getIncompatibilityReason(platform).contains("270"));
    }

    // Scenario: express needs exactly 270 m and platform is exactly 270 m. Expected: compatible (boundary).
    @Test
    void exactLengthIsEnough() {
        Train express = new ExpressTrain("T1", "1", "E", T10_00, T10_00.plusMinutes(10), 3, 10);
        Platform platform = new Platform("P1", 1, 270, types(TrainType.EXPRESS));
        assertTrue(express.canUsePlatform(platform));
    }

    // Scenario: otherwise perfect platform is FAILED. Expected: incompatible (disabled platform).
    @Test
    void failedPlatformIsIncompatible() {
        Train passenger = new PassengerTrain("T1", "1", "P", T10_00, T10_00.plusMinutes(5), 3, 8);
        Platform platform = new Platform("P1", 1, 400, EnumSet.allOf(TrainType.class), PlatformStatus.FAILED);
        assertFalse(passenger.canUsePlatform(platform));
    }

    // Scenario: platforms added out of order. Expected: Station keeps them sorted by number.
    @Test
    void stationKeepsPlatformsSortedByNumber() {
        Station station = new Station("Central");
        station.addPlatform(new Platform("P3", 3, 300, types(TrainType.PASSENGER)));
        station.addPlatform(new Platform("P1", 1, 300, types(TrainType.PASSENGER)));
        station.addPlatform(new Platform("P2", 2, 300, types(TrainType.PASSENGER)));
        assertEquals(1, station.getPlatforms().get(0).getNumber());
        assertEquals(2, station.getPlatforms().get(1).getNumber());
        assertEquals(3, station.getPlatforms().get(2).getNumber());
    }

    // Scenario: adding a second platform number 1. Expected: InvalidPlatformException.
    @Test
    void stationRejectsDuplicatePlatformNumber() {
        Station station = new Station("Central");
        station.addPlatform(new Platform("P1", 1, 300, types(TrainType.PASSENGER)));
        assertThrows(InvalidPlatformException.class,
                () -> station.addPlatform(new Platform("P9", 1, 300, types(TrainType.PASSENGER))));
    }

    // Scenario: remove a platform from the station. Expected: gone from station, but the object still works (aggregation).
    @Test
    void removedPlatformStillExists() {
        Station station = new Station("Central");
        Platform platform = new Platform("P1", 1, 300, types(TrainType.PASSENGER));
        station.addPlatform(platform);
        assertTrue(station.removePlatform(platform));
        assertNull(station.findPlatformById("P1"));
        assertEquals(300, platform.getLengthMeters());
    }

    // Scenario: blank station name. Expected: RailSyncException.
    @Test
    void stationNameIsRequired() {
        assertThrows(RailSyncException.class, () -> new Station(""));
    }
}
