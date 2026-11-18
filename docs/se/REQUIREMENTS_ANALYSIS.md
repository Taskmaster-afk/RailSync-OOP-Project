# Requirement Analysis

This document explains **who** RailSync is for, **what** they need from it, and **how** those needs became the
requirements in the [SRS](SRS.md). It ends with a traceability matrix that links every requirement to the code
that implements it and the tests that prove it.

---

## 1. How the requirements were gathered

| Technique | What we did | What it produced |
|---|---|---|
| Domain study | Read how stations allocate platforms: platform lengths, train categories, clearance times, outer and home signals, and how a single delay propagates. | Domain vocabulary (dwell, clearance buffer, outer signal, ripple), the three train types and their rules. |
| Analogy with operating systems | Compared platform allocation with CPU scheduling (platforms = CPUs, trains = processes, dwell = burst time). | The four strategies, the ready queue, starvation, aging and preemption requirements (FR-ST). |
| Scenario walkthroughs | Wrote a 6-platform, 12-train demo day and walked through it by hand: who waits, who is refused, what a 5-minute delay does. | The demo scenario, expected results used in `DemoScenarioTest`, and the delay order in FR-DL-02. |
| Prototyping | Built the model and allocator first and showed console output, then the GUI, then the diagrams. | Visual requirements (FR-VI), the comparison arena (FR-CA) and the explanation requirements (FR-AL-05). |
| Review with the course brief | Checked every course requirement: OOP concepts, SE activities, documentation. | Constraints C-1 to C-7, persistence (FR-DS), and the quality targets (NFRs). |

---

## 2. Stakeholders

| Stakeholder | Interest in the system | Influence | How RailSync serves them |
|---|---|---|---|
| Station controller (primary user) | A conflict-free plan, fast repair after disruptions, clear reasons. | High | Automatic allocation, delay and breakdown handling, explanations, undo. |
| Operations planner | Choose the best policy for the station; know how many platforms are needed. | Medium | Comparison arena, metrics, sweep-line minimum, generated scenarios. |
| Signalling and platform staff (indirect) | Trains on platforms they actually fit, with safe gaps. | Medium | Length and category checks; clearance buffer; freight extra time. |
| Passengers (indirect) | Short waits outside the station, stable platform assignments. | Low | Waiting-time metrics; least-disruptive delay handling (keeps the same platform first). |
| Freight operators (indirect) | Their long trains are not starved by passenger traffic. | Low | Aging strategy prevents starvation; freight clearance respected. |
| Station administration / auditors | Know what was changed and when. | Medium | Append-only audit log in the database. |
| Course instructor and examiners | Evidence of OOP and SE practice. | High | Documented code, UML, SE documents, tests, demo scenario. |
| Development team | Clear, maintainable code that can be extended. | High | Layered architecture, interfaces, tests, maintenance guide. |

---

## 3. Actors

| Actor | Type | Description |
|---|---|---|
| Station controller | Primary, human | Manages data, allocates, handles disruptions, undoes. |
| Student / examiner | Primary, human | Loads the demo, studies diagrams, compares strategies, reads reports and the audit log. |
| SQLite database | Secondary, system | Stores the session and the audit log; read at start-up, written after every change. |
| File system | Secondary, system | Holds CSV scenario files for import and export. |

The use-case diagram is in [UML_DIAGRAMS.md, section 1](../UML_DIAGRAMS.md#1-use-case-diagram).

---

## 4. Use-case specifications

### UC-01 Allocate trains

| Item | Description |
|---|---|
| Actor | Station controller |
| Goal | Every train without a platform gets one, or a clear reason why not. |
| Preconditions | At least one platform and one train exist. |
| Trigger | The user presses **Allocate all pending trains** on the Allocation screen. |
| Main flow | 1. The user chooses a strategy and checks the settings (buffer, maximum wait, preemption). 2. The user presses Allocate. 3. The system rejects trains that fit no platform, with a reason per platform. 4. The system processes arrivals in time order; at each moment the strategy orders the waiting trains. 5. Each train takes the lowest-numbered free compatible platform. 6. The system shows every decision in order, refreshes every screen and saves the session. |
| Alternative flows | 4a. No platform is free: the train waits at the outer signal. 4b. Preemption is on and one lower-priority booking can move safely: it moves and the train takes its slot. 4c. The earliest free slot is beyond the maximum wait: the train is refused with the earliest possible time. 2a. Every train already has a platform: the system says so. |
| Postconditions | No two bookings overlap; every train is ALLOCATED or UNALLOCATED with a reason; the session and an audit entry are saved; Undo is offered. |
| Requirements | FR-AL-01 to 07, FR-ST-01 to 04, FR-DS-01, FR-DS-03 |

### UC-02 Delay a train

| Item | Description |
|---|---|
| Actor | Station controller |
| Goal | Record a late train and repair the plan with the least disruption. |
| Preconditions | The train exists. |
| Main flow | 1. The user selects the train and enters the delay in minutes. 2. The system adds the delay. 3. If the booking is still valid, nothing else changes. 4. Otherwise the system tries, in order: the same platform at the new time; another platform at the new time; a ripple that pushes the following trains (only within the maximum wait). 5. The system reports the outcome and every booking that changed, animates the change on the diagram, and saves. |
| Alternative flows | 4a. No option works: the train is released and marked UNALLOCATED with the reason. 4b. The train had no platform: the delay is recorded only. |
| Postconditions | No overlapping bookings; every change is reported; Undo can reverse the whole delay including the ripple. |
| Requirements | FR-DL-01, 02, 03, 05; FR-VI-02; FR-DS-05 |

### UC-03 Report a platform breakdown

| Item | Description |
|---|---|
| Actor | Station controller |
| Main flow | 1. The user selects a working platform and presses **Report breakdown**. 2. The system asks for confirmation. 3. The platform is marked FAILED. 4. For each booking on it, the system tries another platform at the same time, then any platform after waiting. 5. The system lists who moved, who could not, and why, and saves. |
| Alternative flows | 2a. The user cancels: nothing changes. 1a. The platform has no bookings: it is just marked failed. |
| Postconditions | No train is booked on a failed platform; every affected train is accounted for. |
| Requirements | FR-DL-04, 05; FR-PM-04; FR-UI-03 |

### UC-04 Add a train

| Item | Description |
|---|---|
| Actor | Station controller |
| Main flow | 1. The user presses **Add train** and fills in the form. 2. The system validates every value. 3. The train is added as SCHEDULED, the table refreshes and the session is saved. |
| Alternative flows | 2a. A value is invalid (departure before arrival, stop too short, priority out of range, duplicate number): the form stays open with a message naming the problem. |
| Requirements | FR-TM-01, 02, 07 |

### UC-05 Compare strategies

| Item | Description |
|---|---|
| Actor | Operations planner or examiner |
| Main flow | 1. The user chooses the live scenario or a generated one (seed and size). 2. The user presses **Run comparison**. 3. The system copies the scenario once per strategy and allocates each copy. 4. The system shows one row per strategy with its metrics, the best value in each column in bold. |
| Alternative flows | 1a. The live scenario is empty: the system asks the user to load data or use a generated scenario. 4a. The user loads the generated scenario into the application (after confirming). |
| Postconditions | The live schedule is unchanged. |
| Requirements | FR-CA-01, 02 |

### UC-06 Undo the last allocation or delay

| Item | Description |
|---|---|
| Actor | Station controller |
| Main flow | 1. The action bar shows what Undo would reverse (for example "Undo delay of 12951 by 5 min"). 2. The user clicks it or presses Ctrl+Z. 3. The system releases every booking, reverses the command's own change (a delay removes its minutes) and books the saved state again through the normal rules. 4. The session is saved. |
| Alternative flows | 2a. Nothing to undo: the system says so. |
| Requirements | FR-DS-05 |

### UC-07 Start the application (restore the session)

| Item | Description |
|---|---|
| Actor | Any user; SQLite database |
| Main flow | 1. The user starts RailSync. 2. The system opens (or creates) the database and upgrades its schema if needed. 3. The saved session is loaded and every booking is checked again. 4. The window opens showing the restored plan and "Saved hh:mm:ss". |
| Alternative flows | 3a. First run: the demo scenario is loaded and saved. 3b. The saved data breaks a rule: the demo is loaded and the user is warned. 2a. The file cannot be opened or was written by a newer version: the session runs in memory only and the user is warned. |
| Requirements | FR-DS-01, 02; NFR-R2, NFR-R4, NFR-M4 |

### UC-08 Import a scenario from CSV

| Item | Description |
|---|---|
| Actor | Station controller |
| Main flow | 1. The user presses **Open CSV** and picks a file. 2. The system checks the whole file (format, values, duplicate ids and numbers). 3. The system asks before replacing the current data. 4. The data is replaced, nothing is allocated yet, and the session is saved. |
| Alternative flows | 2a. A line is invalid: the system names the line and the problem; nothing changes. 3a. The user cancels: nothing changes. |
| Requirements | FR-DS-04; FR-UI-03 |

### UC-09 View the audit log

| Item | Description |
|---|---|
| Actor | Station controller, examiner, auditor |
| Main flow | 1. The user opens **Reports → Audit log (database)**. 2. The system lists the newest entries first (number, time, event type, description) and the total count. |
| Requirements | FR-DS-03; NFR-S4 |

---

## 5. Requirement prioritisation (MoSCoW)

| Priority | Count | Examples |
|---|---|---|
| Must have | 31 | Conflict-free allocation, delays, breakdowns, persistence, CSV, validation |
| Should have | 13 | Aging rate, preemption, sweep line, animation, yard view, audit log, undo |
| Could have | 1 | Light/dark themes |
| Won't have (this version) | - | Multi-day timetables, track layout, multiple users, live data feeds |

---

## 6. Traceability matrix

Each functional requirement is linked to the classes that implement it and the tests that verify it. Test
classes are in `src/test/java/com/railsync/`; ST-xx are the system tests in `SystemTest`.

| Requirement | Implemented in | Verified by |
|---|---|---|
| FR-TM-01, 02 | `Train`, `TrainFactory`, `TrainManager`, `TrainFormDialog` | TrainTest, ManagersTest |
| FR-TM-03 | `SchedulingService.updateTrain()` | SchedulingServiceTest.updatingAllocatedTrainReplacesIt |
| FR-TM-04 | `TrainManager.removeTrain()` | ManagersTest.allocatedTrainCannotBeRemoved |
| FR-TM-05, 06 | `TrainManager.findByNumber()`, `TrainPanel` | ManagersTest.searchByNumberTrimsAndReturnsNullWhenMissing |
| FR-TM-07 | `ExpressTrain`, `PassengerTrain`, `FreightTrain` | TrainTest (subclass behaviour, polymorphism) |
| FR-PM-01 to 04 | `Platform`, `Station`, `PlatformManager`, `PlatformPanel` | PlatformTest, ManagersTest |
| FR-PM-05 | `Train.getIncompatibilityReason()`, `PlatformPanel` | PlatformTest, TrainTest |
| FR-AL-01, 03, 04, 05 | `PlatformAllocator`, `AllocationResult` | PlatformAllocatorTest, DemoScenarioTest, ST-02 |
| FR-AL-02, 06 | `Schedule.reserve()`, `Schedule.findConflict()` | OverlapTest, ScheduleTest |
| FR-AL-07 | `SchedulingService.resetAllocations()` | SchedulingServiceTest |
| FR-ST-01 | `strategy` package, `StrategyCatalog`, `SchedulingService.setStrategy()` | StrategyOrderTest, SchedulingServiceTest, ST-06 |
| FR-ST-02 | `PriorityAgingStrategy` | AgingAndSjfTest |
| FR-ST-03 | `PlatformAllocator.tryPreemption()` | StarvationAndPreemptionTest |
| FR-ST-04 | `QueueOrderingStrategy` | StrategyOrderTest |
| FR-DL-01 to 03, 05 | `DelayManager.delayTrain()`, `DelayReport`, `ReservationChange` | DelayAndBreakdownTest, DemoScenarioTest, ST-05 |
| FR-DL-04 | `DelayManager.handleBreakdown()`, `BreakdownReport` | DelayAndBreakdownTest, DemoScenarioTest |
| FR-MR-01, 02 | `MetricsCalculator`, `ScheduleMetrics` | MetricsCalculatorTest |
| FR-MR-03 | `ReportGenerator`, `ReportsPanel` | MetricsCalculatorTest, ST-08 |
| FR-VI-01, 02 | `TimeDistanceDiagram`, `SimulationClock`, `TrainMovement` | DiagramAndClockTest, TrainMovementTest |
| FR-VI-03 | `YardView`, `SchedulingService.reallocateFrom()` | ArenaAndGeneratorTest (re-planning), ST-10 |
| FR-CA-01, 02 | `StrategyArena`, `ScenarioGenerator`, `Scenario` | ArenaAndGeneratorTest, ST-07 |
| FR-DS-01 | `AutoSaver`, `SqliteStationRepository` | PersistenceTest.autoSaverSavesAfterEveryChange, ST-02 |
| FR-DS-02 | `SchedulingService.takeSnapshot()/restore()`, `Main.build()` | PersistenceTest (round trip), ST-01, ST-04, ST-06 |
| FR-DS-03 | `SqliteAuditLog`, `ReportsPanel` | PersistenceTest.auditLogIsNewestFirstAndBounded, ST-08 |
| FR-DS-04 | `ScenarioCsv` | CsvAndUndoTest |
| FR-DS-05 | `AllocateCommand`, `DelayCommand`, `CommandHistory` | CsvAndUndoTest, ST-03 |
| FR-DS-06 | `DemoScenarioLoader` | DemoScenarioTest |
| FR-UI-01 | `Theme`, `MainFrame` | ST-09 |
| FR-UI-02, 03 | `StatusBar`, `MainFrame`, `Ui.confirm()` | ST-01, manual system tests M-03, M-05 |
