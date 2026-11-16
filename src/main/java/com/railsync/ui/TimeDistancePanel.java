package com.railsync.ui;

import com.railsync.service.SchedulingService;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;

/**
 * The Time-Distance / Gantt screen: the diagram plus the simulation clock
 * controls (play/pause, speed 1x/10x/60x, jump to start) and "clear ghosts".
 *
 * <p>Shows: TimeDistanceDiagram. Calls: SimulationClock (through
 * ClockControls) and the diagram's clearGhosts(); the diagram reads the
 * schedule itself. Observes: schedule events (via RefreshablePanel).</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), Composition
 * (contains a ClockControls and a TimeDistanceDiagram), [OBSERVER].</p>
 */
public class TimeDistancePanel extends RefreshablePanel {

    /**
     * Builds the screen.
     *
     * @param service the scheduling service
     * @param clock   the shared simulation clock
     */
    public TimeDistancePanel(SchedulingService service, SimulationClock clock) {
        super(service);
        TimeDistanceDiagram diagram = new TimeDistanceDiagram(service, clock);

        // Step 1: shared clock controls, plus a button to remove the ghosts of the last disruption.
        ClockControls controls = new ClockControls(clock, diagram::getWindowStart);
        JButton clearGhosts = new JButton("Clear ghosts");
        clearGhosts.addActionListener(e -> diagram.clearGhosts());
        controls.add(clearGhosts);

        // Step 2: a colour legend so the drawing explains itself.
        JLabel legend = new JLabel("<html>Lines: <font color='#3478DC'>express</font>, "
                + "<font color='#28A05A'>passenger</font>, <font color='#D28C1E'>freight</font>. "
                + "Thick flat part = time at the platform (Gantt bar). "
                + "<font color='#D73C3C'>Red flat part on the outer signal = waiting in the queue; "
                + "red X = no platform; red outline = pushed by a ripple.</font> "
                + "Dashed faded line = old position (ghost).</html>");

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Time-Distance Diagram (Gantt / occupancy view)"), BorderLayout.NORTH);
        north.add(controls, BorderLayout.CENTER);
        north.add(legend, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(diagram), BorderLayout.CENTER);
    }

    /** Nothing to re-read here: the diagram and the controls observe changes themselves. */
    @Override
    public void refresh() {
        repaint();
    }
}
