# Software Requirements Specification (SRS)

| | |
|---|---|
| **Product** | RailSync, a Railway Platform Allocation and Scheduling Simulator |
| **Version** | 1.1 (with SQLite storage) |
| **Structure** | Based on IEEE 830 / ISO/IEC/IEEE 29148 |

---

## 1. Introduction

### 1.1 Purpose

This document specifies what RailSync must do (functional requirements) and how well it must do it
(non-functional requirements). It is the reference for design, implementation and testing: every requirement has
an ID, and the traceability matrix in [REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md) links each ID to the
classes that implement it and the tests that verify it.

Intended readers: the development team, the course instructor and examiners, and anyone maintaining the system.

### 1.2 The real-world problem

A railway station has a limited number of platforms. They have different lengths, and not every platform accepts
every kind of train (express, passenger, freight). Trains arrive and depart at fixed times. The station controller
must decide which platform each train uses so that:

- no two trains occupy the same platform at the same time,
- a safe clearance gap separates consecutive trains on a platform,
- every train fits the platform it is given, and
- when a train runs late or a platform breaks down, the plan is repaired with as little disruption as possible,
  and nobody is left without an explanation.

Doing this by hand is slow and error-prone, especially when one delay pushes several other trains (a "ripple").
RailSync automates the allocation, repairs the plan after disruptions, explains every decision, measures the
result, and lets the controller compare different allocation policies before choosing one.

### 1.3 Scope

**In scope:** one station, one service day, minute precision; train and platform management; automatic allocation
with four policies; delay and breakdown handling; metrics and reports; visual diagrams; strategy comparison;
persistent storage in an embedded SQLite database with an audit log; CSV import and export; undo.

**Out of scope:** track layout and route setting, signalling and interlocking, multi-day or recurring
timetables, rolling-stock and crew scheduling, live data feeds, multiple users at once, and any safety-critical
use. RailSync is a decision-support and teaching simulator, not a signalling system.

### 1.4 Definitions and abbreviations

| Term | Meaning |
|---|---|
| Dwell time | How long a train stays at the platform (departure minus arrival). |
| Booking (reservation) | A train assigned to a platform from a start time to an end time. |
| Half-open interval `[start, end)` | The platform is busy at the start minute and free again at the end minute. |
| Clearance buffer | The minimum gap between two trains on one platform (default 5 min; freight adds 5). |
| Outer signal / ready queue | Where a train waits when no platform is free yet. |
| Maximum wait | The longest a train may wait at the outer signal before it is refused (default 20 min). |
| Ripple | A delay that pushes the following trains on the same platform later. |
| Strategy | The policy that decides which waiting train goes next (FCFS, Priority, SJF, Aging). |
| First-Fit | The train takes the lowest-numbered free compatible platform. |
| Sweep line | The algorithm that finds the most trains present at once. |
| FCFS / SJF | First-Come-First-Served / Shortest Job (dwell) First. |
| FR / NFR | Functional / non-functional requirement. |
| DAO | Data Access Object: one class that reads and writes one database table. |

### 1.5 References

- IEEE Std 830-1998, *Recommended Practice for Software Requirements Specifications*.
- Project documents: [REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md), [ARCHITECTURE.md](ARCHITECTURE.md),
  [TEST_PLAN.md](TEST_PLAN.md), [../UML_DIAGRAMS.md](../UML_DIAGRAMS.md), [../USER_GUIDE.md](../USER_GUIDE.md).

---

## 2. Overall description

### 2.1 Product perspective

RailSync is a stand-alone desktop application. It needs no server and no network connection. It stores its data
in a single SQLite file in the user's home folder and can exchange scenarios as CSV files.

### 2.2 Product functions (summary)

1. Manage trains and platforms, with validation of every value.
2. Allocate trains to platforms automatically with a chosen strategy, explaining every refusal.
3. Handle delays (keep, move, ripple, or fail safely) and platform breakdowns (reassign or report).
4. Calculate metrics: waiting time, turnaround, utilization, conflicts, and the minimum platforms needed.
5. Visualise the plan: time-distance (Gantt) diagram with a simulation clock and animation, and a top-down yard view.
6. Compare the four strategies on the same trains, including large generated scenarios.
7. Save every change automatically, restore the session at start-up, and keep an audit log.
8. Import and export scenarios as CSV; undo the last allocation or delay.

### 2.3 User classes and characteristics

| User class | Description | Frequency | Technical skill | Main functions |
|---|---|---|---|---|
| Station controller (primary) | Plans platform use for the day and reacts to disruptions. | Daily, long sessions | Knows railway operations; not a programmer | Manage data, allocate, delay, breakdown, undo |
| Operations planner / analyst | Evaluates which allocation policy works best. | Occasional | Comfortable with metrics | Comparison arena, reports, generated scenarios |
| Student / examiner | Studies or assesses the scheduling and OOP concepts. | During the course | Programming background | Demo scenario, diagrams, reports, audit log |
| Maintainer | Changes or extends the software. | As needed | Java developer | Code, tests, database schema |

### 2.4 Operating environment

- Windows 10/11, macOS or Linux with a desktop.
- Java Runtime / JDK version 21 or newer.
- Screen of at least 1100 x 680 pixels (the window opens maximised on smaller screens up to its minimum size).
- About 60 MB of disk space for the application and its libraries; the database is typically under 1 MB.

### 2.5 Design and implementation constraints

| ID | Constraint |
|---|---|
| C-1 | Language Java 21; graphical interface in Swing (course requirement: desktop, object-oriented). |
| C-2 | No additional threads; animation and the clock use Swing Timers (keeps the program free of concurrency errors). |
| C-3 | Storage must be embedded (no database server to install): SQLite via JDBC. |
| C-4 | The interface layer must contain no business rules; the logic must be testable without a screen. |
| C-5 | One simulated service day at minute precision. |
| C-6 | Build with Maven through the included wrapper, so no tools besides a JDK are needed. |
| C-7 | Every class and public method documented (course commenting standard). |

### 2.6 Assumptions and dependencies

- Train times are known in advance (a timetable); delays are entered by the user.
- Coach and wagon lengths are fixed per train type (express coach 25 m plus a 20 m engine, passenger coach 20 m,
  freight wagon 15 m plus two 20 m locomotives).
- Only one person uses one copy of the database at a time.
- Third-party libraries: FlatLaf (look and feel), FlatLaf Extras with JSVG (icons), Xerial SQLite JDBC (database).

---

## 3. Specific requirements

Priority: **M** = must have, **S** = should have, **C** = could have. Every requirement below is implemented.

### 3.1 Functional requirements

#### Train management (TM)

| ID | Requirement | Priority |
|---|---|---|
| FR-TM-01 | The user shall add a train with type (express, passenger, freight), number, name, arrival, departure, base priority (1 to 5) and coach or wagon count. | M |
| FR-TM-02 | The system shall reject a train whose departure is not after its arrival, whose stop is shorter than the type's minimum (express 5, passenger 3, freight 20 min), or whose number is already used, and say why. | M |
| FR-TM-03 | The user shall edit a train's name, priority and times; if the train held a platform, it shall be placed again and the user told the result. | M |
| FR-TM-04 | The user shall remove a train only when it holds no platform. | M |
| FR-TM-05 | The user shall find a train by its number. | S |
| FR-TM-06 | The user shall release a train's platform and view a train's needs and booking. | S |
| FR-TM-07 | Each train type shall have its own priority bonus, required platform length and minimum stop; freight trains shall need 5 extra minutes of clearance. | M |

#### Platform management (PM)

| ID | Requirement | Priority |
|---|---|---|
| FR-PM-01 | The user shall add a platform with number, length (1 to 1500 m) and accepted train types. | M |
| FR-PM-02 | The system shall refuse a platform edit that would make an existing booking invalid. | M |
| FR-PM-03 | The user shall remove a platform only when it has no bookings. | M |
| FR-PM-04 | The user shall repair a failed platform. | M |
| FR-PM-05 | For a selected platform, the system shall list every train with "OK" or the exact reason it cannot use the platform. | S |

#### Allocation (AL)

| ID | Requirement | Priority |
|---|---|---|
| FR-AL-01 | The system shall allocate all trains without a platform with the chosen strategy, or one chosen train. | M |
| FR-AL-02 | The system shall never place two trains on one platform with overlapping times, using half-open intervals and a clearance gap equal to the larger of the two trains' buffers. | M |
| FR-AL-03 | A train shall take the lowest-numbered free compatible platform (First-Fit). | M |
| FR-AL-04 | A train may wait at the outer signal up to a configurable maximum (0 to 180 min, default 20); beyond that it is refused. | M |
| FR-AL-05 | Every refused train shall be given a reason (per platform when nothing is compatible). | M |
| FR-AL-06 | The user shall set the clearance buffer (0 to 60 min, default 5). | M |
| FR-AL-07 | The user shall clear all bookings to re-plan, keeping delays and failed platforms. | M |

#### Strategies (ST)

| ID | Requirement | Priority |
|---|---|---|
| FR-ST-01 | The system shall offer FCFS, Priority, Shortest Dwell First and Priority with Aging, switchable at any time. | M |
| FR-ST-02 | The aging rate (minutes per +1 priority, default 4) shall be configurable. | S |
| FR-ST-03 | Optional safe preemption: a higher-priority train may take one lower-priority booking that has not started, only if the displaced train keeps its time on another platform; otherwise nothing changes. | S |
| FR-ST-04 | Ties shall be broken by earlier arrival, then train number, so every run is reproducible. | M |

#### Delays and breakdowns (DL)

| ID | Requirement | Priority |
|---|---|---|
| FR-DL-01 | The user shall delay a train by a number of minutes. | M |
| FR-DL-02 | The system shall resolve a delay least-disruptively first: booking still valid; same platform later; another platform at the new time; ripple (push following trains, only within the maximum wait); any platform after waiting; otherwise unallocated with the reason. | M |
| FR-DL-03 | The system shall report every booking that changed, including the whole ripple cascade. | M |
| FR-DL-04 | The user shall report a platform breakdown; each train on it shall be moved if possible, and each that cannot shall be reported with the reason. | M |
| FR-DL-05 | No booking shall ever change without being reported. | M |

#### Metrics and reports (MR)

| ID | Requirement | Priority |
|---|---|---|
| FR-MR-01 | The system shall show totals (trains, allocated, unallocated, delayed, conflicts, working platforms), average waiting time, average turnaround and average utilization. | M |
| FR-MR-02 | The system shall compute the minimum number of platforms for zero waiting (sweep line). | S |
| FR-MR-03 | The system shall produce a text report with per-platform utilization, a text Gantt chart and the last allocation log, which the user can copy. | S |

#### Visualisation (VI)

| ID | Requirement | Priority |
|---|---|---|
| FR-VI-01 | The system shall draw a time-distance (Gantt) diagram of every train's path, with waiting shown in red. | M |
| FR-VI-02 | A simulation clock (1x, 10x, 60x) shall move a "now" line; changes after a delay or breakdown shall be animated with ghosts of the old positions. | S |
| FR-VI-03 | The system shall show a top-down yard view with the live ready queue and allow switching strategy from the current simulated time. | S |

#### Comparison (CA)

| ID | Requirement | Priority |
|---|---|---|
| FR-CA-01 | The system shall run the same trains through all four strategies on independent copies and show the metrics side by side, without changing the live schedule. | M |
| FR-CA-02 | The system shall generate reproducible random scenarios (a seed, 10 to 500 trains) and optionally load one as the live scenario. | S |

#### Data and storage (DS)

| ID | Requirement | Priority |
|---|---|---|
| FR-DS-01 | Every change shall be saved automatically to the SQLite database, without a Save action. | M |
| FR-DS-02 | At start-up the last session (platforms, trains, delays, bookings, failed platforms, given-up trains, strategy and settings) shall be restored; on first run the demo scenario shall be loaded. | M |
| FR-DS-03 | Every change shall be recorded in an append-only audit log (time, type, description), viewable on the Reports screen. | S |
| FR-DS-04 | The user shall export trains and platforms to CSV and import them; an import shall be fully checked first, report the line of any error, and change nothing unless the whole file is valid. | M |
| FR-DS-05 | The user shall undo the last allocation or delay (Ctrl+Z), restoring every booking it changed. | S |
| FR-DS-06 | The user shall reload the demo scenario at any time (after confirming). | M |

#### User interface (UI)

| ID | Requirement | Priority |
|---|---|---|
| FR-UI-01 | The interface shall offer light and dark themes, switchable while the application is open. | C |
| FR-UI-02 | A status bar shall show the latest event (red for conflicts and breakdowns) and the current settings; the sidebar shall show when the session was last saved. | S |
| FR-UI-03 | Destructive actions (remove, breakdown, replace scenario, reset) shall ask for confirmation. | M |

### 3.2 Non-functional requirements

Each NFR is measurable; the "Verified by" column names the evidence (see [TEST_PLAN.md](TEST_PLAN.md) and
[QUALITY_ASSURANCE.md](QUALITY_ASSURANCE.md)).

| ID | Category | Requirement | Verified by |
|---|---|---|---|
| NFR-P1 | Performance | Allocating 500 trains with any strategy takes under 1 s; all four strategies on 500 trains under 10 s. | PerformanceTest (86 ms), ArenaAndGeneratorTest (about 0.2 s for all four) |
| NFR-P2 | Performance | Saving a 500-train session takes under 1 s. | PerformanceTest (275 ms) |
| NFR-P3 | Performance | Restoring a 500-train session takes under 1 s. | PerformanceTest (34 ms) |
| NFR-R1 | Reliability | No two bookings on one platform may ever overlap (including the clearance gap), whatever the strategy. | OverlapTest, PlatformAllocatorTest, Schedule.reserve() as the last line of defence |
| NFR-R2 | Reliability | A save is atomic: a failure part-way leaves the previous save complete. | PersistenceTest.failedSaveLeavesThePreviousSaveIntact |
| NFR-R3 | Reliability | If the database fails, the user's change still succeeds in memory and the failure is shown. | PersistenceTest.autoSaverReportsFailureWithoutThrowing |
| NFR-R4 | Reliability | Damaged saved data is refused, not shown as an impossible plan; the demo is loaded with a warning. | PersistenceTest.restoreRejectsClashingBookings, Main.build() |
| NFR-R5 | Reliability | Invalid input never crashes the program; the user sees a message naming the problem. | TrainTest, PlatformTest, CsvAndUndoTest |
| NFR-S1 | Security | All SQL uses parameters; no user text is ever joined into an SQL statement. | Code review; PersistenceTest.sqlInjectionTextIsStoredAsPlainText |
| NFR-S2 | Security / integrity | The database enforces its own rules (CHECK, UNIQUE, NOT NULL, foreign keys). | PersistenceTest.databaseConstraintsRejectInvalidRows |
| NFR-S3 | Security | No network access; data stays in the user's home folder. | Architecture (no network code) |
| NFR-S4 | Accountability | The audit log is append-only and its entries are bounded in size. | PersistenceTest.auditLogIsNewestFirstAndBounded |
| NFR-U1 | Usability | Every refusal or failure states its reason in plain words. | AllocationResult and DelayReport messages; system tests |
| NFR-U2 | Usability | The main tasks (allocate, delay, undo, compare) take at most 3 clicks from any screen. | Task walkthrough in QUALITY_ASSURANCE.md |
| NFR-U3 | Usability | Consistent layout on every screen (title, one-line purpose, cards, one primary button). | UI review, screenshots |
| NFR-M1 | Maintainability | Layers depend downward only; no Swing below `ui`; no SQL outside `persistence`. | Package diagram; dependency check in QA |
| NFR-M2 | Maintainability | A new strategy needs one new class and one line in `StrategyCatalog`. | Design (Open/Closed); MAINTENANCE.md |
| NFR-M3 | Maintainability | Logic packages (model, strategy, service, persistence) have at least 90% line coverage. | JaCoCo: 95.8% |
| NFR-M4 | Maintainability | The database schema is versioned and upgraded step by step. | Database.migrate(); PersistenceTest.newerSchemaIsRefused |
| NFR-PT1 | Portability | Runs on any OS with Java 21+; no database or other software to install. | Deployment diagram; embedded SQLite |

### 3.3 External interface requirements

- **User interface:** a single window with a sidebar of ten screens (Dashboard, Trains, Platforms, Allocation,
  Schedule, Delay & Breakdown, Time-Distance, Yard View, Comparison, Reports), an action bar (Undo, Open CSV,
  Save CSV, Load demo scenario) and a status bar. Times are typed as `HH:mm`.
- **Software interfaces:** SQLite 3 through the JDBC driver `org.xerial:sqlite-jdbc`; Java SE 21 standard library.
- **File interfaces:** RailSync CSV scenario files (format in [../USER_GUIDE.md](../USER_GUIDE.md)); the
  database file `~/.railsync/railsync.db` (location can be changed with `-Drailsync.db=path`).
- **Hardware and communication interfaces:** none (no network, no special hardware).

### 3.4 Data requirements

The database stores platforms, trains (timetable, delay, given-up flag), bookings, settings and the audit log.
Its schema is shown in the entity-relationship diagram in [../UML_DIAGRAMS.md](../UML_DIAGRAMS.md#12-entity-relationship-diagram-database-schema-version-1).
Expected times are not stored; they are recomputed from the timetable and the delay, so they can never disagree.
