# Team Contributions

**Project:** RailSync, a Railway Platform Allocation and Scheduling Simulator

The work was divided so that **every member owns one complete slice of the project**: a part of the code, the
diagrams and documents for that part, its tests, and its part of the presentation. Each member therefore worked
on requirements, design, implementation, testing and documentation, and the planned effort is the same for all
four (about 70 hours each; see the [project plan](PROJECT_PLAN.md#61-bottom-up-estimate-planned-hours)).

| | Name | Roll number | Role |
|---|---|---|---|
| Member 1 | _______________ | _______________ | Project coordinator and requirements analyst |
| Member 2 | _______________ | _______________ | Scheduling engineer |
| Member 3 | _______________ | _______________ | Architect and data engineer |
| Member 4 | _______________ | _______________ | UI designer and test lead |

---

## Overview

| Area | Member 1 | Member 2 | Member 3 | Member 4 |
|---|---|---|---|---|
| Requirements | **SRS, use cases, traceability** | Algorithm requirements (FR-ST, FR-DL) | Data and storage requirements (FR-DS), NFRs | UI requirements (FR-UI, FR-VI), usability NFRs |
| Design | Domain class model | Algorithm design | **Architecture, database schema** | **Interface design** |
| UML | Use-case, class (domain), state diagrams | Activity, sequence (allocation, delay) | Component, package, deployment, ER, persistence class, save sequence | Class (services), sequence review, screen flow |
| Code | `model`, `exception` | `strategy`, allocator, delays and breakdowns, metrics | `service` facade, `observer`, CSV, undo, `persistence` | `ui` (screens, diagrams, theme), comparison and yard screens |
| Testing | Unit and boundary-value tests | Engine integration tests | Persistence, CSV/undo, performance tests | **Test plan, system tests, manual tests, test report** |
| Documents | SRS, requirement analysis, project plan | Algorithm chapters, design patterns | Architecture, QA report, maintenance plan | Test plan, user guide, README |
| Presentation | Problem, requirements, plan | Algorithms and strategies | Architecture, database, quality | Live demo, testing |
| Planned share | 25% | 25% | 25% | 25% |

**Bold** marks the area each member led for the whole team.

---

## Member 1: Project coordinator and requirements analyst

**Requirements and planning**
- Studied how stations allocate platforms and wrote the problem statement.
- Identified the stakeholders and actors; wrote the SRS with all functional requirements, their priorities and
  the constraints ([SRS.md](SRS.md)).
- Wrote the nine use-case specifications and the traceability matrix ([REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md)).
- Prepared the project plan: process model, WBS, milestones, Gantt chart, effort estimate and risk register
  ([PROJECT_PLAN.md](PROJECT_PLAN.md)); ran the weekly meetings and tracked progress.

**Design and implementation**
- Designed the domain model: the abstract `Train` with `ExpressTrain`, `PassengerTrain` and `FreightTrain`,
  `Platform`, `Station`, `Schedule`, `PlatformReservation`, `TrainFactory` and the enums.
- Implemented the `model` and `exception` packages, including validation, delays and the half-open overlap rule
  with the clearance gap.

**UML:** use-case diagram, domain class diagram, train-status, train-movement and platform-status state diagrams.

**Testing:** `TrainTest`, `PlatformTest`, `ScheduleTest`, `OverlapTest`, `TrainMovementTest` and the boundary
value tests (`BoundaryValueTest`).

**Presentation:** introduction, the real-world problem, requirements and the project plan.

---

## Member 2: Scheduling engineer

**Requirements and design**
- Defined the scheduling and disruption requirements (strategies, preemption, delay order, breakdowns).
- Designed the event-driven allocator with the ready queue and the operating-system analogy.

**Implementation**
- Implemented the `strategy` package: the `PlatformAllocationStrategy` interface, `QueueOrderingStrategy`, FCFS,
  Priority, Shortest Dwell First and Priority with Aging, and `StrategyCatalog`.
- Implemented `PlatformAllocator` (First-Fit, maximum wait, safe preemption with rollback) and `DelayManager`
  (least-disruptive delay order, ripple, breakdown reassignment).
- Implemented `MetricsCalculator` (waiting, turnaround, utilization and the sweep-line minimum) and the
  `StrategyArena` and `ScenarioGenerator` used for comparisons.

**UML:** activity diagram for allocation; sequence diagrams for allocation and for delay with ripple.

**Testing:** `StrategyOrderTest`, `AgingAndSjfTest`, `PlatformAllocatorTest`, `StarvationAndPreemptionTest`,
`DelayAndBreakdownTest`, `MetricsCalculatorTest`, `ArenaAndGeneratorTest`.

**Documents:** the algorithm chapters of the report and [DESIGN_PATTERNS.md](../DESIGN_PATTERNS.md) (Strategy).

**Presentation:** the algorithms, the four strategies, starvation and aging, and the comparison results.

---

## Member 3: Architect and data engineer

**Requirements and design**
- Defined the storage, reliability, security and maintainability requirements.
- Designed the layered architecture, the service facade and the event (Observer) flow; recorded the design
  decisions ([ARCHITECTURE.md](ARCHITECTURE.md)).
- Designed the database schema with its constraints and versioning.

**Implementation**
- Implemented `SchedulingService`, the `observer` package, `TrainManager`, `PlatformManager`, `ScenarioCsv`,
  `ReportGenerator` and the undo commands (`AllocateCommand`, `DelayCommand`, `CommandHistory`).
- Implemented the `persistence` package: `Database`, the four DAOs, `SqliteStationRepository`,
  `SqliteAuditLog`, `AutoSaver`, and session snapshot and restore.

**UML:** component, package, deployment, entity-relationship and persistence class diagrams, and the
"change is saved" sequence diagram.

**Testing:** `SchedulingServiceTest`, `ManagersTest`, `ScheduleEventManagerTest`, `CsvAndUndoTest`,
`PersistenceTest`, `PerformanceTest`.

**Documents:** [ARCHITECTURE.md](ARCHITECTURE.md), [QUALITY_ASSURANCE.md](QUALITY_ASSURANCE.md),
[MAINTENANCE.md](MAINTENANCE.md), [CHANGELOG.md](../../CHANGELOG.md).

**Presentation:** the architecture, the SQLite database and audit log, and quality assurance.

---

## Member 4: UI designer and test lead

**Requirements and design**
- Defined the interface and usability requirements and designed the screens, navigation, colours and icons.

**Implementation**
- Implemented the `ui` package: `MainFrame` with the sidebar and action bar, the ten screens, the form dialogs,
  the table models and badges, `Theme` with light and dark mode, and the shared `Card` and `Ui` helpers.
- Implemented the visualisations: `TimeDistanceDiagram` with the simulation clock and the delay animation, and
  the `YardView` with the live ready queue.

**UML:** services class diagram; review of the sequence diagrams against the screens.

**Testing (test lead)**
- Wrote the test plan with the test levels, techniques, entry and exit criteria and the test report
  ([TEST_PLAN.md](TEST_PLAN.md)).
- Wrote the automated system tests (`SystemTest`, ST-01 to ST-10) and `DiagramAndClockTest`; prepared and ran
  the manual system tests M-01 to M-14; kept the test execution record.
- Produced the screenshots of every screen in both themes.

**Documents:** [USER_GUIDE.md](../USER_GUIDE.md) and the README.

**Presentation:** the live demonstration of the application and the testing results.

---

## Work done together

- Requirements review meeting before the SRS baseline (milestone M1) and design review before M2.
- Code reviews: every member's work was read by at least one other member before being accepted.
- Integration of all parts, the demo scenario (`DemoScenarioTest`) and the final regression run.
- The OOP concepts guide, the viva guide, the final report and the slide deck.
- Rehearsal of the presentation.

---

## Declaration

We confirm that the division of work above is a fair description of each member's contribution to this project.

| Member | Name | Signature | Date |
|---|---|---|---|
| Member 1 | | | |
| Member 2 | | | |
| Member 3 | | | |
| Member 4 | | | |
