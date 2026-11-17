package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.ReservationChange;
import com.railsync.model.Train;
import com.railsync.model.TrainMovement;
import com.railsync.observer.ScheduleEvent;
import com.railsync.observer.ScheduleEventType;
import com.railsync.observer.ScheduleObserver;
import com.railsync.service.SchedulingService;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The time-distance diagram (also the Gantt / occupancy view).
 *
 * <p>What is drawn:</p>
 * <ul>
 *   <li>X axis = time; Y axis = the path into the station: outer signal,
 *       home signal, one row per platform, exit signal.</li>
 *   <li>Each train is a line: a flat part on the outer-signal row while it
 *       waits (drawn red: a conflict made it queue), a slope down through the
 *       home signal, a thick flat bar on its platform row (this bar is the
 *       Gantt bar), and a slope down to the exit signal.</li>
 *   <li>Colour = train type. Red X at the outer signal = a train with no
 *       platform. Red-outlined lines = trains pushed by the latest ripple.</li>
 *   <li>A vertical "now" line shows the SimulationClock time.</li>
 *   <li>After a delay or breakdown, each moved train's OLD line stays as a
 *       faded dashed "ghost", and its new line slides from old to new in
 *       about 500 ms (linear interpolation, driven by a Swing Timer).</li>
 * </ul>
 *
 * <p>Separation of concerns: this class only READS the schedule (bookings,
 * TrainMovement); it never changes anything.</p>
 *
 * <p>Observes: ScheduleEvent (repaint; start an animation on DELAY and
 * BREAKDOWN) and SimulationClock (repaint the now line).</p>
 *
 * <p>OOP concepts: Inheritance (extends JPanel, overrides paintComponent and
 * getPreferredSize), Polymorphism (Swing calls our paintComponent),
 * [OBSERVER] (implements ScheduleObserver and SimulationClock.Listener).</p>
 */
public class TimeDistanceDiagram extends JPanel implements ScheduleObserver, SimulationClock.Listener {

    /** Horizontal scale: pixels per simulated minute. */
    static final int PIXELS_PER_MINUTE = 12;
    /** Space on the left for row labels. */
    static final int LEFT_MARGIN = 120;
    /** Space at the top for time labels. */
    static final int TOP_MARGIN = 34;
    /** Height of one row (signal or platform). */
    static final int ROW_HEIGHT = 36;
    /** Length of the ghost-to-new animation. */
    static final int ANIMATION_MILLIS = 500;

    private static final int RIGHT_MARGIN = 40;
    private static final int OUTER_ROW = 0;
    private static final int HOME_ROW = 1;
    private static final int FIRST_PLATFORM_ROW = 2;

    private final SchedulingService service;
    private final SimulationClock clock;

    // The visible time window, recalculated before each paint.
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    // Animation state: the moves of the latest disruption and when the slide began.
    private List<ReservationChange> ghosts = new ArrayList<>();
    private boolean ghostsAreRipple;
    private long animationStartMillis;
    private final Timer animationTimer;

    /**
     * Creates the diagram and subscribes it to schedule and clock changes.
     *
     * @param service where the bookings are read from
     * @param clock   the shared simulation clock (for the now line)
     */
    public TimeDistanceDiagram(SchedulingService service, SimulationClock clock) {
        this.service = service;
        this.clock = clock;
        // Repaint about 60 times a second while an animation runs; stop when it is done.
        this.animationTimer = new Timer(15, e -> {
            if (animationProgress() >= 1.0) {
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        service.getEvents().subscribe(this);
        clock.addListener(this);
        computeWindow();
    }

    // ------------------------------------------------------------------
    // Reacting to changes.
    // ------------------------------------------------------------------

    /** Uses the theme's canvas colour as background, also after a light/dark switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(Theme.canvas());
    }

    /**
     * Repaints after any change; starts the ghost animation after a delay or breakdown.
     *
     * @param event what happened
     */
    @Override
    public void onScheduleEvent(ScheduleEvent event) {
        boolean disruption = event.getType() == ScheduleEventType.DELAY
                || event.getType() == ScheduleEventType.BREAKDOWN;
        if (disruption && !event.getChanges().isEmpty()) {
            // Step 1: remember the moved bookings (old positions become ghosts).
            ghosts = new ArrayList<>(event.getChanges());
            // In a DELAY event the first change is the delayed train; the rest are the ripple cascade.
            ghostsAreRipple = event.getType() == ScheduleEventType.DELAY && ghosts.size() > 1;
            // Step 2: start the 500 ms slide from old to new.
            animationStartMillis = System.currentTimeMillis();
            animationTimer.restart();
        } else if (event.getType() == ScheduleEventType.SCHEDULE_RESET || event.getType() == ScheduleEventType.UNDO) {
            // After a reset or an undo the old ghosts no longer describe a real change.
            ghosts = new ArrayList<>();
        }
        revalidate();
        repaint();
    }

    /**
     * Repaints so the now line follows the clock.
     *
     * @param now the simulated time
     */
    @Override
    public void onClockChanged(LocalDateTime now) {
        repaint();
    }

    /** Removes the ghost lines of the last disruption. */
    public void clearGhosts() {
        ghosts = new ArrayList<>();
        repaint();
    }

    /** @return the earliest time shown (used to reset the clock to the start of the day) */
    public LocalDateTime getWindowStart() {
        computeWindow();
        return windowStart;
    }

    // ------------------------------------------------------------------
    // Coordinate mapping.
    // ------------------------------------------------------------------

    /**
     * Converts a time to an x pixel: x = left margin + minutes since window start x scale.
     * Example: window starts 07:50, scale 12 px/min: 08:00 -> 120 + 10 x 12 = 240.
     *
     * @param time the time
     * @return x coordinate in pixels
     */
    double xFor(LocalDateTime time) {
        double minutes = Duration.between(windowStart, time).getSeconds() / 60.0;
        return LEFT_MARGIN + minutes * PIXELS_PER_MINUTE;
    }

    /**
     * Converts a row number to the y pixel of the middle of that row.
     * Rows: 0 outer signal, 1 home signal, 2.. platforms, last = exit signal.
     *
     * @param row row number
     * @return y coordinate in pixels
     */
    double yForRow(int row) {
        return TOP_MARGIN + row * ROW_HEIGHT + ROW_HEIGHT / 2.0;
    }

    /** @return the row of a platform, or -1 if it is no longer in the station */
    private int rowOf(Platform platform) {
        int index = service.getStation().getPlatforms().indexOf(platform);
        return index < 0 ? -1 : FIRST_PLATFORM_ROW + index;
    }

    private int exitRow() {
        return FIRST_PLATFORM_ROW + service.getStation().getPlatformCount();
    }

    /**
     * The six corner points of a train's line, in pixels.
     * Always six, so an old and a new line can be blended point by point.
     */
    private Point2D.Double[] pathPoints(TrainMovement movement, int platformRow) {
        return new Point2D.Double[]{
            new Point2D.Double(xFor(movement.getReachesOuterSignal()), yForRow(OUTER_ROW)),
            new Point2D.Double(xFor(movement.getLeavesOuterSignal()), yForRow(OUTER_ROW)),
            new Point2D.Double(xFor(movement.getPassesHomeSignal()), yForRow(HOME_ROW)),
            new Point2D.Double(xFor(movement.getPlatformStart()), yForRow(platformRow)),
            new Point2D.Double(xFor(movement.getPlatformEnd()), yForRow(platformRow)),
            new Point2D.Double(xFor(movement.getClearsExit()), yForRow(exitRow()))
        };
    }

    /** Works out the visible window: all bookings, ghosts and unallocated trains, rounded to 10 minutes. */
    private void computeWindow() {
        LocalDateTime first = null;
        LocalDateTime last = null;
        for (PlatformReservation booking : service.getSchedule().getAllReservations()) {
            TrainMovement movement = TrainMovement.of(booking);
            first = earlier(first, movement.getReachesOuterSignal());
            last = later(last, movement.getClearsExit());
        }
        for (ReservationChange ghost : ghosts) {
            first = earlier(first, ghost.getOldArrival().minusMinutes(TrainMovement.APPROACH_MINUTES));
        }
        for (Train train : service.getTrainManager().getTrainsWithoutPlatform()) {
            first = earlier(first, train.getExpectedArrival().minusMinutes(TrainMovement.APPROACH_MINUTES));
            last = later(last, train.getExpectedDeparture());
        }
        if (first == null) {
            first = Ui.SERVICE_DAY.atTime(8, 0);
            last = Ui.SERVICE_DAY.atTime(10, 0);
        }
        // Round outwards to whole 10-minute marks so the grid labels are tidy.
        windowStart = first.withSecond(0).withNano(0).minusMinutes(first.getMinute() % 10);
        windowEnd = last.withSecond(0).withNano(0).plusMinutes(20 - last.getMinute() % 10);
    }

    /** @return the size needed for the whole window (the panel sits in a scroll pane) */
    @Override
    public Dimension getPreferredSize() {
        computeWindow();
        long minutes = Duration.between(windowStart, windowEnd).toMinutes();
        int width = LEFT_MARGIN + (int) (minutes * PIXELS_PER_MINUTE) + RIGHT_MARGIN;
        int height = TOP_MARGIN + (exitRow() + 1) * ROW_HEIGHT + 20;
        return new Dimension(width, height);
    }

    // ------------------------------------------------------------------
    // Painting.
    // ------------------------------------------------------------------

    /**
     * Draws the whole diagram, back to front.
     *
     * @param graphics Swing's graphics object
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        computeWindow();
        // Step 1: rows, time grid and labels.
        drawGrid(g);
        // Step 2: faded dashed ghosts of where moved trains used to be.
        drawGhosts(g);
        // Step 3: every booked train's line (sliding if it is being animated).
        for (PlatformReservation booking : service.getSchedule().getAllReservations()) {
            drawTrain(g, booking);
        }
        // Step 4: trains with no platform, as red X marks at the outer signal.
        drawUnallocated(g);
        // Step 5: the simulation clock's "now" line on top of everything.
        drawNowLine(g);
        g.dispose();
    }

    /** Draws row bands and labels, failed platforms in red, and a vertical line every 10 minutes. */
    private void drawGrid(Graphics2D g) {
        Color text = UIManager.getColor("Label.foreground");
        Color faint = withAlpha(text, 40);
        int right = getPreferredSize().width - RIGHT_MARGIN;

        // Rows: label on the left, thin line across.
        List<Platform> platforms = service.getStation().getPlatforms();
        for (int row = 0; row <= exitRow(); row++) {
            String label;
            if (row == OUTER_ROW) {
                label = "Outer signal";
            } else if (row == HOME_ROW) {
                label = "Home signal";
            } else if (row == exitRow()) {
                label = "Exit signal";
            } else {
                Platform platform = platforms.get(row - FIRST_PLATFORM_ROW);
                label = "PF-" + platform.getNumber() + " (" + platform.getLengthMeters() + " m)";
                if (!platform.isOperational()) {
                    // A failed platform gets a red band so it is obvious no train may use it.
                    g.setColor(withAlpha(Ui.CONFLICT_COLOR, 45));
                    g.fillRect(LEFT_MARGIN, (int) (yForRow(row) - ROW_HEIGHT / 2.0), right - LEFT_MARGIN, ROW_HEIGHT);
                    label = label + " FAILED";
                }
            }
            g.setColor(faint);
            g.drawLine(LEFT_MARGIN, (int) yForRow(row), right, (int) yForRow(row));
            g.setColor(text);
            g.drawString(label, 8, (int) yForRow(row) + 5);
        }

        // Time grid: a line and a label every 10 minutes.
        LocalDateTime mark = windowStart;
        int bottom = (int) yForRow(exitRow()) + ROW_HEIGHT / 2;
        while (!mark.isAfter(windowEnd)) {
            int x = (int) xFor(mark);
            g.setColor(faint);
            g.drawLine(x, TOP_MARGIN - 4, x, bottom);
            g.setColor(text);
            g.drawString(Ui.time(mark), x - 15, TOP_MARGIN - 10);
            mark = mark.plusMinutes(10);
        }
    }

    /** Draws the old lines of the last disruption, faded and dashed. */
    private void drawGhosts(Graphics2D g) {
        Stroke dashed = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 6f}, 0f);
        Composite normal = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setStroke(dashed);
        for (ReservationChange ghost : ghosts) {
            int row = rowOf(ghost.getOldPlatform());
            if (row < 0) {
                continue;
            }
            g.setColor(Ui.colorFor(ghost.getTrain().getType()));
            g.draw(toPath(oldPoints(ghost, row)));
        }
        g.setComposite(normal);
    }

    /** Draws one booked train: red queue segment, sloped approach, thick platform bar, exit slope, label. */
    private void drawTrain(Graphics2D g, PlatformReservation booking) {
        int row = rowOf(booking.getPlatform());
        if (row < 0) {
            return;
        }
        TrainMovement movement = TrainMovement.of(booking);
        Point2D.Double[] points = pathPoints(movement, row);

        // If this train is part of the running animation, blend from its old line to its new one.
        ReservationChange change = findGhost(booking.getTrain());
        if (change != null) {
            int oldRow = rowOf(change.getOldPlatform());
            if (oldRow >= 0) {
                points = interpolate(oldPoints(change, oldRow), points, animationProgress());
            }
        }

        // Ripple highlight: a wide red outline under the line of every pushed train.
        boolean pushedByRipple = ghostsAreRipple && change != null && ghosts.indexOf(change) > 0;
        if (pushedByRipple) {
            g.setColor(withAlpha(Ui.CONFLICT_COLOR, 150));
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(toPath(points));
        }

        // Approach and exit slopes (thin), then the platform stay as a thick Gantt bar.
        g.setColor(Ui.colorFor(booking.getTrain().getType()));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(toPath(points));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(points[3], points[4]));
        // Waiting at the outer signal = in the ready queue because of a conflict. Drawn last, in red,
        // so it is not hidden under the train's own colour.
        if (points[1].x - points[0].x > 1) {
            g.setColor(Ui.CONFLICT_COLOR);
            g.setStroke(new BasicStroke(4f));
            g.draw(new Line2D.Double(points[0], points[1]));
        }

        // Train number above its platform bar.
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.setColor(UIManager.getColor("Label.foreground"));
        String label = booking.getTrain().getNumber() + (pushedByRipple ? "  ripple " + ghosts.indexOf(change) : "");
        g.drawString(label, (int) points[3].x + 2, (int) points[3].y - 8);
    }

    /** Draws a red X and the train number at the outer signal for every train without a platform. */
    private void drawUnallocated(Graphics2D g) {
        g.setStroke(new BasicStroke(3f));
        int count = 0;
        for (Train train : service.getTrainManager().getTrainsWithoutPlatform()) {
            int x = (int) xFor(train.getExpectedArrival().minusMinutes(TrainMovement.APPROACH_MINUTES));
            int y = (int) yForRow(OUTER_ROW);
            g.setColor(Ui.CONFLICT_COLOR);
            g.drawLine(x - 6, y - 6, x + 6, y + 6);
            g.drawLine(x - 6, y + 6, x + 6, y - 6);
            // Alternate labels above and below the row so neighbouring marks do not overlap.
            int labelY = (count % 2 == 0) ? y - 8 : y + 18;
            g.drawString(train.getNumber() + " no platform", x + 9, labelY);
            count++;
        }
    }

    /** Draws the vertical "now" line of the simulation clock. */
    private void drawNowLine(Graphics2D g) {
        LocalDateTime now = clock.getTime();
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            return;
        }
        int x = (int) xFor(now);
        g.setColor(new Color(150, 80, 220));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, TOP_MARGIN - 4, x, (int) yForRow(exitRow()) + ROW_HEIGHT / 2);
        g.drawString("now " + Ui.time(now), x + 4, TOP_MARGIN + 10);
    }

    // ------------------------------------------------------------------
    // Animation helpers.
    // ------------------------------------------------------------------

    /** @return 0.0 when the slide starts, 1.0 when it has finished (after ANIMATION_MILLIS) */
    private double animationProgress() {
        double elapsed = System.currentTimeMillis() - animationStartMillis;
        return Math.min(1.0, elapsed / ANIMATION_MILLIS);
    }

    /**
     * Linear interpolation of every point: p = old + (new - old) x t.
     * At t = 0 the line is where it used to be; at t = 1 it is in its new place.
     */
    static Point2D.Double[] interpolate(Point2D.Double[] from, Point2D.Double[] to, double t) {
        Point2D.Double[] result = new Point2D.Double[to.length];
        for (int i = 0; i < to.length; i++) {
            double x = from[i].x + (to[i].x - from[i].x) * t;
            double y = from[i].y + (to[i].y - from[i].y) * t;
            result[i] = new Point2D.Double(x, y);
        }
        return result;
    }

    /** The line the train had before the change, using its old arrival, platform and slot. */
    private Point2D.Double[] oldPoints(ReservationChange change, int oldRow) {
        TrainMovement old = new TrainMovement(change.getOldArrival(), change.getOldStart(), change.getOldEnd());
        return pathPoints(old, oldRow);
    }

    /** @return the change record for this train in the current ghost list, or null */
    private ReservationChange findGhost(Train train) {
        for (ReservationChange change : ghosts) {
            if (change.getTrain() == train) {
                return change;
            }
        }
        return null;
    }

    private static Path2D.Double toPath(Point2D.Double[] points) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0].x, points[0].y);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i].x, points[i].y);
        }
        return path;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a == null || b.isBefore(a) ? b : a;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}
