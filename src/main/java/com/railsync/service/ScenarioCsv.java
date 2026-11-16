package com.railsync.service;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformStatus;
import com.railsync.model.Train;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saves a scenario (platforms and trains) to a CSV file and loads it back.
 *
 * <p>File format: one record per line; the first column says what it is.
 * Lines starting with # are comments. Bookings are NOT saved: after loading,
 * you allocate again with any strategy.</p>
 * <pre>
 * # RailSync scenario v1
 * PLATFORM,P1,1,520,OPERATIONAL,EXPRESS;PASSENGER
 * TRAIN,T1,EXPRESS,12951,Rajdhani Express,2026-11-16T08:00,2026-11-16T08:15,4,20,0
 * </pre>
 * <p>TRAIN columns: id, type, number, name, arrival, departure, base
 * priority, carriages, delay minutes. Times are ISO date-times, so a file is
 * never ambiguous about the day. A field containing a comma or quote is
 * written inside double quotes, with any quote doubled ("" ) - standard CSV.</p>
 *
 * <p>Safety: a file is read completely and every line is checked BEFORE
 * anything is loaded. If any line is wrong, the error names the line number
 * and the live scenario is not touched.</p>
 *
 * <p>OOP concepts: SOLID-S (file format only), Encapsulation. All values are
 * validated by the normal Platform and Train constructors, so a CSV file
 * cannot create an object that the GUI would refuse.</p>
 *
 * <p>Works with: Scenario, TrainFactory, Platform, SchedulingService.loadScenario().</p>
 */
public final class ScenarioCsv {

    private static final String HEADER = "# RailSync scenario v1";
    private static final String PLATFORM = "PLATFORM";
    private static final String TRAIN = "TRAIN";

    private ScenarioCsv() {
        // Only static methods.
    }

    /**
     * Writes a scenario to a file (overwriting it).
     *
     * @param scenario the platforms and trains to save
     * @param file     where to write
     * @throws RailSyncException if the file cannot be written
     */
    public static void save(Scenario scenario, Path file) {
        try {
            Files.write(file, toLines(scenario), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RailSyncException("Could not save the file: " + e.getMessage());
        }
    }

    /**
     * Reads a scenario from a file.
     *
     * @param file the file to read
     * @return the scenario
     * @throws RailSyncException if the file cannot be read or a line is invalid (the message names the line)
     */
    public static Scenario load(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RailSyncException("Could not read the file: " + e.getMessage());
        }
        return fromLines(lines);
    }

    /**
     * Turns a scenario into CSV lines.
     *
     * @param scenario the scenario
     * @return the lines, header first
     */
    public static List<String> toLines(Scenario scenario) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("# PLATFORM,id,number,lengthMetres,status,types");
        for (Platform platform : scenario.getPlatforms()) {
            StringBuilder types = new StringBuilder();
            // Enum order, so the file does not depend on the order inside a HashSet.
            for (TrainType type : TrainType.values()) {
                if (platform.supports(type)) {
                    types.append(types.length() > 0 ? ";" : "").append(type);
                }
            }
            lines.add(join(PLATFORM, platform.getId(), String.valueOf(platform.getNumber()),
                    String.valueOf(platform.getLengthMeters()), platform.getStatus().toString(), types.toString()));
        }
        lines.add("# TRAIN,id,type,number,name,arrival,departure,basePriority,carriages,delayMinutes");
        for (Train train : scenario.getTrains()) {
            lines.add(join(TRAIN, train.getId(), train.getType().toString(), train.getNumber(), train.getName(),
                    train.getScheduledArrival().toString(), train.getScheduledDeparture().toString(),
                    String.valueOf(train.getBasePriority()), String.valueOf(train.getCarriageCount()),
                    String.valueOf(train.getDelayMinutes())));
        }
        return lines;
    }

    /**
     * Builds a scenario from CSV lines. Every line is checked; the first bad
     * line stops the whole load with a message naming it.
     *
     * @param lines the file's lines
     * @return the scenario
     * @throws RailSyncException naming the first invalid line
     */
    public static Scenario fromLines(List<String> lines) {
        List<Platform> platforms = new ArrayList<>();
        List<Train> trains = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            // Step 1: skip blank lines and comments.
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            try {
                // Step 2: split into fields and build the object; the constructors validate the values.
                List<String> fields = split(line);
                if (fields.get(0).equals(PLATFORM)) {
                    platforms.add(parsePlatform(fields));
                } else if (fields.get(0).equals(TRAIN)) {
                    trains.add(parseTrain(fields));
                } else {
                    throw new RailSyncException("unknown record type \"" + fields.get(0) + "\"");
                }
            } catch (RailSyncException | IllegalArgumentException | DateTimeParseException e) {
                // Step 3: say exactly where the problem is (IllegalArgumentException covers bad numbers and enum names).
                throw new RailSyncException("Line " + lineNumber + ": " + e.getMessage());
            }
        }
        // Step 4: ids and numbers must be unique, checked now so a load never stops half way.
        requireUnique(platforms, trains);
        return new Scenario(platforms, trains);
    }

    /** Refuses duplicate platform ids/numbers and train ids/numbers (HashSet.add returns false for a repeat). */
    private static void requireUnique(List<Platform> platforms, List<Train> trains) {
        Set<String> seen = new HashSet<>();
        for (Platform platform : platforms) {
            if (!seen.add("P-id " + platform.getId()) || !seen.add("P-no " + platform.getNumber())) {
                throw new RailSyncException("Platform " + platform.getId() + " / number " + platform.getNumber()
                        + " appears more than once in the file.");
            }
        }
        for (Train train : trains) {
            if (!seen.add("T-id " + train.getId()) || !seen.add("T-no " + train.getNumber())) {
                throw new RailSyncException("Train " + train.getId() + " / number " + train.getNumber()
                        + " appears more than once in the file.");
            }
        }
    }

    private static Platform parsePlatform(List<String> f) {
        requireFieldCount(f, 6);
        Set<TrainType> types = EnumSet.noneOf(TrainType.class);
        for (String type : f.get(5).split(";")) {
            if (!type.isBlank()) {
                types.add(TrainType.valueOf(type.trim()));
            }
        }
        return new Platform(f.get(1), Integer.parseInt(f.get(2)), Integer.parseInt(f.get(3)),
                types, PlatformStatus.valueOf(f.get(4)));
    }

    private static Train parseTrain(List<String> f) {
        requireFieldCount(f, 10);
        Train train = TrainFactory.create(TrainType.valueOf(f.get(2)), f.get(1), f.get(3), f.get(4),
                LocalDateTime.parse(f.get(5)), LocalDateTime.parse(f.get(6)),
                Integer.parseInt(f.get(7)), Integer.parseInt(f.get(8)));
        int delay = Integer.parseInt(f.get(9));
        if (delay > 0) {
            train.applyDelay(delay);
        }
        return train;
    }

    private static void requireFieldCount(List<String> fields, int expected) {
        if (fields.size() != expected) {
            throw new RailSyncException("expected " + expected + " fields but found " + fields.size());
        }
    }

    /** Joins fields with commas, quoting any field that contains a comma or a quote. */
    private static String join(String... fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            String field = fields[i];
            if (field.contains(",") || field.contains("\"")) {
                line.append('"').append(field.replace("\"", "\"\"")).append('"');
            } else {
                line.append(field);
            }
        }
        return line.toString();
    }

    /**
     * Splits one CSV line into fields, honouring double quotes.
     * Example: TRAIN,"Rajdhani, Mumbai",x  ->  [TRAIN] [Rajdhani, Mumbai] [x].
     */
    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (insideQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // A doubled quote inside quotes stands for one quote character.
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    insideQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                insideQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
