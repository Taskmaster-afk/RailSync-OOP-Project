# RailSync: Railway Platform Allocation and Scheduling Simulator

RailSync is a Java desktop simulator that assigns arriving and departing trains to station platforms without
time conflicts, and repairs the plan when trains are delayed or platforms break down.

**The idea behind it:** platforms behave like **CPUs**, trains like **processes**, a train's stay at the platform
like **burst time**, and the allocator like an **OS scheduler**. The four allocation policies mirror OS scheduling
algorithms (FCFS, Priority, Shortest-Job-First, Priority with Aging), and trains that wait at the outer signal form
a visible **ready queue**.

Because every train has fixed times, the real problem is closer to *interval scheduling* than to CPU scheduling,
and all four policies are greedy heuristics: none is globally optimal. RailSync is an educational simulation, not a
real signalling or safety system.

Built for an Object-Oriented Programming / Object-Oriented Modelling course. The code is written to be studied:
every class has a header comment, every public method has Javadoc, every algorithm has numbered step comments, and
OOP concepts are tagged inline (`// [POLYMORPHISM]`, `// [COMPOSITION]`, `// [SOLID-D]` ...).

![Time-distance diagram after a delay ripple](docs/screenshots/final-delay-after.png)

## Features

| Module | What you can do |
|---|---|
| A. Trains | Add, edit, search by number, remove (only if it has no platform), view its schedule. Express / Passenger / Freight behave differently (priority bonus, length, minimum stop, clearance). |
| B. Platforms | Add, edit (refused if it would break a booking), remove (only if empty), repair. Compatibility check lists every train with OK or the exact reason. |
| C. Allocation | Places trains with First-Fit, half-open intervals `[start, end)` and a configurable clearance buffer (default 5 min). Trains may wait at the outer signal up to a configurable maximum. Every refusal is explained. |
| D. Strategies | FCFS, Priority, Shortest Dwell First (SJF), Priority + Aging (configurable rate), and optional safe preemption. Switch strategy in the GUI. |
| E. Delays | Delay a train: kept, moved, **ripple** (following trains pushed, cascade reported) or a clear failure. Nothing changes silently. |
| E2. Breakdowns | Mark a platform failed: every train on it is moved if possible, and each one that can't move is reported with the reason. |
| F. Metrics | Waiting time, turnaround, averages, utilization per platform, conflicts, and the **sweep-line** minimum number of platforms. |
| F2. Comparison arena | The same trains through all four strategies on independent copies, side by side. Seeded random generator (10-500 trains). |
| Visualization | Time-distance / Gantt diagram, simulation clock (1x/10x/60x), 500 ms ghost-and-slide animation for delays and breakdowns, top-down yard view with a live ready queue. |
| Extras | CSV save/open, Undo (Ctrl+Z) for allocations and delays, light/dark theme, console demo. |

## Tech stack

Java 21 (runs on any JDK 21+), Swing, Maven (via the included Maven Wrapper), JUnit 5, FlatLaf (look and feel only).
In-memory collections only: no database, network, frameworks or threads. Animation uses Swing Timers.

## Project structure

```
pom.xml, mvnw, mvnw.cmd          build (Maven Wrapper: no Maven install needed)
run-app.cmd                      build and open the desktop app
run-tests.cmd                    build and run all JUnit tests
run-demo.cmd                     text-only demo of the model and services
src/main/java/com/railsync/
  Main.java                      starts the GUI (wires the objects together)
  ConsoleDemo.java               console showcase of stages 1-4
  exception/                     RailSyncException and 4 specific exceptions
  model/                         Train hierarchy, Platform, Station, Schedule, PlatformReservation,
                                 ReservationChange, TrainMovement, TrainFactory, enums
  strategy/                      PlatformAllocationStrategy + FCFS, Priority, SJF, Aging, catalog
  observer/                      ScheduleObserver, ScheduleEvent(Type), ScheduleEventManager
  service/                       SchedulingService (facade), PlatformAllocator, DelayManager,
                                 TrainManager, PlatformManager, MetricsCalculator, ReportGenerator,
                                 StrategyArena, ScenarioGenerator, ScenarioCsv, undo commands ...
  ui/                            Swing screens, diagrams, dialogs, table models
src/test/java/com/railsync/      165 JUnit tests (+ ScreenshotMaker developer tool)
docs/                            this documentation, UML, screenshots, sample CSV
```

## Setup and run (Windows)

You need a JDK, version 21 or newer, on the `PATH`. Check with:

```bash
java -version
```

Then, in the project folder, either double-click `run-app.cmd`, or run:

```bash
mvnw.cmd -B package
```

```bash
java -jar target\railsync-1.0.0.jar
```

The first build downloads Maven, JUnit and FlatLaf from Maven Central (one time only).

Other commands:

```bash
mvnw.cmd test
```

```bash
java -cp target\classes com.railsync.ConsoleDemo
```

To browse all the documentation (README, guides, rendered UML diagrams, screenshots) as a small website,
double-click `preview-docs.cmd`. It serves the project on http://localhost:8080/docs/ with the JDK's built-in
`jwebserver` (Java 18+) and opens your browser; press Ctrl+C in its window to stop.

## Using it

The app starts with the demo scenario loaded but not allocated. A five-minute tour:

1. **Allocation**: keep FCFS and press *Allocate all pending trains*. 11 of 12 trains are placed and F-999 is refused (it fits no platform).
2. Press *Reset all allocations*, choose **Priority**, and allocate again. Now 12002 gets PF-2 at 08:15 and L-102 loses out.
3. **Time-Distance**: see every train's path; red = waiting in the queue. Press *Play*.
4. Reset, allocate with FCFS, then **Delay & Breakdown**: *Demo: delay 12951 by 5 min* shows a ripple of 2 trains, with ghosts. Press **Undo** (Ctrl+Z) to take it back.
5. *Demo: break down PF-2*: S-201 moves to PF-4, and the others are reported as stranded.
6. **Yard View**: press Play and watch the ready queue; *Switch strategy from now* re-plans the future.
7. **Comparison**: run all strategies on the demo or on a generated scenario of up to 500 trains.

See [docs/USER_GUIDE.md](docs/USER_GUIDE.md) for every screen.

## Documentation

| File | Contents |
|---|---|
| [docs/OOP_CONCEPTS.md](docs/OOP_CONCEPTS.md) | Every OOP concept and SOLID principle, with the exact classes, methods and line tags |
| [docs/DESIGN_PATTERNS.md](docs/DESIGN_PATTERNS.md) | Strategy, Observer, Factory, Command, with code excerpts |
| [docs/UML_DIAGRAMS.md](docs/UML_DIAGRAMS.md) | Use-case, class, two sequence diagrams and an activity diagram (Mermaid) |
| [docs/CLASS_EXPLANATION.md](docs/CLASS_EXPLANATION.md) | Every class: purpose, attributes, methods, relationships |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | How to use each screen |
| [docs/VIVA_GUIDE.md](docs/VIVA_GUIDE.md) | Study order, likely questions with answers, trade-offs, limitations |
| [docs/PROGRESS.md](docs/PROGRESS.md) | Stage-by-stage build log with every design decision |
