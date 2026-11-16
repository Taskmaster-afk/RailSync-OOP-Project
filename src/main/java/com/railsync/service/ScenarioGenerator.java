package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.Train;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Creates random but REPRODUCIBLE scenarios for the strategy comparison:
 * the same seed always gives exactly the same platforms and trains.
 *
 * <p>How it works:</p>
 * <ol>
 *   <li>A java.util.Random is created from the seed. A Random with a fixed
 *       seed always returns the same sequence of numbers, which is what makes
 *       runs repeatable.</li>
 *   <li>The day gets longer with more trains (6 minutes per train, between
 *       2 and 16 hours, starting 06:00), so busy-ness stays realistic.</li>
 *   <li>The number of platforms is 80 % of the expected number of trains
 *       present at once (between 4 and 40). The station is deliberately a
 *       little too small, so trains queue and the strategies really compete.</li>
 *   <li>Platforms: 40 % long express/passenger, 25 % freight, the rest short
 *       passenger, with at least one of each, and every platform long enough
 *       for the biggest train of its categories. So a train is only ever
 *       unallocated because platforms are busy, never because it fits nowhere.</li>
 *   <li>Trains: about 30 % express, 50 % passenger, 20 % freight, with random
 *       sizes, priorities, arrival times and stop lengths.</li>
 * </ol>
 *
 * <p>Works with: Scenario, TrainFactory, Platform, StrategyArena.</p>
 */
public final class ScenarioGenerator {

    /** Fewest trains allowed. */
    public static final int MIN_TRAINS = 10;
    /** Most trains allowed. */
    public static final int MAX_TRAINS = 500;

    private static final int DAY_START_HOUR = 6;
    private static final int MINUTES_PER_TRAIN = 6;
    private static final int MIN_SPAN_MINUTES = 120;
    private static final int MAX_SPAN_MINUTES = 16 * 60;
    /** Platforms = this share of the expected number of trains present at once (below 1 = busy station). */
    private static final double SIZING_FACTOR = 0.8;
    /** Average platform occupation per train including the buffer, used only to size the station. */
    private static final double AVERAGE_OCCUPATION_MINUTES = 18.0;

    private ScenarioGenerator() {
        // Only static methods.
    }

    /**
     * Generates a scenario.
     *
     * @param seed       any number; the same seed gives the same scenario
     * @param trainCount 10 to 500
     * @return the scenario (nothing is allocated)
     * @throws RailSyncException if trainCount is out of range
     */
    public static Scenario generate(long seed, int trainCount) {
        if (trainCount < MIN_TRAINS || trainCount > MAX_TRAINS) {
            throw new RailSyncException("Number of trains must be between " + MIN_TRAINS + " and "
                    + MAX_TRAINS + ", but was " + trainCount + ".");
        }
        Random random = new Random(seed);

        // Step 1: length of the day in minutes, growing with the number of trains.
        int span = Math.max(MIN_SPAN_MINUTES, Math.min(MAX_SPAN_MINUTES, trainCount * MINUTES_PER_TRAIN));
        LocalDateTime dayStart = DemoScenarioLoader.SERVICE_DAY.atTime(DAY_START_HOUR, 0);

        // Step 2: slightly fewer platforms than the expected number of trains present at once.
        double expectedAtOnce = trainCount * AVERAGE_OCCUPATION_MINUTES / span;
        int platformCount = Math.max(4, Math.min(40, (int) Math.round(expectedAtOnce * SIZING_FACTOR)));
        // Step 3: split them into the three kinds, at least one of each.
        int longCount = Math.max(1, (int) Math.round(platformCount * 0.40));
        int freightCount = Math.max(1, (int) Math.round(platformCount * 0.25));
        int shortCount = Math.max(1, platformCount - longCount - freightCount);
        List<Platform> platforms = new ArrayList<>();
        for (int i = 0; i < longCount + shortCount + freightCount; i++) {
            int number = i + 1;
            if (i < longCount) {
                // Long enough for the longest express (20 coaches = 520 m).
                platforms.add(new Platform("P" + number, number, between(random, 520, 600),
                        EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
            } else if (i < longCount + shortCount) {
                // Long enough for the longest passenger train (14 coaches = 280 m).
                platforms.add(new Platform("P" + number, number, between(random, 280, 320), EnumSet.of(TrainType.PASSENGER)));
            } else {
                // Long enough for the longest freight train (45 wagons + 2 locos = 715 m).
                platforms.add(new Platform("P" + number, number, between(random, 720, 800), EnumSet.of(TrainType.FREIGHT)));
            }
        }

        // Step 4: the trains, each with a random kind, size, priority and time.
        List<Train> trains = new ArrayList<>();
        for (int i = 1; i <= trainCount; i++) {
            trains.add(randomTrain(random, i, dayStart, span));
        }
        return new Scenario(platforms, trains);
    }

    /** One train of a random kind, with a size and stop length typical for that kind. */
    private static Train randomTrain(Random random, int index, LocalDateTime dayStart, int span) {
        double kind = random.nextDouble();
        TrainType type;
        int carriages;
        int dwell;
        String prefix;
        if (kind < 0.30) {
            type = TrainType.EXPRESS;
            carriages = between(random, 12, 20);
            dwell = between(random, 5, 15);
            prefix = "EX";
        } else if (kind < 0.80) {
            type = TrainType.PASSENGER;
            carriages = between(random, 8, 14);
            dwell = between(random, 3, 10);
            prefix = "PS";
        } else {
            type = TrainType.FREIGHT;
            carriages = between(random, 20, 45);
            dwell = between(random, 20, 45);
            prefix = "FR";
        }
        LocalDateTime arrival = dayStart.plusMinutes(random.nextInt(span));
        int basePriority = between(random, Train.MIN_BASE_PRIORITY, Train.MAX_BASE_PRIORITY);
        String number = prefix + "-" + (1000 + index);
        return TrainFactory.create(type, "G" + index, number, type + " " + index,
                arrival, arrival.plusMinutes(dwell), basePriority, carriages);
    }

    /** @return a random whole number from low to high, both included */
    private static int between(Random random, int low, int high) {
        return low + random.nextInt(high - low + 1);
    }
}
