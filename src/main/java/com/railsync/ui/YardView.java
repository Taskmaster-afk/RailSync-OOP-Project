package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Train;
import com.railsync.model.TrainMovement;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-down yard view: the station seen from above at the simulated "now".
 *
 * <p>What is drawn:</p>
 * <ul>
 *   <li>Left: the <b>ready queue</b> box at the outer signal. Trains that
 *       have arrived but have no free platform wait here (OS: ready queue),
 *       listed in arrival order. A red entry has no platform at all.</li>
 *   <li>Right: one horizontal track per platform; the grey block is the
 *       platform, drawn to scale (its length in metres).</li>
 *   <li>Trains are coloured rectangles, as long as the train (to scale). They
 *       slide in from the entry, stand at the platform, then slide out to the
 *       exit, using TrainMovement.phaseAt() and progressAt() at the clock time.</li>
 * </ul>
 *
 * <p>Read-only: like the time-distance diagram, it never changes the model.</p>
 *
 * <p>Observes: ScheduleEvent and SimulationClock (repaint on both).</p>
 *
 * <p>OOP concepts: Inheritance (extends JPanel, overrides paintComponent),
 * [OBSERVER] (two listener interfaces).</p>
 */
public class YardView extends JPanel implements ScheduleObserver, SimulationClock.Listener {

    private static final int TOP = 50;
    private static final int ROW_HEIGHT = 46;
    private static final int QUEUE_X = 16;
    private static final int QUEUE_WIDTH = 200;
    private static final int QUEUE_SLOT = 22;
    /** Where trains appear (the outer/home signal side of the tracks). */
    private static final int ENTRY_X = 250;
    /** Where every platform begins. */
    private static final int PLATFORM_X = 330;
    /** Drawing scale: pixels per metre of platform or train. */
    private static final double PIXELS_PER_METRE = 0.55;
    /** Platforms longer than this are drawn at this length, so the view keeps a sensible width. */
    private static final int MAX_DRAWN_METRES = 800;
    private static final int EXIT_X = PLATFORM_X + (int) (MAX_DRAWN_METRES * PIXELS_PER_METRE) + 50;
    private static final int TRAIN_HEIGHT = 16;

    private final SchedulingService service;
    private final SimulationClock clock;

    /**
     * Creates the view and subscribes it to schedule and clock changes.
     *
     * @param service where the bookings are read from
     * @param clock   the shared simulation clock
     */
    public YardView(SchedulingService service, SimulationClock clock) {
        this.service = service;
        this.clock = clock;
        service.getEvents().subscribe(this);
        clock.addListener(this);
    }

    /** Uses the theme's canvas colour as background, also after a light/dark switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(Theme.canvas());
    }

    /**
     * Repaints after any schedule change.
     *
     * @param event what happened
     */
    @Override
    public void onScheduleEvent(ScheduleEvent event) {
        revalidate();
        repaint();
    }

    /**
     * Repaints as the clock moves, so trains slide.
     *
     * @param now the simulated time
     */
    @Override
    public void onClockChanged(LocalDateTime now) {
        repaint();
    }

    /**
     * Returns a good starting time for the clock: when the first train reaches the outer signal.
     *
     * @return the earliest outer-signal arrival, or 07:50 if there are no trains
     */
    public LocalDateTime getFirstArrival() {
        LocalDateTime first = null;
        for (Train train : service.getTrainManager().getAllTrains()) {
            LocalDateTime atOuter = train.getExpectedArrival().minusMinutes(TrainMovement.APPROACH_MINUTES);
            if (first == null || atOuter.isBefore(first)) {
                first = atOuter;
            }
        }
        return first == null ? Ui.SERVICE_DAY.atTime(7, 50) : first;
    }

    /** @return the size needed for all tracks and the queue */
    @Override
    public Dimension getPreferredSize() {
        int rows = Math.max(service.getStation().getPlatformCount(), 4);
        return new Dimension(EXIT_X + 120, TOP + rows * ROW_HEIGHT + 60);
    }

    /**
     * Draws the yard at the clock's current time.
     *
     * @param graphics Swing's graphics object
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        LocalDateTime now = clock.getTime();
        // Step 1: the fixed scenery: tracks, platforms, signals, the queue box.
        drawTracks(g);
        // Step 2: trains on the tracks; collect the ones waiting at the outer signal.
        List<String> queue = new ArrayList<>();
        List<Color> queueColors = new ArrayList<>();
        for (PlatformReservation booking : service.getSchedule().getAllReservations()) {
            drawBookedTrain(g, booking, now, queue, queueColors);
        }
        // Step 3: trains with no platform wait in the queue (red) until their maximum wait runs out.
        for (Train train : service.getTrainManager().getTrainsWithoutPlatform()) {
            LocalDateTime atOuter = train.getExpectedArrival().minusMinutes(TrainMovement.APPROACH_MINUTES);
            LocalDateTime givesUp = atOuter.plusMinutes(service.getMaxWaitMinutes());
            if (!now.isBefore(atOuter) && now.isBefore(givesUp)) {
                queue.add(train.getNumber() + "  no platform");
                queueColors.add(Ui.CONFLICT_COLOR);
            }
        }
        // Step 4: the queue contents, then the clock time.
        drawQueue(g, queue, queueColors);
        g.setColor(UIManager.getColor("Label.foreground"));
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.drawString("Simulated time " + Ui.time(now) + "   |   strategy: " + service.getStrategy().getName(),
                PLATFORM_X, 24);
        g.dispose();
    }

    /** Track lines, platform blocks drawn to scale, and the entry/exit signal markers. */
    private void drawTracks(Graphics2D g) {
        Color text = UIManager.getColor("Label.foreground");
        List<Platform> platforms = service.getStation().getPlatforms();
        for (int i = 0; i < platforms.size(); i++) {
            Platform platform = platforms.get(i);
            int y = rowY(i);
            g.setColor(new Color(130, 130, 130));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(ENTRY_X - 20, y, EXIT_X, y);
            int width = scaled(platform.getLengthMeters());
            g.setColor(platform.isOperational() ? new Color(160, 160, 160, 90) : new Color(215, 60, 60, 90));
            g.fillRect(PLATFORM_X, y - TRAIN_HEIGHT, width, TRAIN_HEIGHT * 2);
            g.setColor(text);
            g.setFont(getFont().deriveFont(11f));
            g.drawString("PF-" + platform.getNumber() + (platform.isOperational() ? "" : " FAILED"),
                    ENTRY_X - 20, y - TRAIN_HEIGHT - 2);
        }
        // Signal markers above the tracks.
        g.setColor(Ui.CONFLICT_COLOR);
        g.fillOval(ENTRY_X - 26, TOP - 30, 10, 10);
        g.setColor(text);
        g.drawString("home signal", ENTRY_X - 12, TOP - 21);
        g.drawString("exit", EXIT_X - 10, TOP - 21);
    }

    /** Draws one booked train where it is now, or adds it to the queue if it is holding. */
    private void drawBookedTrain(Graphics2D g, PlatformReservation booking, LocalDateTime now,
                                 List<String> queue, List<Color> queueColors) {
        Train train = booking.getTrain();
        TrainMovement movement = TrainMovement.of(booking);
        TrainMovement.Phase phase = movement.phaseAt(now);
        // Step 1: not here yet, or already gone: nothing to draw.
        if (phase == TrainMovement.Phase.NOT_YET_ARRIVED || phase == TrainMovement.Phase.GONE) {
            return;
        }
        // Step 2: waiting at the outer signal: it goes in the ready-queue list instead of on a track.
        if (phase == TrainMovement.Phase.HOLDING_AT_OUTER_SIGNAL) {
            long waited = Duration.between(movement.getReachesOuterSignal(), now).toMinutes();
            queue.add(train.getNumber() + "  pr " + train.getPriority() + "  waiting " + waited + "m");
            queueColors.add(Ui.colorFor(train.getType()));
            return;
        }
        // Step 3: work out x along its platform's track from the phase and how far through it is.
        int row = service.getStation().getPlatforms().indexOf(booking.getPlatform());
        if (row < 0) {
            return;
        }
        int length = Math.min(scaled(train.getRequiredPlatformLength()), scaled(MAX_DRAWN_METRES));
        double progress = movement.progressAt(now);
        double x;
        if (phase == TrainMovement.Phase.APPROACHING) {
            // Slides in: from the entry signal to the start of the platform.
            x = ENTRY_X + (PLATFORM_X - ENTRY_X) * progress;
        } else if (phase == TrainMovement.Phase.AT_PLATFORM) {
            x = PLATFORM_X;
        } else {
            // DEPARTING: slides from the platform to the exit.
            x = PLATFORM_X + (EXIT_X - PLATFORM_X) * progress;
        }
        // Step 4: the train itself, coloured by type, with its number.
        int y = rowY(row) - TRAIN_HEIGHT / 2;
        g.setColor(Ui.colorFor(train.getType()));
        g.fillRoundRect((int) x, y, length, TRAIN_HEIGHT, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.drawString(train.getNumber(), (int) x + 4, y + TRAIN_HEIGHT - 4);
    }

    /** The ready-queue box with one line per waiting train, in arrival order. */
    private void drawQueue(Graphics2D g, List<String> queue, List<Color> colors) {
        int height = Math.max(service.getStation().getPlatformCount() * ROW_HEIGHT, (queue.size() + 2) * QUEUE_SLOT);
        // A thin, softly rounded outline, matching the cards elsewhere in the app.
        g.setColor(Theme.lineColor());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(QUEUE_X, TOP - 30, QUEUE_WIDTH, height, 10, 10);
        g.setColor(UIManager.getColor("Label.foreground"));
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("Ready queue (outer signal): " + queue.size(), QUEUE_X + 6, TOP - 14);
        g.setFont(getFont().deriveFont(11f));
        for (int i = 0; i < queue.size(); i++) {
            int y = TOP + i * QUEUE_SLOT;
            g.setColor(colors.get(i));
            g.fillRoundRect(QUEUE_X + 6, y - 4, 14, 14, 4, 4);
            g.setColor(UIManager.getColor("Label.foreground"));
            g.drawString(queue.get(i), QUEUE_X + 26, y + 8);
        }
    }

    private static int rowY(int index) {
        return TOP + 20 + index * ROW_HEIGHT;
    }

    private static int scaled(int metres) {
        return (int) (Math.min(metres, MAX_DRAWN_METRES) * PIXELS_PER_METRE);
    }
}
