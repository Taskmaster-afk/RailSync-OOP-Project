package com.railsync;

import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.ui.MainFrame;
import com.railsync.ui.Theme;

import javax.swing.SwingUtilities;

/**
 * Starts the RailSync desktop application.
 *
 * <p>Why it exists: it is the one place where the objects are created and
 * wired together (the "composition root"): station, schedule, event manager,
 * the starting strategy and the service, then the window. Every other class
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
        // Step 3: build the service with its collaborators (dependency injection).
        ScheduleEventManager events = new ScheduleEventManager();
        // [SOLID-D] the only place a concrete strategy is chosen; everything else sees the interface.
        SchedulingService service = new SchedulingService(new Station("RailSync Central"), new Schedule(),
                new FcfsStrategy(), events);
        // Step 4: build the window first (so its screens subscribe), then load the demo data.
        MainFrame frame = new MainFrame(service);
        DemoScenarioLoader.load(service);
        frame.setVisible(true);
    }
}
