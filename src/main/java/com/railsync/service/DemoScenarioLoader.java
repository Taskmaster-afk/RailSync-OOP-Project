package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Loads the fixed demo scenario: 6 platforms and 12 trains, designed by hand
 * so that every feature can be shown without relying on luck.
 *
 * <p>Guaranteed (and checked by DemoScenarioTest), with the default
 * 5-minute buffer and 20-minute maximum wait:</p>
 * <ul>
 *   <li><b>Conflicts:</b> at 08:05-08:10 three trains that need PF-1 or PF-2
 *       (L-101, L-102, 12002) arrive while both are busy and must queue.</li>
 *   <li><b>FCFS vs Priority differ:</b> when PF-2 frees at 08:15, FCFS serves
 *       L-101 (arrived first) and 12002 waits 20 min; Priority serves 12002
 *       at once and L-102 ends up unallocated.</li>
 *   <li><b>A train that can never be allocated:</b> F-999 (60 wagons = 940 m)
 *       is longer than every platform.</li>
 *   <li><b>Delay with ripple:</b> after FCFS allocation, delaying 12951 by
 *       5 min keeps it on PF-1 (the only platform long enough) and pushes
 *       L-102 and 12909 later: a cascade of 2.</li>
 *   <li><b>Breakdown with reassignment:</b> after that, breaking PF-2 moves
 *       S-201 to PF-4 at the same time, while the long trains on PF-2 cannot
 *       move and are reported with reasons.</li>
 * </ul>
 *
 * <p>Platform summary: PF-1 520 m E/P, PF-2 450 m E/P, PF-3 and PF-4 260 m P,
 * PF-5 750 m F, PF-6 600 m F.</p>
 *
 * <p>Works with: SchedulingService (all data is added through it, so the
 * normal validation and events apply), TrainFactory, Platform.</p>
 */
public final class DemoScenarioLoader {

    /** The service day used by the demo (and by the GUI time fields). */
    public static final LocalDate SERVICE_DAY = LocalDate.of(2026, 11, 16);
    /** Train to delay for the ripple demonstration. */
    public static final String RIPPLE_TRAIN_NUMBER = "12951";
    /** Delay that causes the ripple. */
    public static final int RIPPLE_DELAY_MINUTES = 5;
    /** Platform to break down for the breakdown demonstration. */
    public static final String BREAKDOWN_PLATFORM_ID = "P2";

    private DemoScenarioLoader() {
        // Only static methods.
    }

    /**
     * Replaces everything in the service with the demo scenario. Nothing is
     * allocated yet; the user (or test) chooses a strategy and allocates.
     *
     * @param service the service to fill
     */
    public static void load(SchedulingService service) {
        // Step 1: start from an empty scenario with the default settings.
        service.clearEverything();
        service.setClearanceBufferMinutes(5);
        service.setMaxWaitMinutes(PlatformAllocator.DEFAULT_MAX_WAIT_MINUTES);
        service.setPreemptionEnabled(false);

        // Step 2: six platforms with different lengths and categories.
        service.addPlatform(new Platform("P1", 1, 520, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        service.addPlatform(new Platform("P2", 2, 450, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        service.addPlatform(new Platform("P3", 3, 260, EnumSet.of(TrainType.PASSENGER)));
        service.addPlatform(new Platform("P4", 4, 260, EnumSet.of(TrainType.PASSENGER)));
        service.addPlatform(new Platform("P5", 5, 750, EnumSet.of(TrainType.FREIGHT)));
        service.addPlatform(new Platform("P6", 6, 600, EnumSet.of(TrainType.FREIGHT)));

        // Step 3: twelve trains. Comments give the length needed and where it fits.
        // 20 coaches x 25 + 20 = 520 m: PF-1 only.
        add(service, TrainType.EXPRESS, "T1", "12951", "Rajdhani Express", 8, 0, 8, 15, 4, 20);
        // 12 coaches x 20 = 240 m: PF-1 to PF-4.
        add(service, TrainType.PASSENGER, "T2", "S-201", "Short Local A", 8, 2, 8, 10, 3, 12);
        add(service, TrainType.PASSENGER, "T3", "S-202", "Short Local B", 8, 4, 8, 12, 2, 12);
        // 14 coaches x 20 = 280 m: PF-1 or PF-2.
        add(service, TrainType.PASSENGER, "T4", "L-101", "Long Local 1", 8, 5, 8, 15, 3, 14);
        add(service, TrainType.PASSENGER, "T5", "L-102", "Long Local 2", 8, 8, 8, 18, 2, 14);
        // 16 coaches x 25 + 20 = 420 m: PF-1 or PF-2.
        add(service, TrainType.EXPRESS, "T6", "12002", "Shatabdi Express", 8, 10, 8, 20, 5, 16);
        // Freight: 40 wagons = 640 m (PF-5 only); 30 wagons = 490 m (PF-5 or PF-6).
        add(service, TrainType.FREIGHT, "T7", "F-401", "Coal Rake", 8, 0, 8, 40, 2, 40);
        add(service, TrainType.FREIGHT, "T8", "F-402", "Cement Rake", 8, 10, 8, 40, 1, 30);
        // 60 wagons = 940 m: longer than every platform, so it can never be allocated.
        add(service, TrainType.FREIGHT, "T9", "F-999", "Iron Ore Long Rake", 8, 20, 8, 50, 1, 60);
        add(service, TrainType.EXPRESS, "T10", "12909", "Garib Rath Express", 8, 30, 8, 40, 3, 16);
        add(service, TrainType.PASSENGER, "T11", "L-103", "Long Local 3", 8, 45, 8, 55, 3, 14);
        add(service, TrainType.EXPRESS, "T12", "22691", "Duronto Express", 9, 0, 9, 10, 4, 16);
    }

    /** Builds a train with the factory and adds it through the service. */
    private static void add(SchedulingService service, TrainType type, String id, String number, String name,
                            int arriveHour, int arriveMinute, int departHour, int departMinute,
                            int basePriority, int carriages) {
        LocalDateTime arrival = SERVICE_DAY.atTime(arriveHour, arriveMinute);
        LocalDateTime departure = SERVICE_DAY.atTime(departHour, departMinute);
        service.addTrain(TrainFactory.create(type, id, number, name, arrival, departure, basePriority, carriages));
    }
}
