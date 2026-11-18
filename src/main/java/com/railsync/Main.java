package com.railsync;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.persistence.AuditLog;
import com.railsync.persistence.AutoSaver;
import com.railsync.persistence.Database;
import com.railsync.persistence.SqliteAuditLog;
import com.railsync.persistence.SqliteStationRepository;
import com.railsync.persistence.StationRepository;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;
import com.railsync.service.StationSnapshot;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.ui.MainFrame;
import com.railsync.ui.Theme;
import com.railsync.ui.Ui;

import javax.swing.SwingUtilities;
import java.util.Optional;

/**
 * Starts the RailSync desktop application.
 *
 * <p>Why it exists: it is the one place where the objects are created and
 * wired together (the "composition root"): station, schedule, event manager,
 * the starting strategy and the service, the SQLite database with its
 * repositories and auto-saver, then the window. Every other class
 * receives what it needs through its constructor.</p>
 *
 * <p>OOP concepts: SOLID-D (the concrete FcfsStrategy is chosen here, the
 * service only knows the interface).</p>
 */
public final class Main {

    private Main() {
        // Not meant to be instantiated.
    }

    /**
     * Launches the GUI.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        // Step 1: RailSync's look and feel (FlatLaf + Theme). Must be set before any window is created.
        Theme.apply(false);
        // Step 2: Swing objects must be created on the Swing event thread.
        SwingUtilities.invokeLater(Main::createAndShow);
    }

    private static void createAndShow() {
        // Step 3: open the SQLite database. If the file cannot be opened, keep working in memory and say so.
        String openWarning = null;
        Database database;
        try {
            database = Database.open(Database.defaultFile());
        } catch (RailSyncException e) {
            openWarning = e.getMessage() + "\n\nRailSync will run without saving this session.";
            database = Database.inMemory();
        }
        Database openDatabase = database;
        Runtime.getRuntime().addShutdownHook(new Thread(openDatabase::close));

        // Step 4: build everything and show the window.
        Application app = build(database);
        app.getFrame().setVisible(true);
        String warning = openWarning != null ? openWarning : app.getWarning();
        if (warning != null) {
            Ui.showError(app.getFrame(), warning);
        }
    }

    /**
     * Builds the whole application on an open database without showing it:
     * service, repositories, auto-saver and window, with the last session
     * restored (or the demo scenario on first run). Main uses it at start-up;
     * the system tests use it with a temporary database file.
     * Must be called on the Swing event thread.
     *
     * @param database the open database
     * @return the wired-up application
     */
    public static Application build(Database database) {
        // Step 1: the service with its collaborators (dependency injection).
        ScheduleEventManager events = new ScheduleEventManager();
        // [SOLID-D] the only place a concrete strategy is chosen; everything else sees the interface.
        SchedulingService service = new SchedulingService(new Station("RailSync Central"), new Schedule(),
                new FcfsStrategy(), events);
        // [SOLID-D] the rest of the program only sees these two interfaces.
        StationRepository repository = new SqliteStationRepository(database);
        AuditLog auditLog = new SqliteAuditLog(database);
        AutoSaver autoSaver = new AutoSaver(service, repository, auditLog);

        // Step 2: build the window first (so its screens subscribe), then load the data.
        MainFrame frame = new MainFrame(service, autoSaver, database.getLocation());
        String warning = null;
        // Step 3: restore the last session; on first run (or damaged data) start from the demo scenario.
        try {
            Optional<StationSnapshot> saved = repository.load();
            if (saved.isPresent()) {
                service.restore(saved.get());
            } else {
                DemoScenarioLoader.load(service);
            }
        } catch (RailSyncException e) {
            warning = "The saved session could not be restored (" + e.getMessage()
                    + ").\nThe demo scenario was loaded instead.";
            DemoScenarioLoader.load(service);
        }
        // Step 4: from now on every change is saved and written to the audit log automatically.
        // [OBSERVER] the auto-saver is just one more subscriber.
        events.subscribe(autoSaver);
        try {
            autoSaver.saveNow();
        } catch (RailSyncException e) {
            warning = e.getMessage();
        }
        return new Application(service, autoSaver, frame, warning);
    }

    /** The wired-up application: what build() returns. */
    public static final class Application {
        private final SchedulingService service;
        private final AutoSaver autoSaver;
        private final MainFrame frame;
        private final String warning;

        private Application(SchedulingService service, AutoSaver autoSaver, MainFrame frame, String warning) {
            this.service = service;
            this.autoSaver = autoSaver;
            this.frame = frame;
            this.warning = warning;
        }

        /** @return the scheduling service */
        public SchedulingService getService() {
            return service;
        }

        /** @return the auto-saver (and through it, the audit log) */
        public AutoSaver getAutoSaver() {
            return autoSaver;
        }

        /** @return the main window (not yet visible) */
        public MainFrame getFrame() {
            return frame;
        }

        /** @return a message for the user if the saved session could not be used, else null */
        public String getWarning() {
            return warning;
        }
    }
}
