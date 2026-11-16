package com.railsync.service;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Schedule;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base for commands that undo by putting back a saved picture of the
 * bookings.
 *
 * <p>How undo works:</p>
 * <ol>
 *   <li>Just before execute() changes anything, takeSnapshot() records every
 *       booking (train, platform, start) and every train's status.</li>
 *   <li>undo() releases ALL current bookings, lets the subclass reverse its
 *       own extra change (for example removing a delay), then books every
 *       saved booking again and restores each status.</li>
 * </ol>
 * <p>Why a full snapshot and not "reverse each step": an allocation can also
 * move other trains (preemption) and a delay can push several (ripple).
 * Restoring the saved picture undoes all of that in one simple, reliable way.
 * The restore uses the normal Schedule.reserve(), so every rule is checked
 * again on the way back.</p>
 *
 * <p>OOP concepts: Abstract class + interface (shared undo code here, the
 * contract in ScheduleCommand), Template Method idea (undo() is fixed; the
 * hook reverseOwnChange() is overridden), Encapsulation (the snapshot is
 * private).</p>
 *
 * <p>Collections: an ArrayList of saved bookings (restored in order) and a
 * HashMap from train to its saved status.</p>
 */
public abstract class SnapshotCommand implements ScheduleCommand {

    /** The schedule this command changes. */
    protected final Schedule schedule;
    private final List<Train> trains;
    private final List<SavedBooking> savedBookings = new ArrayList<>();
    private final Map<Train, TrainStatus> savedStatuses = new HashMap<>();

    /**
     * Creates the command.
     *
     * @param schedule the schedule it changes
     * @param trains   every train in the scenario (their statuses are saved)
     */
    protected SnapshotCommand(Schedule schedule, List<Train> trains) {
        this.schedule = schedule;
        this.trains = new ArrayList<>(trains);
    }

    /** Records every booking and status. Subclasses call this first in execute(). */
    protected final void takeSnapshot() {
        savedBookings.clear();
        savedStatuses.clear();
        for (PlatformReservation booking : schedule.getAllReservations()) {
            savedBookings.add(new SavedBooking(booking.getTrain(), booking.getPlatform(), booking.getStart()));
        }
        for (Train train : trains) {
            savedStatuses.put(train, train.getStatus());
        }
    }

    /** Puts every booking and status back as it was at takeSnapshot(). */
    @Override
    public final void undo() {
        // Step 1: clear the current bookings (copy first, because release() changes the list).
        List<PlatformReservation> current = new ArrayList<>(schedule.getAllReservations());
        for (PlatformReservation booking : current) {
            schedule.release(booking.getTrain());
        }
        // Step 2: the subclass reverses its own change (for a delay: remove the delay minutes).
        // [POLYMORPHISM] the real command class decides what this means.
        reverseOwnChange();
        // Step 3: book everything again, exactly where it was.
        for (SavedBooking saved : savedBookings) {
            schedule.reserve(saved.train, saved.platform, saved.start);
        }
        // Step 4: trains without a booking get their old status back (SCHEDULED or UNALLOCATED).
        for (Train train : trains) {
            if (savedStatuses.get(train) == TrainStatus.UNALLOCATED) {
                train.markUnallocated();
            } else {
                train.markScheduled();
            }
        }
    }

    /** Reverses anything the command changed apart from bookings. Does nothing by default. */
    protected void reverseOwnChange() {
        // Allocation changes only bookings and statuses, which the snapshot restores.
    }

    /** One saved booking. */
    private static final class SavedBooking {
        private final Train train;
        private final Platform platform;
        private final LocalDateTime start;

        private SavedBooking(Train train, Platform platform, LocalDateTime start) {
            this.train = train;
            this.platform = platform;
            this.start = start;
        }
    }
}
