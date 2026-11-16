package com.railsync.service;

/**
 * An action on the schedule that can be carried out and later taken back.
 *
 * <p>Command pattern: each user action that may be undone (allocating,
 * delaying) is wrapped in an object with execute() and undo(). The
 * SchedulingService (the "invoker") runs it and keeps it on a history stack;
 * Undo pops the newest one and calls undo().</p>
 *
 * <p>OOP concepts: Abstraction / interface, Polymorphism (the history calls
 * undo() without knowing which command it is).</p>
 *
 * <p>Works with: SnapshotCommand (shared base), AllocateCommand,
 * DelayCommand, CommandHistory.</p>
 */
public interface ScheduleCommand {

    /** Carries out the action. */
    void execute();

    /** Puts the schedule back exactly as it was before execute(). */
    void undo();

    /**
     * Describes the action for the Undo button, e.g. "allocation with FCFS".
     *
     * @return a short description
     */
    String getDescription();
}
