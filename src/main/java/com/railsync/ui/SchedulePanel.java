package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.Train;
import com.railsync.service.AllocationResult;
import com.railsync.service.SchedulingService;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * The Schedule screen: every platform booking in a table (filterable by
 * platform), and below it the trains that have no platform with the reason.
 *
 * <p>Calls: read-only methods of SchedulingService. Observes: every event.</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER].</p>
 */
public class SchedulePanel extends RefreshablePanel {

    private final ReservationTableModel model;
    private final JComboBox<Platform> platformFilter = new JComboBox<>();
    private final JTextArea unallocated = new JTextArea(6, 40);
    /** True while refresh() refills the filter, so its listener does not reload twice. */
    private boolean updatingFilter;

    /**
     * Builds the schedule screen.
     *
     * @param service the scheduling service
     */
    public SchedulePanel(SchedulingService service) {
        super(service);
        model = new ReservationTableModel(service);
        JTable table = new JTable(model);

        platformFilter.setRenderer(new PlatformRenderer());
        platformFilter.addActionListener(e -> {
            if (!updatingFilter) {
                model.reload((Platform) platformFilter.getSelectedItem());
            }
        });
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.add(new JLabel("Show platform:"));
        tools.add(platformFilter);
        tools.add(new JLabel("   \"Waited\" = platform start minus timetable arrival (includes delay)."));

        unallocated.setEditable(false);
        unallocated.setLineWrap(true);
        unallocated.setWrapStyleWord(true);
        unallocated.setFont(Ui.monoFont());
        JScrollPane unallocatedScroll = new JScrollPane(unallocated);

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.header("Schedule",
                "Every platform booking in time order, and the trains that are still without a platform."), BorderLayout.NORTH);
        north.add(tools, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table),
                Ui.section("Trains without a platform", unallocatedScroll));
        split.setResizeWeight(0.75);
        add(north, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        refresh();
    }

    /** Refills the platform filter, the table and the unallocated list. */
    @Override
    public void refresh() {
        // Step 1: rebuild the filter list (null item = all platforms), keeping the choice.
        Platform chosen = (Platform) platformFilter.getSelectedItem();
        updatingFilter = true;
        platformFilter.removeAllItems();
        platformFilter.addItem(null);
        for (Platform platform : service.getStation().getPlatforms()) {
            platformFilter.addItem(platform);
        }
        platformFilter.setSelectedItem(chosen);
        updatingFilter = false;
        model.reload((Platform) platformFilter.getSelectedItem());

        // Step 2: list every train without a platform and the latest reason we know.
        StringBuilder text = new StringBuilder();
        for (Train train : service.getTrainManager().getTrainsWithoutPlatform()) {
            text.append(String.format("%-8s %-22s %-11s ", train.getNumber(), train.getName(), train.getStatus()));
            text.append(lastReasonFor(train)).append('\n');
        }
        unallocated.setText(text.length() == 0 ? "Every train has a platform." : text.toString());
        unallocated.setCaretPosition(0);
    }

    /** @return the message of the latest allocation result for this train, or a hint */
    private String lastReasonFor(Train train) {
        for (AllocationResult result : service.getLastResults()) {
            if (result.getTrain() == train && !result.isAllocated()) {
                return result.getMessage();
            }
        }
        return "(not processed yet - use the Allocation screen)";
    }

    /** Shows "All platforms" for the null item. */
    private static final class PlatformRenderer extends DefaultListCellRenderer {
        /** Swing calls this for every list item; we only replace the text shown. */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            setText(value == null ? "All platforms" : value.toString());
            return this;
        }
    }
}
