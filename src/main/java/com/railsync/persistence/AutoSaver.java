package com.railsync.persistence;

import com.railsync.exception.RailSyncException;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the session to the database after every change, and writes each
 * change to the audit log.
 *
 * <p>Why it exists: persistence is added WITHOUT touching the scheduling
 * logic. The service already announces every change to its observers; this
 * class is simply one more observer. Users never need a "Save" button, and a
 * crash loses at most the change being made.</p>
 *
 * <p>OOP concepts: [OBSERVER] implements ScheduleObserver; [SOLID-O] the service
 * was extended with saving without being modified; [SOLID-D] depends on the
 * StationRepository and AuditLog interfaces, not on SQLite.</p>
 *
 * <p>Works with: SchedulingService (reads its snapshot), StationRepository, AuditLog,
 * the status bar (through SaveListener).</p>
 */
public final class AutoSaver implements ScheduleObserver {

    private final SchedulingService service;
    private final StationRepository repository;
    private final AuditLog auditLog;
    private final List<SaveListener> listeners = new ArrayList<>();
    private LocalDateTime lastSavedAt;
    private String lastError;

    /**
     * @param service    the service whose session is saved
     * @param repository where the session goes
     * @param auditLog   where each change is recorded
     */
    public AutoSaver(SchedulingService service, StationRepository repository, AuditLog auditLog) {
        this.service = service;
        this.repository = repository;
        this.auditLog = auditLog;
    }

    /**
     * Called by the event manager after every change.
     *
     * @param event what changed
     */
    @Override
    public void onScheduleEvent(ScheduleEvent event) {
        // Step 1: record the change in the audit log.
        // Step 2: save the whole session. A failure is reported, never thrown at the screen
        // that made the change: the change itself already succeeded in memory.
        try {
            auditLog.record(event.getType().name(), event.getMessage());
            saveNow();
        } catch (RailSyncException e) {
            lastError = e.getMessage();
            notifyListeners(false, e.getMessage());
        }
    }

    /**
     * Saves the session immediately (also used once at start-up).
     *
     * @throws RailSyncException if the database refuses the data
     */
    public void saveNow() {
        repository.save(service.takeSnapshot());
        lastSavedAt = LocalDateTime.now();
        lastError = null;
        notifyListeners(true, "Saved");
    }

    /**
     * Registers someone who wants to know when a save succeeds or fails.
     *
     * @param listener the listener
     */
    public void addSaveListener(SaveListener listener) {
        listeners.add(listener);
    }

    /** @return when the last successful save happened, or null */
    public LocalDateTime getLastSavedAt() {
        return lastSavedAt;
    }

    /** @return the last error message, or null if the last save worked */
    public String getLastError() {
        return lastError;
    }

    /** @return the audit log this saver writes to */
    public AuditLog getAuditLog() {
        return auditLog;
    }

    private void notifyListeners(boolean ok, String message) {
        for (SaveListener listener : new ArrayList<>(listeners)) {
            listener.saveFinished(ok, message);
        }
    }

    /** Told after every save attempt. */
    @FunctionalInterface
    public interface SaveListener {
        /**
         * @param ok      true if the session was saved
         * @param message "Saved", or what went wrong
         */
        void saveFinished(boolean ok, String message);
    }
}
