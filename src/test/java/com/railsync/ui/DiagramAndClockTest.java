package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.model.Train;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.service.DelayReport;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.FcfsStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the simulation clock, the diagram's coordinate mapping and
 * interpolation, and that the diagram paints every demo state without errors.
 */
class DiagramAndClockTest {

    private SchedulingService service;
    private SimulationClock clock;
    private TimeDistanceDiagram diagram;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(new Station("Demo"), new Schedule(), new FcfsStrategy(), new ScheduleEventManager());
        clock = new SimulationClock(Ui.SERVICE_DAY.atTime(8, 0));
        diagram = new TimeDistanceDiagram(service, clock);
        DemoScenarioLoader.load(service);
        service.allocatePendingTrains();
    }

    private void paintOnce() {
        Dimension size = diagram.getPreferredSize();
        diagram.setSize(size);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        diagram.paint(g);
        g.dispose();
    }

    // Scenario: clock at 60x ticks once (100 ms real). Expected: 6 simulated seconds pass, listener told.
    @Test
    void clockTickAdvancesBySpeed() {
        List<LocalDateTime> seen = new ArrayList<>();
        clock.addListener(seen::add);
        clock.tick();
        assertEquals(Ui.SERVICE_DAY.atTime(8, 0, 6), clock.getTime());
        assertEquals(1, seen.size());
        clock.setSpeed(1);
        clock.tick();
        assertEquals(Ui.SERVICE_DAY.atTime(8, 0, 6).plusNanos(100_000_000L), clock.getTime());
    }

    // Scenario: speed 5x requested. Expected: refused (only 1, 10, 60).
    @Test
    void clockOnlyAcceptsOfferedSpeeds() {
        assertThrows(RailSyncException.class, () -> clock.setSpeed(5));
        assertFalse(clock.isPlaying());
    }

    // Scenario: map the window start and 10 minutes later to x. Expected: left margin, then +120 px (12 px/min).
    @Test
    void timeMapsToXLinearly() {
        LocalDateTime start = diagram.getWindowStart();
        assertEquals(TimeDistanceDiagram.LEFT_MARGIN, diagram.xFor(start), 0.001);
        assertEquals(TimeDistanceDiagram.LEFT_MARGIN + 10 * TimeDistanceDiagram.PIXELS_PER_MINUTE,
                diagram.xFor(start.plusMinutes(10)), 0.001);
        assertEquals(diagram.yForRow(0) + TimeDistanceDiagram.ROW_HEIGHT, diagram.yForRow(1), 0.001);
    }

    // Scenario: blend (0,0)->(10,20) at t = 0, 0.5, 1. Expected: start, middle (5,10), end.
    @Test
    void interpolationIsLinear() {
        Point2D.Double[] from = {new Point2D.Double(0, 0)};
        Point2D.Double[] to = {new Point2D.Double(10, 20)};
        assertEquals(0.0, TimeDistanceDiagram.interpolate(from, to, 0.0)[0].x, 0.001);
        assertEquals(5.0, TimeDistanceDiagram.interpolate(from, to, 0.5)[0].x, 0.001);
        assertEquals(10.0, TimeDistanceDiagram.interpolate(from, to, 0.5)[0].y, 0.001);
        assertEquals(20.0, TimeDistanceDiagram.interpolate(from, to, 1.0)[0].y, 0.001);
    }

    // Scenario: paint after allocation, after the ripple delay and after the breakdown. Expected: no exceptions.
    @Test
    void diagramPaintsEveryDemoState() {
        assertDoesNotThrow(this::paintOnce);
        Train rajdhani = service.getTrainManager().findByNumber(DemoScenarioLoader.RIPPLE_TRAIN_NUMBER);
        DelayReport report = service.delayTrain(rajdhani, DemoScenarioLoader.RIPPLE_DELAY_MINUTES);
        assertEquals(DelayReport.Outcome.RIPPLE, report.getOutcome());
        assertDoesNotThrow(this::paintOnce);
        service.reportBreakdown(service.getPlatformManager().findById(DemoScenarioLoader.BREAKDOWN_PLATFORM_ID));
        assertDoesNotThrow(this::paintOnce);
    }

    // Scenario: the delayed train's change record. Expected: it remembers the arrival from BEFORE the delay (for the ghost).
    @Test
    void delayedTrainChangeKeepsOldArrival() {
        Train rajdhani = service.getTrainManager().findByNumber(DemoScenarioLoader.RIPPLE_TRAIN_NUMBER);
        LocalDateTime before = rajdhani.getExpectedArrival();
        DelayReport report = service.delayTrain(rajdhani, DemoScenarioLoader.RIPPLE_DELAY_MINUTES);
        assertEquals(before, report.getChanges().get(0).getOldArrival());
        assertEquals(before.plusMinutes(5), rajdhani.getExpectedArrival());
    }
}
