package com.railsync.service;

import com.railsync.exception.InvalidPlatformException;
import com.railsync.exception.InvalidTrainException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.FreightTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests TrainManager, PlatformManager and TrainFactory.
 */
class ManagersTest {

    private static final LocalDateTime T10 = LocalDateTime.of(2026, 11, 16, 10, 0);

    private Schedule schedule;
    private Station station;
    private TrainManager trains;
    private PlatformManager platforms;

    @BeforeEach
    void setUp() {
        schedule = new Schedule();
        station = new Station("S");
        trains = new TrainManager(schedule);
        platforms = new PlatformManager(station, schedule);
    }

    private Train local(String id, String number) {
        return new PassengerTrain(id, number, "Local", T10, T10.plusMinutes(10), 3, 8);
    }

    // Scenario: add two trains with the same number. Expected: InvalidTrainException.
    @Test
    void duplicateTrainNumberRejected() {
        trains.addTrain(local("T1", "100"));
        assertThrows(InvalidTrainException.class, () -> trains.addTrain(local("T2", "100")));
    }

    // Scenario: search " 100 " with spaces. Expected: train 100 found; unknown number gives null.
    @Test
    void searchByNumberTrimsAndReturnsNullWhenMissing() {
        Train train = local("T1", "100");
        trains.addTrain(train);
        assertSame(train, trains.findByNumber(" 100 "));
        assertNull(trains.findByNumber("999"));
    }

    // Scenario: T1 and T2 already exist. Expected: next id is T3.
    @Test
    void nextIdSkipsUsedIds() {
        trains.addTrain(local("T1", "1"));
        trains.addTrain(local("T2", "2"));
        assertEquals("T3", trains.nextTrainId());
    }

    // Scenario: remove a train that holds a platform, then after release. Expected: refused first, allowed after.
    @Test
    void allocatedTrainCannotBeRemoved() {
        Platform p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.PASSENGER));
        Train train = local("T1", "100");
        trains.addTrain(train);
        schedule.reserve(train, p1, T10);
        assertThrows(InvalidTrainException.class, () -> trains.removeTrain(train));
        schedule.release(train);
        trains.removeTrain(train);
        assertNull(trains.findByNumber("100"));
    }

    // Scenario: one of two trains is booked. Expected: only the other one is listed as without platform.
    @Test
    void listsTrainsWithoutPlatform() {
        Platform p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.PASSENGER));
        Train booked = local("T1", "1");
        Train free = local("T2", "2");
        trains.addTrain(booked);
        trains.addTrain(free);
        schedule.reserve(booked, p1, T10);
        assertEquals(1, trains.getTrainsWithoutPlatform().size());
        assertSame(free, trains.getTrainsWithoutPlatform().get(0));
    }

    // Scenario: remove a platform with a booking. Expected: InvalidPlatformException; empty platform removable.
    @Test
    void platformWithBookingCannotBeRemoved() {
        Platform p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.PASSENGER));
        platforms.addPlatform(p1);
        Train train = local("T1", "1");
        schedule.reserve(train, p1, T10);
        assertThrows(InvalidPlatformException.class, () -> platforms.removePlatform(p1));
        schedule.release(train);
        platforms.removePlatform(p1);
        assertEquals(0, station.getPlatformCount());
    }

    // Scenario: shorten a platform below a booked 320 m express. Expected: refused, length unchanged.
    @Test
    void updateRefusedIfBookedTrainNoLongerFits() {
        Platform p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS));
        platforms.addPlatform(p1);
        Train express = new ExpressTrain("T1", "1", "E", T10, T10.plusMinutes(10), 3, 12);
        schedule.reserve(express, p1, T10);
        assertThrows(InvalidPlatformException.class,
                () -> platforms.updatePlatform(p1, 300, EnumSet.of(TrainType.EXPRESS)));
        assertThrows(InvalidPlatformException.class,
                () -> platforms.updatePlatform(p1, 500, EnumSet.of(TrainType.PASSENGER)));
        assertEquals(500, p1.getLengthMeters());
    }

    // Scenario: safe update of an empty platform. Expected: new length and categories applied.
    @Test
    void safeUpdateIsApplied() {
        Platform p1 = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS));
        platforms.addPlatform(p1);
        platforms.updatePlatform(p1, 650, EnumSet.of(TrainType.FREIGHT));
        assertEquals(650, p1.getLengthMeters());
        assertTrue(p1.supports(TrainType.FREIGHT));
    }

    // Scenario: platforms 1 and 4 exist. Expected: next number 5, next id P2.
    @Test
    void suggestsNextPlatformNumberAndId() {
        platforms.addPlatform(new Platform("P1", 1, 300, EnumSet.of(TrainType.PASSENGER)));
        platforms.addPlatform(new Platform("P3", 4, 300, EnumSet.of(TrainType.PASSENGER)));
        assertEquals(5, platforms.nextPlatformNumber());
        assertEquals("P2", platforms.nextPlatformId());
    }

    // Scenario: factory asked for each type. Expected: the matching subclass; null type rejected.
    @Test
    void factoryCreatesMatchingSubclass() {
        assertTrue(TrainFactory.create(TrainType.EXPRESS, "1", "1", "E", T10, T10.plusMinutes(10), 3, 10) instanceof ExpressTrain);
        assertTrue(TrainFactory.create(TrainType.PASSENGER, "2", "2", "P", T10, T10.plusMinutes(10), 3, 10) instanceof PassengerTrain);
        assertTrue(TrainFactory.create(TrainType.FREIGHT, "3", "3", "F", T10, T10.plusMinutes(30), 3, 10) instanceof FreightTrain);
        assertThrows(InvalidTrainException.class,
                () -> TrainFactory.create(null, "4", "4", "X", T10, T10.plusMinutes(10), 3, 10));
    }
}
