# Class-by-Class Explanation

Packages, in the order to read them: `exception` -> `model` -> `strategy` -> `observer` -> `service` -> `ui`.
Every class also has a header comment in the source with the same information.

---

## Package `exception`

| Class | Purpose |
|---|---|
| `RailSyncException` | Unchecked base of every RailSync error, so the GUI can catch all of them in one place and show the friendly message. Ordinary outcomes ("no free platform") are not exceptions. |
| `InvalidTrainException` | Bad train data: blank number, departure before arrival, stop shorter than the type's minimum, priority out of 1-5, invalid delay. |
| `InvalidPlatformException` | Bad platform data or a refused platform change: length out of range, no categories, duplicate number, removing or shrinking a platform that has bookings. |
| `PlatformUnavailableException` | `Schedule.reserve` was asked to put a train on a platform it cannot use (failed, wrong category, too short). |
| `SchedulingConflictException` | `Schedule.reserve` was asked to double-book (overlap incl. buffer), to book a train twice, or to start before arrival. |

---

## Package `model`

### `Train` (abstract)
- **Represents:** a train that wants a platform. OS analogy: a process; its dwell time is its burst time.
- **Attributes:** `id`, `number`, `name`, `scheduledArrival`, `scheduledDeparture` (LocalDateTime), `basePriority`
  (1-5), `carriageCount`, `delayMinutes`, `status` (TrainStatus), `assignedPlatform` (association).
- **Constructor:** `protected Train(...)` validates every value, including the subclass's minimum dwell.
- **Abstract methods:** `getType()`, `getPriority()`, `getRequiredPlatformLength()`, `getMinimumDwellMinutes()`.
- **Concrete, overridable:** `getClearanceBufferMinutes(base)` (returns base).
- **Key methods:** `getIncompatibilityReason(platform)` (all compatibility rules in one place: operational,
  category, length; returns null if OK), `canUsePlatform`, `updateTimes`, `applyDelay`, `reduceDelay`,
  `getExpectedArrival/Departure` (timetable + delay), `getDwellTime`, `markUnallocated`, `markScheduled`.
  Package-private `assignPlatform`/`clearPlatform`: only `Schedule` may call them.
- **Relationships:** parent of the three train kinds; refers to one `Platform`; booked by `Schedule`.
- **Why needed:** shared data and validation are written once; behaviour differences go to subclasses.

### `ExpressTrain`, `PassengerTrain`, `FreightTrain`
- **Represent:** the three kinds of train. Constructors just call `super(...)`.
- **Override:** priority bonus (+3 / +1 / 0), required length (25 m coaches + 20 m engine / 20 m coaches /
  15 m wagons + 2 x 20 m locos), minimum dwell (5 / 3 / 20 min). `FreightTrain` also overrides
  `getClearanceBufferMinutes` (+5 min).
- **Why needed:** real behaviour differences without `switch (type)` anywhere (see OOP_CONCEPTS section 4).

### `Platform`
- **Represents:** one platform (a CPU in the analogy).
- **Attributes:** `id`, `number`, `lengthMeters`, `status` (OPERATIONAL/FAILED), `supportedTypes` (HashSet<TrainType>).
- **Constructors:** 4-argument (status OPERATIONAL) chains to the 5-argument one, which validates.
- **Methods:** `supports(type)`, `isOperational`, `markFailed`, `markOperational`, validated `setLengthMeters`,
  `setSupportedTypes` (defensive copy), read-only `getSupportedTypes`.
- **Relationships:** aggregated by `Station`; referred to by trains and reservations.

### `Station`
- **Represents:** the station: a name and its platforms (aggregation).
- **Attributes:** `name`, `platforms` (ArrayList, always sorted by number).
- **Methods:** `addPlatform` (refuses duplicate id/number, inserts in number order), `removePlatform`,
  `findPlatformById`, read-only `getPlatforms`, `getPlatformCount`.
- **Why needed:** ascending order makes First-Fit deterministic and easy to trace.

### `Schedule`
- **Represents:** all platform bookings, and the rules about time.
- **Attributes:** `reservations` (ArrayList), `reservationByTrainId` (HashMap), `clearanceBufferMinutes` (default 5, 0-60).
- **Constructors:** `Schedule()` chains to `Schedule(5)`.
- **Key methods:**
  - `reserve(train, platform, start)`: checks there is no second booking, the start is not before arrival,
    compatibility, and the overlap rule; then creates the reservation and links the train.
  - `release(train)`: deletes the booking (composition) and unlinks the train.
  - `overlaps(a, b)`: half-open interval test.
  - `requiredGapMinutes(t1, t2)`: the larger of the two trains' buffers.
  - `findConflict` / `findAllConflicts`: both ends stretched by the gap, then the overlap test.
  - `earliestFreeStart`: jumps past clashing bookings.
  - `getReservationsOn(platform)` (sorted), `hasReservations`, `findReservation`.
- **Relationships:** composition of `PlatformReservation`; used by the allocator, managers and commands.

### `PlatformReservation`
- **Represents:** one booking `[start, end)` of a train on a platform. Immutable; package-private constructor (only Schedule creates it).

### `ReservationChange`
- **Represents:** a booking's before/after (old arrival, platform, start, end; new platform, start, end or null).
  Used by delay and breakdown reports, events and the ghost animation.

### `TrainMovement`
- **Represents:** where a train is at any time, from its booking: reaches outer signal -> (waits) -> home signal
  -> platform -> leaves -> clears exit (approach 4 min, exit 3 min).
- **Methods:** `phaseAt(time)` (enum Phase), `progressAt(time)` (0..1 within the phase), the key-time getters, `getHoldMinutes`.
- **Used by:** the time-distance diagram and the yard view (read-only).

### `TrainFactory`
- **Purpose:** creates the right Train subclass from a `TrainType` (Factory pattern). The only `switch` on train type.

### Enums `TrainType`, `TrainStatus`, `PlatformStatus`
- `TrainType` (EXPRESS, PASSENGER, FREIGHT): the label platforms store. `TrainStatus` (SCHEDULED, ALLOCATED,
  UNALLOCATED). `PlatformStatus` (OPERATIONAL, FAILED).

---

## Package `strategy`

### `PlatformAllocationStrategy` (interface)
- `getName()`, `getDescription()`, `orderReadyQueue(readyQueue, now)`: the scheduling policy that decides the order of service.

### `QueueOrderingStrategy` (abstract)
- `orderReadyQueue` (final): copies the queue and sorts it by `compareByPolicy` (abstract), then by earlier arrival, then by train number (deterministic).

### `FcfsStrategy`, `PriorityStrategy`, `ShortestDwellFirstStrategy`
- One comparison each: earlier expected arrival / higher `getPriority()` / shorter dwell.

### `PriorityAgingStrategy`
- **Attribute:** `minutesPerPriorityPoint` (default 4, 1-120, validated).
- **Method:** `getEffectivePriority(train, now)` = priority + minutes waited / N. Prevents starvation.

### `StrategyCatalog`
- `createAll()`: one instance of each strategy, used by the comparison arena and the yard view.

---

## Package `observer`

| Class | Purpose |
|---|---|
| `ScheduleObserver` | Interface with one method `onScheduleEvent(event)`. |
| `ScheduleEvent` | Immutable: type, message, list of `ReservationChange` (for animation). |
| `ScheduleEventType` | TRAINS_CHANGED, PLATFORMS_CHANGED, ALLOCATION, DELAY, REASSIGNMENT, CONFLICT, BREAKDOWN, SCHEDULE_RESET, SETTINGS_CHANGED, UNDO. |
| `ScheduleEventManager` | Keeps the observers (ArrayList) and `publish`es to all of them (iterating over a copy, so unsubscribing during a notification is safe). |

---

## Package `service`

### `SchedulingService` (facade, the class the GUI talks to)
- **Attributes:** station, schedule, events, the owned `TrainManager`, `PlatformManager`, `PlatformAllocator`,
  `DelayManager`, `MetricsCalculator`, `CommandHistory`; the current `strategy`, `conflictsDetected`, `lastResults`.
- **Constructor:** receives `Station`, `Schedule`, a `PlatformAllocationStrategy` (injected) and the event manager.
- **Methods (each ends by publishing an event):**
  - Settings: `setStrategy`, `setClearanceBufferMinutes`, `setMaxWaitMinutes`, `setPreemptionEnabled`.
  - Data: `addTrain`, `removeTrain`, `updateTrain`, `addPlatform`, `removePlatform`, `updatePlatform`, `repairPlatform`.
  - Allocation: `allocatePendingTrains`, `allocateTrain` (both undoable), `releaseTrain`, `resetAllocations`,
    `reallocateFrom(now)`.
  - Disruptions: `delayTrain` (undoable), `reportBreakdown`.
  - Scenario: `clearEverything`, `loadScenario`.
  - Undo: `undoLast`, `canUndo`, `getUndoDescription`.
  - Metrics: `calculateMetrics`.

### `PlatformAllocator` (the scheduler)
- **Attributes:** station, schedule, `maxWaitMinutes` (default 20), `preemptionEnabled`.
- **`allocateAll(trains, strategy[, notBefore])`** step by step:
  1. Reject trains that fit no platform (with reasons).
  2. Sort the rest by arrival.
  3. Event loop: arrivals join the ready queue; the strategy orders it; each train takes the first free platform
     (or preempts safely); trains that can no longer start within the maximum wait are refused; the clock jumps to
     the next event.
- **Also:** `allocateSingle`, `findFirstFreePlatform` (First-Fit), `explainNoCompatiblePlatform`, private `tryPreemption` (with exact rollback).

### `DelayManager`
- **`delayTrain(train, minutes)`:** still valid -> same platform -> other platform at the same time -> ripple
  (`planRipple`, only if no pushed train waits over the maximum) -> wait elsewhere -> failure with the reason.
- **`handleBreakdown(platform)`:** marks it failed; for each booking, same time elsewhere, else `allocateSingle`.

### `TrainManager` / `PlatformManager`
- Train register (ArrayList plus a HashMap by number; unique ids and numbers; no removal while booked; `nextTrainId`).
- Platform rules (no removal while booked; an update may not break a booking; repair; suggested next number and id).

### Results and reports
| Class | Purpose |
|---|---|
| `AllocationResult` | Immutable outcome for one train: allocated or not, platform, start, conflict flag, message. |
| `DelayReport` | Outcome enum (NOT_ALLOCATED, STILL_VALID, KEPT_PLATFORM, REASSIGNED, RIPPLE, FAILED), message, changes, cascade. |
| `BreakdownReport` | Per-train outcomes and changes; moved and stranded counts. |
| `ScheduleMetrics` | Immutable numbers of one schedule (counts, averages, utilization per platform, sweep minimum, platforms used, window). |
| `MetricsCalculator` | The formulas: waiting, turnaround, utilization, `minimumPlatformsNeeded` (sweep line), `platformsUsed`, `countConflicts`. |
| `ReportGenerator` | Text only: summary report, comparison table, occupancy listing (text Gantt chart). |

### Scenarios
| Class | Purpose |
|---|---|
| `Scenario` | Platforms and trains without bookings; `copy()` makes a deep copy (via TrainFactory); `fromLive(service)`. |
| `ScenarioGenerator` | Seeded random scenarios (10-500 trains); the same seed always gives the same scenario. |
| `ScenarioCsv` | Save/load CSV; checks every line (errors name the line) and uniqueness before anything is loaded. |
| `DemoScenarioLoader` | The hand-designed 6-platform / 12-train demo; its promises are proven by `DemoScenarioTest`. |
| `StrategyArena` | Runs every strategy on its own copy and returns their metrics (`Entry`). |

### Undo (Command pattern)
| Class | Purpose |
|---|---|
| `ScheduleCommand` | Interface: `execute`, `undo`, `getDescription`. |
| `SnapshotCommand` | Abstract: saves bookings and statuses before execute; `undo()` restores them (hook `reverseOwnChange`). |
| `AllocateCommand` / `DelayCommand` | Wrap allocation / delay; DelayCommand also removes the delay minutes on undo. |
| `CommandHistory` | Stack of up to 20 commands. |

---

## Package `ui` (Swing; no business rules)

| Class | Shows / does | Calls | Observes |
|---|---|---|---|
| `Main` (in `com.railsync`) | Wires the objects together, opens the window | constructors | - |
| `MainFrame` | Toolbar (Undo, CSV, demo, theme), sidebar, card area, status bar; owns the `SimulationClock` | `SchedulingService`, `ScenarioCsv` | events (Undo button) |
| `RefreshablePanel` | Abstract base of every screen: subscribe + `refresh()` | - | all events |
| `DashboardPanel` | Number cards, strategy, guide, live event log | `calculateMetrics` | all events |
| `TrainPanel` (+ `TrainTableModel`, `TrainFormDialog`) | Train table, search, add/edit/remove/release/view | service train methods, `TrainFactory` | all events |
| `PlatformPanel` (+ `PlatformTableModel`, `PlatformFormDialog`) | Platform table, add/edit/remove/repair, compatibility check | service platform methods | all events |
| `AllocationPanel` | Strategy and settings, allocate all/one, reset, results | service allocation and settings | all events |
| `SchedulePanel` (+ `ReservationTableModel`) | Bookings table with filter; trains without a platform | read only | all events |
| `TimeDistancePanel` | `TimeDistanceDiagram` + `ClockControls` | clock | events |
| `TimeDistanceDiagram` | The time-distance / Gantt drawing, ghosts and 500 ms slide | read only | events, clock |
| `DisruptionPanel` | Delay / breakdown / scripted demo, report, own diagram | `delayTrain`, `reportBreakdown` | events |
| `YardPanel` / `YardView` | Top-down yard, ready queue, switch strategy from now | `setStrategy`, `reallocateFrom` | events, clock |
| `ComparisonPanel` | Arena table, seed and train count, load generated | `StrategyArena`, `ScenarioGenerator` | - (on demand) |
| `ReportsPanel` | Full text report | `ReportGenerator` | all events |
| `StatusBar` | Last event and current settings | read only | all events |
| `SimulationClock` | Simulated time via a Swing Timer (100 ms ticks, 1x/10x/60x) | - | - |
| `ClockControls` | Play/Pause, speed, jump to start (shared by two screens) | clock | clock |
| `StrategyListRenderer`, `Ui` | Strategy names in drop-downs; time format/parse, colours, dialogs | - | - |

`ConsoleDemo` (in `com.railsync`) prints the behaviour of stages 1-4 without the GUI.
