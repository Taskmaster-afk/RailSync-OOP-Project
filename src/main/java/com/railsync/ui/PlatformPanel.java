package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.PlatformStatus;
import com.railsync.model.Train;
import com.railsync.service.SchedulingService;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * Module B screen: the table of platforms with add, edit, remove (with
 * confirmation) and repair, plus a compatibility check that lists, for the
 * selected platform, every train that can or cannot use it and why.
 *
 * <p>Calls: SchedulingService (addPlatform via the dialog, removePlatform,
 * repairPlatform) and Train.getIncompatibilityReason() (read-only).
 * Observes: every event, and reloads.</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * [POLYMORPHISM] in the compatibility list (each Train subclass computes its
 * own required length).</p>
 */
public class PlatformPanel extends RefreshablePanel {

    private final PlatformTableModel model;
    private final JTable table;
    private final JTextArea compatibility = new JTextArea();

    /**
     * Builds the platform screen.
     *
     * @param service the scheduling service
     */
    public PlatformPanel(SchedulingService service) {
        super(service);
        model = new PlatformTableModel(service);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        table.getSelectionModel().addListSelectionListener(e -> showCompatibility());

        JButton add = Ui.primary(new JButton("Add platform"));
        add.addActionListener(e -> new PlatformFormDialog(SwingUtilities.getWindowAncestor(this), service, null).setVisible(true));
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> editSelected());
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> removeSelected());
        JButton repair = new JButton("Repair (mark operational)");
        repair.addActionListener(e -> repairSelected());
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.add(add);
        tools.add(edit);
        tools.add(remove);
        tools.add(repair);

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.header("Platforms",
                "Lengths, accepted train types and status. Select a platform to see which trains can use it."), BorderLayout.NORTH);
        north.add(tools, BorderLayout.CENTER);

        compatibility.setEditable(false);
        compatibility.setFont(Ui.monoFont());
        JScrollPane compatibilityScroll = new JScrollPane(compatibility);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table),
                Ui.section("Compatibility of the selected platform", compatibilityScroll));
        split.setResizeWeight(0.5);

        add(north, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        refresh();
    }

    /** Reloads the table, keeping the selection, and updates the compatibility list. */
    @Override
    public void refresh() {
        Platform selected = getSelectedPlatform();
        model.reload();
        if (selected != null && model.rowOf(selected) >= 0) {
            int row = model.rowOf(selected);
            table.setRowSelectionInterval(row, row);
        } else if (model.getRowCount() > 0) {
            // Select the first platform so the compatibility check is never empty.
            table.setRowSelectionInterval(0, 0);
        }
        showCompatibility();
    }

    /** Lists every train with OK or the reason it cannot use the selected platform. */
    private void showCompatibility() {
        Platform platform = getSelectedPlatform();
        if (platform == null) {
            compatibility.setText("Select a platform to see which trains can use it.");
            return;
        }
        StringBuilder text = new StringBuilder(platform + " accepts " + PlatformTableModel.describeTypes(platform)
                + ", status " + platform.getStatus() + "\n\n");
        for (Train train : service.getTrainManager().getAllTrains()) {
            String reason = train.getIncompatibilityReason(platform);
            text.append(String.format("%-8s %-22s %4d m needed   %s%n", train.getNumber(), train.getName(),
                    train.getRequiredPlatformLength(), reason == null ? "OK" : "NO: " + reason));
        }
        compatibility.setText(text.toString());
        compatibility.setCaretPosition(0);
    }

    private void editSelected() {
        Platform platform = requireSelection();
        if (platform != null) {
            new PlatformFormDialog(SwingUtilities.getWindowAncestor(this), service, platform).setVisible(true);
        }
    }

    private void removeSelected() {
        Platform platform = requireSelection();
        if (platform == null || !Ui.confirm(this, "Remove " + platform + "?")) {
            return;
        }
        try {
            service.removePlatform(platform);
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private void repairSelected() {
        Platform platform = requireSelection();
        if (platform == null) {
            return;
        }
        if (platform.isOperational()) {
            Ui.showInfo(this, platform + " is already operational.");
            return;
        }
        service.repairPlatform(platform);
    }

    private Platform getSelectedPlatform() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getPlatformAt(row);
    }

    private Platform requireSelection() {
        Platform platform = getSelectedPlatform();
        if (platform == null) {
            Ui.showInfo(this, "Select a platform in the table first.");
        }
        return platform;
    }

    /** Shows FAILED platforms in red. */
    private static final class StatusRenderer extends DefaultTableCellRenderer {
        /** Swing calls this for every cell; we only adjust its colour or font. */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (!selected) {
                cell.setForeground(value == PlatformStatus.FAILED ? Ui.CONFLICT_COLOR : table.getForeground());
            }
            return cell;
        }
    }
}
