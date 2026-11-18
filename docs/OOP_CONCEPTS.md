# OOP Concepts in RailSync

Every concept below points at real code. The same tags appear as comments in the source, so you can jump from
this page to the code and back. To list every tag, search the source for `// [` (for example `// [POLYMORPHISM]`).

Paths are relative to `src/main/java/com/railsync/`. Line numbers are from the final build.

| Tag in the code | Concept |
|---|---|
| `[ENCAPSULATION]` | Private state, changed only by validated methods |
| `[ABSTRACTION]` | Depending on what something does, not how |
| `[INHERITANCE]` | A subclass reusing and extending a parent |
| `[POLYMORPHISM]` | The real object's class decides which code runs |
| `[ASSOCIATION]` / `[AGGREGATION]` / `[COMPOSITION]` | The three strengths of "has a" |
| `[STRATEGY]` / `[OBSERVER]` / `[COMMAND]` | Design patterns (see DESIGN_PATTERNS.md) |
| `[SOLID-S]` `[SOLID-O]` `[SOLID-L]` `[SOLID-I]` `[SOLID-D]` | The five SOLID principles |

---

## 1. Classes and objects

A **class** is a blueprint; an **object** is one thing built from it, with its own values.

- `Platform` is a class; PF-1 (520 m, express + passenger) and PF-5 (750 m, freight) are two Platform objects.
- `DemoScenarioLoader.load()` creates 6 Platform objects and 12 Train objects; each keeps its own number, times and status.
- Objects talk by calling methods on each other: `Schedule.reserve(train, platform, start)` asks the train
  `getIncompatibilityReason(platform)`, and the train asks the platform `supports(type)` and `getLengthMeters()`.

## 2. Encapsulation `[ENCAPSULATION]`

All fields are `private`. State changes only through methods that check the new value first.

| Where | What is protected |
|---|---|
| `model/Train.java:46` | Every field private; times change only through `updateTimes()` (line 205), which validates order and minimum dwell |
| `model/Train.java:220` / `:237` | `applyDelay()` and `reduceDelay()`: delay never negative, never over 24 h |
| `model/Train.java:299` | `assignPlatform()` is **package-private**: only `Schedule` (same package) can link a train to a platform, so the train and its booking can never disagree |
| `model/Platform.java:111` / `:167` | The category set is copied in and handed out read-only, so no caller can change it behind the platform's back |
| `model/PlatformReservation` | Immutable (all fields final, no setters); to move a train, Schedule deletes and creates |
| `model/Schedule.java` `getAllReservations()` | Returns `Collections.unmodifiableList(...)` |
| `service/AllocationResult`, `DelayReport`, `ScheduleMetrics`, `ReservationChange` | Immutable result objects; private constructors / static factories |

Why it matters: a train can never have a departure before its arrival, a platform can never be shortened below a
booked train (`PlatformManager.updatePlatform`), and a booking can never exist without the train knowing it.

## 3. Abstraction `[ABSTRACTION]`

Abstraction means using something through **what it does**, without knowing **how**.

- `Train` is `abstract` (`model/Train.java:35`). Its abstract methods (`getType`, `getPriority`,
  `getRequiredPlatformLength`, `getMinimumDwellMinutes`, from line 103) state what every train can answer.
- `PlatformAllocationStrategy` (`strategy/PlatformAllocationStrategy.java:35`) is the scheduling policy as an
  interface. `PlatformAllocator` and `SchedulingService` only know this interface (`service/SchedulingService.java:278`).
- `ScheduleObserver` lets the services notify screens without knowing they are Swing panels.

## 4. Inheritance `[INHERITANCE]`

| Parent | Children | What is inherited / overridden |
|---|---|---|
| `Train` (abstract) | `ExpressTrain`, `PassengerTrain`, `FreightTrain` | All data, validation, `canUsePlatform`, delays. Each overrides priority, length, minimum dwell; Freight also overrides the concrete `getClearanceBufferMinutes` |
| `QueueOrderingStrategy` (abstract) | `FcfsStrategy`, `PriorityStrategy`, `ShortestDwellFirstStrategy`, `PriorityAgingStrategy` | Sorting and tie-break; each overrides `compareByPolicy` |
| `SnapshotCommand` (abstract) | `AllocateCommand`, `DelayCommand` | Snapshot and `undo()`; Delay overrides the hook `reverseOwnChange` |
| `RailSyncException` | 4 specific exceptions | GUI catches all with one `catch` |
| `RefreshablePanel` (abstract, extends `JPanel`) | 8 screens | Subscribe-and-refresh; each overrides `refresh()` |
| `AbstractTableModel` (Swing) | `TrainTableModel`, `PlatformTableModel`, `ReservationTableModel` | `JTable` calls our overridden `getValueAt` |

Constructor chaining through inheritance: `ExpressTrain` calls `super(...)` (`model/ExpressTrain.java:44`), so all
validation lives once in `Train`.

### Why subclasses and not just an enum field?

`TrainType` exists, but only as a **label** a platform can store ("accepts EXPRESS, PASSENGER"). The **behaviour**
differs in four methods with different formulas:

| | Express | Passenger | Freight |
|---|---|---|---|
| `getPriority()` | base + 3 | base + 1 | base |
| `getRequiredPlatformLength()` | coaches x 25 + 20 (engine) | coaches x 20 (EMU) | wagons x 15 + 2 x 20 (locos) |
| `getMinimumDwellMinutes()` | 5 | 3 | 20 |
| `getClearanceBufferMinutes(b)` | b (inherited) | b (inherited) | b + 5 (overridden) |

With only an enum, every one of these would be a `switch (type)` repeated in several classes. Adding a fourth kind
would mean editing all of them (breaking Open/Closed). With subclasses, each rule sits in one place and the callers
never change.

## 5. Polymorphism `[POLYMORPHISM]`

One method call, different code at runtime depending on the real object. **Exact dynamic-dispatch lines:**

| Call site | Variable's declared type | Code that actually runs |
|---|---|---|
| `model/Train.java:167` `getType()` and `:173` `getRequiredPlatformLength()` inside `getIncompatibilityReason` | `Train` (this) | ExpressTrain / PassengerTrain / FreightTrain version |
| `model/Train.java:419` `getMinimumDwellMinutes()` inside `validateTimes` | `Train` | the subclass's minimum |
| `model/Schedule.java:162-163` `getClearanceBufferMinutes(...)` | `Train` | FreightTrain adds 5; others inherit |
| `service/MetricsCalculator.java:194` `getClearanceBufferMinutes(...)` (sweep line) | `Train` | as above |
| `service/PlatformAllocator.java:254` `strategy.orderReadyQueue(...)` | `PlatformAllocationStrategy` | FCFS / Priority / SJF / Aging |
| `strategy/QueueOrderingStrategy.java:60` `compareByPolicy(...)` | `QueueOrderingStrategy` (this) | the concrete strategy's rule |
| `strategy/PriorityStrategy.java:49`, `PriorityAgingStrategy.java:77`, `service/PlatformAllocator.java:299` `getPriority()` | `Train` | base + subclass bonus |
| `service/StrategyArena.java:80` `allocateAll(..., strategy)` in a loop over all strategies | `PlatformAllocationStrategy` | each strategy in turn |
| `service/SchedulingService.java:434` `command.undo()` | `ScheduleCommand` | AllocateCommand / DelayCommand |
| `service/SnapshotCommand.java:82` `reverseOwnChange()` | `SnapshotCommand` (this) | DelayCommand removes the delay; AllocateCommand does nothing |
| `observer/ScheduleEventManager.java:56` `observer.onScheduleEvent(event)` | `ScheduleObserver` | each screen's own reaction |
| `ui/RefreshablePanel.java:52` `refresh()` | `RefreshablePanel` (this) | each screen's own refresh |

The `TrainTest.polymorphicListDispatchesToRealSubclass` test shows this with a `List<Train>` holding different subclasses.

## 6. Association, aggregation, composition

| Relationship | Where | Meaning in RailSync |
|---|---|---|
| **Association** `[ASSOCIATION]` | `Train.assignedPlatform` (`model/Train.java:59`) | A train *uses* a platform. Neither owns the other; both exist independently. |
| **Aggregation** `[AGGREGATION]` | `Station.platforms` (`model/Station.java:34`) | A station *holds* platforms that are created outside and passed in (`addPlatform`). Removing one from the station does not destroy it (test `removedPlatformStillExists`). The arena puts copied platforms into a different station. |
| **Composition** `[COMPOSITION]` | `Schedule.reservations` (`model/Schedule.java:53`) | Schedule **creates** (`:116`, via the package-private constructor at `PlatformReservation.java:37`) and **destroys** (`release()`, `:277`) its reservations. No other class can create one; a reservation has no meaning without its schedule. |
| Composition (service level) | `SchedulingService` constructor (`:69`) | The service creates and owns its TrainManager, PlatformManager, PlatformAllocator, DelayManager. |
| Composition (persistence) | `SqliteStationRepository` | The repository creates and owns one DAO per table; they exist only inside it. |
| Association (persistence) | `AutoSaver` → `StationRepository`, `AuditLog` | The auto-saver uses repositories it is given; it does not own or create them. |

## 7. Interfaces vs abstract classes

| | Interface | Abstract class |
|---|---|---|
| Holds | Method contracts only | Shared fields and code, plus abstract methods |
| A class can have | Many | One parent |
| RailSync examples | `PlatformAllocationStrategy`, `ScheduleObserver`, `ScheduleCommand`, `SimulationClock.Listener`, `StationRepository`, `AuditLog` | `Train`, `QueueOrderingStrategy`, `SnapshotCommand`, `RefreshablePanel` |

RailSync uses them together on purpose. `PlatformAllocationStrategy` says *what* a strategy does (any class may be
one). `QueueOrderingStrategy implements` it and adds the *shared how* (copy, sort, tie-break), so the four concrete
strategies each write one comparison. The same pairing appears in `ScheduleCommand`/`SnapshotCommand` and
`ScheduleObserver`/`RefreshablePanel`.

## 8. Constructors

- **Validation in constructors**: `Train(...)` (`model/Train.java:73`) checks text, carriage count, priority and
  times; `Platform(...)` checks id, number, length, categories. An invalid object can never exist.
- **Chaining with `this(...)`**: `Platform` 4-argument -> 5-argument (`model/Platform.java:56`, default OPERATIONAL);
  `Schedule()` -> `Schedule(5)` (`model/Schedule.java:59`); `PriorityAgingStrategy()` -> `(4)` (`:42`);
  `ReservationChange(before, after)` -> 3-argument version; `ScheduleEvent(type, msg)` -> with empty changes.
- **Chaining with `super(...)`**: every Train subclass and command class.
- **Private constructors + static factories**: `AllocationResult.success/failure`, so a half-filled result is impossible.
- **No pointless overloads**: each extra constructor supplies a sensible default that is really used.
- Viva trap answered in the code: `Train`'s constructor calls the overridable `getMinimumDwellMinutes()`. That is safe
  only because every subclass returns a constant; the comment at `model/Train.java:87` explains why.

## 9. Exceptions

- `RailSyncException` (`exception/RailSyncException.java:22`) is **unchecked**; four subclasses name the cause:
  `InvalidTrainException`, `InvalidPlatformException`, `PlatformUnavailableException`, `SchedulingConflictException`.
- Thrown only when a **rule is broken**: for example `Schedule.reserve` throws `PlatformUnavailableException` (`:103`)
  or `SchedulingConflictException` (`:109`) if asked to break compatibility or double-book.
- **Not used for ordinary outcomes**: "no platform free" is a normal `AllocationResult.failure(...)`, and
  `findConflict` returns `null`; both are handled with plain `if` checks.
- The GUI catches `RailSyncException` and shows its friendly message (for example `ui/TrainFormDialog.java:126`).
- `ScenarioCsv` turns file and parse errors into `RailSyncException("Line 3: ...")`.

## 10. Collections

| Collection | Where | Why this one |
|---|---|---|
| `ArrayList` | `Station.platforms`, `Schedule.reservations`, `TrainManager.trains`, `CommandHistory`, observer list | Ordered (platform number, insertion), index access, small sizes |
| `HashMap` | `Schedule.reservationByTrainId` (`model/Schedule.java:54`), `TrainManager.trainsByNumber` (`service/TrainManager.java:34`), `SnapshotCommand.savedStatuses` | Find by key in one step ("where is train X?", "search by number") |
| `LinkedHashMap` | `ScheduleMetrics` utilization per platform | A HashMap that keeps platform order for reports |
| `HashSet` | `Platform.supportedTypes` (`model/Platform.java:42`), `MetricsCalculator.platformsUsed` (`:223`), CSV duplicate check | Only "contains?" matters; no duplicates; order irrelevant |

## 11. SOLID principles

| Principle | Definition | Where (tag in code) | Why it helps |
|---|---|---|---|
| **S** Single responsibility | A class has one reason to change | `MetricsCalculator` only calculates (`[SOLID-S]` at `service/MetricsCalculator.java:55`), `ReportGenerator` only formats (`service/ReportGenerator.java:34`); `Schedule` stores bookings, `PlatformAllocator` decides them, `DelayManager` handles disruptions | Changing a report layout never risks the maths, and the reverse |
| **O** Open/closed | Open for extension, closed for modification | `[SOLID-O]` at `strategy/StrategyCatalog.java:25`; new observers just subscribe; new train kinds are new subclasses | A fifth strategy needs one new class; `PlatformAllocator` is untouched |
| **L** Liskov substitution | A subclass can stand in for its parent | `[SOLID-L]` at `service/PlatformAllocator.java:253` (any strategy) and `model/Schedule.java:87` (any Train); Freight's buffer override only adds time, never breaks the rule | The allocator and schedule work with any train or strategy without special cases |
| **I** Interface segregation | Small, focused interfaces | `[SOLID-I]` at `observer/ScheduleObserver.java:19`: one method; `PlatformAllocationStrategy` has three; `SimulationClock.Listener` has one | A screen implements only what it needs |
| **D** Dependency inversion | Depend on abstractions; inject them | `[SOLID-D]` at `service/SchedulingService.java:79` (strategy injected through the constructor and `setStrategy`) and `Main.java:46` (the only place a concrete strategy is chosen) | Strategies swap at runtime; tests and the arena inject copies and other strategies |

## 12. Where to see each concept running

- Console: `run-demo.cmd` prints polymorphism tables, composition, observer events and more.
- Tests: `TrainTest` (inheritance and polymorphism), `ScheduleTest` and `OverlapTest` (composition and encapsulation),
  `StrategyOrderTest` (strategy), `ScheduleEventManagerTest` (observer), `CsvAndUndoTest` (command).

## 13. Object-oriented design in the storage layer (SQLite)

The `persistence` package applies the same principles:

- **Abstraction / SOLID-D:** the rest of the program depends on the interfaces `StationRepository` and `AuditLog`;
  only `Main.build()` names the SQLite classes.
- **Polymorphism:** `AutoSaver` calls `repository.save(...)` and `auditLog.record(...)` on interface references; the
  SQLite versions run (a test or another database could supply different ones).
- **Encapsulation:** the JDBC `Connection` is private to `Database`; DAOs are package-private, so no class outside
  `persistence` can run SQL. `StationSnapshot` and `AuditEntry` are immutable.
- **SOLID-S:** one DAO per table; `Database` only connects and migrates; the repository only coordinates.
- **SOLID-O:** saving was added as one more observer, without changing the scheduling classes.
- **Factory reuse:** `TrainDao` rebuilds each saved train through `TrainFactory`, so the right subclass (and its
  behaviour) comes back.
