# Test Plan and Test Report

This document describes how RailSync is tested (strategy, levels, techniques, environment, criteria), lists the
test cases, and reports the results of the latest run.

---

## 1. Objectives

1. Show that every functional requirement in the [SRS](SRS.md) works (traceability in
   [REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md#6-traceability-matrix)).
2. Show that the measurable non-functional requirements are met (performance, reliability, security).
3. Protect the core safety rule (no overlapping bookings) against every strategy and every disruption.
4. Make regressions visible immediately: the whole automated suite runs in under a minute.

## 2. Strategy

| Level | What is tested | How | Tools |
|---|---|---|---|
| Unit | One class at a time: validation, the overlap rule, each strategy's ordering, metrics formulas, event delivery, diagram geometry. | Automated, white-box and black-box | JUnit 5, parameterised tests |
| Integration | Classes working together: allocator + schedule + strategies; delay manager + allocator; service facade + observers; CSV and undo; service + SQLite repositories. | Automated, mostly black-box through public APIs | JUnit 5, in-memory and temporary-file SQLite |
| System | The complete application (window, all screens, services, real database file) driven by clicking the real buttons. | Automated (ST-01 to ST-10) plus manual cases for dialog flows (M-01 to M-14) | JUnit 5 + Swing, manual checklist |
| Performance | The largest supported scenario (500 trains): allocation, save and restore times. | Automated with timing | JUnit 5 |
| Acceptance | Each use case performed end to end against its postconditions. | Mapped to system tests and the demo script | Section 7 |
| Regression | Everything above, after every change. | `run-tests.cmd` / `mvnw test` | Maven Surefire, JaCoCo |

**Test-first for rules.** Every new rule (for example the clearance gap, the ripple limit, the database round
trip) was given a test with the exact expected value before or together with the code. Each test method starts
with a one-line comment: `// Scenario: ... Expected: ...`, so the suite doubles as a specification.

**Deterministic tests.** Generated scenarios use fixed seeds, ties are broken by arrival and number, and each
test builds its own data, so every run gives the same result.

## 3. Test design techniques

### 3.1 Equivalence partitioning

| Input | Valid classes | Invalid classes |
|---|---|---|
| Train times | departure after arrival, stop ≥ type minimum | departure = arrival; departure before arrival; stop below minimum; missing time |
| Train number | unique, non-blank | blank; already used |
| Train / platform compatibility | accepted type and long enough and operational | wrong type; too short; platform failed |
| Platform categories | one or more types | empty set |
| CSV line | correct field count, known type, valid numbers | wrong field count; unknown type; bad number; duplicate id or number; departure before arrival |
| Saved database content | consistent bookings | clashing bookings; booking for a missing train; unknown strategy name; newer schema |
| Strategy | FCFS, Priority, SJF, Aging | (not enterable: chosen from a list) |

### 3.2 Boundary value analysis

For every numeric range the values *min − 1, min, max, max + 1* are tested in `BoundaryValueTest` (34 cases,
all passing).

| Input | Valid range | Tested values (accepted / rejected) |
|---|---|---|
| Base priority | 1 to 5 | 1, 5 / 0, 6 |
| Coach or wagon count | 1 to 60 | 1, 60 / 0, 61 |
| Minimum stop: express / passenger / freight | ≥ 5 / ≥ 3 / ≥ 20 min | 5, 3, 20 / 4, 2, 19 |
| Platform length | 1 to 1500 m | 1, 1500 / 0, 1501 |
| Clearance buffer | 0 to 60 min | 0, 60 / −1, 61 |
| Maximum wait | 0 to 180 min | 0, 180 / −1, 181 |
| Delay | 1 to 1440 min | 1, 1440 / 0, 1441 |
| Train length vs platform | length ≤ platform | 270 m train on 270 m (fits) / 269 m (does not) |
| Second booking after the gap | start ≥ end + gap | 10:15 (free) / 10:14 (conflict) |
| Generated scenario size | 10 to 500 trains | 10, 500 / 9, 501 (ArenaAndGeneratorTest) |

### 3.3 Other techniques

- **Decision tables** for the delay resolution order (six outcomes, each forced by its own test in
  `DelayAndBreakdownTest`).
- **State-transition testing** for train and platform status (allocate, release, give up, reset, fail, repair),
  following the [state diagrams](../UML_DIAGRAMS.md#6-state-diagram-train-status-trainstatus).
- **Scenario testing** on the demo day (`DemoScenarioTest` proves every number used in the presentation).
- **Fault injection:** closing the database under the auto-saver, a failing save half-way, clashing saved data,
  and a file from a newer version (`PersistenceTest`).
- **Security testing:** an SQL-injection-style train name, and a direct SQL insert that breaks a CHECK rule.

## 4. Test environment

| Item | Value |
|---|---|
| Operating system | Windows 11 |
| Java | OpenJDK 25 (the code targets Java 21) |
| Build | Maven 3.9 via the Maven Wrapper; JUnit Jupiter 5.14; JaCoCo 0.8.16 |
| Database | SQLite through `org.xerial:sqlite-jdbc` 3.54.1.0; in-memory for unit and integration tests, a temporary file for system and performance tests |
| Display | A desktop session is required for the system tests (they skip themselves on a headless machine) |

## 5. Entry and exit criteria

| | Criteria |
|---|---|
| Entry | The code compiles; the demo scenario loads; test data is defined in the test itself. |
| Exit | 100% of automated tests pass; every functional requirement has at least one passing test; line coverage of the logic packages ≥ 90%; no open defect of severity "high"; the manual system tests M-01 to M-14 executed and recorded. |
| Suspension | Any failure in the overlap rule (NFR-R1) stops further testing until it is fixed. |

## 6. Test cases

### 6.1 Automated unit and integration tests

| Test class | Level | Tests | Covers |
|---|---|---|---|
| TrainTest | Unit | 15 | Creation, validation, delays, subclass rules, polymorphism |
| PlatformTest | Unit | 13 | Platform validation, defensive copy, Station aggregation, compatibility |
| ScheduleTest | Unit | 10 | Reservations, composition, release, read-only lists |
| OverlapTest | Unit | 12 | Half-open intervals, clearance gap, freight gap, earliest free start |
| TrainMovementTest | Unit | 4 | Movement phases used by the diagrams |
| BoundaryValueTest | Unit | 34 | Boundary value analysis of every numeric rule |
| StrategyOrderTest | Unit | 5 | FCFS and Priority ordering, tie-breaks |
| AgingAndSjfTest | Unit | 6 | Shortest Dwell First, aging formula |
| ScheduleEventManagerTest | Unit | 4 | Observer subscribe, unsubscribe, publish |
| MetricsCalculatorTest | Unit | 11 | Waiting, turnaround, utilization, sweep line, reports |
| DiagramAndClockTest | Unit | 6 | Simulation clock, coordinate mapping, interpolation, painting |
| PlatformAllocatorTest | Integration | 14 | First-Fit, waiting, maximum wait, incompatibility, no double-booking |
| StarvationAndPreemptionTest | Integration | 9 | Starvation under Priority/SJF, aging, safe preemption and rollback |
| DelayAndBreakdownTest | Integration | 11 | Every delay outcome including the ripple; breakdowns |
| ManagersTest | Integration | 10 | Train and platform managers, TrainFactory |
| SchedulingServiceTest | Integration | 8 | Strategy injection and switching, observer events, rules |
| CsvAndUndoTest | Integration | 13 | CSV round trip and errors; undo of allocations and delays |
| ArenaAndGeneratorTest | Integration | 8 | Seeded generator, deep copies, arena, re-planning |
| DemoScenarioTest | Integration | 6 | Every number used in the demo |
| PersistenceTest | Integration | 14 | Schema, round trip, transactions, constraints, injection, audit log, auto-saver |

### 6.2 Automated system tests (`SystemTest`, real window and database file)

| ID | Test case | Steps | Expected result | Result |
|---|---|---|---|---|
| ST-01 | First start | Start on an empty database file | Demo loaded (12 trains, 6 platforms) and saved; "Saved" time set; no warning | Pass |
| ST-02 | Allocate through the window | Allocation screen → click "Allocate all pending trains" | 11 of 12 placed; 11 bookings in the database; ALLOCATION in the audit log | Pass |
| ST-03 | Undo button | Allocate → click "Undo allocation ..." | No bookings in memory or in the database | Pass |
| ST-04 | Restart keeps the session | Allocate → scripted delay → close → start again on the same file | Same number of bookings; 12951 still 5 min late | Pass |
| ST-05 | Scripted delay | Allocate → click "Demo: delay 12951 by 5 min" | 12951 delayed 5 min; DELAY in the audit log | Pass |
| ST-06 | Strategy remembered | Choose "Priority" in the strategy list → restart | Priority in use at once and after the restart | Pass |
| ST-07 | Comparison on copies | Comparison screen → "Run comparison" | 4 rows (one per strategy); live schedule unchanged | Pass |
| ST-08 | Audit log on Reports | Allocate → Reports screen, audit tab | Entries listed newest first (CONFLICT / ALLOCATION at the top) | Pass |
| ST-09 | Live dark mode | Click "Dark mode", then "Light mode" | Theme colours and text colour change; switching back restores light | Pass |
| ST-10 | All screens open | Open each of the ten screens | Every screen shown without an error | Pass |

### 6.3 Performance test

| ID | Scenario | Requirement | Measured | Result |
|---|---|---|---|---|
| PT-01 | 500 generated trains (seed 2026), FCFS | Allocation < 1 s (NFR-P1) | 86 ms (424 bookings) | Pass |
| PT-01 | Save the 500-train session to a file | Save < 1 s (NFR-P2) | 275 ms | Pass |
| PT-01 | Restore it into a new service | Restore < 1 s (NFR-P3) | 34 ms | Pass |
| ArenaAndGeneratorTest | 500 trains through all four strategies | < 10 s (NFR-P1) | about 0.2 s | Pass |

Times are from one run on the test machine and vary slightly between runs.

### 6.4 Manual system test cases (dialog flows)

These cases involve confirmation or message dialogs, so they are run by a person before each submission or
demonstration and recorded in the execution record (section 8).

| ID | Test case | Steps | Expected result |
|---|---|---|---|
| M-01 | Invalid train times | Trains → Add train; arrival 10:00, departure 09:50; OK | Message "departure must be after arrival"; form stays open; nothing added |
| M-02 | Duplicate train number | Add a train with number 12951 | Message about the duplicate number; nothing added |
| M-03 | Remove an allocated train | Allocate; select 12951; Remove; confirm | Refused: the train holds a platform |
| M-04 | Remove a free train | Release a train's platform; Remove; confirm | Train removed; table and dashboard updated; saved |
| M-05 | Report a breakdown | Allocate; Delay & Breakdown → PF-2 → Report breakdown → Yes | PF-2 FAILED; S-201 moved to PF-4; others reported with reasons; red status bar |
| M-06 | Cancel a breakdown | Report breakdown → No | Nothing changes |
| M-07 | Repair a platform | Platforms → select failed PF-2 → Repair | PF-2 OPERATIONAL again |
| M-08 | Edit a platform that would break a booking | Allocate; edit PF-1 length to 300 m | Refused with the train that would no longer fit |
| M-09 | Open an invalid CSV | Open CSV → a file with a bad line 3 | Message naming line 3; current data unchanged |
| M-10 | Save and reopen CSV | Save CSV → Load demo → Open the saved file → Yes | Same trains and platforms as saved; nothing allocated |
| M-11 | Load the demo scenario | Load demo scenario → Yes | 12 trains, 6 platforms, nothing allocated; information message |
| M-12 | Nothing to undo | Load demo; press Ctrl+Z | Message "Nothing to undo" |
| M-13 | Database cannot be used | Start with `-Drailsync.db` pointing at a read-only or invalid location | Warning shown; the app works without saving; sidebar says not saved |
| M-14 | Copy the report | Reports → Copy report; paste into a text editor | The full report text is pasted |

## 7. Acceptance tests (per use case)

| Use case | Accepted when | Evidence |
|---|---|---|
| UC-01 Allocate trains | Demo FCFS places 11 of 12, F-999 refused with reasons, no overlaps | DemoScenarioTest, ST-02 |
| UC-02 Delay a train | 12951 +5 min ripples L-102 and 12909, all changes listed, undoable | DemoScenarioTest, ST-05, CsvAndUndoTest |
| UC-03 Breakdown | PF-2 failure moves S-201 to PF-4 and reports the others | DemoScenarioTest, M-05 |
| UC-04 Add a train | Valid trains added; every invalid class refused with a reason | TrainTest, BoundaryValueTest, M-01, M-02 |
| UC-05 Compare strategies | Four rows, live data unchanged, 500 trains in well under 10 s | ArenaAndGeneratorTest, ST-07 |
| UC-06 Undo | Allocation and delay (with ripple) fully reversed | CsvAndUndoTest, ST-03 |
| UC-07 Restore session | Same plan after restart; damaged data refused | PersistenceTest, ST-04, ST-06 |
| UC-08 Import CSV | Valid file loaded; invalid file refused with its line | CsvAndUndoTest, M-09, M-10 |
| UC-09 Audit log | Every change listed, newest first | PersistenceTest, ST-08 |

## 8. Test report (latest run)

| Measure | Result |
|---|---|
| Automated tests | **224 run, 224 passed, 0 failed, 0 skipped** (20 test classes, plus 10 system tests) |
| By level | Unit 120 · Integration 93 · System 10 · Performance 1 |
| Requirements covered | 45 of 45 functional requirements have at least one passing automated test (traceability matrix) |
| Line coverage (JaCoCo) | Logic packages (model, strategy, service, persistence, observer): **95.9%**; whole program 77% (screens 71%) |
| Branch coverage | Logic packages 89%; whole program 69% |
| Defects found during testing | See section 9 |

**Manual system test execution record** (to be filled in by the tester for each submission build):

| ID | Tester | Date | Build | Result (Pass / Fail) | Notes |
|---|---|---|---|---|---|
| M-01 to M-14 | | | | | |

## 9. Defects found and fixed

Defects found by testing during development (all fixed and covered by a test):

| # | Found by | Defect | Fix |
|---|---|---|---|
| 1 | PersistenceTest (newer schema) | A database file refused by the version check stayed open and locked. | `Database` now closes the connection whenever preparing the file fails. |
| 2 | Visual system check (dark mode) | Switching to dark mode while the app was open left light colours on many components. | Theme colours are now stored as look-and-feel resources so Swing replaces them; covered by ST-09. |
| 3 | Visual check on a 1536 x 864 screen | The window was taller than the screen. | The window size is limited to the usable screen area and opens maximised on small screens. |
| 4 | Visual check (tables) | Status and type badges were cut off in narrow columns. | Minimum column widths for badge columns. |
| 5 | Visual check (high-DPI screen, first UI refresh) | Fractional font sizes clipped some labels at 125% scaling. | Fonts are set through FlatLaf styles, which scale correctly. |

## 10. How to run the tests

```bash
mvnw.cmd test
```

- The console prints every test class and result. The coverage report is written to
  `target/site/jacoco/index.html`.
- `run-tests.cmd` does the same with a short summary.
- The system tests need a desktop session; on a server without a screen they are skipped automatically.
