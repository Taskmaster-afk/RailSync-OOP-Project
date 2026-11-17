package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Train;
import com.railsync.service.AllocationResult;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.FcfsStrategy;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.strategy.PriorityAgingStrategy;
import com.railsync.strategy.PriorityStrategy;
import com.railsync.strategy.ShortestDwellFirstStrategy;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

/**
 * Modules C and D screen: choose the strategy and settings, allocate all
 * pending trains or one train, reset, and read the explained results.
 *
 * <p>Calls: SchedulingService.setStrategy(), setClearanceBufferMinutes(),
 * setMaxWaitMinutes(), setPreemptionEnabled(), allocatePendingTrains(),
 * allocateTrain(), resetAllocations(), getLastResults().
 * Observes: every event; it re-syncs its controls and result list.</p>
 *
 * <p>OOP concepts: [STRATEGY] (the combo box holds four objects of the same
 * interface type and passes the chosen one to the service at runtime),
 * Inheritance (extends RefreshablePanel), [OBSERVER].</p>
 */
public class AllocationPanel extends RefreshablePanel {

    private final PriorityAgingStrategy agingStrategy = new PriorityAgingStrategy();
    private final JComboBox<PlatformAllocationStrategy> strategyBox = new JComboBox<>();
    private final JSpinner agingSpinner = new JSpinner(new SpinnerNumberModel(PriorityAgingStrategy.DEFAULT_MINUTES_PER_POINT, 1, PriorityAgingStrategy.MAX_MINUTES_PER_POINT, 1));
    private final JSpinner bufferSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 60, 1));
    private final JSpinner maxWaitSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 180, 5));
    private final JCheckBox preemptionBox = new JCheckBox("Allow safe preemption");
    private final JTextArea description = new JTextArea(4, 30);
    private final JComboBox<Train> singleTrainBox = new JComboBox<>();
    private final JTextArea results = new JTextArea();
    /** True while refresh() is setting controls, so their listeners do not call the service back. */
    private boolean updatingControls;

    /**
     * Builds the allocation screen.
     *
     * @param service the scheduling service
     */
    public AllocationPanel(SchedulingService service) {
        super(service);

        // Step 1: the four strategies, all held through the interface type.
        // [STRATEGY] the list holds different classes behind one interface.
        strategyBox.addItem(new FcfsStrategy());
        strategyBox.addItem(new PriorityStrategy());
        strategyBox.addItem(new ShortestDwellFirstStrategy());
        strategyBox.addItem(agingStrategy);
        strategyBox.setRenderer(new StrategyListRenderer());
        strategyBox.addActionListener(e -> strategyChosen());

        // Step 2: settings that apply immediately when changed.
        agingSpinner.addChangeListener(e -> agingRateChanged());
        bufferSpinner.addChangeListener(e -> applySetting(() -> service.setClearanceBufferMinutes((Integer) bufferSpinner.getValue())));
        maxWaitSpinner.addChangeListener(e -> applySetting(() -> service.setMaxWaitMinutes((Integer) maxWaitSpinner.getValue())));
        preemptionBox.addActionListener(e -> applySetting(() -> service.setPreemptionEnabled(preemptionBox.isSelected())));

        JPanel settings = new JPanel(new GridLayout(0, 2, 8, 8));
        settings.add(new JLabel("Strategy"));
        settings.add(strategyBox);
        settings.add(new JLabel("Aging: minutes per +1 priority"));
        settings.add(agingSpinner);
        settings.add(new JLabel("Clearance buffer (min)"));
        settings.add(bufferSpinner);
        settings.add(new JLabel("Max wait at outer signal (min)"));
        settings.add(maxWaitSpinner);
        settings.add(new JLabel("Preemption"));
        settings.add(preemptionBox);

        // The strategy explanation reads as plain text, not as a boxed input field.
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setFocusable(false);

        // Step 3: actions. The main one is emphasised; the single-train pair sits on one row.
        JButton allocateAll = Ui.primary(new JButton("Allocate all pending trains"));
        allocateAll.addActionListener(e -> allocateAll());
        singleTrainBox.setRenderer(new TrainRenderer());
        JButton allocateOne = new JButton("Allocate this train");
        allocateOne.addActionListener(e -> allocateOne());
        JButton reset = new JButton("Reset all allocations");
        reset.addActionListener(e -> resetAll());
        JPanel singleRow = new JPanel(new BorderLayout(6, 0));
        singleRow.add(singleTrainBox, BorderLayout.CENTER);
        singleRow.add(allocateOne, BorderLayout.EAST);
        JPanel actions = new JPanel(new GridLayout(0, 1, 6, 6));
        actions.add(allocateAll);
        actions.add(singleRow);
        actions.add(reset);

        // The three sections are stacked at the top; any spare height stays below them, not between them.
        JPanel stack = new JPanel(new BorderLayout(0, 16));
        stack.add(Ui.section("Strategy and settings", settings), BorderLayout.NORTH);
        stack.add(Ui.section("How this strategy works", description), BorderLayout.CENTER);
        stack.add(Ui.section("Actions", actions), BorderLayout.SOUTH);
        JPanel left = new JPanel(new BorderLayout());
        left.add(stack, BorderLayout.NORTH);
        left.setPreferredSize(new Dimension(430, 10));

        // Step 4: results of the last run, one line per train with the reason.
        results.setEditable(false);
        results.setLineWrap(true);
        results.setWrapStyleWord(true);
        results.setFont(Ui.monoFont());
        results.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane resultsScroll = new JScrollPane(results);

        add(Ui.header("Allocation",
                "Choose a scheduling strategy and place the waiting trains on platforms."), BorderLayout.NORTH);
        add(left, BorderLayout.WEST);
        add(Ui.section("Results of the last allocation, in decision order", resultsScroll), BorderLayout.CENTER);
        refresh();
    }

    /** Syncs every control with the service and redraws the result list. */
    @Override
    public void refresh() {
        updatingControls = true;
        strategyBox.setSelectedItem(findStrategyItem(service.getStrategy()));
        bufferSpinner.setValue(service.getSchedule().getClearanceBufferMinutes());
        maxWaitSpinner.setValue(service.getMaxWaitMinutes());
        preemptionBox.setSelected(service.isPreemptionEnabled());
        agingSpinner.setEnabled(service.getStrategy() == agingStrategy);
        description.setText(service.getStrategy().getDescription()
                + "\n\nPlatform choice is always First-Fit (lowest-numbered free compatible platform).");
        singleTrainBox.removeAllItems();
        for (Train train : service.getTrainManager().getTrainsWithoutPlatform()) {
            singleTrainBox.addItem(train);
        }
        updatingControls = false;
        showResults(service.getLastResults());
    }

    private void strategyChosen() {
        if (updatingControls) {
            return;
        }
        // [STRATEGY] algorithm chosen at runtime through the interface.
        service.setStrategy((PlatformAllocationStrategy) strategyBox.getSelectedItem());
    }

    private void agingRateChanged() {
        if (updatingControls) {
            return;
        }
        agingStrategy.setMinutesPerPriorityPoint((Integer) agingSpinner.getValue());
        // Setting it again announces the change to every observer.
        service.setStrategy(agingStrategy);
    }

    /** Runs a settings change unless refresh() is the one moving the control. */
    private void applySetting(Runnable change) {
        if (updatingControls) {
            return;
        }
        try {
            change.run();
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private void allocateAll() {
        if (service.getTrainManager().getTrainsWithoutPlatform().isEmpty()) {
            Ui.showInfo(this, "Every train already has a platform. Press \"Reset all allocations\" to try another strategy.");
            return;
        }
        service.allocatePendingTrains();
    }

    private void allocateOne() {
        Train train = (Train) singleTrainBox.getSelectedItem();
        if (train == null) {
            Ui.showInfo(this, "There is no train without a platform.");
            return;
        }
        AllocationResult result = service.allocateTrain(train);
        Ui.showInfo(this, result.getMessage());
    }

    private void resetAll() {
        if (Ui.confirm(this, "Release every platform booking? (Delays and failed platforms stay.)")) {
            service.resetAllocations();
        }
    }

    private void showResults(List<AllocationResult> list) {
        if (list.isEmpty()) {
            results.setText("No allocation run yet. Choose a strategy and press \"Allocate all pending trains\".");
            return;
        }
        StringBuilder text = new StringBuilder();
        int placed = 0;
        for (AllocationResult result : list) {
            text.append(result.isAllocated() ? "OK   " : "NO   ").append(result.getMessage()).append('\n');
            if (result.isAllocated()) {
                placed++;
            }
        }
        text.append("\nPlaced ").append(placed).append(" of ").append(list.size()).append(" train(s).");
        results.setText(text.toString());
        results.setCaretPosition(0);
    }

    /** @return the combo item that is the given strategy object, or the first item */
    private PlatformAllocationStrategy findStrategyItem(PlatformAllocationStrategy current) {
        for (int i = 0; i < strategyBox.getItemCount(); i++) {
            PlatformAllocationStrategy item = strategyBox.getItemAt(i);
            // Same object, or same kind of strategy (the service may hold its own FCFS instance).
            if (item == current || item.getClass() == current.getClass()) {
                return item;
            }
        }
        return strategyBox.getItemAt(0);
    }

    /** Shows trains as "12951  Rajdhani Express  08:00". */
    private static final class TrainRenderer extends DefaultListCellRenderer {
        /** Swing calls this for every list item; we only replace the text shown. */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof Train) {
                Train train = (Train) value;
                setText(train.getNumber() + "  " + train.getName() + "  " + Ui.time(train.getExpectedArrival()));
            }
            return this;
        }
    }
}
