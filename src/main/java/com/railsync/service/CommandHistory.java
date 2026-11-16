package com.railsync.service;

import java.util.ArrayList;
import java.util.List;

/**
 * The undo stack: the most recent commands, newest last.
 *
 * <p>It keeps at most {@value #MAX_SIZE} commands; older ones are forgotten.
 * It is cleared whenever the scenario changes in a way the commands cannot
 * reverse (adding a train, a breakdown, a new buffer...), because restoring
 * an old snapshot after such a change could break the rules.</p>
 *
 * <p>OOP concepts: [COMMAND] pattern (this is where commands are stored),
 * Encapsulation (the list is private).</p>
 *
 * <p>Collections: an ArrayList used as a stack (add at the end, remove from the end).</p>
 */
public class CommandHistory {

    /** Most commands remembered. */
    public static final int MAX_SIZE = 20;

    private final List<ScheduleCommand> commands = new ArrayList<>();

    /**
     * Remembers a command that has just been executed.
     *
     * @param command the command
     */
    public void push(ScheduleCommand command) {
        commands.add(command);
        if (commands.size() > MAX_SIZE) {
            commands.remove(0);
        }
    }

    /**
     * Takes the newest command off the stack.
     *
     * @return the command, or null if there is nothing to undo
     */
    public ScheduleCommand pop() {
        if (commands.isEmpty()) {
            return null;
        }
        return commands.remove(commands.size() - 1);
    }

    /** @return the newest command without removing it, or null */
    public ScheduleCommand peek() {
        return commands.isEmpty() ? null : commands.get(commands.size() - 1);
    }

    /** Forgets every command. */
    public void clear() {
        commands.clear();
    }

    /** @return how many commands can be undone */
    public int size() {
        return commands.size();
    }
}
