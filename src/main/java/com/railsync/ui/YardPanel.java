package com.railsync.ui;

import com.railsync.service.AllocationResult;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.strategy.StrategyCatalog;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * The Yard View screen: the top-down YardView with clock controls and a
 * "switch strategy from now" button.
 *
 * <p>Switching strategy here re-plans only what has not happened yet: trains
 * already at or past a platform keep their booking; trains still on the way
 * or waiting in the queue are decided again by the new strategy, starting at
 * the current simulated time. So with the clock running you can watch the
 * queue being served in a different order.</p>
 *
 * <p>Calls: SchedulingService.setStrategy() and reallocateFrom().
 * Observes: schedule events (via RefreshablePanel).</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), Composition
 * (contains ClockControls and YardView), [STRATEGY] (choice made at runtime).</p>
 */
public class YardPanel extends RefreshablePanel {

    private final SimulationClock clock;
    private final JComboBox<PlatformAllocationStrategy> strategyBox = new JComboBox<>();

    /**
     * Builds the screen.
     *
     * @param service the scheduling service
     * @param clock   the shared simulation clock
     */
    public YardPanel(SchedulingService service, SimulationClock clock) {
        super(service);
        this.clock = clock;
        YardView yard = new YardView(service, clock);

        // Step 1: clock controls (shared with the Time-Distance screen through the same clock).
        ClockControls controls = new ClockControls(clock, yard::getFirstArrival);

        // Step 2: live strategy switching.
        for (PlatformAllocationStrategy strategy : StrategyCatalog.createAll()) {
            strategyBox.addItem(strategy);
        }
        strategyBox.setRenderer(new StrategyListRenderer());
        JButton switchNow = new JButton("Switch strategy from now");
        switchNow.addActionListener(e -> switchStrategyNow());
        JPanel strategyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        strategyRow.add(new JLabel("Strategy:"));
        strategyRow.add(strategyBox);
        strategyRow.add(switchNow);
        strategyRow.add(new JLabel("  (re-plans only trains that have not reached a platform yet)"));

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Yard View (top-down, follows the simulation clock)"), BorderLayout.NORTH);
        north.add(controls, BorderLayout.CENTER);
        north.add(strategyRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(yard), BorderLayout.CENTER);
    }

    /** The yard redraws itself; nothing else to refresh. */
    @Override
    public void refresh() {
        repaint();
    }

    /** Applies the chosen strategy from the current simulated time onwards and reports the result. */
    private void switchStrategyNow() {
        if (service.getSchedule().size() == 0) {
            Ui.showInfo(this, "Allocate the trains first (Allocation screen), then switch while the clock runs.");
            return;
        }
        // [STRATEGY] the algorithm is swapped at runtime, then the future is re-planned with it.
        service.setStrategy((PlatformAllocationStrategy) strategyBox.getSelectedItem());
        List<AllocationResult> results = service.reallocateFrom(clock.getTime());
        int placed = 0;
        for (AllocationResult result : results) {
            if (result.isAllocated()) {
                placed++;
            }
        }
        Ui.showInfo(this, "From " + Ui.time(clock.getTime()) + " the " + service.getStrategy().getName()
                + " strategy re-planned " + results.size() + " train(s); " + placed + " got a platform.");
    }
}
