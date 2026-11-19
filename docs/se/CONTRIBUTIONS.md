# Team Contributions

**Project:** RailSync, a Railway Platform Allocation and Scheduling Simulator

The work was divided so that **every member owns one complete slice of the project**: a part of the code, the
diagrams and documents for that part, its tests, and its part of the presentation. Each member therefore worked
on requirements, design, implementation, testing and documentation. The planned effort is the same for all
three members, about 93 hours each (see the [project plan](PROJECT_PLAN.md#61-bottom-up-estimate-planned-hours)).

| Member | Name | Roll number | Role |
|---|---|---|---|
| Member 1 | Soumil | IIT2025064 | Project coordinator, requirements analyst and UI developer |
| Member 2 | Kushagra | IIT2025036 | Scheduling engineer and visualisation developer |
| Member 3 | Abeer | IIT2025047 | Architect, data engineer and test lead |

---

## Overview

| Area | Soumil | Kushagra | Abeer |
|---|---|---|---|
| Requirements | **SRS, use cases, traceability** | Algorithm requirements (FR-AL, FR-ST, FR-DL) | Storage requirements (FR-DS), non-functional requirements |
| Analysis | Level 0 and Level 1 DFDs | Level 2 DFDs for allocation and delays | Level 2 DFD for saving |
| Design | Domain class model, **interface design** | Algorithm design | **Architecture, database schema** |
| UML | Use-case, domain class, state diagrams | Activity diagram, allocation and delay sequence diagrams, services class diagram | Component, package, deployment, ER, persistence class diagrams, save sequence diagram |
| Code | `model`, `exception`, `ui` screens, forms and theme | `strategy`, allocator, delays and breakdowns, metrics, arena, time-distance diagram, yard view | `service` facade, `observer`, CSV, undo, `persistence` |
| Testing | Unit, boundary-value and system tests | Engine integration tests, diagram tests | **Test plan**, persistence and performance tests, manual tests, test report |
| Documents | SRS, requirement analysis, DFDs, project plan, user guide | Algorithm chapters, design patterns, viva guide | Architecture, test plan, QA report, maintenance plan |
| Presentation | Problem, requirements, plan, live demo | Algorithms, strategies, comparison results | Architecture, database, testing and quality |
| Planned share | 33% | 33% | 33% |

**Bold** marks the area each member led for the whole team.

---

## Soumil (IIT2025064): Project coordinator, requirements analyst and UI developer

**Requirements and planning**
- Studied how stations allocate platforms and wrote the problem statement.
- Identified the stakeholders and actors. Wrote the SRS with the functional requirements, their priorities and
  the constraints ([SRS.md](SRS.md)).
- Wrote the nine use-case specifications and the traceability matrix ([REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md)).
- Drew the context diagram (Level 0) and the Level 1 data flow diagram ([DFD.md](DFD.md)).
- Prepared the project plan: process model, WBS, milestones, Gantt chart, effort estimate and risk register
  ([PROJECT_PLAN.md](PROJECT_PLAN.md)). Ran the weekly meetings and tracked progress.

**Design and implementation**
- Designed the domain model: the abstract `Train` with `ExpressTrain`, `PassengerTrain` and `FreightTrain`,
  `Platform`, `Station`, `Schedule`, `PlatformReservation`, `TrainFactory` and the enums.
- Implemented the `model` and `exception` packages, including validation, delays and the overlap rule with the
  clearance gap.
- Designed the screens, navigation, colours and icons. Implemented `MainFrame` with the sidebar and action bar,
  the train, platform, allocation, schedule and report screens, the form dialogs, the table models and badges,
  and `Theme` with light and dark mode.

**UML:** use-case diagram, domain class diagram, train-status, train-movement and platform-status state diagrams.

**Testing:** `TrainTest`, `PlatformTest`, `ScheduleTest`, `OverlapTest`, `TrainMovementTest`, the boundary
value tests (`BoundaryValueTest`) and the automated system tests (`SystemTest`, ST-01 to ST-10).

**Documents:** SRS, requirement analysis, DFDs, project plan, [USER_GUIDE.md](../USER_GUIDE.md) and the README.

**Presentation:** introduction, the real-world problem, requirements, the project plan and the live demonstration.

---

## Kushagra (IIT2025036): Scheduling engineer and visualisation developer

**Requirements and design**
- Defined the allocation, strategy and disruption requirements (strategies, preemption, delay order, breakdowns).
- Designed the event-driven allocator with the ready queue and the operating-system analogy.
- Drew the Level 2 data flow diagrams for allocation (3.0) and for delays and breakdowns (4.0).

**Implementation**
- Implemented the `strategy` package: the `PlatformAllocationStrategy` interface, `QueueOrderingStrategy`, FCFS,
  Priority, Shortest Dwell First and Priority with Aging, and `StrategyCatalog`.
- Implemented `PlatformAllocator` (First-Fit, maximum wait, safe preemption with rollback) and `DelayManager`
  (least-disruptive delay order, ripple, breakdown reassignment).
- Implemented `MetricsCalculator` (waiting, turnaround, utilization and the sweep-line minimum), `StrategyArena`
  and `ScenarioGenerator`.
- Implemented the visualisations: `TimeDistanceDiagram` with the simulation clock and the delay animation, the
  `YardView` with the live ready queue, and the comparison and delay screens.

**UML:** activity diagram for allocation, sequence diagrams for allocation and for delay with ripple, and the
services class diagram.

**Testing:** `StrategyOrderTest`, `AgingAndSjfTest`, `PlatformAllocatorTest`, `StarvationAndPreemptionTest`,
`DelayAndBreakdownTest`, `MetricsCalculatorTest`, `ArenaAndGeneratorTest`, `DiagramAndClockTest`.

**Documents:** the algorithm chapters of the report, [DESIGN_PATTERNS.md](../DESIGN_PATTERNS.md) and the
[VIVA_GUIDE.md](../VIVA_GUIDE.md).

**Presentation:** the algorithms, the four strategies, starvation and aging, and the comparison results.

---

## Abeer (IIT2025047): Architect, data engineer and test lead

**Requirements and design**
- Defined the storage, reliability, security, performance and maintainability requirements.
- Designed the layered architecture, the service facade and the event (Observer) flow. Recorded the design
  decisions ([ARCHITECTURE.md](ARCHITECTURE.md)).
- Designed the database schema with its constraints and versioning.
- Drew the Level 2 data flow diagram for saving and restoring (8.0).

**Implementation**
- Implemented `SchedulingService`, the `observer` package, `TrainManager`, `PlatformManager`, `ScenarioCsv`,
  `ReportGenerator` and the undo commands (`AllocateCommand`, `DelayCommand`, `CommandHistory`).
- Implemented the `persistence` package: `Database`, the four DAOs, `SqliteStationRepository`,
  `SqliteAuditLog`, `AutoSaver`, and session snapshot and restore.

**UML:** component, package, deployment, entity-relationship and persistence class diagrams, and the
"change is saved" sequence diagram.

**Testing (test lead)**
- Wrote the test plan with the test levels, techniques, entry and exit criteria and the test report
  ([TEST_PLAN.md](TEST_PLAN.md)).
- Wrote `SchedulingServiceTest`, `ManagersTest`, `ScheduleEventManagerTest`, `CsvAndUndoTest`,
  `PersistenceTest` and `PerformanceTest`.
- Prepared and ran the manual system tests M-01 to M-14 and kept the test execution record.

**Documents:** [ARCHITECTURE.md](ARCHITECTURE.md), [TEST_PLAN.md](TEST_PLAN.md),
[QUALITY_ASSURANCE.md](QUALITY_ASSURANCE.md), [MAINTENANCE.md](MAINTENANCE.md), [CHANGELOG.md](../../CHANGELOG.md).

**Presentation:** the architecture, the SQLite database and audit log, testing and quality assurance.

---

## Work done together

- Requirements review meeting before the SRS baseline (milestone M1) and design review before M2.
- Code reviews: every member's work was read by at least one other member before being accepted.
- Integration of all parts, the demo scenario (`DemoScenarioTest`) and the final regression run.
- The OOP concepts guide, the final report and the slide decks.
- Rehearsal of the presentation.

---

## Declaration

We confirm that the division of work above is a fair description of each member's contribution to this project.

| Member | Name | Roll number | Signature | Date |
|---|---|---|---|---|
| Member 1 | Soumil | IIT2025064 | | |
| Member 2 | Kushagra | IIT2025036 | | |
| Member 3 | Abeer | IIT2025047 | | |
