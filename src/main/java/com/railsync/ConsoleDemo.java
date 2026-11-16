package com.railsync;

import com.railsync.exception.RailSyncException;
import com.railsync.model.ExpressTrain;
import com.railsync.model.FreightTrain;
import com.railsync.model.PassengerTrain;
import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.model.ReservationChange;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.observer.ScheduleEventType;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.AllocationResult;
import com.railsync.service.BreakdownReport;
import com.railsync.service.DelayReport;
import com.railsync.service.SchedulingService;
import com.railsync.service.MetricsCalculator;
import com.railsync.service.ReportGenerator;
import com.railsync.service.ScheduleMetrics;
import com.railsync.service.PlatformAllocator;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.strategy.PriorityAgingStrategy;
import com.railsync.strategy.PriorityStrategy;
import com.railsync.strategy.ShortestDwellFirstStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A text-only showcase that prints what each finished stage can do.
 *
 * <p>Why it exists: it lets you SEE progress before the GUI exists, and it is
 * a quick way to demonstrate the model in a viva without opening Swing.
 * It only calls model/service classes; it contains no business rules.</p>
 *
 * <p>Works with: model classes (Stage 1); PlatformAllocator and the
 * strategies (Stage 2); metrics and reports (Stage 3); SchedulingService,
 * the Observer pattern and DelayManager (Stage 4).</p>
 */
public class ConsoleDemo {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 16, 0, 0);

    /**
     * Runs the demo and prints the results.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        stage1();
        System.out.println();
        stage2();
        System.out.println();
        stage3();
        System.out.println();
        stage4();
    }

    /** Stage 1: the domain model, polymorphism, compatibility and validation. */
    private static void stage1() {
        printHeading("RailSync - Stage 1: domain model");

        // Step 1: build a small station (aggregation: platforms created first, then added).
        Station station = new Station("RailSync Central");
        station.addPlatform(new Platform("P2", 2, 220, EnumSet.of(TrainType.PASSENGER)));
        station.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        station.addPlatform(new Platform("P3", 3, 700, EnumSet.of(TrainType.FREIGHT)));
        System.out.println("Station: " + station.getName() + " (platforms kept sorted by number)");
        for (Platform platform : station.getPlatforms()) {
            System.out.println("  " + platform + " accepts " + platform.getSupportedTypes());
        }

        // Step 2: create one train of each subclass.
        List<Train> trains = new ArrayList<>();
        trains.add(new ExpressTrain("T1", "12951", "Rajdhani", at(10, 0), at(10, 10), 4, 16));
        trains.add(new PassengerTrain("T2", "66012", "Suburban Local", at(10, 30), at(10, 37), 3, 12));
        trains.add(new FreightTrain("T3", "F-401", "Coal Rake", at(10, 20), at(10, 50), 2, 40));

        // Step 3: polymorphism - the same calls give different answers per subclass.
        System.out.println();
        System.out.println("Polymorphism (same method calls, different subclass answers):");
        System.out.printf("  %-34s %-9s %-9s %-11s %-9s%n", "Train", "priority", "length", "min dwell", "buffer");
        for (Train train : trains) {
            System.out.printf("  %-34s %-9d %-9s %-11s %-9s%n", train,
                    train.getPriority(), train.getRequiredPlatformLength() + " m",
                    train.getMinimumDwellMinutes() + " min",
                    train.getClearanceBufferMinutes(5) + " min");
        }

        // Step 4: compatibility check of every train against every platform.
        System.out.println();
        System.out.println("Compatibility matrix:");
        for (Train train : trains) {
            for (Platform platform : station.getPlatforms()) {
                String reason = train.getIncompatibilityReason(platform);
                String verdict = (reason == null) ? "OK" : "NO - " + reason;
                System.out.println("  " + train.getNumber() + " -> " + platform + ": " + verdict);
            }
        }

        // Step 5: composition - the Schedule creates reservations.
        System.out.println();
        System.out.println("Schedule (composition: Schedule creates and owns reservations):");
        Schedule schedule = new Schedule();
        Platform p1 = station.findPlatformById("P1");
        Platform p3 = station.findPlatformById("P3");
        schedule.reserve(trains.get(0), p1, trains.get(0).getExpectedArrival());
        schedule.reserve(trains.get(1), p1, trains.get(1).getExpectedArrival());
        schedule.reserve(trains.get(2), p3, trains.get(2).getExpectedArrival());
        for (PlatformReservation reservation : schedule.getAllReservations()) {
            System.out.println("  " + reservation + "  -> train status " + reservation.getTrain().getStatus());
        }

        // Step 6: validation - bad input is refused with a friendly message.
        System.out.println();
        System.out.println("Validation (friendly error messages):");
        tryAndReport("Freight with a 10-minute stop", () ->
                new FreightTrain("T9", "F-9", "Short stop", at(11, 0), at(11, 10), 1, 10));
        tryAndReport("Departure before arrival", () ->
                new PassengerTrain("T9", "9", "Backwards", at(11, 0), at(10, 0), 1, 5));
        tryAndReport("Express on freight-only platform", () -> {
            Train extra = new ExpressTrain("T9", "12009", "Shatabdi", at(12, 0), at(12, 8), 3, 10);
            schedule.reserve(extra, p3, extra.getExpectedArrival());
        });
        tryAndReport("Duplicate platform number 1", () ->
                station.addPlatform(new Platform("P9", 1, 400, EnumSet.of(TrainType.EXPRESS))));

        // Step 7: delay shifts the expected times without changing the dwell.
        System.out.println();
        Train rajdhani = trains.get(0);
        rajdhani.applyDelay(15);
        System.out.println("Delay: " + rajdhani.getNumber() + " delayed 15 min -> expected "
                + rajdhani.getExpectedArrival().toLocalTime() + "-" + rajdhani.getExpectedDeparture().toLocalTime()
                + ", dwell still " + rajdhani.getDwellTime().toMinutes() + " min"
                + " (re-checking its booking is DelayManager's job in Stage 4)");

        System.out.println();
        System.out.println("Stage 1 demo finished.");
    }

    /** Stage 2: buffered overlap check, the allocation engine, FCFS vs Priority. */
    private static void stage2() {
        printHeading("RailSync - Stage 2: overlap rule, allocator, FCFS vs Priority");

        // Step 1: the clearance-buffer rule on one platform (worked example from Schedule.findConflict).
        Schedule schedule = new Schedule(5);
        Platform platform = new Platform("PX", 1, 500, EnumSet.of(TrainType.PASSENGER));
        Train a = new PassengerTrain("A", "A", "Train A", at(10, 0), at(10, 30), 3, 8);
        Train b = new PassengerTrain("B", "B", "Train B", at(10, 30), at(11, 0), 3, 8);
        schedule.reserve(a, platform, a.getExpectedArrival());
        System.out.println("Buffer rule (5 min): A booked " + schedule.findReservation(a));
        tryAndReport("B at 10:30 (touching A)", () -> schedule.reserve(b, platform, at(10, 30)));
        System.out.println("  B can first start at " + schedule.earliestFreeStart(b, platform, at(10, 30)).toLocalTime());

        // Step 2: run the same scenario with each strategy (fresh copies, so runs do not affect each other).
        PlatformAllocationStrategy[] strategies = {new FcfsStrategy(), new PriorityStrategy()};
        for (PlatformAllocationStrategy strategy : strategies) {
            Station station = buildStage2Station();
            Schedule runSchedule = new Schedule(5);
            PlatformAllocator allocator = new PlatformAllocator(station, runSchedule, 20);
            List<Train> trains = buildStage2Trains();

            System.out.println();
            System.out.println("Strategy: " + strategy.getName()
                    + "  (buffer 5 min, max wait 20 min, platforms PF-1 E/P 500 m, PF-2 P 220 m, PF-3 F 700 m)");
            List<AllocationResult> results = allocator.allocateAll(trains, strategy);
            int placed = 0;
            long totalWait = 0;
            for (AllocationResult result : results) {
                System.out.println("  " + result.getMessage());
                if (result.isAllocated()) {
                    placed++;
                    totalWait += result.getQueueWaitMinutes();
                }
            }
            System.out.println("  => allocated " + placed + " of " + trains.size()
                    + ", total queue wait " + totalWait + " min");
        }
        System.out.println();
        System.out.println("Notice: at 10:25 PF-1 frees up while P-206 (arrived 10:06) and express 12002 (10:08)");
        System.out.println("both wait. FCFS serves P-206 first; Priority serves the express first.");
        System.out.println();
        System.out.println("Stage 2 demo finished.");
    }

    /** Stage 3: SJF, Aging, preemption, metrics and the sweep line. */
    private static void stage3() {
        printHeading("RailSync - Stage 3: all four strategies, starvation, metrics, preemption");
        System.out.println("Scenario: ONE platform (PF-1, express + passenger), buffer 5 min, max wait 30 min.");
        System.out.println("  E0 10:00-10:10, L (slow local, priority 2, 12-min stop) 10:01, E1 10:12, E2 10:27");
        System.out.println("  (expresses have priority 7 and 10-min stops)");

        // Step 1: run every strategy on its own fresh copy of the scenario.
        PlatformAllocationStrategy[] strategies = {new FcfsStrategy(), new PriorityStrategy(),
                new ShortestDwellFirstStrategy(), new PriorityAgingStrategy(4)};
        MetricsCalculator calculator = new MetricsCalculator();
        ReportGenerator reports = new ReportGenerator();
        List<String> names = new ArrayList<>();
        List<ScheduleMetrics> allMetrics = new ArrayList<>();
        String agingListing = "";
        ScheduleMetrics agingMetrics = null;
        for (PlatformAllocationStrategy strategy : strategies) {
            Station station = new Station("Starvation Halt");
            station.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
            Schedule schedule = new Schedule(5);
            PlatformAllocator allocator = new PlatformAllocator(station, schedule, 30);
            List<Train> trains = buildStarvationTrains();

            List<AllocationResult> results = allocator.allocateAll(trains, strategy);
            System.out.println();
            System.out.println(strategy.getName() + ":");
            for (AllocationResult result : results) {
                System.out.println("  " + result.getMessage());
            }
            ScheduleMetrics metrics = calculator.calculate(station, schedule, trains, calculator.countConflicts(results));
            names.add(strategy.getName());
            allMetrics.add(metrics);
            if (strategy instanceof PriorityAgingStrategy) {
                agingListing = reports.buildOccupancyListing(station, schedule);
                agingMetrics = metrics;
            }
        }

        // Step 2: the side-by-side comparison (the arena in Stage 7 reuses this table).
        System.out.println();
        System.out.println("Comparison (same trains, different strategy):");
        System.out.print(reports.buildComparisonTable(names, allMetrics));
        System.out.println("Priority and SJF starve L; Aging raises L's priority while it waits");
        System.out.println("(at 10:30: L = 2 + 29/4 = 9 beats E2 = 7 + 3/4 = 7), so nobody starves.");
        System.out.println("'Needed' = sweep-line peak: 3 platforms would let every train in with zero waiting.");

        // Step 3: the full report and a text Gantt chart for one strategy.
        System.out.println();
        System.out.print(reports.buildSummaryReport("Priority + Aging", agingMetrics));
        System.out.println("Platform occupancy (Priority + Aging):");
        System.out.print(agingListing);

        // Step 4: safe preemption, once allowed and once refused.
        System.out.println();
        System.out.println("Preemption (optional, off by default):");
        System.out.println("  Express X (10:28) needs PF-1, where local L is booked from 10:30. PF-2 takes locals only.");
        runPreemptionDemo(false);
        runPreemptionDemo(true);
        System.out.println();
        System.out.println("Stage 3 demo finished.");
    }

    /** Stage 4: the SchedulingService facade, the Observer pattern, delay ripple and breakdown. */
    private static void stage4() {
        printHeading("RailSync - Stage 4: services, observer events, delay ripple, breakdown");

        // Step 1: wire the service the same way the GUI will (strategy injected, events shared).
        ScheduleEventManager events = new ScheduleEventManager();
        // [OBSERVER] a tiny console observer; in Stage 5 the GUI panels subscribe the same way.
        events.subscribe(new ScheduleObserver() {
            /** Prints each event, except plain data edits, to show the Observer pattern working. */
            @Override
            public void onScheduleEvent(ScheduleEvent event) {
                if (event.getType() != ScheduleEventType.TRAINS_CHANGED
                        && event.getType() != ScheduleEventType.PLATFORMS_CHANGED) {
                    System.out.println("    [observer got " + event.getType() + "] " + firstLine(event.getMessage()));
                }
            }
        });
        SchedulingService service = new SchedulingService(new Station("RailSync Central"), new Schedule(5),
                new FcfsStrategy(), events);

        // Step 2: platforms and trains (long locals and the express only fit PF-1).
        service.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        service.addPlatform(new Platform("P2", 2, 220, EnumSet.of(TrainType.PASSENGER)));
        service.addPlatform(new Platform("P3", 3, 700, EnumSet.of(TrainType.FREIGHT)));
        service.addTrain(TrainFactory.create(TrainType.PASSENGER, "T1", "L-101", "Long Local 1", at(10, 0), at(10, 10), 3, 14));
        service.addTrain(TrainFactory.create(TrainType.FREIGHT, "T2", "F-401", "Coal Rake", at(10, 0), at(10, 40), 2, 40));
        service.addTrain(TrainFactory.create(TrainType.PASSENGER, "T3", "S-201", "Short Local", at(10, 5), at(10, 15), 2, 8));
        service.addTrain(TrainFactory.create(TrainType.PASSENGER, "T4", "L-102", "Long Local 2", at(10, 15), at(10, 25), 3, 14));
        service.addTrain(TrainFactory.create(TrainType.EXPRESS, "T5", "12951", "Rajdhani", at(10, 30), at(10, 40), 4, 12));
        service.addTrain(TrainFactory.create(TrainType.PASSENGER, "T6", "L-103", "Long Local 3", at(11, 0), at(11, 10), 3, 14));
        System.out.println("Loaded " + service.getTrainManager().getAllTrains().size() + " trains (via TrainFactory) and "
                + service.getStation().getPlatformCount() + " platforms.");

        // Step 3: allocate with the injected strategy.
        System.out.println();
        System.out.println("Allocate all pending trains:");
        for (AllocationResult result : service.allocatePendingTrains()) {
            System.out.println("  " + result.getMessage());
        }
        ReportGenerator reports = new ReportGenerator();
        System.out.print(reports.buildOccupancyListing(service.getStation(), service.getSchedule()));

        // Step 4: a delay that ripples down PF-1 (no other platform fits L-101 at 10:10).
        System.out.println();
        System.out.println("Delay L-101 by 10 minutes:");
        DelayReport delay = service.delayTrain(service.getTrainManager().findByNumber("L-101"), 10);
        System.out.println("  Outcome: " + delay.getOutcome() + ", cascade of " + delay.getCascadeCount() + " train(s)");
        for (ReservationChange change : delay.getChanges()) {
            System.out.println("    " + change);
        }
        System.out.print(reports.buildOccupancyListing(service.getStation(), service.getSchedule()));

        // Step 5: a delay that is solved by simply keeping the platform.
        System.out.println();
        System.out.println("Delay L-103 by 5 minutes:");
        System.out.println("  " + service.delayTrain(service.getTrainManager().findByNumber("L-103"), 5).getMessage());

        // Step 6: a new platform opens, then PF-1 breaks down.
        System.out.println();
        service.addPlatform(new Platform("P4", 4, 300, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        System.out.println("PF-4 (300 m, express + passenger) opened. Now PF-1 breaks down:");
        BreakdownReport breakdown = service.reportBreakdown(service.getPlatformManager().findById("P1"));
        System.out.println("  " + breakdown.getMovedCount() + " moved, " + breakdown.getStrandedCount() + " could not be moved:");
        for (AllocationResult outcome : breakdown.getOutcomes()) {
            System.out.println("    " + outcome.getMessage());
        }
        System.out.print(reports.buildOccupancyListing(service.getStation(), service.getSchedule()));

        // Step 7: rules enforced by the managers, shown as friendly messages.
        System.out.println();
        System.out.println("Rules enforced by the managers:");
        tryAndReport("Remove allocated train L-102", () ->
                service.removeTrain(service.getTrainManager().findByNumber("L-102")));
        tryAndReport("Remove PF-4 while trains are booked", () ->
                service.removePlatform(service.getPlatformManager().findById("P4")));
        tryAndReport("Add a second train numbered 12951", () ->
                service.addTrain(TrainFactory.create(TrainType.EXPRESS, "T9", "12951", "Copy", at(12, 0), at(12, 10), 3, 10)));

        System.out.println();
        System.out.print(reports.buildSummaryReport("Live schedule after the disruptions", service.calculateMetrics()));
        System.out.println();
        System.out.println("Stage 4 demo finished.");
    }

    /** @return the first line of a possibly multi-line message */
    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    /** Local L booked on PF-1 at 10:30; express X arrives 10:28. Optionally PF-2 is blocked too. */
    private static void runPreemptionDemo(boolean blockPlatform2) {
        Station station = new Station("Preemption Junction");
        Platform mixed = new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER));
        Platform localOnly = new Platform("P2", 2, 500, EnumSet.of(TrainType.PASSENGER));
        station.addPlatform(mixed);
        station.addPlatform(localOnly);
        Schedule schedule = new Schedule(5);
        Train local = new PassengerTrain("L", "L-100", "Local", at(10, 30), at(10, 40), 1, 8);
        schedule.reserve(local, mixed, local.getExpectedArrival());
        if (blockPlatform2) {
            Train blocker = new PassengerTrain("B", "B-200", "Blocker", at(10, 25), at(10, 45), 3, 8);
            schedule.reserve(blocker, localOnly, blocker.getExpectedArrival());
        }
        PlatformAllocator allocator = new PlatformAllocator(station, schedule, 0);
        allocator.setPreemptionEnabled(true);
        List<Train> newcomers = new ArrayList<>();
        newcomers.add(new ExpressTrain("X", "X-900", "Express", at(10, 28), at(10, 38), 4, 12));

        List<AllocationResult> results = allocator.allocateAll(newcomers, new PriorityStrategy());
        System.out.println(blockPlatform2 ? "  Case 2, PF-2 also busy:" : "  Case 1, PF-2 free:");
        System.out.println("    " + results.get(0).getMessage());
        System.out.println("    L-100 is now " + schedule.findReservation(local));
    }

    /** The starvation scenario: low-priority L keeps being passed over by expresses. */
    private static List<Train> buildStarvationTrains() {
        List<Train> trains = new ArrayList<>();
        trains.add(new ExpressTrain("E0", "E0", "Express 0", at(10, 0), at(10, 10), 4, 12));
        trains.add(new PassengerTrain("L", "L", "Slow local", at(10, 1), at(10, 13), 1, 8));
        trains.add(new ExpressTrain("E1", "E1", "Express 1", at(10, 12), at(10, 22), 4, 12));
        trains.add(new ExpressTrain("E2", "E2", "Express 2", at(10, 27), at(10, 37), 4, 12));
        return trains;
    }

    /** Three platforms with different lengths and categories. */
    private static Station buildStage2Station() {
        Station station = new Station("Stage 2 Junction");
        station.addPlatform(new Platform("P1", 1, 500, EnumSet.of(TrainType.EXPRESS, TrainType.PASSENGER)));
        station.addPlatform(new Platform("P2", 2, 220, EnumSet.of(TrainType.PASSENGER)));
        station.addPlatform(new Platform("P3", 3, 700, EnumSet.of(TrainType.FREIGHT)));
        return station;
    }

    /** Seven trains designed so that FCFS and Priority give visibly different results. */
    private static List<Train> buildStage2Trains() {
        List<Train> trains = new ArrayList<>();
        trains.add(new ExpressTrain("T1", "12951", "Rajdhani", at(10, 0), at(10, 20), 4, 16));     // 420 m: PF-1 only
        trains.add(new FreightTrain("T2", "F-401", "Coal Rake", at(10, 0), at(10, 40), 2, 40));     // 640 m: PF-3
        trains.add(new PassengerTrain("T3", "P-102", "Short Local", at(10, 2), at(10, 12), 2, 8));  // 160 m: PF-1 or PF-2
        trains.add(new PassengerTrain("T4", "P-206", "Long Local", at(10, 6), at(10, 16), 3, 14));  // 280 m: PF-1 only
        trains.add(new ExpressTrain("T5", "12002", "Shatabdi", at(10, 8), at(10, 18), 5, 12));      // 320 m: PF-1 only
        trains.add(new FreightTrain("T6", "F-512", "Cement", at(10, 10), at(10, 40), 1, 30));       // PF-3 busy too long
        trains.add(new FreightTrain("T7", "F-999", "Iron Ore Long Rake", at(10, 15), at(10, 45), 1, 60)); // 940 m: fits nowhere
        return trains;
    }

    /** Runs an action that is expected to fail and prints its message. */
    private static void tryAndReport(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": accepted (unexpected!)");
        } catch (RailSyncException e) {
            System.out.println("  " + label + ": refused -> " + e.getMessage());
        }
    }

    private static LocalDateTime at(int hour, int minute) {
        return DAY.withHour(hour).withMinute(minute);
    }

    private static void printHeading(String text) {
        System.out.println("=".repeat(70));
        System.out.println(text);
        System.out.println("=".repeat(70));
    }
}
