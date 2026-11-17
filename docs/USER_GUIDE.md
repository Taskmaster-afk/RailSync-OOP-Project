# RailSync User Guide

Start the app with `run-app.cmd` (or `java -jar target\railsync-1.0.0.jar` after a build). The window has a
toolbar at the top, the screens on the left (grouped as Overview, Data, Planning, Visualise and Analysis), and a
status bar at the bottom. The status bar shows the latest event, with a green dot for routine events and a red dot
for conflicts and breakdowns, plus the current strategy, buffer and maximum wait. Every screen starts with a title
and one line saying what it is for, and the main action on a screen is the blue button.

All times are on one simulated service day (16 Nov 2026) and are typed as `HH:mm`, e.g. `08:15`.

## Toolbar

| Control | What it does |
|---|---|
| **Undo ...** (Ctrl+Z) | Takes back the last allocation or delay, including every train it moved. The label says what will be undone. Undo history is cleared by changes it cannot safely reverse (adding or editing data, a breakdown, changing the buffer). |
| **Open CSV...** | Loads platforms and trains from a RailSync CSV file. The whole file is checked first; an error names the bad line and nothing changes. You are asked before the current data is replaced. |
| **Save CSV...** | Saves the current platforms and trains (with delays and failed platforms, but not bookings). |
| **Load demo scenario** | Restores the 6-platform, 12-train demo (nothing allocated). |
| **Dark mode** | Switches between the light and dark themes. |

## Dashboard

Headline numbers (trains, allocated, unallocated, delayed, conflicts, working platforms, average waiting and
turnaround, average utilization, platforms needed for zero wait), the current strategy, a short demo guide, and
the **event log**, which lists every event the screen received (the Observer pattern made visible).

## Trains (Module A)

- The table shows every train: type (coloured), timetable, dwell, base and effective priority, delay, status
  (UNALLOCATED in red), platform and platform start.
- **Search by number** selects the train.
- **Add train / Edit**: a form. Type, number and coach count are fixed after creation. If an allocated train's times
  change, it is placed again and you are told where.
- **Remove**: asks for confirmation, and is refused while the train holds a platform.
- **Release platform** frees its booking. **View schedule** shows its needs and booking.

Rules checked for you: departure after arrival, minimum stop (express 5, passenger 3, freight 20 min), priority 1-5,
unique train number.

## Platforms (Module B)

- The table lists number, length, status (FAILED in red), accepted categories, bookings, first booking and when it
  is last free.
- **Add / Edit**: number, length, accepted categories. An edit that would make a booked train no longer fit is refused.
- **Remove**: confirmation; refused while trains are booked. **Repair**: makes a failed platform operational.
- The lower box is the **compatibility check**: for the selected platform, every train with OK or the exact reason
  it cannot use it.

## Allocation (Modules C and D)

- **Strategy**: FCFS, Priority, Shortest Dwell First (SJF), Priority + Aging. The box below explains it, with the OS analogy.
- **Aging: minutes per +1 priority** (Aging only), **Clearance buffer**, **Max wait at outer signal**, **Allow safe
  preemption**. Each takes effect immediately.
- **Allocate all pending trains** runs the chosen strategy on every train without a platform.
- **Allocate this train** places one chosen train.
- **Reset all allocations** clears every booking (delays and failed platforms stay), so you can try another strategy.
- The right side lists every decision in order: `OK ... at 08:15 (waited 10 min at the outer signal)` or `NO ... reason`.

## Schedule

Every booking, sorted by platform and time, with timetable arrival, platform start/end, dwell, waited minutes and
delay. Filter by platform. Below it: the trains with no platform, and the latest reason for each.

## Time-Distance (Gantt / occupancy view)

- X = time, Y = outer signal, home signal, each platform, exit signal.
- Each train is a line. The thick flat part is its time at the platform (the Gantt bar), the slopes are running in
  and out, and a **red flat part on the outer signal** means it waited in the queue.
- Red X = a train with no platform. A failed platform's row is shaded red.
- **Play / Pause**, **Speed** 1x/10x/60x and **Jump to start** drive the purple *now* line.
- After a delay or breakdown the old lines stay as **faded dashed ghosts** while the new ones slide into place. Trains
  pushed by a ripple are outlined in red and labelled `ripple 1`, `ripple 2` ... **Clear ghosts** removes the ghosts.

## Delay & Breakdown (Modules E and E2)

- **Delay a train**: choose the train and the minutes, then press *Apply delay*. The report shows the outcome:
  - *still valid*: the train was waiting anyway, so nothing changes;
  - *kept platform*: same platform, later slot;
  - *reassigned*: moved to another platform, or to a later slot after waiting;
  - *ripple*: keeps its platform and pushes the following trains (the chain and count are listed);
  - *failed*: no safe slot, so the train loses its platform and the report says why.
- **Platform breakdown**: choose a platform and press *Report breakdown* (with confirmation). The report lists who
  moved where and who could not, and why.
- **Scripted demo** buttons do the ripple and breakdown of the demo scenario in one click (allocate with FCFS first).
- The diagram on the right animates every change.

## Yard View

The station from above at the clock time. Platforms are drawn to scale. Trains slide in, stand at the platform and
slide out. Trains waiting at the outer signal are listed in the **Ready queue** box (red = no platform).
**Switch strategy from now** re-plans only the trains that have not reached a platform yet, so with the clock
running you can watch the queue being served in a new order.

## Comparison (Module F2)

- Choose **Copy of the live scenario** or **Generated scenario** (seed, 10-500 trains; the same seed always gives the
  same scenario).
- **Run comparison** runs all four strategies, each on its own copy, using the live buffer, maximum wait and
  preemption setting. The live schedule is never changed.
- The best value in each column is shown in bold. Read *Avg wait* together with *Unallocated*: averages cover only
  allocated trains. *Needed for zero wait* is the sweep-line figure; it ignores categories and lengths.
- **Load generated scenario into the app** replaces the live data with the generated scenario (after confirmation),
  so you can explore it on the other screens.

## Reports

Full text report: metrics with definitions, utilization per platform, a text Gantt chart and the last allocation
log. **Copy report** puts it on the clipboard.

## CSV file format

```
# RailSync scenario v1
PLATFORM,P1,1,520,OPERATIONAL,EXPRESS;PASSENGER
TRAIN,T1,EXPRESS,12951,Rajdhani Express,2026-11-16T08:00,2026-11-16T08:15,4,20,0
```

TRAIN columns: id, type, number, name, arrival, departure, base priority, coaches/wagons, delay minutes.
See `docs/sample-demo-scenario.csv`.

## Console demo

`run-demo.cmd` prints, without the GUI, what the model and services do: polymorphism, compatibility, the buffer
rule, FCFS vs Priority, starvation and aging, preemption, metrics, observer events, a ripple and a breakdown.
