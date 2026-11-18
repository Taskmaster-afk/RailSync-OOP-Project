# Software Architecture and Design

This document describes how RailSync is built: its architectural style, its modules and their responsibilities,
the main design decisions with their reasons, and the data and interface design. The diagrams referred to are in
[UML_DIAGRAMS.md](../UML_DIAGRAMS.md).

---

## 1. Architectural style

RailSync combines three well-known styles:

1. **Layered architecture.** Presentation (`ui`) → application (`service`) → domain (`model`), with
   `persistence` beside the application layer. A layer only uses the layers below it
   ([package diagram](../UML_DIAGRAMS.md#10-package-diagram-dependencies-point-downward-only)).
2. **Event-driven updates (publish/subscribe).** Every change is published once as a `ScheduleEvent`. The screens,
   the status bar, the Undo button and the database auto-saver all subscribe. Nothing calls the screens directly.
3. **Facade.** The interface layer talks to exactly one class, `SchedulingService`, which coordinates the
   specialised services.

```
 ┌──────────────────────── Presentation (ui) ─────────────────────────┐
 │  MainFrame · 10 screens · diagrams · dialogs · Theme · Card        │
 └───────────────┬───────────────────────────────────▲────────────────┘
                 │ calls                             │ ScheduleEvent (observer)
 ┌───────────────▼──── Application (service) ────────┴───────┐  ┌──── persistence ─────┐
 │ SchedulingService (facade)                                │  │ AutoSaver (observer) │
 │ PlatformAllocator · DelayManager · MetricsCalculator      │  │ StationRepository ◄──┼── interfaces
 │ TrainManager · PlatformManager · StrategyArena · commands │  │ AuditLog          ◄──┤
 └──────┬──────────────────────────────┬─────────────────────┘  │ Sqlite* + DAOs       │
        │ PlatformAllocationStrategy   │                         │ Database (JDBC)      │
 ┌──────▼──── strategy ─────┐   ┌──────▼──────── model ───────┐  └──────────┬──────────┘
 │ FCFS · Priority · SJF ·  │   │ Train (+3 kinds) · Platform │             ▼
 │ Aging                    │──►│ Station · Schedule · ...   │       railsync.db (SQLite)
 └──────────────────────────┘   └─────────────────────────────┘
```

The full [component diagram](../UML_DIAGRAMS.md#9-component-diagram) and the
[deployment diagram](../UML_DIAGRAMS.md#11-deployment-diagram) show the same structure in UML.

---

## 2. Modules and their responsibilities

| Module (package) | Classes | Lines | Responsibility | Must not |
|---|---|---|---|---|
| `model` | 14 | 1,841 | Domain objects and their rules: the train hierarchy, platforms, the station, the schedule with the overlap rule, train movement phases. | Know about screens, files or the database. |
| `strategy` | 7 | 428 | The allocation policies behind one interface. | Change bookings (they only order the queue). |
| `observer` | 4 | 187 | Change notifications: event types, events, the event manager. | Know who listens. |
| `service` | 22 | 3,639 | Use cases: allocation, delays, breakdowns, metrics, reports, comparison, scenarios, CSV, undo; the facade. | Import Swing or SQL. |
| `persistence` | 11 | 1,001 | Storage in SQLite: database and schema, one DAO per table, repositories, audit log, auto-saver. | Contain scheduling rules. |
| `ui` | 27 | 4,780 | Swing screens, diagrams, dialogs, table models, theme. | Contain business rules (they call the service). |
| `exception` | 5 | 119 | Friendly, typed errors for broken rules. | Be used for ordinary outcomes. |
| root (`Main`, `ConsoleDemo`) | 2 | 581 | Composition root (wires everything together) and the console demo. | |
| **Total** | **92** | **12,576** | | |

### 2.1 Key components

| Component | Responsibility | Collaborates with |
|---|---|---|
| `SchedulingService` | Single entry point for every user action; wraps allocations and delays in undoable commands; publishes events; takes and restores snapshots. | All services, `ScheduleEventManager`, `StationSnapshot` |
| `Schedule` | Stores bookings; the **only** place a booking can be created (`reserve`) or removed (`release`); enforces the overlap and compatibility rules. | `Train`, `Platform`, `PlatformReservation` |
| `PlatformAllocator` | Event-driven dispatcher: ready queue, strategy ordering, First-Fit, maximum wait, safe preemption. | `Schedule`, `Station`, `PlatformAllocationStrategy` |
| `DelayManager` | Delay resolution in a fixed order (least disruptive first) and breakdown reassignment. | `Schedule`, `PlatformAllocator` |
| `MetricsCalculator` / `ReportGenerator` | Calculate metrics (including the sweep line) / format them as text. | `Schedule`, `Station` |
| `StrategyArena` / `ScenarioGenerator` | Run all strategies on deep copies / generate seeded scenarios. | `Scenario`, `PlatformAllocator` |
| `ScenarioCsv` | Read and write CSV with full validation before anything is replaced. | `Scenario`, `TrainFactory` |
| `AllocateCommand`, `DelayCommand`, `CommandHistory` | Undo by snapshot. | `Schedule` |
| `AutoSaver` | Observer that writes the audit entry and the whole session after every change. | `StationRepository`, `AuditLog` |
| `SqliteStationRepository` / `SqliteAuditLog` | Save and load the session in one transaction / append and read the audit log. | `Database`, DAOs |
| `Database` | Opens the file, enables foreign keys, creates and upgrades the schema. | JDBC driver |
| `MainFrame` and screens | Show data and collect input; every screen extends `RefreshablePanel` and refreshes on events. | `SchedulingService` only |

---

## 3. Design decisions and their justification

Each decision is recorded with the alternatives considered (a lightweight "architecture decision record").

| # | Decision | Alternatives considered | Why we chose it |
|---|---|---|---|
| D-1 | **Layered architecture with a service facade** | Screens calling the model directly | Business rules stay testable without a screen (180 of the 190 tests run without a window); the GUI can change freely. |
| D-2 | **Observer for every change** | Screens refreshing each other; polling | One change updates ten screens, the status bar, Undo and the database with no coupling between them; adding persistence needed no change to the scheduling code. |
| D-3 | **Strategy pattern for allocation policies** | `if/else` on a policy name | Policies are swappable at runtime, comparable in the arena with one loop, and new ones need no change to the allocator (Open/Closed). |
| D-4 | **Event-driven allocator with a ready queue** | Sort all trains once | Matches the OS analogy; aging needs a real "time waited"; trains can wait instead of being refused at once. |
| D-5 | **`Schedule.reserve()` as the single gate for bookings** | Each algorithm checks clashes itself | One place enforces the overlap, buffer and compatibility rules, so even a faulty strategy cannot double-book (NFR-R1). |
| D-6 | **Half-open intervals plus the larger buffer** | Closed intervals; one fixed gap | Back-to-back bookings are unambiguous; freight trains always get their extra clearance. |
| D-7 | **Embedded SQLite through JDBC** | Plain files; a database server (MySQL); an ORM such as Hibernate | No installation for users (NFR-PT1), real SQL with transactions and constraints, a single file to back up. An ORM would hide the SQL we want to show and add many dependencies. |
| D-8 | **Repository and DAO layers behind interfaces** | SQL inside the services | The services stay free of SQL; the storage can be replaced or faked in tests (Dependency Inversion). |
| D-9 | **Save the whole session after each change, in one transaction** | Save only the changed rows | Simple and obviously correct; a 500-train session saves in about 0.3 s, so the cost is invisible. A crash part-way keeps the previous save. |
| D-10 | **Restore by re-checking every saved booking** | Trust the database | Damaged or hand-edited data cannot produce an impossible plan (NFR-R4). |
| D-11 | **Undo by snapshot (Command pattern)** | Reversing each step | Ripples and preemption move several trains; restoring a snapshot reverses them all in one step and re-checks every rule. |
| D-12 | **Unchecked exceptions for broken rules; return values for ordinary outcomes** | Checked exceptions everywhere | Short signatures, one central catch in the GUI; "no platform free" is not an error, so it is a result with a reason. |
| D-13 | **No threads; Swing Timers** | Background threads for animation | Removes a whole class of concurrency bugs; all work is fast enough for the event thread. |
| D-14 | **Composition root in `Main.build()`** | Objects creating their own dependencies | Every dependency is injected; the system tests build the real application on a temporary database. |

---

## 4. Detailed design

### 4.1 Object-oriented design

The class model, with its inheritance hierarchy (abstract `Train` and three subclasses), the three relationship
strengths (association `Train → Platform`, aggregation `Station ◇ Platform`, composition
`Schedule ◆ PlatformReservation`) and the four design patterns, is documented in
[OOP_CONCEPTS.md](../OOP_CONCEPTS.md), [DESIGN_PATTERNS.md](../DESIGN_PATTERNS.md) and the class diagrams in
[UML_DIAGRAMS.md](../UML_DIAGRAMS.md).

### 4.2 Behavioural design

- Allocation: [sequence diagram](../UML_DIAGRAMS.md#3-sequence-diagram-allocation) and
  [activity diagram](../UML_DIAGRAMS.md#5-activity-diagram-allocation-of-a-train).
- Delay and ripple: [sequence diagram](../UML_DIAGRAMS.md#4-sequence-diagram-delay-and-reassignment-ripple).
- Saving: [sequence diagram](../UML_DIAGRAMS.md#14-sequence-diagram-a-change-is-saved-automatically).
- Object life cycles: [train status](../UML_DIAGRAMS.md#6-state-diagram-train-status-trainstatus),
  [train movement](../UML_DIAGRAMS.md#7-state-diagram-a-train-moving-through-the-station-trainmovementphase) and
  [platform status](../UML_DIAGRAMS.md#8-state-diagram-platform-platformstatus) state diagrams.

### 4.3 Data design

The database has five tables
([ER diagram](../UML_DIAGRAMS.md#12-entity-relationship-diagram-database-schema-version-1)):

| Table | Key | Purpose | Integrity rules |
|---|---|---|---|
| `platforms` | `id` | Platforms and their status | `number` unique and > 0; `length_m` > 0; `status` in (OPERATIONAL, FAILED) |
| `trains` | `id` | Timetable, delay, given-up flag | `number` unique; `type` in (EXPRESS, PASSENGER, FREIGHT); `base_priority` 1 to 5; `carriages` > 0; `delay_min` ≥ 0 |
| `bookings` | `train_id` | Which train stands at which platform from when | `train_id` references `trains` (cascade delete); `platform_id` references `platforms`; one booking per train |
| `settings` | `key` | Strategy, aging rate, buffer, maximum wait, preemption, conflicts, last save time | Key/value: new settings need no schema change |
| `audit_log` | `id` (auto) | Every change: time, type, message | Append-only; message at most 2,000 characters |

Design choices: times are stored as ISO-8601 text (readable, sortable, exact); derived values such as expected
times and booking end times are **not** stored, because the model recomputes them, so they can never disagree.
The schema version is stored in the file (`PRAGMA user_version`) and `Database.migrate()` upgrades older files
step by step. The Java validation is the first line of defence; the database constraints are the second.

### 4.4 Interface design

- **Navigation:** a sidebar of ten screens in five groups (Overview, Data, Planning, Visualise, Analysis).
- **Consistency:** every screen has the same header (title and one-line purpose), content on rounded cards, and
  at most one highlighted primary button.
- **Feedback:** coloured status badges (green allocated, red unallocated or failed, amber waiting); a status bar
  with the latest event; a "Saved hh:mm:ss" indicator; every refusal explained in words.
- **Safety:** confirmation before destructive actions; Undo for allocations and delays.
- **Accessibility:** colour is never the only signal (badges also carry words); light and dark themes; the
  window fits the screen.

### 4.5 Error handling strategy

| Situation | Handling |
|---|---|
| Invalid user input | The model throws a specific `RailSyncException` subclass; the screen shows its message; nothing changes. |
| Ordinary "cannot do" outcomes | Returned as results with reasons (`AllocationResult`, `DelayReport`, `BreakdownReport`). |
| Database cannot be opened, or is from a newer version | The application runs in memory and warns the user. |
| Saved data breaks a rule | The demo scenario is loaded and the user is warned. |
| A save fails | The change stays in memory; the sidebar shows "Save failed" with the reason in a tooltip. |
| CSV file has an error | The whole file is rejected with the line number; nothing changes. |

### 4.6 Configuration

| Setting | Where | Default |
|---|---|---|
| Strategy, aging rate, clearance buffer, maximum wait, preemption | Allocation screen (saved in `settings`) | FCFS, 4 min, 5 min, 20 min, off |
| Database location | System property `-Drailsync.db=path` | `~/.railsync/railsync.db` |
| Theme | Sidebar switch | Light |
