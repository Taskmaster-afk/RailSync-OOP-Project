# RailSync UML Diagrams

All diagrams are written in **Mermaid**. They render automatically on GitHub, in VS Code (with a Mermaid
extension) and at https://mermaid.live (paste the code). Every class, method and relationship shown exists in the
final code; nothing is fictional. To keep the diagrams readable, getters and small helpers are left out.

---

## 1. Use-case diagram

Mermaid has no dedicated use-case syntax, so actors are drawn as nodes and use cases as rounded boxes.

```mermaid
flowchart LR
    Controller(["Station controller (user)"])
    Examiner(["Student / examiner (user)"])

    subgraph RailSync
        UC1([Manage trains])
        UC2([Manage platforms])
        UC3([Allocate trains with a strategy])
        UC4([Delay a train])
        UC5([Report platform breakdown])
        UC6([View schedule and time-distance diagram])
        UC7([Watch yard view / run clock])
        UC8([Compare strategies])
        UC9([View reports and metrics])
        UC10([Save / open scenario CSV])
        UC11([Undo last allocation or delay])
        UC12([Load demo scenario])
        UC13([Explain why a train has no platform])
        UC14([Resolve ripple / reassign trains])
        UC15([View audit log])
        UC16([Save session automatically])
    end
    DB[("SQLite database (secondary actor)")]

    Controller --- UC1
    Controller --- UC2
    Controller --- UC3
    Controller --- UC4
    Controller --- UC5
    Controller --- UC6
    Controller --- UC7
    Controller --- UC11
    Controller --- UC10
    Examiner --- UC8
    Examiner --- UC9
    Examiner --- UC12
    Examiner --- UC6
    UC3 -. includes .-> UC13
    UC4 -. includes .-> UC14
    UC5 -. includes .-> UC14
    Examiner --- UC15
    Controller --- UC15
    UC1 -. includes .-> UC16
    UC3 -. includes .-> UC16
    UC4 -. includes .-> UC16
    UC16 --- DB
    UC15 --- DB
```

**In words:** the controller manages trains and platforms, allocates, delays trains and reports breakdowns.
Allocation always *includes* explaining refusals; delays and breakdowns *include* the shared reassignment logic.
The examiner (or student) mainly loads the demo, compares strategies and reads the reports and diagrams.
Every change *includes* saving the session automatically; the SQLite database is a secondary actor that stores
the session and the audit log.

---

## 2. Class diagram

```mermaid
classDiagram
    direction TB

    class Train {
        <<abstract>>
        -String id
        -String number
        -String name
        -LocalDateTime scheduledArrival
        -LocalDateTime scheduledDeparture
        -int basePriority
        -int carriageCount
        -int delayMinutes
        -TrainStatus status
        -Platform assignedPlatform
        +getType()* TrainType
        +getPriority()* int
        +getRequiredPlatformLength()* int
        +getMinimumDwellMinutes()* int
        +getClearanceBufferMinutes(int base) int
        +getIncompatibilityReason(Platform) String
        +canUsePlatform(Platform) boolean
        +updateTimes(LocalDateTime, LocalDateTime)
        +applyDelay(int)
        +reduceDelay(int)
        +getExpectedArrival() LocalDateTime
        +getDwellTime() Duration
        ~assignPlatform(Platform)
        ~clearPlatform()
    }
    class ExpressTrain {
        +getPriority() int
        +getRequiredPlatformLength() int
        +getMinimumDwellMinutes() int
    }
    class PassengerTrain {
        +getPriority() int
        +getRequiredPlatformLength() int
        +getMinimumDwellMinutes() int
    }
    class FreightTrain {
        +getPriority() int
        +getRequiredPlatformLength() int
        +getMinimumDwellMinutes() int
        +getClearanceBufferMinutes(int base) int
    }
    Train <|-- ExpressTrain
    Train <|-- PassengerTrain
    Train <|-- FreightTrain

    class Platform {
        -String id
        -int number
        -int lengthMeters
        -PlatformStatus status
        -Set~TrainType~ supportedTypes
        +supports(TrainType) boolean
        +isOperational() boolean
        +markFailed()
        +markOperational()
    }
    class Station {
        -String name
        -List~Platform~ platforms
        +addPlatform(Platform)
        +removePlatform(Platform) boolean
        +findPlatformById(String) Platform
    }
    class Schedule {
        -List~PlatformReservation~ reservations
        -Map~String,PlatformReservation~ reservationByTrainId
        -int clearanceBufferMinutes
        +reserve(Train, Platform, LocalDateTime) PlatformReservation
        +release(Train) boolean
        +findConflict(Train, Platform, LocalDateTime) PlatformReservation
        +findAllConflicts(Train, Platform, LocalDateTime) List
        +earliestFreeStart(Train, Platform, LocalDateTime) LocalDateTime
        +requiredGapMinutes(Train, Train) int
        +overlaps(...)$ boolean
    }
    class PlatformReservation {
        -Train train
        -Platform platform
        -LocalDateTime start
        -LocalDateTime end
    }
    Station "1" o-- "0..*" Platform : aggregation
    Schedule "1" *-- "0..*" PlatformReservation : composition
    PlatformReservation "0..*" --> "1" Train
    PlatformReservation "0..*" --> "1" Platform
    Train "0..*" --> "0..1" Platform : assignedPlatform (association)

    class PlatformAllocationStrategy {
        <<interface>>
        +getName() String
        +getDescription() String
        +orderReadyQueue(List~Train~, LocalDateTime) List~Train~
    }
    class QueueOrderingStrategy {
        <<abstract>>
        +orderReadyQueue(List~Train~, LocalDateTime) List~Train~
        #compareByPolicy(Train, Train, LocalDateTime)* int
    }
    class FcfsStrategy
    class PriorityStrategy
    class ShortestDwellFirstStrategy
    class PriorityAgingStrategy {
        -int minutesPerPriorityPoint
        +getEffectivePriority(Train, LocalDateTime) int
    }
    PlatformAllocationStrategy <|.. QueueOrderingStrategy
    QueueOrderingStrategy <|-- FcfsStrategy
    QueueOrderingStrategy <|-- PriorityStrategy
    QueueOrderingStrategy <|-- ShortestDwellFirstStrategy
    QueueOrderingStrategy <|-- PriorityAgingStrategy

    class SchedulingService {
        -PlatformAllocationStrategy strategy
        -CommandHistory history
        -int conflictsDetected
        +setStrategy(PlatformAllocationStrategy)
        +allocatePendingTrains() List~AllocationResult~
        +delayTrain(Train, int) DelayReport
        +reportBreakdown(Platform) BreakdownReport
        +reallocateFrom(LocalDateTime) List~AllocationResult~
        +undoLast() String
        +calculateMetrics() ScheduleMetrics
    }
    class PlatformAllocator {
        -int maxWaitMinutes
        -boolean preemptionEnabled
        +allocateAll(List~Train~, PlatformAllocationStrategy) List~AllocationResult~
        +allocateSingle(Train) AllocationResult
        +findFirstFreePlatform(Train, LocalDateTime) Platform
    }
    class DelayManager {
        +delayTrain(Train, int) DelayReport
        +handleBreakdown(Platform) BreakdownReport
    }
    class TrainManager {
        -List~Train~ trains
        -Map~String,Train~ trainsByNumber
        +addTrain(Train)
        +removeTrain(Train)
        +findByNumber(String) Train
    }
    class PlatformManager {
        +addPlatform(Platform)
        +removePlatform(Platform)
        +updatePlatform(Platform, int, Set)
    }
    class MetricsCalculator {
        +calculate(Station, Schedule, List~Train~, int) ScheduleMetrics
        +minimumPlatformsNeeded(List~Train~, int) int
    }
    SchedulingService *-- TrainManager
    SchedulingService *-- PlatformManager
    SchedulingService *-- PlatformAllocator
    SchedulingService *-- DelayManager
    SchedulingService *-- MetricsCalculator
    SchedulingService o-- Station
    SchedulingService o-- Schedule
    SchedulingService --> PlatformAllocationStrategy : injected
    PlatformAllocator ..> PlatformAllocationStrategy : uses
    PlatformAllocator --> Schedule
    PlatformAllocator --> Station
    DelayManager --> PlatformAllocator
    DelayManager --> Schedule

    class ScheduleObserver {
        <<interface>>
        +onScheduleEvent(ScheduleEvent)
    }
    class ScheduleEventManager {
        -List~ScheduleObserver~ observers
        +subscribe(ScheduleObserver)
        +publish(ScheduleEvent)
    }
    class RefreshablePanel {
        <<abstract>>
        +refresh()*
    }
    SchedulingService --> ScheduleEventManager : publishes
    ScheduleEventManager "1" o-- "0..*" ScheduleObserver
    ScheduleObserver <|.. RefreshablePanel
    ScheduleObserver <|.. TimeDistanceDiagram
    ScheduleObserver <|.. StatusBar
    RefreshablePanel <|-- AllocationPanel
    RefreshablePanel <|-- TrainPanel

    class ScheduleCommand {
        <<interface>>
        +execute()
        +undo()
        +getDescription() String
    }
    class SnapshotCommand {
        <<abstract>>
        +undo()
        #reverseOwnChange()
    }
    ScheduleCommand <|.. SnapshotCommand
    SnapshotCommand <|-- AllocateCommand
    SnapshotCommand <|-- DelayCommand
    SchedulingService *-- CommandHistory
    CommandHistory "1" o-- "0..20" ScheduleCommand

    class TrainFactory {
        +create(TrainType, ...)$ Train
    }
    TrainFactory ..> Train : creates
```

**How to read it**

- **Inheritance** (solid line, hollow triangle): the three Train kinds extend the abstract `Train`; the four
  strategies extend `QueueOrderingStrategy`; the two commands extend `SnapshotCommand`; the screens extend `RefreshablePanel`.
- **Interface realization** (dashed line, hollow triangle): `QueueOrderingStrategy` implements
  `PlatformAllocationStrategy`; `SnapshotCommand` implements `ScheduleCommand`; screens implement `ScheduleObserver`.
- **Composition** (filled diamond): `Schedule` owns 0..* `PlatformReservation`s (it creates and deletes them);
  `SchedulingService` creates and owns its managers, allocator, delay manager, calculator and history.
- **Aggregation** (hollow diamond): `Station` holds 0..* `Platform`s that exist on their own; the service is given
  the station and schedule; the event manager holds observers it did not create.
- **Association** (arrow): each `Train` refers to 0..1 assigned `Platform`; each reservation refers to one train and one platform.
- **Dependency** (dashed arrow): the allocator *uses* a strategy passed in per call; the factory *creates* trains.
- `*` after a method = abstract; `$` = static; `~` = package-private; `#` = protected.

---

## 3. Sequence diagram: allocation

```mermaid
sequenceDiagram
    actor User
    participant AP as AllocationPanel
    participant SS as SchedulingService
    participant AC as AllocateCommand
    participant PA as PlatformAllocator
    participant ST as PlatformAllocationStrategy
    participant SC as Schedule
    participant T as Train
    participant EM as ScheduleEventManager

    User->>AP: click "Allocate all pending trains"
    AP->>SS: allocatePendingTrains()
    SS->>AC: new AllocateCommand(..., strategy)
    SS->>AC: execute()
    AC->>AC: takeSnapshot()
    AC->>PA: allocateAll(trains, strategy)
    loop for each event time (arrival or platform free)
        PA->>PA: moveArrivedTrains() into ready queue
        PA->>ST: orderReadyQueue(readyQueue, clock)
        ST-->>PA: trains in service order
        loop for each train in that order
            PA->>T: canUsePlatform(platform)
            PA->>SC: findConflict(train, platform, clock)
            alt compatible and free (First-Fit)
                PA->>SC: reserve(train, platform, clock)
                SC->>T: assignPlatform(platform)
            end
        end
        PA->>PA: dropHopelessTrains() (waited too long -> failure with reason)
    end
    PA-->>AC: List of AllocationResult
    SS->>SS: history.push(command), count conflicts
    SS->>EM: publish(ALLOCATION) [+ CONFLICT]
    EM-->>AP: onScheduleEvent() -> refresh()
    Note over EM: every other subscribed screen refreshes too
```

**In words:** the panel only calls the service. The service wraps the work in an undoable command. The allocator
runs its event loop: at each moment the strategy orders the ready queue, and each train takes the first free
compatible platform. Schedule is the only class that creates the booking and links the train. Finally the service
publishes events and every screen refreshes itself.

---

## 4. Sequence diagram: delay and reassignment (ripple)

```mermaid
sequenceDiagram
    actor User
    participant DP as DisruptionPanel
    participant SS as SchedulingService
    participant DC as DelayCommand
    participant DM as DelayManager
    participant SC as Schedule
    participant PA as PlatformAllocator
    participant T as Train
    participant EM as ScheduleEventManager
    participant TD as TimeDistanceDiagram

    User->>DP: Apply delay (12951, 5 min)
    DP->>SS: delayTrain(train, 5)
    SS->>DC: new DelayCommand(...) then execute()
    DC->>DC: takeSnapshot()
    DC->>DM: delayTrain(train, 5)
    DM->>T: applyDelay(5)
    DM->>SC: release(train)
    DM->>SC: findConflict(train, home, newStart)
    alt same platform free
        DM->>SC: reserve(train, home, newStart)  [KEPT_PLATFORM]
    else another platform free at the same time
        DM->>PA: findFirstFreePlatform(train, newStart)
        DM->>SC: reserve(train, other, newStart)  [REASSIGNED]
    else ripple within the max wait
        DM->>DM: planRipple() (walk following bookings)
        DM->>SC: release(pushed trains)
        DM->>SC: reserve(train) then reserve(each pushed train later)  [RIPPLE]
    else wait for any platform
        DM->>PA: allocateSingle(train)  [REASSIGNED or FAILED with reason]
    end
    DM-->>SS: DelayReport (outcome, changes, cascade)
    SS->>SS: history.push(command)
    SS->>EM: publish(DELAY with ReservationChanges)
    EM-->>TD: onScheduleEvent() -> ghosts + 500 ms slide
    SS->>EM: publish(REASSIGNMENT or CONFLICT)
    SS-->>DP: DelayReport
    DP->>User: show outcome and cascade chain
```

**In words:** the delay is recorded first (invalid minutes stop everything before any change). The old slot is freed
and the least disruptive option is tried first. Every move becomes a `ReservationChange`, which the report lists and
the diagram animates. A breakdown (`DelayManager.handleBreakdown`) reuses the same two reassignment steps:
same time on another platform, otherwise `allocateSingle` with waiting.

---

## 5. Activity diagram: allocation of a train

```mermaid
flowchart TD
    A([Train has no platform]) --> B{Fits ANY platform?<br/>operational, category, length}
    B -- no --> R1[Failure: list the reason for every platform]
    B -- yes --> C[Wait until its expected arrival<br/>joins the ready queue]
    C --> D[Strategy orders the ready queue]
    D --> E{Its turn: a compatible platform<br/>free now incl. buffer? First-Fit}
    E -- yes --> F[Schedule.reserve: create booking,<br/>link train to platform]
    F --> Z([Allocated])
    E -- no --> P{Preemption on and exactly one<br/>lower-priority booking not yet started<br/>that can move at the same time?}
    P -- yes --> Q[Move that train, take its slot]
    Q --> Z
    P -- no --> G[Earliest free slot on any compatible platform]
    G --> H{Within max wait?}
    H -- yes --> I[Keep waiting at the outer signal<br/>until the next event]
    I --> D
    H -- no --> R2[Failure: busy, earliest slot X, max wait Y]
    R1 --> U([Unallocated, reason shown])
    R2 --> U
```

**In words:** compatibility is checked first. Then the train queues and the strategy decides who goes next; the
train takes the lowest-numbered free compatible platform (the half-open interval plus buffer must be free). If
nothing is free it may preempt (only safely), or it waits, and once the earliest possible slot is beyond the
maximum wait it is refused with the reason.

---

## 6. State diagram: train status (`TrainStatus`)

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : created
    SCHEDULED --> ALLOCATED : Schedule.reserve()
    SCHEDULED --> UNALLOCATED : allocator gives up (no compatible platform, or wait over max)
    ALLOCATED --> SCHEDULED : Schedule.release() (reset, undo, train edited)
    ALLOCATED --> ALLOCATED : delay kept, moved or rippled
    ALLOCATED --> UNALLOCATED : delay or breakdown with no safe option
    UNALLOCATED --> SCHEDULED : markScheduled() (reset, re-plan, undo)
    UNALLOCATED --> ALLOCATED : allocated again successfully
    SCHEDULED --> [*] : removed (only without a platform)
    UNALLOCATED --> [*] : removed
```

**In words:** a new train is SCHEDULED. Only `Schedule.reserve()` can make it ALLOCATED (the link method is
package-private), and only `release()` takes that away, so the status and the booking can never disagree. A
train the allocator or the delay manager cannot place safely becomes UNALLOCATED, always with a reason, and
returns to SCHEDULED when the plan is reset. A train holding a platform cannot be removed.

## 7. State diagram: a train moving through the station (`TrainMovement.Phase`)

```mermaid
stateDiagram-v2
    [*] --> NOT_YET_ARRIVED
    NOT_YET_ARRIVED --> HOLDING_AT_OUTER_SIGNAL : expected arrival, platform not free yet
    NOT_YET_ARRIVED --> APPROACHING : expected arrival, platform free
    HOLDING_AT_OUTER_SIGNAL --> APPROACHING : 4 min before the booking starts
    APPROACHING --> AT_PLATFORM : booking start
    AT_PLATFORM --> DEPARTING : booking end
    DEPARTING --> GONE : 3 min later
    GONE --> [*]
```

**In words:** this is the behaviour the time-distance diagram and the yard view draw at the simulated time.
Each phase is a half-open interval, so at any minute a train is in exactly one phase. Holding at the outer
signal is the visible ready queue.

## 8. State diagram: platform (`PlatformStatus`)

```mermaid
stateDiagram-v2
    [*] --> OPERATIONAL : created
    OPERATIONAL --> FAILED : breakdown reported (bookings moved or reported)
    FAILED --> OPERATIONAL : repaired
    OPERATIONAL --> [*] : removed (only with no bookings)
    FAILED --> [*] : removed (only with no bookings)
```

---

## 9. Component diagram

```mermaid
flowchart TB
    subgraph Presentation["Presentation layer: ui"]
        MF["MainFrame and 10 screens"]
        DG["TimeDistanceDiagram, YardView"]
        TH["Theme, Card, BadgeRenderer"]
    end
    subgraph Application["Application layer: service"]
        SS[["SchedulingService (facade)"]]
        AL[PlatformAllocator]
        DM[DelayManager]
        MC["MetricsCalculator, ReportGenerator"]
        AR["StrategyArena, ScenarioGenerator"]
        CMD["Undo commands"]
        CSV[ScenarioCsv]
    end
    subgraph Policies["Policies: strategy"]
        ST["PlatformAllocationStrategy: FCFS, Priority, SJF, Aging"]
    end
    subgraph Domain["Domain layer: model"]
        MD["Train hierarchy, Platform, Station, Schedule, PlatformReservation"]
    end
    subgraph Events["Events: observer"]
        EV[ScheduleEventManager]
    end
    subgraph Persistence["Persistence layer: persistence"]
        AS[AutoSaver]
        RP["StationRepository and AuditLog (interfaces)"]
        SQ["SqliteStationRepository, SqliteAuditLog, DAOs"]
        DBC[Database]
    end
    SQLITE[("SQLite file railsync.db")]
    FILES[/"Scenario CSV files"/]

    MF --> SS
    DG --> SS
    SS --> AL
    SS --> DM
    SS --> MC
    SS --> CMD
    SS --> AR
    AL --> ST
    AL --> MD
    DM --> MD
    SS --> EV
    EV -. notifies .-> MF
    EV -. notifies .-> AS
    AS --> RP
    RP --> SQ
    SQ --> DBC
    DBC --> SQLITE
    MF --> CSV
    CSV --> FILES
```

**In words:** the screens only talk to the `SchedulingService` facade. The services use the strategy interface
and the domain model. Every change is published as an event; the screens and the `AutoSaver` both listen. The
auto-saver writes through the two repository interfaces, so only the `persistence` package knows about SQLite.

## 10. Package diagram (dependencies point downward only)

```mermaid
flowchart TB
    ROOT["com.railsync: Main (composition root)"]
    UI[ui]
    PERSIST[persistence]
    SERVICE[service]
    STRATEGY[strategy]
    OBSERVER[observer]
    MODEL[model]
    EXC[exception]
    ROOT --> UI
    ROOT --> PERSIST
    ROOT --> SERVICE
    UI --> SERVICE
    UI --> PERSIST
    PERSIST --> SERVICE
    PERSIST --> OBSERVER
    SERVICE --> STRATEGY
    SERVICE --> OBSERVER
    SERVICE --> MODEL
    STRATEGY --> MODEL
    OBSERVER --> MODEL
    MODEL --> EXC
```

`ui` depends on `persistence` only for the `AuditLog` interface (Reports screen) and the `AutoSaver` save
indicator. No package below `ui` imports Swing, and nothing outside `persistence` imports `java.sql`.

## 11. Deployment diagram

```mermaid
flowchart LR
    subgraph PC["User's PC: Windows, macOS or Linux"]
        subgraph JVM["Java 21+ runtime"]
            JAR["railsync-1.1.0.jar"]
            LIB["lib/: flatlaf, flatlaf-extras, jsvg, sqlite-jdbc (with native SQLite)"]
        end
        DBF[("~/.railsync/railsync.db")]
        CSVF[/"scenario .csv files"/]
    end
    JAR --> LIB
    JAR -- "JDBC, embedded, no server" --> DBF
    JAR -- "read and write" --> CSVF
```

**In words:** RailSync is a single desktop process. There is no server and no network: SQLite runs inside the
same process through the JDBC driver and stores everything in one file in the user's home folder (the location
can be changed with `-Drailsync.db=path`).

## 12. Entity-relationship diagram (database schema, version 1)

```mermaid
erDiagram
    PLATFORMS ||--o{ BOOKINGS : hosts
    TRAINS ||--o| BOOKINGS : "has at most one"
    PLATFORMS {
        TEXT id PK
        INTEGER number UK
        INTEGER length_m
        TEXT train_types
        TEXT status
    }
    TRAINS {
        TEXT id PK
        TEXT number UK
        TEXT name
        TEXT type
        TEXT arrival
        TEXT departure
        INTEGER base_priority
        INTEGER carriages
        INTEGER delay_min
        INTEGER given_up
    }
    BOOKINGS {
        TEXT train_id PK
        TEXT platform_id FK
        TEXT start_time
    }
    SETTINGS {
        TEXT key PK
        TEXT value
    }
    AUDIT_LOG {
        INTEGER id PK
        TEXT logged_at
        TEXT event_type
        TEXT message
    }
```

**In words:** a booking belongs to exactly one train (the train id is its primary key and a foreign key with
`ON DELETE CASCADE`, so the database itself refuses a second booking for a train) and one platform. CHECK
constraints guard the values (number and length above 0, priority 1 to 5, delay at least 0, status and type from
a fixed list). `SETTINGS` is a key/value table, so new settings need no schema change. `AUDIT_LOG` is
append-only. The schema version is kept in `PRAGMA user_version`.

## 13. Class diagram: persistence layer

```mermaid
classDiagram
    direction LR
    class StationRepository {
        <<interface>>
        +save(StationSnapshot)
        +load() Optional
    }
    class AuditLog {
        <<interface>>
        +record(String, String)
        +recent(int) List
        +count() int
    }
    class ScheduleObserver {
        <<interface>>
        +onScheduleEvent(ScheduleEvent)
    }
    class SqliteStationRepository {
        -Database database
        +save(StationSnapshot)
        +load() Optional
    }
    class SqliteAuditLog {
        -Database database
    }
    class Database {
        +SCHEMA_VERSION int
        -Connection connection
        +open(Path) Database
        +inMemory() Database
        -migrate()
        +close()
    }
    class AutoSaver {
        -SchedulingService service
        +onScheduleEvent(ScheduleEvent)
        +saveNow()
        +addSaveListener(SaveListener)
    }
    class StationSnapshot {
        +getPlatforms() List
        +getTrains() List
        +getBookings() List
        +getSettings() Settings
    }
    StationRepository <|.. SqliteStationRepository
    AuditLog <|.. SqliteAuditLog
    ScheduleObserver <|.. AutoSaver
    SqliteStationRepository *-- PlatformDao
    SqliteStationRepository *-- TrainDao
    SqliteStationRepository *-- BookingDao
    SqliteStationRepository *-- SettingsDao
    SqliteStationRepository --> Database
    SqliteAuditLog --> Database
    AutoSaver --> StationRepository
    AutoSaver --> AuditLog
    AutoSaver ..> StationSnapshot : saves
    SqliteStationRepository ..> StationSnapshot : builds
```

## 14. Sequence diagram: a change is saved automatically

```mermaid
sequenceDiagram
    actor User
    participant Screen as AllocationPanel
    participant Service as SchedulingService
    participant Events as ScheduleEventManager
    participant Panels as Other screens
    participant Saver as AutoSaver
    participant Repo as SqliteStationRepository
    participant DB as SQLite

    User->>Screen: click "Allocate all pending trains"
    Screen->>Service: allocatePendingTrains()
    Service->>Service: allocate with the current strategy
    Service->>Events: publish(ALLOCATION)
    Events->>Panels: onScheduleEvent(), each refreshes
    Events->>Saver: onScheduleEvent()
    Saver->>DB: INSERT INTO audit_log
    Saver->>Service: takeSnapshot()
    Saver->>Repo: save(snapshot)
    Repo->>DB: BEGIN, DELETE old rows, INSERT new rows, COMMIT
    Saver-->>Screen: SaveListener: "Saved 10:42:07" in the sidebar
```

**In words:** the service does not know that it is being saved. It publishes the same event it always did; the
auto-saver, as one more observer, writes the audit entry and the whole session in one transaction. If the
database fails, the change still succeeds in memory and the sidebar shows "Save failed".
