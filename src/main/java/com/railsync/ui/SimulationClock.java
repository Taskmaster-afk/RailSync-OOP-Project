package com.railsync.ui;

import com.railsync.exception.RailSyncException;

import javax.swing.Timer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The simulated "now" used by the diagrams: a time that moves forward while
 * playing, at 1x, 10x or 60x real speed.
 *
 * <p>How it works: a Swing Timer fires every {@value #TICK_MILLIS} ms on the
 * Swing event thread. Each tick moves the simulated time forward by
 * TICK_MILLIS x speed, then tells every listener, which simply repaints.
 * Example at 60x: every 100 ms of real time adds 6 seconds of simulated time,
 * so one real second is one simulated minute. No extra threads are used.</p>
 *
 * <p>It only affects drawing. Allocation does not depend on this clock.</p>
 *
 * <p>OOP concepts: Encapsulation (the time changes only through play/tick/
 * setTime), a tiny Observer of its own (Listener) so several views can follow
 * one clock.</p>
 *
 * <p>Works with: TimeDistancePanel (controls), TimeDistanceDiagram and later
 * the yard view (listeners).</p>
 */
public class SimulationClock {

    /** Real milliseconds between ticks. */
    public static final int TICK_MILLIS = 100;
    /** The speeds offered in the GUI. */
    public static final int[] SPEEDS = {1, 10, 60};

    /** Something that wants to know when the simulated time changes. */
    public interface Listener {
        /**
         * Called after every change of the simulated time.
         *
         * @param now the new simulated time
         */
        void onClockChanged(LocalDateTime now);
    }

    private final Timer timer;
    private final List<Listener> listeners = new ArrayList<>();
    private LocalDateTime now;
    private int speed = 60;

    /**
     * Creates a paused clock.
     *
     * @param start the starting simulated time
     */
    public SimulationClock(LocalDateTime start) {
        this.now = start;
        // The Swing Timer calls tick() on the event thread, so painting code is never touched from another thread.
        this.timer = new Timer(TICK_MILLIS, e -> tick());
    }

    /** Starts moving time forward (listeners are told, so Play buttons can change to Pause). */
    public void play() {
        timer.start();
        notifyListeners();
    }

    /** Stops moving time forward (listeners are told). */
    public void pause() {
        timer.stop();
        notifyListeners();
    }

    /** @return true if time is moving */
    public boolean isPlaying() {
        return timer.isRunning();
    }

    /**
     * Moves time forward by one tick (TICK_MILLIS x speed) and notifies
     * listeners. The Timer calls this; tests may call it directly.
     */
    public void tick() {
        now = now.plusNanos((long) TICK_MILLIS * speed * 1_000_000L);
        notifyListeners();
    }

    /**
     * Jumps to a time (for example "reset to start of day").
     *
     * @param time the new simulated time
     */
    public void setTime(LocalDateTime time) {
        this.now = time;
        notifyListeners();
    }

    /** @return the current simulated time */
    public LocalDateTime getTime() {
        return now;
    }

    /**
     * Changes the speed.
     *
     * @param newSpeed 1, 10 or 60
     * @throws RailSyncException for any other value
     */
    public void setSpeed(int newSpeed) {
        for (int allowed : SPEEDS) {
            if (allowed == newSpeed) {
                this.speed = newSpeed;
                return;
            }
        }
        throw new RailSyncException("Speed must be 1x, 10x or 60x.");
    }

    /** @return the current speed factor */
    public int getSpeed() {
        return speed;
    }

    /**
     * Registers a listener.
     *
     * @param listener the listener
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onClockChanged(now);
        }
    }
}
