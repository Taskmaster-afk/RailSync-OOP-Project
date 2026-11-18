# RailSync build progress

How to see the current output at any stage:

- `run-tests.cmd` (double-click): builds and runs every JUnit test.
- `run-app.cmd` (double-click): builds and opens the RailSync desktop app (from Stage 5).
- `run-demo.cmd` (double-click): runs `ConsoleDemo`, which prints what Stages 1-4 do, as text.
- `docs/screenshots/`: a PNG of every screen, taken from the real window at the end of each GUI stage.
- Or from a terminal in the project folder: `mvnw.cmd test` and
  `java -cp target\classes com.railsync.ConsoleDemo`.

Maven itself does not need to be installed. `mvnw.cmd` (the official Maven Wrapper)
downloads Maven 3.9.16 the first time and caches it in `%USERPROFILE%\.m2`.

## Stage 1: domain model (done)

Built: exceptions, Train hierarchy, Platform, Station, Schedule, PlatformReservation.
Tests: 38 run, 0 failures.

Decisions made:

| Decision | Why |
|---|---|
| Exceptions extend one unchecked `RailSyncException` | The GUI can catch one type in one place. "No platform found" is a normal result, not an exception. |
| `TrainType` enum kept as well as subclasses | Platforms need a simple label to store which categories they accept. Behaviour stays in the subclasses. |
| Priority = base priority (1-5) + subclass bonus (Express +3, Passenger +1, Freight 0) | The user's choice still matters, but type decides the band. Overlapping bands keep FCFS vs Priority interesting. |
| Required length: Express 25 m/coach + 20 m engine; Passenger 20 m/coach; Freight 15 m/wagon + 2 x 20 m locos | Three genuinely different formulas, not just three constants. |
| Minimum dwell: Express 5, Passenger 3, Freight 20 minutes | Checked when times are set, so a train can never hold an impossible stop. |
| Clearance buffer: station-wide value in `Schedule` (default 5, 0-60) and `FreightTrain` adds 5 | Configurable, not hardcoded. Shows a subclass overriding a concrete method. |
| `canUsePlatform()` written once in `Train`, calls the abstract methods | Rules in one place. Polymorphism happens through the calls it makes. |
| `Train.assignPlatform()` / `clearPlatform()` are package-private | Only `Schedule` can link a train to a platform, so the two can never disagree. |
| `PlatformReservation` is immutable with a package-private constructor | Composition: only `Schedule` creates or deletes it. Moving a train = delete + create. |
| Delay shifts expected times, keeps the original timetable | Waiting time (Stage 3) is measured from the original requested arrival. |
| Time-overlap check left for Stage 2 | Matches the stage plan. Stage 1 `reserve()` checks compatibility, double-booking and start-before-arrival only. |

## Stage 2: overlap rule, allocator, FCFS and Priority (done)

Built: buffered overlap logic in `Schedule` (`overlaps`, `requiredGapMinutes`, `findConflict`,
`earliestFreeStart`), the strategy interface `PlatformAllocationStrategy`, the shared base
`QueueOrderingStrategy`, `FcfsStrategy`, `PriorityStrategy`, `PlatformAllocator` and `AllocationResult`.
Tests: 69 run, 0 failures (31 new).

Decisions made:

| Decision | Why |
|---|---|
| Gap test = stretch the END of both intervals by the gap, then a normal half-open overlap test | One simple rule that covers trains on both sides. Worked example is in `Schedule.findConflict`. |
| Gap between two trains = the larger of their two buffers | A freight train gets its extra 5 minutes whether it comes first or second. |
| Allocation is a small event-driven loop with a ready queue, not one sort of the whole list | This is the real OS idea: the strategy picks among trains that are waiting *now*. "Batch mode" means the strategy sorts the whole current queue at each decision point. It also gives aging (Stage 3) a meaningful "time waited". |
| Trains may wait at the outer signal up to a configurable maximum (default 20 min, 0-180) | Without waiting, the strategies differ only in who gets rejected. Waiting makes queue-wait metrics meaningful. |
| Platform choice is First-Fit (lowest number) for every strategy | Strategies decide the ORDER of service, like OS schedulers. Placement stays simple and traceable. |
| Ties broken by earlier arrival, then train number | Runs are deterministic and reproducible. |
| "No platform" is an `AllocationResult.failure(...)`, not an exception | Exceptions are only for broken rules (for example `reserve()` asked to double-book). |
| `Schedule.reserve()` re-checks the overlap itself | Last line of defence: even a buggy strategy can never double-book. |

## Stage 3: SJF, Aging, preemption, metrics, sweep line, reports (done)

Built: `ShortestDwellFirstStrategy`, `PriorityAgingStrategy`, optional preemption in `PlatformAllocator`,
`Schedule.findAllConflicts`, `ScheduleMetrics`, `MetricsCalculator` (with the sweep line) and `ReportGenerator`.
Tests: 95 run, 0 failures (26 new).

Decisions made:

| Decision | Why |
|---|---|
| Aging: effective priority = priority + minutes waited / N (N = 4 by default, 1-120) | One line of maths, easy to compute by hand. Integer steps. |
| Preemption is an allocator option (off by default), not a fifth strategy | Works with any ordering policy. Uses `getPriority()` to decide who may displace whom. |
| Preemption displaces at most ONE lower-priority train, only if it has not reached the platform, and only if it can keep the SAME time on another platform | "Safe reassignment" has one clear meaning. If the move fails, the swap is undone exactly, so nothing is silently broken. |
| A "conflict" = every compatible platform was busy when the train needed one (it waited, preempted, or was refused for being busy) | Countable and meaningful. A train that fits no platform at all is not a conflict. |
| Waiting time is measured from the ORIGINAL timetable arrival | Delay and queue time both count, as the spec defines. |
| Averages cover allocated trains only | An unallocated train has no start time. Note for the viva: this is why Priority and SJF can *look* better on average wait. The starved train simply drops out of the average, so always read "Unalloc" next to it. |
| Utilization window = first booking start to last booking end | No arbitrary day length. |
| Sweep line uses each train's own buffer and ignores compatibility | It gives "platforms needed for zero waiting" if every platform suited every train: a theoretical yardstick, not a guarantee. Fact for the viva: on identical platforms, serving trains in arrival order with First-Fit reaches this minimum (the classic interval-partitioning result). Category and length rules can push the real need higher. |

## Stage 4: managers, SchedulingService, Observer, DelayManager, breakdown (done)

Built: `TrainFactory`, `TrainManager`, `PlatformManager`, `SchedulingService`, `DelayManager`, `DelayReport`,
`BreakdownReport`, `ReservationChange`, and the observer package (`ScheduleObserver`, `ScheduleEvent`,
`ScheduleEventType`, `ScheduleEventManager`). Tests: 128 run, 0 failures (33 new).

Decisions made:

| Decision | Why |
|---|---|
| `SchedulingService` is the one class the GUI talks to (a facade) | Panels never touch allocation, delay or metrics code. It gets the strategy through its constructor and can swap it with `setStrategy()`. |
| Services publish events; managers do not know about events | Managers stay simple and testable. `SchedulingService` adds `publish(...)` after each action. |
| `ReservationChange` (before/after) travels with DELAY, REASSIGNMENT and BREAKDOWN events | Reports list exactly what moved. The Stage 6 animation needs the old position to draw the faded "ghost". |
| Delay resolution order: still valid -> same platform -> other platform at same time -> ripple -> wait elsewhere -> fail | Least disruptive first. Ripple disturbs other trains, so it only comes after a free platform has been tried. |
| Ripple only if every pushed train waits no more than the maximum wait | Gives ripple a clear safety limit. Otherwise the cascade is reported as the reason and the next option is tried. |
| Pushed trains are NOT marked delayed | Their arrival is unchanged; they wait at the outer signal. Metrics count that time as waiting, not delay. |
| On failure the train is released and marked UNALLOCATED with the reason | A stale booking would show the train at a platform it cannot reach. Releasing it and reporting why is honest, not silent. |
| Breakdown reuses the same two reassignment steps as delays (`placeElsewhereAtSameTime`, `allocateSingle`) | No duplicated reassignment logic. |
| Breakdown reassigns every booking on the platform | Simplification: there is no live "now" in the services yet, so trains already standing at the platform are treated like the rest. Noted as a limitation. |
| `TrainFactory` added (optional pattern) | Genuinely needed: the GUI form, the demo loader and the random generator all create trains from a `TrainType`. |
| Delays and failed platforms survive `resetAllocations()` | Lets you re-run another strategy on the same disrupted day. |

## Stage 5: Swing GUI and demo scenario (done)

Built: `Main`, `DemoScenarioLoader`, and the `ui` package (`MainFrame`, `StatusBar`, `RefreshablePanel`, `Ui`,
`DashboardPanel`, `TrainPanel` + `TrainTableModel` + `TrainFormDialog`, `PlatformPanel` + `PlatformTableModel`
+ `PlatformFormDialog`, `AllocationPanel`, `SchedulePanel` + `ReservationTableModel`, `ReportsPanel`).
Added `getDescription()` to the strategy interface. Tests: 134 run, 0 failures (6 new, proving the demo scenario).
Screenshots: superseded by the Stage 6 set (the same screens plus the new ones).

Decisions made:

| Decision | Why |
|---|---|
| Every screen extends `RefreshablePanel` (abstract class implementing `ScheduleObserver`) | The subscribe-and-refresh code is written once. Each screen only writes `refresh()`. |
| Screens call only `SchedulingService` (plus read-only model getters) | Business logic stays out of Swing. Forms collect text; the model validates it and the friendly message is shown. |
| Demo scenario built by hand, with every promise proven by `DemoScenarioTest` | The live demo never depends on luck: conflicts, FCFS vs Priority, an impossible train, a ripple of 2, and a breakdown that moves one train and strands others. |
| The demo is loaded at start-up but not allocated | You choose the strategy and press Allocate, so the strategy's effect is visible. |
| Times are typed as HH:mm on one simulated service day (16 Nov 2026) | Simple input. Overnight trains are not supported, which is noted as a limitation. |
| FlatLaf light/dark toggle in the toolbar | Allowed optional dependency. `run-app.cmd` builds a jar plus `target/lib` (via maven-dependency-plugin). |
| `ScreenshotMaker` lives in test sources | A developer tool that renders the real window to PNGs. It is not part of the shipped app. |

## Stage 6: time-distance diagram, simulation clock, animation, Delay & Breakdown screen (done)

Built: `TrainMovement` (the positionAt calculation), `SimulationClock`, `TimeDistanceDiagram`, `TimeDistancePanel`
and `DisruptionPanel`. `ReservationChange` now remembers the delayed train's old arrival (needed to draw its ghost).
Tests: 144 run, 0 failures (10 new). Screenshots: `docs/screenshots/stage6-*.png`, including `stage6-delay-after.png`
(ghosts and ripple outlines) and `stage6-breakdown-after.png`.

Decisions made:

| Decision | Why |
|---|---|
| Fixed approach (4 min, outer signal to platform) and exit (3 min) times, straight lines between five key moments | No physics, as the spec asks. One small class (`TrainMovement`) gives the phase and progress at any time, and the yard view (Stage 7) will reuse it. |
| A train whose slot is later than its arrival waits on the outer-signal row, drawn red | The ready queue becomes visible in the diagram. Red = "a conflict made this train queue". |
| Every train line has exactly six points | Old and new lines can be blended point by point: p = old + (new - old) x t over 500 ms (linear). |
| Ghosts stay until the next disruption, a reset, or "Clear ghosts" | You can study before/after calmly. The slide itself lasts 500 ms. |
| The DELAY event drives the animation; the REASSIGNMENT event that follows it is ignored by the diagram | Both carry the same changes, so reacting twice would restart the slide. |
| The clock is a Swing Timer (100 ms ticks, 1x/10x/60x); it only moves the drawing, never the allocation | No threads. Allocation stays a pure, testable calculation. |
| The Delay & Breakdown screen has its own diagram, sharing the one clock | The animation plays right where you click. |
| `ScreenshotMaker` clicks the real "Demo: delay" button | Captures prove the GUI calls the real services. |

## Stage 7: strategy comparison arena, seeded generator, yard view (done)

Built: `Scenario` (deep copies), `ScenarioGenerator` (seeded, 10-500 trains), `StrategyArena`, `StrategyCatalog`,
`SchedulingService.loadScenario()` and `reallocateFrom(now)`, a `notBefore` option on `PlatformAllocator.allocateAll`,
and the screens `ComparisonPanel`, `YardPanel` + `YardView`. Two refactors: shared `ClockControls` and
`StrategyListRenderer`. Tests: 152 run, 0 failures (8 new). Screenshots: `docs/screenshots/stage7-*.png`.

Arena check on generated data (seed 2026, buffer 5, max wait 20), measured on this laptop:

| 500 trains, 8 platforms | Allocated | Unallocated | Avg wait | Avg turnaround | Run time |
|---|---|---|---|---|---|
| FCFS | 424 | 76 | 5.4 min | 15.9 min | 51 ms |
| Priority | 413 | 87 | 3.7 min | 14.1 min | 47 ms |
| SJF | 412 | 88 | 3.2 min | 13.2 min | 39 ms |
| Priority + Aging | 422 | 78 | 4.9 min | 15.4 min | 43 ms |

This is the classic OS trade-off: SJF gives the lowest average wait but drops the most trains. FCFS places the
most trains but waits longest. Aging recovers most of FCFS's coverage while keeping priority ordering.
Sweep-line "needed for zero wait" = 18 versus 8 platforms available, so heavy queuing is expected.

Decisions made:

| Decision | Why |
|---|---|
| Arena runs on deep copies (`Scenario.copy()` rebuilds trains with `TrainFactory`) | The live schedule can never be changed by a comparison. A test checks this. |
| Generator: fixed seed -> same `java.util.Random` sequence -> same scenario | Reproducible runs. A test checks this. |
| Generator gives every category platforms long enough for its biggest train, and slightly too few platforms (80 % of the expected load) | A train is only ever unallocated because platforms are busy. Strategies then really compete; with too many platforms they all look the same. |
| "Switch strategy from now" re-plans only bookings that have not started, and nothing may start before now | Switching strategy while the clock runs visibly changes who leaves the queue next, without rewriting the past. |
| The yard view draws trains to scale and uses `TrainMovement`, the same code as the diagram | One movement model, two views. |
| Everything runs on the Swing thread (500 trains x 4 strategies takes about 0.2 s) | No threads needed, as the spec requires. |


## Stage 7b: CSV save/load and Undo (Command pattern) (done)

Built: `ScenarioCsv`, the command classes (`ScheduleCommand`, `SnapshotCommand`, `AllocateCommand`, `DelayCommand`,
`CommandHistory`), `Train.reduceDelay()`, `SchedulingService.undoLast()`, `canUndo()` and `getUndoDescription()`,
a new `UNDO` event type, and toolbar buttons Undo (also Ctrl+Z), Open CSV... and Save CSV....
Tests: 165 run, 0 failures (13 new). Sample file: `docs/sample-demo-scenario.csv`.
Screenshots: `stage7-delay-after.png` (Undo button offered) and `stage7-after-undo.png`.

Decisions made:

| Decision | Why |
|---|---|
| CSV holds platforms and trains (with delays and failed status), not bookings | A file describes a situation. After opening it you allocate with any strategy, which is the point of the tool. |
| ISO date-times in the file; standard CSV quoting for commas and quotes | Never ambiguous, and it opens correctly in Excel. |
| The whole file is checked (values, then unique ids and numbers) before anything is replaced; errors name the line | A bad file can never leave a half-loaded scenario. |
| Undo restores a snapshot taken just before the command ran (in `SnapshotCommand`), not a step-by-step reversal | One allocation can preempt other trains and one delay can ripple through several. Restoring the saved picture undoes all of it, and every rule is re-checked by `Schedule.reserve()` on the way back. |
| Undo covers allocations and delays; history is cleared after any other change (breakdown, edits, buffer change...) and keeps at most 20 steps | Restoring an old snapshot after such a change could break the rules, so Undo is only offered when it is guaranteed to be safe. |
| Commands are created inside `SchedulingService` (the invoker) | The GUI is unchanged: Allocate and Delay simply become undoable. |

Still not done (optional): a separate what-if sandbox (Prototype). The comparison arena already runs scenarios
on independent copies, which covers most of that need.

## Stage 8: documentation, UML, final verification (done)

Written from the final code: `README.md`, `docs/OOP_CONCEPTS.md`, `docs/DESIGN_PATTERNS.md`, `docs/UML_DIAGRAMS.md`,
`docs/CLASS_EXPLANATION.md`, `docs/USER_GUIDE.md`, `docs/VIVA_GUIDE.md`. The screenshot files from earlier stages
were replaced by one final set, `docs/screenshots/final-*.png` (14 images taken from the final build).

Code changes in this stage (comments only, no behaviour change): inline `[SOLID-S/O/L/I/D]` and `[ABSTRACTION]` tags
where each principle is visible, and Javadoc on the 22 Swing override methods that lacked it.

Verification, all actually run on this machine:

| Check | Result |
|---|---|
| `mvnw clean package` | BUILD SUCCESS |
| JUnit | 165 run, 0 failures, 0 errors, 0 skipped (18 test classes) |
| Packaged app `java -jar target/railsync-1.0.0.jar` | Starts and stays open (checked with a 10 s timeout) |
| Console demo | All four stage sections run to "finished" |
| GUI end to end (`ScreenshotMaker`) | Real window: demo loaded, FCFS allocation, every screen captured; real buttons clicked for Run comparison, Demo delay (ripple + ghosts) and Undo; breakdown captured |
| Mermaid diagrams | All 5 rendered without errors (Mermaid 11, local test page) |
| Doc line references | Each `file:line` in OOP_CONCEPTS.md checked against the source |
| No fictional classes | Every CamelCase name in the docs is a project class or a JDK/library type |
| Every UML method name exists in the code | Yes (26 checked) |
| Javadoc on every public/protected type and member (main sources) | 0 missing (automated scan) |
| Every test method has a scenario comment | 165 of 165 |
| TODO / FIXME left | 0 |

## UI refresh (after Stage 8)

A calm, classic look with no new features and no behaviour changes:

- New `Theme` class: one accent colour (#3B6FB6), rounded corners, slim scrollbars, airy tables (horizontal lines
  only, 28 px rows, cell padding, subtle alternating rows), and the same settings in light and dark mode.
- Sidebar grouped under small section labels (Overview, Data, Planning, Visualise, Analysis) with flat navigation
  items; thin separator lines instead of heavy borders.
- Every screen starts with a title and one plain-English line saying what it is for (`Ui.header`).
- Etched "titled box" borders replaced by light labelled sections (`Ui.section`); the main action on each screen is
  a filled accent button (`Ui.primary`).
- Dashboard cards: caption above a large value, inside a thin rounded outline. Status bar: green or red dot, then
  the message, with the settings in muted text.
- Diagrams draw on a clean canvas colour; slightly softer train-type colours.
- Font sizes use FlatLaf's own text styles, because custom half-point sizes were clipped under Windows display scaling.

Verified: 165 tests pass; the app was captured in light and dark mode (`docs/screenshots/final-*`, `dark-*`).

## Stage 9: SQLite storage and audit log (done)

| Decision | Why |
|---|---|
| Embedded SQLite through JDBC (`org.xerial:sqlite-jdbc`) | Real SQL with transactions and constraints, nothing to install, one file to back up. |
| `StationSnapshot` between the service and the storage | The service does not know how it is stored; the storage does not reach into the service. |
| Save the whole session after every change, in one transaction | Simple and obviously correct; 500 trains save in about 0.3 s; a failure keeps the previous save. |
| `AutoSaver` as an observer | No change to the scheduling code (Open/Closed). |
| Restore re-checks every booking through `Schedule.reserve()` | Damaged or hand-edited data cannot produce an impossible plan. |
| Versioned schema (`PRAGMA user_version`) | Older files are upgraded step by step; newer files are refused, not damaged. |
| Append-only audit log | Every change traceable afterwards. |

Verification: 14 persistence tests (round trip, given-up trains, restart from a file, failed save rolls back,
clashing data refused, SQL-injection text stored as text, CHECK constraints, newer schema refused, unknown
strategy, audit log, auto-saver, failing database); all earlier tests still pass.

## Stage 10: interface redesign, system testing and software engineering documents (done)

| Item | Result |
|---|---|
| Interface | Navy sidebar with icons and logo, cards, dashboard tiles, colour-coded event log, status badges, save indicator, live light/dark switch; window fits the screen |
| Defects found and fixed | Dark mode switch left light colours (theme values now look-and-feel resources); refused database file stayed locked; window taller than a 864 px screen |
| New tests | 10 system tests on the real window and database, 1 performance test, 34 boundary-value cases |
| Coverage | JaCoCo: logic packages 95.9% of lines, whole program 77% |
| Documents | `docs/se/`: SRS, requirement analysis, architecture, test plan, QA, project plan, maintenance, contributions; 9 new UML diagrams |
| Total | 224 automated tests, all passing |
