package com.railsync.ui;

import com.formdev.flatlaf.ui.FlatLineBorder;
import com.railsync.model.Platform;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventType;
import com.railsync.service.ScheduleMetrics;
import com.railsync.service.SchedulingService;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The start screen: headline numbers, the current strategy, a short guide to
 * the demo, and a live log of every event.
 *
 * <p>Shows: counts and averages from ScheduleMetrics. Calls:
 * SchedulingService.calculateMetrics(). Observes: every event (refreshes the
 * numbers and appends the event to the log).</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * Polymorphism (overrides onScheduleEvent() to add logging, then calls the
 * parent version with super).</p>
 */
public class DashboardPanel extends RefreshablePanel {

    private static final int MAX_LOG_LINES = 200;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JLabel trainsValue = new JLabel();
    private final JLabel allocatedValue = new JLabel();
    private final JLabel unallocatedValue = new JLabel();
    private final JLabel delayedValue = new JLabel();
    private final JLabel conflictsValue = new JLabel();
    private final JLabel platformsValue = new JLabel();
    private final JLabel waitValue = new JLabel();
    private final JLabel turnaroundValue = new JLabel();
    private final JLabel utilizationValue = new JLabel();
    private final JLabel neededValue = new JLabel();
    private final JLabel strategyLabel = new JLabel();
    private final DefaultListModel<String> logModel = new DefaultListModel<>();

    /**
     * Builds the dashboard.
     *
     * @param service the scheduling service
     */
    public DashboardPanel(SchedulingService service) {
        super(service);
        add(Ui.header("Dashboard",
                "The station at a glance: allocation results, waiting times and every event as it happens."), BorderLayout.NORTH);

        // Step 1: a grid of number cards.
        JPanel cards = new JPanel(new GridLayout(2, 5, 12, 12));
        cards.add(card("Trains", trainsValue));
        cards.add(card("Allocated", allocatedValue));
        cards.add(card("Unallocated", unallocatedValue));
        cards.add(card("Delayed", delayedValue));
        cards.add(card("Conflicts detected", conflictsValue));
        cards.add(card("Platforms working", platformsValue));
        cards.add(card("Avg waiting time", waitValue));
        cards.add(card("Avg turnaround", turnaroundValue));
        cards.add(card("Avg utilization", utilizationValue));
        cards.add(card("Needed for zero wait", neededValue));

        // Step 2: strategy line and a short "how to demo" guide.
        strategyLabel.putClientProperty("FlatLaf.style", "font: bold +1");
        JTextArea guide = new JTextArea(2, 60);
        guide.setText(
                "Demo guide: 1) Allocation: choose FCFS, press Allocate.  2) Reset, choose Priority, Allocate again "
                        + "and compare (12002 vs L-102).  3) Schedule / Reports show the result and the metrics.  "
                        + "Loading the demo again restores the starting data.");
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        guide.setOpaque(false);
        guide.setFocusable(false);
        guide.setForeground(Theme.mutedText());
        JPanel middle = new JPanel(new BorderLayout(6, 4));
        middle.add(strategyLabel, BorderLayout.NORTH);
        middle.add(guide, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(10, 16));
        top.add(cards, BorderLayout.NORTH);
        top.add(middle, BorderLayout.CENTER);

        // Step 3: the live event log (the Observer pattern made visible).
        JList<String> log = new JList<>(logModel);
        log.setFont(Ui.monoFont());
        JScrollPane logScroll = new JScrollPane(log);

        JPanel body = new JPanel(new BorderLayout(10, 16));
        body.add(top, BorderLayout.NORTH);
        body.add(Ui.section("Event log  ·  every ScheduleEvent this screen has received, newest first", logScroll),
                BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        refresh();
    }

    /**
     * Logs the event, then refreshes the numbers.
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
        // Newest first, so the latest change is always visible at the top.
        logModel.add(0, LocalTime.now().format(CLOCK) + "  " + pad(event.getType()) + text);
        if (logModel.size() > MAX_LOG_LINES) {
            logModel.remove(logModel.size() - 1);
        }
        super.onScheduleEvent(event);
    }

    /** Re-reads the metrics and updates every card. */
    @Override
    public void refresh() {
        ScheduleMetrics m = service.calculateMetrics();
        trainsValue.setText(String.valueOf(m.getTotalTrains()));
        allocatedValue.setText(String.valueOf(m.getAllocatedTrains()));
        unallocatedValue.setText(String.valueOf(m.getUnallocatedTrains()));
        unallocatedValue.setForeground(m.getUnallocatedTrains() > 0 ? Ui.CONFLICT_COLOR : normalColor());
        delayedValue.setText(String.valueOf(m.getDelayedTrains()));
        conflictsValue.setText(String.valueOf(m.getConflictsDetected()));
        conflictsValue.setForeground(m.getConflictsDetected() > 0 ? Ui.CONFLICT_COLOR : normalColor());
        int working = 0;
        for (Platform platform : service.getStation().getPlatforms()) {
            if (platform.isOperational()) {
                working++;
            }
        }
        platformsValue.setText(working + " / " + service.getStation().getPlatformCount());
        waitValue.setText(String.format("%.1f min", m.getAverageWaitingMinutes()));
        turnaroundValue.setText(String.format("%.1f min", m.getAverageTurnaroundMinutes()));
        utilizationValue.setText(String.format("%.0f %%", m.getAverageUtilization() * 100));
        neededValue.setText(String.valueOf(m.getMinimumPlatformsNeeded()));
        strategyLabel.setText("Current strategy: " + service.getStrategy().getName()
                + "    (change it on the Allocation screen)");
    }

    /** Builds one card: a small quiet caption above a large number. */
    private JPanel card(String caption, JLabel value) {
        JPanel panel = new Card();
        value.putClientProperty("FlatLaf.styleClass", "h1");
        value.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel label = new JLabel(caption);
        label.putClientProperty("FlatLaf.style", "font: -1");
        label.setForeground(Theme.mutedText());
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Colour for normal values. Null means "inherit from the look and feel", so
     * the numbers follow the light/dark theme automatically.
     */
    private static Color normalColor() {
        return null;
    }

    /** A card with a thin, softly rounded outline that follows the light/dark theme. */
    private static final class Card extends JPanel {
        private Card() {
            super(new BorderLayout(0, 4));
        }

        /** Re-creates the outline in the current theme's line colour. */
        @Override
        public void updateUI() {
            super.updateUI();
            setBorder(new FlatLineBorder(new Insets(10, 14, 10, 14), Theme.lineColor(), 1f, 10));
        }
    }

    private static String pad(ScheduleEventType type) {
        String name = type.toString();
        StringBuilder text = new StringBuilder(name);
        while (text.length() < 18) {
            text.append(' ');
        }
        return text.toString();
    }
}
