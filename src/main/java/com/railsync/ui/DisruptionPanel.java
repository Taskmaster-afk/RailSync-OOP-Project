package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.ReservationChange;
import com.railsync.model.Train;
import com.railsync.service.BreakdownReport;
import com.railsync.service.DelayReport;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * Modules E and E2 screen: delay a train, break down a platform, and read
 * exactly what happened (outcome, ripple cascade, who moved and who could
 * not). Its own time-distance diagram underneath shows the ghost-and-slide
 * animation the moment an action is taken.
 *
 * <p>Calls: SchedulingService.delayTrain() and reportBreakdown(). Observes:
 * every event (refreshes the drop-down lists).</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER].</p>
 */
public class DisruptionPanel extends RefreshablePanel {

    private final JComboBox<Train> trainBox = new JComboBox<>();
    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 240, 1));
    private final JComboBox<Platform> platformBox = new JComboBox<>();
    private final JTextArea report = new JTextArea();

    /**
     * Builds the screen.
     *
     * @param service the scheduling service
     * @param clock   the shared simulation clock (for the embedded diagram)
     */
    public DisruptionPanel(SchedulingService service, SimulationClock clock) {
        super(service);

        // Step 1: delay controls.
        trainBox.setRenderer(new NameRenderer());
        JButton applyDelay = Ui.primary(new JButton("Apply delay"));
        applyDelay.addActionListener(e -> applyDelay((Train) trainBox.getSelectedItem(), (Integer) minutesSpinner.getValue()));
        JPanel delayBox = new JPanel(new GridLayout(0, 1, 4, 4));
        delayBox.add(trainBox);
        delayBox.add(labelled("Delay (min):", minutesSpinner));
        delayBox.add(applyDelay);

        // Step 2: breakdown controls.
        platformBox.setRenderer(new NameRenderer());
        JButton breakdown = new JButton("Report breakdown");
        breakdown.addActionListener(e -> breakDown((Platform) platformBox.getSelectedItem()));
        JPanel breakdownBox = new JPanel(new GridLayout(0, 1, 4, 4));
        breakdownBox.add(platformBox);
        breakdownBox.add(breakdown);

        // Step 3: one-click scripted demo steps (they work on the loaded demo scenario).
        JButton demoDelay = new JButton("Demo: delay " + DemoScenarioLoader.RIPPLE_TRAIN_NUMBER + " by "
                + DemoScenarioLoader.RIPPLE_DELAY_MINUTES + " min (ripple)");
        demoDelay.addActionListener(e -> applyDelay(service.getTrainManager().findByNumber(DemoScenarioLoader.RIPPLE_TRAIN_NUMBER),
                DemoScenarioLoader.RIPPLE_DELAY_MINUTES));
        JButton demoBreakdown = new JButton("Demo: break down PF-2");
        demoBreakdown.addActionListener(e -> breakDown(service.getPlatformManager().findById(DemoScenarioLoader.BREAKDOWN_PLATFORM_ID)));
        JPanel demoBox = new JPanel(new GridLayout(0, 1, 4, 4));
        demoBox.add(demoDelay);
        demoBox.add(demoBreakdown);

        JPanel controls = new JPanel(new GridLayout(0, 1, 8, 8));
        controls.add(Ui.section("Delay a train", delayBox));
        controls.add(Ui.section("Platform breakdown", breakdownBox));
        controls.add(Ui.section("Scripted demo (after FCFS allocation)", demoBox));

        // Step 4: the report of the last action.
        report.setEditable(false);
        report.setLineWrap(true);
        report.setWrapStyleWord(true);
        report.setFont(Ui.monoFont());
        report.setText("Apply a delay or a breakdown. The result is explained here, and the diagram below "
                + "animates the change: the old line stays as a faded dashed ghost.");
        JScrollPane reportScroll = new JScrollPane(report);

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(controls, BorderLayout.NORTH);
        left.add(Ui.section("What happened", reportScroll), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(420, 10));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
                new JScrollPane(new TimeDistanceDiagram(service, clock)));
        split.setResizeWeight(0.0);
        add(Ui.header("Delay & Breakdown",
                "Delay a train or take a platform out of service, and see exactly what moved and why."), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        refresh();
    }

    /** Refills the train and platform lists, keeping the current choices. */
    @Override
    public void refresh() {
        Train chosenTrain = (Train) trainBox.getSelectedItem();
        trainBox.removeAllItems();
        for (Train train : service.getTrainManager().getAllTrains()) {
            trainBox.addItem(train);
        }
        if (chosenTrain != null) {
            trainBox.setSelectedItem(chosenTrain);
        }
        Platform chosenPlatform = (Platform) platformBox.getSelectedItem();
        platformBox.removeAllItems();
        for (Platform platform : service.getStation().getPlatforms()) {
            if (platform.isOperational()) {
                platformBox.addItem(platform);
            }
        }
        if (chosenPlatform != null) {
            platformBox.setSelectedItem(chosenPlatform);
        }
    }

    private void applyDelay(Train train, int minutes) {
        if (train == null) {
            Ui.showInfo(this, "That train is not in the current scenario.");
            return;
        }
        try {
            DelayReport result = service.delayTrain(train, minutes);
            // Step 1: outcome and explanation.
            StringBuilder text = new StringBuilder("Outcome: " + result.getOutcome() + "\n\n" + result.getMessage() + "\n");
            // Step 2: every booking that moved (the delayed train first).
            if (!result.getChanges().isEmpty()) {
                text.append("\nBookings changed:\n");
                for (ReservationChange change : result.getChanges()) {
                    text.append("  ").append(change).append('\n');
                }
            }
            // Step 3: the ripple cascade, as a chain.
            if (result.getCascadeCount() > 0) {
                text.append("\nRipple cascade: ").append(result.getCascadeCount()).append(" train(s) pushed: ")
                        .append(train.getNumber());
                for (ReservationChange change : result.getCascade()) {
                    text.append(" -> ").append(change.getTrain().getNumber());
                }
                text.append('\n');
            }
            report.setText(text.toString());
            report.setCaretPosition(0);
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private void breakDown(Platform platform) {
        if (platform == null || !platform.isOperational()) {
            Ui.showInfo(this, "Choose a working platform (failed platforms can be repaired on the Platforms screen).");
            return;
        }
        if (!Ui.confirm(this, "Mark " + platform + " as failed and move its trains?")) {
            return;
        }
        BreakdownReport result = service.reportBreakdown(platform);
        report.setText(result.toString());
        report.setCaretPosition(0);
    }

    private static JPanel labelled(String text, Component field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(text), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** Shows trains as "12951  Rajdhani Express" and platforms by their label. */
    private static final class NameRenderer extends DefaultListCellRenderer {
        /** Swing calls this for every list item; we only replace the text shown. */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof Train) {
                Train train = (Train) value;
                setText(train.getNumber() + "  " + train.getName()
                        + (train.getAssignedPlatform() == null ? "  (no platform)" : "  on " + train.getAssignedPlatform()));
            }
            return this;
        }
    }
}
