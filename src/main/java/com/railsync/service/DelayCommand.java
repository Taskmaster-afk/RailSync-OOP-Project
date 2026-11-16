package com.railsync.service;

import com.railsync.model.Schedule;
import com.railsync.model.Train;

import java.util.List;

/**
 * Undoable "delay this train by N minutes" (including any ripple or reassignment it caused).
 *
 * <p>OOP concepts: [COMMAND] pattern, Inheritance (extends SnapshotCommand),
 * Polymorphism (overrides reverseOwnChange() to also take the delay back).</p>
 *
 * <p>Works with: DelayManager, SchedulingService (creates and runs it).</p>
 */
public class DelayCommand extends SnapshotCommand {

    private final DelayManager delayManager;
    private final Train train;
    private final int minutes;
    private DelayReport report;

    /**
     * Creates the command (nothing happens until execute()).
     *
     * @param schedule     the schedule
     * @param allTrains    every train (for the status snapshot)
     * @param delayManager does the actual delay handling
     * @param train        the late train
     * @param minutes      the delay
     */
    public DelayCommand(Schedule schedule, List<Train> allTrains, DelayManager delayManager, Train train, int minutes) {
        super(schedule, allTrains);
        this.delayManager = delayManager;
        this.train = train;
        this.minutes = minutes;
    }

    /** Saves the current state, then applies the delay (which may ripple or reassign). */
    @Override
    public void execute() {
        takeSnapshot();
        report = delayManager.delayTrain(train, minutes);
    }

    /** Takes the delay minutes back off the train, before the old bookings are restored. */
    @Override
    protected void reverseOwnChange() {
        train.reduceDelay(minutes);
    }

    /** @return the report of execute() */
    public DelayReport getReport() {
        return report;
    }

    /** @return e.g. "delay of 12951 by 5 min" */
    @Override
    public String getDescription() {
        return "delay of " + train.getNumber() + " by " + minutes + " min";
    }
}
