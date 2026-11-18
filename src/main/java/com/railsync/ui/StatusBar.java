package com.railsync.ui;

import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventType;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * The strip at the bottom of the window: the latest event on the left
 * (with a red dot for conflicts and breakdowns) and the current settings on the right.
 *
 * <p>Shows: a coloured dot (green = routine, red = conflict or breakdown), the
 * last event message, and the strategy, buffer and maximum wait.
 * Observes: every ScheduleEvent. Calls: only read methods of SchedulingService.</p>
 *
 * <p>OOP concepts: [OBSERVER] (implements ScheduleObserver directly, since it
 * is a small strip rather than a full screen).</p>
 */
public class StatusBar extends JPanel implements ScheduleObserver {

    /** Green dot: the last event was routine. */
    private static final Color OK_COLOR = new Color(46, 139, 87);

    private final SchedulingService service;
    private final JLabel dot = new JLabel("●");
    private final JLabel lastEvent = new JLabel("Ready.");
    private final JLabel settings = new Ui.MutedLabel("");

    /**
     * Creates the status bar and subscribes it to events.
     *
     * @param service the scheduling service
     */
    public StatusBar(SchedulingService service) {
        super(new BorderLayout());
        this.service = service;
        // A thin line on top, then the message (with a coloured dot) on the left and the settings on the right.
        dot.setForeground(OK_COLOR);
        dot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        JPanel message = new JPanel(new BorderLayout());
        message.add(dot, BorderLayout.WEST);
        message.add(lastEvent, BorderLayout.CENTER);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBorder(BorderFactory.createEmptyBorder(5, 14, 6, 14));
        row.add(message, BorderLayout.CENTER);
        row.add(settings, BorderLayout.EAST);
        add(new JSeparator(), BorderLayout.NORTH);
        add(row, BorderLayout.CENTER);
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
        lastEvent.setText(text);
        lastEvent.setToolTipText(event.getType().toString());
        boolean bad = event.getType() == ScheduleEventType.CONFLICT || event.getType() == ScheduleEventType.BREAKDOWN;
        dot.setForeground(bad ? Ui.CONFLICT_COLOR : OK_COLOR);
        updateSettings();
    }

    private void updateSettings() {
        settings.setText(service.getStrategy().getName()
                + "   ·   buffer " + service.getSchedule().getClearanceBufferMinutes() + " min"
                + "   ·   max wait " + service.getMaxWaitMinutes() + " min"
                + (service.isPreemptionEnabled() ? "   ·   preemption on" : ""));
    }
}
