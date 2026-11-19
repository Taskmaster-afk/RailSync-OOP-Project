# Project Plan and Management

This document records how the RailSync project was organised: the process model, the work breakdown, milestones
and schedule, roles and responsibilities, effort estimation, risks, and how the team communicated.

> The team has three members: **Soumil** (IIT2025064), **Kushagra** (IIT2025036) and **Abeer** (IIT2025047).
> Their roles and contributions are listed in [CONTRIBUTIONS.md](CONTRIBUTIONS.md).

---

## 1. Process model

RailSync was developed **iteratively and incrementally** over ten weeks. Requirements and the architecture were
fixed first (weeks 1 to 2); then each iteration added one working, tested increment that could be demonstrated:

| Increment | Delivered | Demonstrable result |
|---|---|---|
| 1 | Domain model and validation | Console demo creating trains and platforms, rejecting invalid ones |
| 2 | Overlap rule, allocator, FCFS and Priority | Console allocation of the demo day |
| 3 | SJF, Aging, preemption, metrics, sweep line, reports | Strategy results compared in the console |
| 4 | Services, Observer, delays and breakdowns | Ripple and breakdown reports |
| 5 | Swing GUI and demo scenario | Every module usable in the window |
| 6 | Time-distance diagram, clock, animation | Visual delay ripple |
| 7 | Comparison arena, generator, yard view | Strategies compared on 500 trains |
| 8 | CSV, Undo, SQLite storage and audit log | Session survives a restart |
| 9 | UI redesign, system and performance tests | Final look; 224 passing tests |
| 10 | Documentation, report, presentation | Submission package |

Why incremental: the scheduling rules were the main risk, so they were built and tested before any interface
work; every increment kept all earlier tests passing, so the product was always in a working state.

---

## 2. Work breakdown structure (WBS)

```
RailSync
├── 1 Project management
│   ├── 1.1 Planning, schedule, risk register
│   ├── 1.2 Weekly meetings and progress tracking
│   └── 1.3 Final report and presentation
├── 2 Requirements
│   ├── 2.1 Domain study and stakeholder analysis
│   ├── 2.2 SRS (functional and non-functional requirements)
│   ├── 2.3 Use-case specifications and traceability
│   └── 2.4 Data flow diagrams (Levels 0, 1 and 2)
├── 3 Design
│   ├── 3.1 Architecture and package structure
│   ├── 3.2 Class, sequence, activity and state diagrams
│   ├── 3.3 Database schema (ER diagram)
│   └── 3.4 User-interface design
├── 4 Implementation
│   ├── 4.1 Domain model (model, exception)
│   ├── 4.2 Scheduling engine (strategy, allocator, delays, breakdowns)
│   ├── 4.3 Services (facade, observer, metrics, reports, arena, CSV, undo)
│   ├── 4.4 Persistence (SQLite, repositories, audit log)
│   └── 4.5 User interface (screens, diagrams, theme)
├── 5 Testing
│   ├── 5.1 Unit and boundary-value tests
│   ├── 5.2 Integration tests
│   ├── 5.3 System and performance tests
│   └── 5.4 Manual system tests and test report
└── 6 Documentation and maintenance
    ├── 6.1 User guide and README
    ├── 6.2 Quality assurance report
    └── 6.3 Maintenance plan
```

---

## 3. Milestones

| ID | Milestone | Week | Deliverable / exit criterion |
|---|---|---|---|
| M1 | Requirements baseline | 2 | SRS and use cases reviewed by the whole team |
| M2 | Design baseline | 3 | Architecture, class diagram and database schema agreed |
| M3 | Scheduling engine complete | 5 | All four strategies, delays and breakdowns pass their tests |
| M4 | GUI complete | 7 | Every module usable in the window; demo scenario works end to end |
| M5 | Feature freeze | 8 | CSV, undo and SQLite storage done; no new features after this |
| M6 | Testing complete | 9 | All automated tests pass; coverage target met; manual tests recorded |
| M7 | Submission | 10 | Report, documents, presentation and source submitted |

---

## 4. Schedule (Gantt chart)

`■` = planned work in that week; `◆` = milestone.

| WBS | Task | Owner | W1 | W2 | W3 | W4 | W5 | W6 | W7 | W8 | W9 | W10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2.1 | Domain study, stakeholders | Soumil | ■ | | | | | | | | | |
| 2.2 | SRS | Soumil | ■ | ■◆ | | | | | | | | |
| 2.3 | Use cases, traceability | Soumil | | ■ | | | | | | | ■ | |
| 2.4 | Data flow diagrams | Soumil, Kushagra, Abeer | | ■ | ■ | | | | | ■ | | |
| 3.1 | Architecture | Abeer | | ■ | ■◆ | | | | | | | |
| 3.2 | UML diagrams | All | | ■ | ■ | | | | | ■ | ■ | |
| 3.3 | Database schema | Abeer | | | ■ | | | | | ■ | | |
| 3.4 | UI design | Soumil | | | ■ | | | ■ | | | ■ | |
| 4.1 | Domain model | Soumil | | | ■ | ■ | | | | | | |
| 4.2 | Scheduling engine | Kushagra | | | | ■ | ■◆ | | | | | |
| 4.3 | Services, CSV, undo | Abeer | | | | ■ | ■ | | | ■ | | |
| 4.4 | Persistence (SQLite) | Abeer | | | | | | | | ■◆ | | |
| 4.5 | User interface screens | Soumil | | | | | | ■ | ■◆ | | ■ | |
| 4.5 | Diagrams, yard view, arena | Kushagra | | | | | | ■ | ■ | | | |
| 5.1 | Unit and boundary tests | Soumil, Kushagra | | | ■ | ■ | ■ | | | | ■ | |
| 5.2 | Integration tests | Kushagra, Abeer | | | | | ■ | ■ | | ■ | | |
| 5.3 | System and performance tests | Soumil, Abeer | | | | | | | | | ■◆ | |
| 5.4 | Manual tests, test report | Abeer | | | | | | | | | ■ | ■ |
| 6.x | Documentation | All | | | | | | | | ■ | ■ | ■◆ |
| 1.3 | Report and presentation | All | | | | | | | | | ■ | ■ |

---

## 5. Roles and responsibilities

| Member | Role | Main responsibilities |
|---|---|---|
| Soumil | Project coordinator, requirements analyst and UI developer | Schedule and meetings; SRS, use cases, traceability; Level 0 and 1 DFDs; domain model; screens and theme; use-case and state diagrams; unit, boundary and system tests; user guide |
| Kushagra | Scheduling engineer and visualisation developer | Strategies, allocator, preemption, delays and breakdowns; metrics and comparison arena; time-distance diagram and yard view; Level 2 DFDs for 3.0 and 4.0; activity and sequence diagrams; engine integration tests |
| Abeer | Architect, data engineer and test lead | Architecture; service facade, observer, CSV, undo; SQLite persistence and audit log; Level 2 DFD for 8.0; component, deployment and ER diagrams; test plan, persistence, performance and manual tests; QA report and maintenance plan |

### 5.1 RACI matrix

R = responsible (does the work), A = accountable (signs off), C = consulted, I = informed.

| Activity | Soumil | Kushagra | Abeer |
|---|---|---|---|
| Project plan and schedule | A/R | C | C |
| Requirements (SRS, use cases) | A/R | C | C |
| Data flow diagrams | A/R | R | R |
| Architecture and design decisions | C | C | A/R |
| Domain model | A/R | C | C |
| Scheduling algorithms | C | A/R | I |
| Services, CSV, undo | I | C | A/R |
| Database and persistence | I | I | A/R |
| User interface | A/R | R | C |
| UML diagrams | R | R | R |
| Test plan and system testing | R | R | A/R |
| QA report and maintenance plan | C | C | A/R |
| User guide | A/R | I | C |
| Final report and presentation | A/R | R | R |

---

## 6. Effort estimation

### 6.1 Bottom-up estimate (planned hours)

| WBS area | Soumil | Kushagra | Abeer | Total |
|---|---|---|---|---|
| Management and meetings | 12 | 5 | 6 | 23 |
| Requirements and DFDs | 16 | 4 | 5 | 25 |
| Design and UML | 8 | 10 | 14 | 32 |
| Implementation | 30 | 40 | 34 | 104 |
| Testing | 12 | 16 | 18 | 46 |
| Documentation, report, presentation | 15 | 18 | 16 | 49 |
| **Total** | **93** | **93** | **93** | **279** |

### 6.2 COCOMO (basic, organic mode) as a cross-check

With about 12.6 thousand delivered lines of code (KLOC):

| Quantity | Formula | Result |
|---|---|---|
| Effort | E = 2.4 × KLOC^1.05 | 2.4 × 12.6^1.05 ≈ **34 person-months** |
| Development time | T = 2.5 × E^0.38 | 2.5 × 34^0.38 ≈ **9.6 months** |
| Average team size | E / T | ≈ **3.6 people** |

**Interpretation.** Basic COCOMO is calibrated on industrial projects that include formal reviews, integration
with other systems, deployment and support, and it counts every line as hand-written production code. It
therefore overestimates a student project, where a large share of the lines are comments and documentation
written to the course standard, the scope is one desktop program, and there is no deployment or support phase.
The estimate is still useful: it shows that the product is substantial for three people and justified fixing
the scope early (one station, one day) and freezing features at week 8. The team-size figure (≈ 3.6) is close to our
team of three.

---

## 7. Risk management

| # | Risk | Probability | Impact | Mitigation | Status |
|---|---|---|---|---|---|
| R1 | The scheduling rules are wrong or inconsistent (double bookings). | Medium | High | Single booking gate (`Schedule.reserve`), test-first for every rule, overlap tests. | Closed: no overlap failures |
| R2 | Scope creep (track layout, multi-day timetables). | High | Medium | Scope fixed in the SRS; "won't have" list; feature freeze at M5. | Closed |
| R3 | GUI work delays the core logic. | Medium | High | Logic built and tested first in the console; GUI only after M3. | Closed |
| R4 | Uneven workload in the team. | Medium | Medium | Vertical ownership (each member owns code, diagrams, tests and documents); weekly check of the WBS hours. | Closed |
| R5 | Data loss or corruption with the database. | Low | High | Transactions, constraints, re-checked restore, fault-injection tests. | Closed |
| R6 | Members unavailable before exams. | Medium | Medium | Documentation written alongside the code; every area understood by at least two members. | Monitored |
| R7 | The demo fails during the presentation. | Low | High | Deterministic demo proved by `DemoScenarioTest`; Undo and "Load demo scenario" to recover; system tests. | Closed |
| R8 | Library or JDK incompatibility on examiners' machines. | Low | Medium | Maven Wrapper, Java 21 target, embedded SQLite; tested on JDK 25. | Closed |

---

## 8. Communication and tracking

| Practice | Details |
|---|---|
| Weekly meeting | 30 to 45 minutes: progress against the Gantt chart, blockers, next week's tasks. |
| Shared repository | One Git repository for code, documents and diagrams; descriptive commit messages. |
| Group chat | Day-to-day questions and quick decisions. |
| Definition of done | Code written and documented, tests passing, reviewed by another member, documents updated. |
| Progress log | Each increment and its design decisions are recorded in [PROGRESS.md](../PROGRESS.md). |
