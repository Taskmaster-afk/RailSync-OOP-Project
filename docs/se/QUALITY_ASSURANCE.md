# Software Quality Assurance

This document describes how the quality of RailSync is defined, built in and checked. It follows the quality
characteristics of ISO/IEC 25010 that matter for this project: **reliability, maintainability, security,
usability and performance**, plus portability. For each one it gives the goal, the measures taken and the
evidence.

---

## 1. Quality plan in one page

| Characteristic | Goal | Main measures | Evidence (latest run) |
|---|---|---|---|
| Reliability | Never an impossible plan; no data loss | Single booking gate, validation everywhere, transactional saves, re-checked restore | 224/224 tests pass; fault-injection tests |
| Maintainability | Easy to change and extend | Layers, interfaces, patterns, documentation standard, tests | 95.9% line coverage of logic; 0 TODO/FIXME |
| Security | Data cannot be corrupted by input | Parameterised SQL, database constraints, full CSV validation, no network | Injection and constraint tests |
| Usability | Clear, consistent, forgiving | Consistent screens, explanations, confirmations, undo, themes | Task walkthrough; heuristic review |
| Performance | No noticeable waiting | Event-driven allocator, one transaction per save | 500 trains: 86 ms allocate, 275 ms save, 34 ms restore |
| Portability | Runs anywhere Java runs | Pure Java, embedded SQLite, Maven Wrapper | Builds with only a JDK |

---

## 2. Reliability

**Goal:** the plan shown is always valid, nothing changes silently, and saved data is never lost or corrupted.

| Measure | How it works |
|---|---|
| One gate for bookings | Every booking is created by `Schedule.reserve()`, which checks compatibility, arrival time and the overlap rule. Even a faulty strategy cannot double-book. |
| Validation in constructors | `Train` and `Platform` reject invalid values, so an invalid object can never exist. |
| Nothing changes silently | Every move is recorded as a `ReservationChange` and reported; every refusal has a reason. |
| Atomic saves | A save runs in one SQLite transaction; if it fails part-way it is rolled back and the previous save stays complete. |
| Restore re-checks everything | Saved bookings pass through `Schedule.reserve()` again; damaged data is refused and the user is warned. |
| Graceful degradation | If the database cannot be opened, the app runs in memory and says so; if a save fails, the change still succeeds in memory and the sidebar shows "Save failed". |
| No concurrency | No extra threads (Swing Timers only), so there are no race conditions. |

Evidence: `OverlapTest`, `PlatformAllocatorTest` (many trains on two platforms never break the buffer rule), `PersistenceTest`
(transactions, damaged data, closed database), system tests ST-01 to ST-10.

---

## 3. Maintainability

**Goal:** a new team member can understand, change and extend the code safely.

### 3.1 Measures

- **Modularity:** seven packages with one-way dependencies; the screens contain no business rules; only the
  `persistence` package knows SQL.
- **Extensibility through interfaces:** new strategy = one class + one line (`StrategyCatalog`); new storage =
  one class implementing `StationRepository`; new screen = one `RefreshablePanel` subclass + one line in
  `MainFrame`.
- **Coding standard:** every class has a header comment (purpose, OOP concepts, collaborators); every public
  method has Javadoc; algorithms have numbered `// Step N:` comments; OOP concepts are tagged inline
  (`// [POLYMORPHISM]`, `// [SOLID-D]`).
- **Tests as a safety net:** the automated suite runs in under a minute; every test states its scenario and
  expected result.
- **Versioned database schema:** upgrades are written as numbered steps in `Database.migrate()`.

### 3.2 Metrics

| Metric | Value |
|---|---|
| Size | 92 production classes, about 12,600 lines; 23 test files, about 3,500 lines |
| Line coverage, logic packages | 95.9% (model 96.9%, service 96.4%, strategy 95.7%, persistence 92.7%, observer 97.1%) |
| Line coverage, whole program | 77% (Swing screens 71%, covered by the system tests) |
| Branch coverage, logic / whole | 89% / 69% |
| Compiler warnings (`javac -Xlint:all`) | Only two kinds, both accepted for Swing: `serial` (screens have no serialVersionUID; they are never serialised) and `this-escape` (screens subscribe to events in their constructors, by design). No unchecked, raw-type, deprecation or fall-through warnings. |
| TODO / FIXME in the code | 0 |
| Largest class | `TimeDistanceDiagram` (about 490 lines, mostly drawing code) |

---

## 4. Security

RailSync is a single-user desktop program without network access, so the main risks are **corrupted data**
(from damaged files or unexpected input) and **loss of accountability**. Measures:

| Threat | Measure | Evidence |
|---|---|---|
| SQL injection through names or numbers | Every statement uses `PreparedStatement` parameters; no user text is ever concatenated into SQL. | PersistenceTest: a train named `Robert'); DROP TABLE trains; --` is stored as plain text |
| Invalid rows written by other tools | The schema has CHECK, NOT NULL, UNIQUE and foreign-key constraints; foreign keys are switched on for every connection. | PersistenceTest.databaseConstraintsRejectInvalidRows |
| Damaged or hand-edited database | Restore re-validates every value and booking; refused data is not shown. | PersistenceTest.restoreRejectsClashingBookings |
| Malformed CSV files | The whole file is validated before anything is replaced; errors name the line. | CsvAndUndoTest |
| Database from a newer version | Refused with a clear message; the file is not modified. | PersistenceTest.newerSchemaIsRefused |
| Unbounded input filling the disk | Audit messages are limited to 2,000 characters; scenarios to 500 trains. | PersistenceTest.auditLogIsNewestFirstAndBounded |
| Silent changes | Append-only audit log of every change, with timestamps. | ST-08 |
| Data exposure | No network code at all; data stays in the user's home folder with the operating system's file permissions. | Architecture review |

Known limitation: the database file is not encrypted and the app has no user accounts. This is acceptable for a
single-user planning tool, and is listed as future work in [MAINTENANCE.md](MAINTENANCE.md).

---

## 5. Usability

### 5.1 Heuristic review (Nielsen's ten heuristics)

| Heuristic | How RailSync meets it |
|---|---|
| Visibility of system status | Status bar with the latest event (red for problems); "Saved hh:mm:ss" in the sidebar; live dashboard numbers. |
| Match with the real world | Railway terms (outer signal, platform, dwell, delay, breakdown); times typed as `HH:mm`. |
| User control and freedom | Undo (Ctrl+Z) for allocations and delays; reset allocations; confirmations before destructive actions. |
| Consistency and standards | Every screen: same header, cards, one primary (blue) button; the same colours for train types everywhere. |
| Error prevention | Spinners with limits; strategy chosen from a list; refused edits that would break bookings. |
| Recognition rather than recall | Sidebar always visible; the Undo button says what it will undo; the strategy description is shown. |
| Flexibility and efficiency | Keyboard shortcut Ctrl+Z; scripted demo buttons; generated scenarios. |
| Aesthetic and minimalist design | Calm palette, cards, no clutter; details in reports rather than on every screen. |
| Help users recover from errors | Messages say what is wrong and how to fix it (for example which line of a CSV file). |
| Help and documentation | User guide, the one-line purpose under every title, tooltips on actions. |

### 5.2 Task walkthrough (clicks from the Dashboard)

| Task | Clicks |
|---|---|
| Allocate all trains | 2 (Allocation, Allocate all pending trains) |
| Undo the last action | 1 (Undo) or Ctrl+Z |
| Delay a train | 3 (Delay & Breakdown, choose train, Apply delay) plus typing the minutes |
| Compare strategies | 2 (Comparison, Run comparison) |
| Switch to dark mode | 1 |

All main tasks need at most 3 clicks (NFR-U2).

---

## 6. Performance

| Scenario | Requirement | Measured |
|---|---|---|
| Allocate 500 trains (FCFS) | < 1 s | 86 ms |
| 500 trains through all four strategies | < 10 s | about 0.2 s |
| Save a 500-train session to a file | < 1 s | 275 ms |
| Restore a 500-train session | < 1 s | 34 ms |
| Demo scenario: any action | Instant to the user | well under 0.1 s |

The allocator jumps from event to event instead of stepping minute by minute, and the database writes each
session in a single transaction with batched inserts.

---

## 7. Quality control activities

| Activity | When | What |
|---|---|---|
| Automated test run | After every change | `mvnw test`: all 224 tests and the coverage report |
| Code review | Before a change is accepted into the main version | Checklist below; at least one other member reads each change |
| Visual review | After every UI change | Screenshots of all screens in light and dark mode (`ScreenshotMaker`) |
| Documentation review | At each milestone | SRS, design and test documents updated with the code |
| Manual system tests | Before each submission or demonstration | M-01 to M-14 in the test plan |

### 7.1 Code review checklist

- Does the change keep business rules out of the `ui` package and SQL out of everything but `persistence`?
- Are all new fields private, and is every new value validated before it is stored?
- Does every new public method have Javadoc, and does every algorithm have step comments?
- Is there a test with the exact expected value for every new rule, including a boundary case?
- Can the change produce a booking without going through `Schedule.reserve()`? (It must not.)
- Does every outcome the user sees have a plain-English reason?
- Are SQL values passed as parameters?
- Do light and dark mode both still look right?

### 7.2 Configuration management

- All source, documents and diagrams are kept in one Git repository; each feature is one commit with a
  descriptive message.
- The build is reproducible through the Maven Wrapper (fixed Maven and plugin versions in `pom.xml`).
- Library versions are fixed in `pom.xml` (no version ranges).
