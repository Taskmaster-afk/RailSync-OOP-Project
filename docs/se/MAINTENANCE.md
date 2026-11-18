# Software Maintenance Plan

This document explains how RailSync can be modified, extended and kept working after it is delivered: the kinds
of maintenance expected, step-by-step guides for the most likely changes, how the database is upgraded, how
versions and defects are managed, and the known limitations that future versions could address.

---

## 1. Kinds of maintenance

| Kind | Meaning | Examples for RailSync |
|---|---|---|
| Corrective | Fixing defects found after delivery | A train edit that leaves a stale booking; a label cut off on a small screen |
| Adaptive | Keeping the software working in a changed environment | A new Java version; a new SQLite driver; a different operating system; a new data format from a railway |
| Perfective | Adding or improving features | A fifth allocation strategy; multi-day timetables; a new report; exporting to PDF |
| Preventive | Making future changes easier and safer | More tests for the screens; splitting the largest classes; upgrading libraries before they become outdated |

---

## 2. Why the design makes maintenance easier

| Design property | Effect on maintenance |
|---|---|
| Layers with one-way dependencies | A change in the screens cannot break the rules; a change in storage cannot break the screens. |
| One facade for the GUI | New features are added as one service method plus one screen action. |
| Interfaces at the variation points (strategy, repository, audit log, observer) | New behaviour is a new class; existing classes are not edited (Open/Closed). |
| One gate for bookings (`Schedule.reserve`) | Rules are changed in one place, and every feature obeys them automatically. |
| Tests with exact expected values | A change that breaks behaviour is caught within a minute. |
| Documentation standard and tags | Every class says what it does and which concepts it shows; algorithms have numbered steps. |
| Versioned schema | Old database files are upgraded automatically. |

---

## 3. How to make common changes

### 3.1 Add a fifth allocation strategy

1. Create a class in `strategy` that extends `QueueOrderingStrategy` and implements `getName()`,
   `getDescription()` and `compareByPolicy(a, b, now)`.
2. Add one line to `StrategyCatalog.createAll()` (and to the strategy list in `AllocationPanel` if it should be
   selectable there).
3. Add tests to `StrategyOrderTest` with a small scenario whose correct order you know.
4. Nothing else changes: the allocator, the comparison arena, saving and restoring (by name) all work at once.

### 3.2 Add a new kind of train (for example "Suburban")

1. Add the value to `TrainType`.
2. Create `SuburbanTrain extends Train` and implement the four abstract methods (priority, required length,
   minimum stop, type); override `getClearanceBufferMinutes` only if it needs more clearance.
3. Add one case to `TrainFactory.create()` and a colour in `Ui.colorFor()`.
4. Add the value to the `type` CHECK constraint with a schema migration (section 4).
5. Add tests to `TrainTest` and `BoundaryValueTest` for its minimum stop.

### 3.3 Add a new screen

1. Create a class in `ui` that extends `RefreshablePanel` and implements `refresh()`; it is subscribed to events
   automatically.
2. Use `Ui.header(...)` for the title, `Ui.section(...)` or `Ui.card(...)` for content, and `Ui.primary(...)`
   for the main button.
3. Add one line in `MainFrame` with an icon from `src/main/resources/com/railsync/ui/icons`.
4. Add a check to `SystemTest.st10EveryScreenOpens` (the screen count) and, if it has buttons, a system test.

### 3.4 Add a setting that is saved

1. Add the field to `StationSnapshot.Settings` and fill it in `SchedulingService.takeSnapshot()`.
2. Apply it in `SchedulingService.restore()`.
3. Add a key in `SqliteStationRepository` (settings are a key/value table, so **no schema change** is needed).
4. Extend `PersistenceTest.roundTripRestoresTheWholeSession`.

### 3.5 Replace SQLite with another database

Write one class implementing `StationRepository` and one implementing `AuditLog` (for example for PostgreSQL or a
cloud service), and create them in `Main.build()` instead of the SQLite ones. No other class changes, because
the services and screens only know the interfaces.

---

## 4. Database maintenance

### 4.1 Changing the schema

The schema version is stored in the database file (`PRAGMA user_version`). To change the schema:

1. Raise `Database.SCHEMA_VERSION` (for example to 2).
2. Add a step at the end of `Database.migrate()`:
   `if (version < 2) { statement.execute("ALTER TABLE ..."); statement.execute("PRAGMA user_version = 2"); }`
3. Existing files are upgraded automatically the next time they are opened; new files get every step.
4. Add a test that creates a version-1 file, opens it with the new code and checks the result.

Never edit an earlier step: files already upgraded would not run it again. A file written by a newer version
than the program understands is refused rather than damaged.

### 4.2 Backup, reset and moving the data

| Task | How |
|---|---|
| Find the data | `C:\Users\<name>\.railsync\railsync.db` on Windows; `~/.railsync/railsync.db` on macOS and Linux |
| Back up | Close RailSync and copy the `.db` file. |
| Start fresh | Close RailSync and delete or rename the file; the demo scenario is loaded on the next start. |
| Use another file | Start with `java -Drailsync.db=D:\plans\station2.db -jar target\railsync-1.1.0.jar` |
| Inspect the data | Open the file with any SQLite tool (for example DB Browser for SQLite or the `sqlite3` command). |
| Exchange scenarios | Use Save CSV and Open CSV (the CSV holds trains and platforms, not bookings). |

---

## 5. Versions and releases

- **Version numbers** follow *major.minor.patch*: patch for bug fixes, minor for new features that keep old
  database files working, major for changes that need a manual step. The version is set in `pom.xml`.
- **Changelog:** every release is described in [CHANGELOG.md](../../CHANGELOG.md).
- **Release steps:** run all tests (`mvnw.cmd test`); run the manual system tests in the test plan; update the
  version and the changelog; build (`mvnw.cmd -B package`); hand over `target\railsync-<version>.jar` with the
  `target\lib` folder.
- **Dependencies:** library versions are fixed in `pom.xml`. Check for updates of FlatLaf, the SQLite driver,
  JUnit and JaCoCo once per release; update one at a time and run all tests after each.

---

## 6. Defect handling

1. **Report:** what was done (steps), what happened, what was expected, the screen, and if possible the audit log
   entries around that time (Reports → Audit log) and the CSV of the scenario.
2. **Reproduce:** load the scenario and repeat the steps.
3. **Write a failing test** that shows the defect (unit test if possible, system test if it is a screen problem).
4. **Fix** the smallest possible piece of code, keeping business rules out of the screens.
5. **Run all tests**, update the documentation if behaviour changed, and record the fix in the changelog.

Severity: **high** = wrong or lost plan data, crash, or a double booking; **medium** = a feature does not work but
there is a workaround; **low** = cosmetic.

---

## 7. Known limitations and future work

| Limitation | Possible improvement |
|---|---|
| All strategies are greedy heuristics | Add an optimal assignment (integer programming) to measure how far each heuristic is from the best result. |
| One service day, minute precision | Multi-day and recurring timetables. |
| No track or route model | Model the station throat and conflicting routes. |
| One user, unencrypted local file | User accounts with roles; an encrypted database or a server database through a new `StationRepository`. |
| Undo covers allocations and delays only | Extend the Command pattern to edits and breakdowns. |
| The sweep-line minimum ignores types and lengths | A per-type sweep line. |
| Screens are tested by system tests, not unit tests | More automated screen tests for dialog flows (the manual cases M-01 to M-14). |
