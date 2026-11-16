package com.railsync.ui;

import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventType;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;

/**
 * The strip at the bottom of the window: the latest event on the left
 * (red for conflicts and breakdowns) and the current settings on the right.
 *
 * <p>Shows: last event message, strategy, buffer, maximum wait.
 * Observes: every ScheduleEvent. Calls: only read methods of SchedulingService.</p>
 *
 * <p>OOP concepts: [OBSERVER] (implements ScheduleObserver directly, since it
 * is a small strip rather than a full screen).</p>
 */
public class StatusBar extends JPanel implements ScheduleObserver {

    private final SchedulingService service;
    private final JLabel lastEvent = new JLabel("Ready.");
    private final JLabel settings = new JLabel();

    /**
     * Creates the status bar and subscribes it to events.
     *
     * @param service the scheduling service
     */
    public StatusBar(SchedulingService service) {
        super(new BorderLayout());
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(lastEvent, BorderLayout.CENTER);
        add(settings, BorderLayout.EAST);
        service.getEvents().subscribe(this);
        updateSettings();
    }

    /**
     * Shows the newest event; conflicts and breakdowns are shown in red.
     *
     * @param event what happened
     */
    @Override
    public void onScheduleEvent(ScheduleEvent event) {
        String text = event.getMessage();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline);
        }
        lastEvent.setText(event.getType() + ": " + text);
        boolean bad = event.getType() == ScheduleEventType.CONFLICT || event.getType() == ScheduleEventType.BREAKDOWN;
        lastEvent.setForeground(bad ? Ui.CONFLICT_COLOR : UIManager.getColor("Label.foreground"));
        updateSettings();
    }

    private void updateSettings() {
        settings.setText("Strategy: " + service.getStrategy().getName()
                + "   |   Buffer: " + service.getSchedule().getClearanceBufferMinutes() + " min"
                + "   |   Max wait: " + service.getMaxWaitMinutes() + " min"
                + (service.isPreemptionEnabled() ? "   |   Preemption ON" : ""));
    }
}
