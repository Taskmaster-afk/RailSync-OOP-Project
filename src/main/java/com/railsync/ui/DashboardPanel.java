package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventType;
import com.railsync.service.ScheduleMetrics;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The start screen: headline numbers as tiles, the current strategy with a
 * short guide to the demo, and a live, colour-coded log of every event.
 *
 * <p>Shows: counts and averages from ScheduleMetrics. Calls:
 * SchedulingService.calculateMetrics(). Observes: every event (refreshes the
 * numbers and adds the event to the log).</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * Polymorphism (overrides onScheduleEvent() to add logging, then calls the
 * parent version with super; the log renderer implements ListCellRenderer).</p>
 */
public class DashboardPanel extends RefreshablePanel {

    private static final int MAX_LOG_LINES = 200;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Tile trains = new Tile("Trains", "train", Theme.ACCENT);
    private final Tile allocated = new Tile("Allocated", "check", Theme.GOOD);
    private final Tile unallocated = new Tile("Unallocated", "alert", Theme.BAD);
    private final Tile delayed = new Tile("Delayed", "clock", Theme.WARN);
    private final Tile conflicts = new Tile("Conflicts detected", "alert", Theme.BAD);
    private final Tile platforms = new Tile("Platforms working", "platform", Theme.ACCENT);
    private final Tile waiting = new Tile("Avg waiting time", "wait", Theme.WARN);
    private final Tile turnaround = new Tile("Avg turnaround", "turnaround", Theme.ACCENT);
    private final Tile utilization = new Tile("Avg utilization", "gauge", Theme.GOOD);
    private final Tile needed = new Tile("Needed for zero wait", "layers", new Color(0x6B5BA8));
    private final JLabel strategyBadge = Ui.badge("", Theme.ACCENT);
    private final DefaultListModel<LogLine> logModel = new DefaultListModel<>();

    /**
     * Builds the dashboard.
     *
     * @param service the scheduling service
     */
    public DashboardPanel(SchedulingService service) {
        super(service);
        add(Ui.header("Dashboard",
                "The station at a glance: allocation results, waiting times and every event as it happens."),
                BorderLayout.NORTH);

        // Step 1: a grid of number tiles.
        JPanel tiles = new JPanel(new GridLayout(2, 5, 12, 12));
        tiles.setOpaque(false);
        for (Tile tile : new Tile[] {trains, allocated, unallocated, delayed, conflicts,
            platforms, waiting, turnaround, utilization, needed}) {
            tiles.add(tile);
        }

        // Step 2: the strategy in use and a short "how to demo" guide, on one card.
        JLabel strategyCaption = new JLabel("Current strategy");
        strategyCaption.putClientProperty("FlatLaf.style", "font: bold +1");
        JLabel change = new Ui.MutedLabel("change it on the Allocation screen");
        JPanel strategyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        strategyRow.add(strategyCaption);
        strategyRow.add(javax.swing.Box.createHorizontalStrut(10));
        strategyRow.add(strategyBadge);
        strategyRow.add(javax.swing.Box.createHorizontalStrut(10));
        strategyRow.add(change);
        JLabel guide = new Ui.MutedLabel("<html>Demo guide: <b>1</b> Allocation: choose FCFS and press Allocate. "
                + "<b>2</b> Reset, choose Priority, allocate again and compare 12002 with L-102. "
                + "<b>3</b> Schedule and Reports show the result and the metrics. "
                + "Loading the demo again restores the starting data.</html>");
        Card strategyCard = new Card(new BorderLayout(0, 6));
        strategyCard.add(strategyRow, BorderLayout.NORTH);
        strategyCard.add(guide, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(tiles, BorderLayout.NORTH);
        top.add(strategyCard, BorderLayout.CENTER);

        // Step 3: the live event log (the Observer pattern made visible).
        JList<LogLine> log = new JList<>(logModel);
        log.setCellRenderer(new LogRenderer());
        log.setFixedCellHeight(30);
        JScrollPane logScroll = new JScrollPane(log);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
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
        logModel.add(0, new LogLine(LocalTime.now().format(CLOCK), event.getType(), text));
        if (logModel.size() > MAX_LOG_LINES) {
            logModel.remove(logModel.size() - 1);
        }
        super.onScheduleEvent(event);
    }

    /** Re-reads the metrics and updates every tile. */
    @Override
    public void refresh() {
        ScheduleMetrics m = service.calculateMetrics();
        trains.setValue(String.valueOf(m.getTotalTrains()), false);
        allocated.setValue(String.valueOf(m.getAllocatedTrains()), false);
        unallocated.setValue(String.valueOf(m.getUnallocatedTrains()), m.getUnallocatedTrains() > 0);
        delayed.setValue(String.valueOf(m.getDelayedTrains()), false);
        conflicts.setValue(String.valueOf(m.getConflictsDetected()), m.getConflictsDetected() > 0);
        int working = 0;
        for (Platform platform : service.getStation().getPlatforms()) {
            if (platform.isOperational()) {
                working++;
            }
        }
        platforms.setValue(working + " / " + service.getStation().getPlatformCount(),
                working < service.getStation().getPlatformCount());
        waiting.setValue(String.format("%.1f min", m.getAverageWaitingMinutes()), false);
        turnaround.setValue(String.format("%.1f min", m.getAverageTurnaroundMinutes()), false);
        utilization.setValue(String.format("%.0f %%", m.getAverageUtilization() * 100), false);
        needed.setValue(String.valueOf(m.getMinimumPlatformsNeeded()), false);
        strategyBadge.setText(service.getStrategy().getName());
    }

    /** One number tile: an icon in a tinted circle, a quiet caption and a large value. */
    private static final class Tile extends Card {
        private final JLabel value = new JLabel("-");
        private final Color color;

        Tile(String caption, String iconName, Color color) {
            super(new BorderLayout(12, 0));
            this.color = color;
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JLabel label = new Ui.MutedLabel(caption);
            label.putClientProperty("FlatLaf.style", "font: -1");
            value.putClientProperty("FlatLaf.style", "font: bold +9");
            JPanel words = new JPanel(new BorderLayout(0, 2));
            words.add(label, BorderLayout.NORTH);
            words.add(value, BorderLayout.CENTER);
            add(new IconCircle(Theme.icon(iconName, 18, color), color), BorderLayout.WEST);
            add(words, BorderLayout.CENTER);
        }

        /**
         * @param text  the value to show
         * @param alert true to show it in the tile's warning colour
         */
        void setValue(String text, boolean alert) {
            value.setText(text);
            value.setForeground(alert ? color : null);
        }
    }

    /** A soft circle holding an icon, used on the tiles. */
    private static final class IconCircle extends JComponent {
        private final javax.swing.Icon icon;
        private final Color color;

        IconCircle(javax.swing.Icon icon, Color color) {
            this.icon = icon;
            this.color = color;
            setPreferredSize(new Dimension(40, 40));
        }

        /** Paints the tinted circle and the icon in its centre. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = 40;
            int y = (getHeight() - size) / 2;
            g2.setColor(Theme.tint(color));
            g2.fillOval(0, y, size, size);
            icon.paintIcon(this, g2, (size - icon.getIconWidth()) / 2, y + (size - icon.getIconHeight()) / 2);
            g2.dispose();
        }
    }

    /** One line of the event log. */
    private static final class LogLine {
        private final String time;
        private final ScheduleEventType type;
        private final String message;

        LogLine(String time, ScheduleEventType type, String message) {
            this.time = time;
            this.type = type;
            this.message = message;
        }
    }

    /** Draws a log line as: time, a coloured event badge, then the message. */
    private static final class LogRenderer extends JPanel implements ListCellRenderer<LogLine> {
        private final JLabel time = new Ui.MutedLabel("");
        private final Ui.Badge type = new Ui.Badge();
        private final JLabel message = new JLabel();

        LogRenderer() {
            super(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            time.setFont(Ui.monoFont());
            type.setHorizontalAlignment(SwingConstants.LEFT);
            type.setPreferredSize(new Dimension(170, 22));
            JPanel left = new JPanel(new BorderLayout(12, 0));
            left.setOpaque(false);
            left.add(time, BorderLayout.WEST);
            left.add(type, BorderLayout.CENTER);
            add(left, BorderLayout.WEST);
            add(message, BorderLayout.CENTER);
        }

        /** Fills the line for one entry. */
        @Override
        public Component getListCellRendererComponent(JList<? extends LogLine> list, LogLine line, int index,
                                                      boolean selected, boolean focused) {
            time.setText(line.time);
            type.setText(line.type.toString());
            type.setForeground(BadgeRenderer.colorFor(line.type.toString()));
            message.setText(line.message);
            message.setForeground(list.getForeground());
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            return this;
        }
    }
}
