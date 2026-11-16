# Design Patterns in RailSync

Four patterns are used: **Strategy** and **Observer** (required), **Factory** and **Command** (optional, added
because they solve a real problem). No other patterns are claimed. One method, `QueueOrderingStrategy.orderReadyQueue`,
follows the *Template Method idea* (a fixed algorithm with one overridable step); it is mentioned below but not
counted as a separate pattern.

Paths are relative to `src/main/java/com/railsync/`.

---

## 1. Strategy `[STRATEGY]`

**Problem:** the station must be able to serve waiting trains in different orders (FCFS, Priority, SJF, Aging),
switch policy at runtime, and compare policies, without the allocation engine containing a `switch` on the policy.

**Where:**

| Role | Class |
|---|---|
| Strategy interface | `strategy/PlatformAllocationStrategy` |
| Shared base (template for sorting) | `strategy/QueueOrderingStrategy` |
| Concrete strategies | `FcfsStrategy`, `PriorityStrategy`, `ShortestDwellFirstStrategy`, `PriorityAgingStrategy` |
| Context (uses a strategy) | `service/PlatformAllocator.dispatch()`, held by `service/SchedulingService` |
| Switched at runtime by | `ui/AllocationPanel` (combo box), `ui/YardPanel` (switch from now), `service/StrategyArena` (all in turn) |

**Code (the interface):**

```java
public interface PlatformAllocationStrategy {
    String getName();
    String getDescription();
    List<Train> orderReadyQueue(List<Train> readyQueue, LocalDateTime now);
}
```

**Code (the context never knows which strategy it has), `service/PlatformAllocator.java`:**

```java
// [STRATEGY] the algorithm chosen at runtime decides the order of service.
// [SOLID-L] any PlatformAllocationStrategy can be substituted here and the loop still works.
List<Train> serviceOrder = strategy.orderReadyQueue(readyQueue, clock);
for (Train train : serviceOrder) {
    Platform platform = findFirstFreePlatform(train, clock);
```

**Code (a concrete strategy is one rule), `strategy/PriorityStrategy.java`:**

```java
@Override
protected int compareByPolicy(Train a, Train b, LocalDateTime now) {
    return Integer.compare(b.getPriority(), a.getPriority());
}
```

**Injection, `service/SchedulingService.java`:** the strategy arrives through the constructor and `setStrategy(PlatformAllocationStrategy)`.

**Why chosen:** it mirrors the OS idea directly (the scheduler policy is pluggable), adding a policy is one new class
(Open/Closed), and the comparison arena can run every policy through the same loop.

---

## 2. Observer `[OBSERVER]`

**Problem:** after any change (allocation, delay, breakdown, edit) up to ten screens, the status bar and the Undo
button must refresh. The services must not know about Swing panels.

**Where:**

| Role | Class |
|---|---|
| Observer interface | `observer/ScheduleObserver` (one method) |
| Event | `observer/ScheduleEvent` (+ `ScheduleEventType`), carries `ReservationChange`s for the animation |
| Subject / publisher | `observer/ScheduleEventManager` |
| Publishes | `service/SchedulingService` (after every change) |
| Concrete observers | `ui/RefreshablePanel` and its 8 screens, `StatusBar`, `TimeDistanceDiagram`, `YardView`, the Undo button (a lambda in `MainFrame`), `ConsoleDemo`'s console observer |

A second, smaller observer exists for the clock: `SimulationClock.Listener`, implemented by both diagrams and `ClockControls`.

**Code (publishing), `observer/ScheduleEventManager.java`:**

```java
public void publish(ScheduleEvent event) {
    // Loop over a copy, so an observer may safely unsubscribe while being notified.
    List<ScheduleObserver> snapshot = new ArrayList<>(observers);
    for (ScheduleObserver observer : snapshot) {
        // [OBSERVER] each subscribed panel refreshes itself.
        observer.onScheduleEvent(event);
    }
}
```

**Code (a screen subscribes once, in the shared base), `ui/RefreshablePanel.java`:**

```java
service.getEvents().subscribe(this);
...
@Override
public void onScheduleEvent(ScheduleEvent event) {
    refresh();
}
```

**Why chosen:** screens stay independent (no screen calls another), and a new screen only needs to subscribe. The
event also carries the before/after of moved bookings, which the time-distance diagram uses for its ghost animation.

---

## 3. Factory `TrainFactory`

**Problem:** three places turn a `TrainType` chosen by a user or a file into the right object: the Train form,
`DemoScenarioLoader`/`ScenarioGenerator`, and `Scenario.copy()`/`ScenarioCsv`. Without a factory each would repeat the
same `switch`.

**Code, `model/TrainFactory.java`:**

```java
public static Train create(TrainType type, String id, String number, String name,
                           LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                           int basePriority, int carriageCount) {
    ...
    switch (type) {
        case EXPRESS:
            return new ExpressTrain(id, number, name, scheduledArrival, scheduledDeparture, basePriority, carriageCount);
        case PASSENGER:
            return new PassengerTrain(...);
        default:
            return new FreightTrain(...);
    }
}
```

**Why chosen:** the single `switch` on train type in the whole project lives here; callers work with the abstract `Train`.

---

## 4. Command `[COMMAND]` (Undo)

**Problem:** the user may undo the last allocation or delay. A delay can ripple through several trains and an
allocation can preempt others, so the action and its reversal must be packaged together.

**Where:**

| Role | Class |
|---|---|
| Command interface | `service/ScheduleCommand` (`execute`, `undo`, `getDescription`) |
| Shared base | `service/SnapshotCommand` (saves bookings and statuses; `undo()` restores them) |
| Concrete commands | `service/AllocateCommand`, `service/DelayCommand` |
| History (stack) | `service/CommandHistory` (max 20) |
| Invoker | `service/SchedulingService` (`runAllocation`, `delayTrain`, `undoLast`) |
| Triggered by | the toolbar Undo button / Ctrl+Z in `ui/MainFrame` |

**Code (invoker), `service/SchedulingService.java`:**

```java
// [COMMAND] the delay (with any ripple or reassignment) can be undone as one step.
DelayCommand command = new DelayCommand(schedule, trainManager.getAllTrains(), delayManager, train, minutes);
command.execute();
history.push(command);
...
public String undoLast() {
    ScheduleCommand command = history.pop();
    ...
    command.undo();
```

**Code (the one thing DelayCommand adds), `service/DelayCommand.java`:**

```java
@Override
protected void reverseOwnChange() {
    train.reduceDelay(minutes);
}
```

**Why chosen:** each undoable action is an object that knows how to reverse itself. Undo restores a snapshot taken
just before `execute()`, which handles ripples and preemption in one step, and `Schedule.reserve()` re-checks every
rule on the way back. History is cleared after changes that a snapshot could not safely reverse.
