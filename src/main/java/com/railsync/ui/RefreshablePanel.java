package com.railsync.ui;

import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Base class for every screen that shows live data. It subscribes the panel
 * to schedule events and calls refresh() whenever something changes.
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>[OBSERVER]: implements ScheduleObserver; each screen refreshes itself
 *       when the service publishes an event.</li>
 *   <li>Abstract class + interface together: the interface says "can be
 *       notified"; this abstract class adds the shared subscribe-and-refresh
 *       code so no screen repeats it.</li>
 *   <li>Inheritance and Polymorphism: each screen overrides refresh().</li>
 * </ul>
 *
 * <p>Works with: SchedulingService, ScheduleEventManager, every screen.</p>
 */
public abstract class RefreshablePanel extends JPanel implements ScheduleObserver {

    /** The service every screen talks to. Screens never touch the model directly to change it. */
    protected final SchedulingService service;

    /**
     * Sets up the shared layout and subscribes to events.
     *
     * @param service the scheduling service
     */
    protected RefreshablePanel(SchedulingService service) {
        super(new BorderLayout(10, 10));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        // [OBSERVER] register, so onScheduleEvent() is called after every change.
        service.getEvents().subscribe(this);
    }

    /**
     * Called by the event manager after any change.
     *
     * @param event what happened
     */
    @Override
    public void onScheduleEvent(ScheduleEvent event) {
        refresh();
    }

    /** Re-reads the data from the service and redraws the screen. */
    public abstract void refresh();
}
