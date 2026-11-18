# RailSync Viva Guide

## 1. Study order (most important first)

Study in this order. Each step builds on the one before. Allow about 30-45 minutes per step.

| # | Read | Focus on | Concepts you can then explain |
|---|---|---|---|
| 1 | `model/Train.java`, `ExpressTrain`, `FreightTrain` | Constructor validation, the 4 abstract methods, `getIncompatibilityReason`, `getClearanceBufferMinutes` override | Abstraction, inheritance, polymorphism, encapsulation, constructor chaining |
| 2 | `model/Platform.java`, `Station.java` | Defensive copy of categories, `this(...)` chaining, sorted insert | Encapsulation, aggregation, HashSet vs ArrayList |
| 3 | `model/Schedule.java` + `PlatformReservation.java` | `reserve` (6 steps), `overlaps`, `findConflict` worked example, `earliestFreeStart`, `release` | Composition, half-open intervals, buffer rule |
| 4 | `strategy/` (interface, `QueueOrderingStrategy`, 4 strategies) | `orderReadyQueue`, `compareByPolicy`, aging formula | Strategy pattern, interface vs abstract class, OS scheduling |
| 5 | `service/PlatformAllocator.java` | `allocateAll` steps 1-3d, `findFirstFreePlatform`, `dropHopelessTrains`, `tryPreemption` | Ready queue, First-Fit, heuristics, safe preemption |
| 6 | `service/DelayManager.java` | `delayTrain` order of attempts, `planRipple` example, `handleBreakdown` | Reassignment reuse, never changing silently |
| 7 | `observer/` + `SchedulingService.java` | `publish`, the facade methods, constructor injection | Observer pattern, SOLID-D, SOLID-S |
| 8 | `service/MetricsCalculator.java` | Waiting/turnaround definitions, `minimumPlatformsNeeded` (sweep line) | Metrics, sweep line |
| 9 | `ui/RefreshablePanel`, `AllocationPanel`, `TimeDistanceDiagram` (`xFor`, `pathPoints`, `interpolate`) | How the GUI only calls the service; painting and animation | Observer in the GUI, separation of concerns |
| 10 | `service/SnapshotCommand`, `DelayCommand`, `ScenarioCsv`, `StrategyArena` | Undo, CSV validation, comparison on copies | Command pattern, deep copies |

Before the viva: run `run-demo.cmd` once and read its output top to bottom. It shows steps 1-7 with real numbers.
Then do the 5-minute GUI tour in the README.

## 2. Likely questions and short answers

**OOP basics**

1. *Why is `Train` abstract?* No train is "just a train": every real train is express, passenger or freight. The
   abstract class holds the shared data and validation and forces each subclass to answer the four abstract methods.
2. *Where is polymorphism?* For example `getIncompatibilityReason` calls `getRequiredPlatformLength()`, which runs the
   Express, Passenger or Freight version at runtime. Also `strategy.orderReadyQueue(...)` in `PlatformAllocator`.
   OOP_CONCEPTS section 5 lists all the exact lines.
3. *Overriding vs overloading?* Overriding = same signature in a subclass (`FreightTrain.getClearanceBufferMinutes`).
   Overloading = same name, different parameters (`allocateAll(trains, strategy)` and `allocateAll(trains, strategy, notBefore)`).
4. *Why not just a `TrainType` enum?* The enum is only a label platforms store. Four behaviours differ by formula; an
   enum would need a `switch` in many places, and a new type would mean editing all of them.
5. *Encapsulation example?* `Train.assignPlatform` is package-private, so only `Schedule` can link a train to a
   platform; the train and its booking can never disagree. Times change only through validated `updateTimes`.
6. *Aggregation vs composition here?* Station *holds* platforms that exist without it (aggregation). Schedule
   *creates and destroys* its reservations, which have no meaning without it (composition; package-private constructor).
7. *Interface vs abstract class?* `PlatformAllocationStrategy` (interface: what a strategy does) vs
   `QueueOrderingStrategy` (abstract class: shared sorting code). A class may implement many interfaces but extend one class.
8. *Is calling an overridable method from a constructor safe?* Normally risky, because subclass fields are not set yet.
   Here `getMinimumDwellMinutes()` returns a constant in every subclass, so it is safe; the comment in `Train` says so.
   `setBasePriority` is `final` for the same reason.
9. *Checked or unchecked exceptions? Why?* Unchecked (`RuntimeException`). They signal invalid input or a broken rule,
   the GUI catches the base class once, and method signatures stay short. Normal outcomes are return values, not exceptions.
10. *Why `HashMap` in Schedule?* "Where is train X?" is asked constantly (delays, reports); the map answers in one
    step instead of scanning the list.

**SOLID and patterns**

11. *Show dependency inversion.* `SchedulingService` receives a `PlatformAllocationStrategy` through its constructor
    and `setStrategy`; only `Main` (and the GUI choice) names a concrete class.
12. *Open/closed?* A fifth strategy = one new class + one line in `StrategyCatalog`; the allocator is unchanged.
13. *Why Strategy and not if/else?* Policies are interchangeable at runtime and comparable in the arena with one loop;
    each policy is one small class.
14. *How does Observer work here?* Screens subscribe to `ScheduleEventManager`; the service publishes an event after
    every change; each screen's `onScheduleEvent` calls its own `refresh()`. The services never import Swing.
15. *How does Undo work?* Command pattern. `AllocateCommand`/`DelayCommand` save every booking before running;
    `undo()` releases everything and books the saved picture again (DelayCommand also removes the delay minutes).
    History is cleared after changes a snapshot could not safely reverse.

**Algorithms**

16. *Explain the overlap rule.* Bookings are half-open `[start, end)`, and two bookings on one platform need a
    clearance gap (the larger of the two trains' buffers). Stretch both ends by the gap, then overlap if
    `aStart < bEnd && bStart < aEnd`. Example: A 10:00-10:30, B wants 10:30, gap 5 -> conflict; B may start 10:35.
17. *How does allocation work?* An event loop: arrivals join the ready queue, the strategy orders it, each train takes
    the lowest-numbered free compatible platform; a train that cannot start within the maximum wait is refused with the reason.
18. *FCFS vs Priority in the demo?* At 08:15 PF-2 frees while L-101, L-102 and 12002 wait. FCFS serves L-101 (first to
    arrive) and 12002 waits 20 min; Priority serves 12002 at once and L-102 is refused.
19. *What is starvation, and how does aging fix it?* With Priority or SJF a low-priority or long train can be passed
    over forever. Aging adds 1 point per N minutes waited: at 10:30, L = 2 + 29/4 = 9 beats E2 = 7 + 0.
20. *When is preemption allowed?* Only for exactly one lower-priority booking that has not started yet, and only if
    that train can keep its time on another platform. Otherwise nothing changes (exact rollback).
21. *What is a delay ripple?* The delayed train keeps its platform and each following train is pushed to "previous end
    + gap"; the chain stops at the first booking that already has room. It is refused if a pushed train would wait
    longer than the maximum wait.
22. *What does the sweep line compute?* The most trains present at once (+1 at arrival, -1 at departure + buffer;
    leaving before arriving on ties). That many platforms would give zero waiting if every platform suited every train.
23. *Is any strategy optimal?* No. All are greedy heuristics, one decision at a time. The arena shows the trade-offs:
    SJF has the lowest average wait but drops the most trains.
24. *Waiting time vs turnaround?* Waiting = platform start - original timetable arrival (includes delay and
    queueing). Turnaround = waiting + dwell. Averages cover allocated trains only.

**Design and testing**

25. *Why is the GUI free of business logic?* Panels call only `SchedulingService`. Forms collect text; Train/Platform
    validate. The same services run under JUnit, the console demo and the arena.
26. *How do you know the demo works?* `DemoScenarioTest` proves every promise (conflicts, FCFS vs Priority, the
    impossible train, ripple of 2, breakdown moves one and strands others). 165 tests pass.
27. *How does the animation work without threads?* A Swing Timer repaints every 15 ms for 500 ms; each train line has
    6 points, and each point moves linearly: `p = old + (new - old) x t`.

## 3. Design decisions and trade-offs

| Decision | Benefit | Cost / trade-off |
|---|---|---|
| Event-driven ready queue instead of one sort of all trains | Matches the OS idea; aging has a real "time waited" | Slightly more code than a single sort |
| Trains may wait up to a maximum (default 20 min) | Strategies differ in waiting, not only in who is refused | One more setting to explain |
| First-Fit platform choice for every strategy | Simple, deterministic, traceable on a whiteboard | Not "best-fit"; may use long platforms for short trains |
| Gap = larger of the two buffers | Freight's extra time is always respected | Slightly conservative |
| Ripple only within the maximum wait, after a free platform was tried | Least disruptive first; clear safety limit | Some delays fail rather than pushing trains far |
| Failed delay -> train released and marked UNALLOCATED with the reason | Never shows an impossible booking | The train needs manual handling |
| Undo by snapshot, not step-by-step reversal | Handles ripples and preemption in one step; rules re-checked | History cleared after other changes |
| Unchecked exceptions | Short signatures, one central catch | Callers are not forced by the compiler |
| One simulated service day | Simple time input | No overnight trains |
| Everything on the Swing thread | No concurrency bugs (spec forbids threads) | Very large inputs would block briefly (500 trains x 4 strategies take about 0.2 s) |

## 4. Honest limitations

- **Heuristics, not optimal.** All four policies are greedy. Nothing guarantees the fewest refused trains, the
  lowest total waiting or the fewest platforms. Optimal assignment with categories and lengths is a harder
  combinatorial problem.
- **Interval scheduling, not true CPU scheduling.** Trains have fixed times and fixed stays, so the core problem is
  interval scheduling or partitioning. The OS analogy covers queueing order, starvation and utilization well, but a
  CPU can time-slice a process and a platform cannot "time-slice" a train. Preemption here means moving a booking
  *before* it starts, never interrupting a train at a platform.
- **Simplified railway model.** No track layout, routes, crossovers, signalling blocks, headways between approach
  paths, platform halves, train joining/splitting, crew or rolling-stock constraints. Approach (4 min) and exit
  (3 min) times are fixed for drawing only. Lengths use fixed coach/wagon sizes.
- **Single service day, minute precision**; no overnight trains or recurring timetables.
- **Breakdowns are planned, not live.** All bookings on the failed platform are reassigned, even a train that would
  already be standing there; the services have no "now" except in `reallocateFrom`.
- **Sweep-line minimum ignores categories and lengths**, so it is a yardstick, not an achievable target.
- **CSV stores the scenario, not bookings**; undo covers allocations and delays only.
- **Educational simulation only**: not a real signalling or safety system.

## 5. Questions about the database and software engineering

28. *Why SQLite and not MySQL or plain files?* SQLite is embedded: no server to install, one file, but still real
    SQL with transactions, constraints and foreign keys. Plain files would need our own format and give no
    transactions; a server database would make the app hard to run on an examiner's laptop.
29. *How is the data saved?* `AutoSaver` is an observer. After every event it writes an audit entry and saves the
    whole session (`StationSnapshot`) in one transaction through `StationRepository`.
30. *What if the save fails half-way?* The transaction is rolled back, so the previous save stays complete; the
    change still exists in memory and the sidebar shows "Save failed".
31. *How do you stop SQL injection?* Every value is a `PreparedStatement` parameter; a test stores the name
    `Robert'); DROP TABLE trains; --` and reads it back as plain text.
32. *What is the Repository pattern here?* `StationRepository` and `AuditLog` say *what* is stored; the SQLite
    classes say *how*. Only `Main.build()` names SQLite (dependency inversion).
33. *How would you change the database schema?* Raise `Database.SCHEMA_VERSION` and add a step in `migrate()`;
    old files are upgraded automatically (`PRAGMA user_version`).
34. *What test levels do you have?* Unit (including boundary values), integration, system (the real window and
    database, `SystemTest`) and performance; 224 tests in total, all passing; 95.9% line coverage of the logic.
35. *Show a state diagram.* Train status: SCHEDULED → ALLOCATED via `Schedule.reserve()`; back via `release()`;
    UNALLOCATED when no safe option exists (see UML_DIAGRAMS.md, sections 6 to 8).
