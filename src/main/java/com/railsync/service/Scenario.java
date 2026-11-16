package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.TrainFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A self-contained set of platforms and trains (with no bookings): a "what
 * if" copy that can be allocated without touching the live schedule.
 *
 * <p>Why it exists: the strategy comparison arena must run the SAME trains
 * through every strategy, and must never change the live data. Each run
 * gets its own fresh copy, made by {@link #copy()} or {@link #fromLive}.</p>
 *
 * <p>OOP concepts: Encapsulation (read-only lists), copying objects field by
 * field (a deep copy: new Platform and Train objects, so changing a copy
 * never changes the original).</p>
 *
 * <p>Works with: ScenarioGenerator (creates scenarios), StrategyArena (copies
 * one per strategy), SchedulingService (can load one as the live scenario).</p>
 */
public final class Scenario {

    private final List<Platform> platforms;
    private final List<Train> trains;

    /**
     * Creates a scenario from lists (the lists are copied, the objects are not).
     *
     * @param platforms the platforms
     * @param trains    the trains
     */
    public Scenario(List<Platform> platforms, List<Train> trains) {
        this.platforms = new ArrayList<>(platforms);
        this.trains = new ArrayList<>(trains);
    }

    /**
     * Takes a deep copy of the live station and trains. Bookings are NOT
     * copied; delays and failed platforms ARE, because they are part of the
     * situation being compared.
     *
     * @param service the live service
     * @return an independent scenario
     */
    public static Scenario fromLive(SchedulingService service) {
        return new Scenario(service.getStation().getPlatforms(), service.getTrainManager().getAllTrains()).copy();
    }

    /**
     * Makes a deep copy: new Platform and Train objects with the same values.
     *
     * @return an independent scenario
     */
    public Scenario copy() {
        List<Platform> platformCopies = new ArrayList<>();
        for (Platform platform : platforms) {
            platformCopies.add(new Platform(platform.getId(), platform.getNumber(), platform.getLengthMeters(),
                    platform.getSupportedTypes(), platform.getStatus()));
        }
        List<Train> trainCopies = new ArrayList<>();
        for (Train train : trains) {
            // The factory rebuilds the right subclass from its type, so behaviour is copied too.
            Train copy = TrainFactory.create(train.getType(), train.getId(), train.getNumber(), train.getName(),
                    train.getScheduledArrival(), train.getScheduledDeparture(),
                    train.getBasePriority(), train.getCarriageCount());
            if (train.isDelayed()) {
                copy.applyDelay(train.getDelayMinutes());
            }
            trainCopies.add(copy);
        }
        return new Scenario(platformCopies, trainCopies);
    }

    /**
     * Builds a station holding this scenario's platforms.
     *
     * @param name station name
     * @return a new station
     */
    public Station buildStation(String name) {
        Station station = new Station(name);
        for (Platform platform : platforms) {
            station.addPlatform(platform);
        }
        return station;
    }

    /** @return the platforms, read-only */
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    /** @return the trains, read-only */
    public List<Train> getTrains() {
        return Collections.unmodifiableList(trains);
    }
}
