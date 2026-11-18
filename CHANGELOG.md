# Changelog

All notable changes to RailSync. Versions follow *major.minor.patch* (see docs/se/MAINTENANCE.md).

## 1.1.0

### Added
- **SQLite storage:** the whole session (platforms, trains, delays, bookings, failed platforms, given-up trains,
  strategy and settings) is saved automatically after every change to `~/.railsync/railsync.db` and restored at
  start-up. Location can be changed with `-Drailsync.db=path`.
- **Audit log:** every change is recorded with a timestamp in an append-only table, shown on
  Reports → Audit log (database).
- `persistence` package: `Database` (connection, versioned schema), one DAO per table, `StationRepository` and
  `AuditLog` interfaces with SQLite implementations, `AutoSaver` (an observer).
- System tests that drive the real window and database (`SystemTest`), a performance test for 500 trains, and
  boundary value tests (`BoundaryValueTest`); test coverage reports with JaCoCo.
- Software engineering documents in `docs/se/`: SRS, requirement analysis, architecture, test plan, quality
  assurance, project plan, maintenance plan, contributions.
- UML: three state diagrams, component, package, deployment and ER diagrams, persistence class diagram, and a
  sequence diagram for saving.

### Changed
- **New interface design:** navy sidebar with icons and the signal-light logo, rounded cards on a soft page,
  dashboard tiles with icons, a colour-coded event log, status badges in tables, a "Saved" indicator, and
  consistent light and dark themes.
- The window now fits the screen (opens maximised on small screens).
- `Main.build()` wires the whole application, so tests can start it on a temporary database.

### Fixed
- Switching to dark mode while the app was open left light colours on many components.
- A database file refused by the version check stayed locked.

## 1.0.0

- First complete version: train and platform management, four allocation strategies with preemption, delays
  with ripple, breakdowns, metrics and sweep line, time-distance diagram with animation, yard view, strategy
  comparison arena, CSV import and export, undo, light and dark themes, 165 tests, OOP documentation and UML.
