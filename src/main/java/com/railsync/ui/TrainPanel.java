package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.service.SchedulingService;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * Module A screen: the table of trains with search, add, edit, remove
 * (with confirmation), release and "view schedule".
 *
 * <p>Calls: SchedulingService (addTrain via the dialog, removeTrain,
 * releaseTrain, findReservation) and TrainManager.findByNumber().
 * Observes: every event, and reloads the table.</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * Polymorphism (TypeRenderer overrides getTableCellRendererComponent()).</p>
 */
public class TrainPanel extends RefreshablePanel {

    private final TrainTableModel model;
    private final JTable table;
    private final JTextField searchField = new JTextField(10);

    /**
     * Builds the train screen.
     *
     * @param service the scheduling service
     */
    public TrainPanel(SchedulingService service) {
        super(service);
        model = new TrainTableModel(service);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(model));
        // Colour the Type and Status columns so categories and problems stand out.
        table.getColumnModel().getColumn(3).setCellRenderer(new TypeRenderer());
        table.getColumnModel().getColumn(10).setCellRenderer(new TypeRenderer());

        // Step 1: toolbar with search and actions.
        JButton find = new JButton("Find");
        find.addActionListener(e -> searchByNumber());
        searchField.addActionListener(e -> searchByNumber());
        JButton add = new JButton("Add train");
        add.addActionListener(e -> openForm(null));
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> editSelected());
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> removeSelected());
        JButton release = new JButton("Release platform");
        release.addActionListener(e -> releaseSelected());
        JButton view = new JButton("View schedule");
        view.addActionListener(e -> viewSelected());

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.add(new JLabel("Search by number:"));
        tools.add(searchField);
        tools.add(find);
        tools.add(add);
        tools.add(edit);
        tools.add(remove);
        tools.add(release);
        tools.add(view);

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Train Management"), BorderLayout.NORTH);
        north.add(tools, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(new JLabel("Priority = base priority + type bonus (express +3, passenger +1, freight 0). "
                + "Times use the simulated service day."), BorderLayout.SOUTH);
        refresh();
    }

    /** Reloads the table, keeping the selected train selected. */
    @Override
    public void refresh() {
        Train selected = getSelectedTrain();
        model.reload();
        select(selected);
    }

    private void searchByNumber() {
        Train train = service.getTrainManager().findByNumber(searchField.getText());
        if (train == null) {
            Ui.showError(this, "No train with number \"" + searchField.getText().trim() + "\".");
            return;
        }
        select(train);
    }

    private void openForm(Train existing) {
        TrainFormDialog dialog = new TrainFormDialog(SwingUtilities.getWindowAncestor(this), service, existing);
        dialog.setVisible(true);
    }

    private void editSelected() {
        Train train = requireSelection();
        if (train != null) {
            openForm(train);
        }
    }

    private void removeSelected() {
        Train train = requireSelection();
        if (train == null || !Ui.confirm(this, "Remove train " + train.getNumber() + " (" + train.getName() + ")?")) {
            return;
        }
        try {
            service.removeTrain(train);
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private void releaseSelected() {
        Train train = requireSelection();
        if (train == null) {
            return;
        }
        if (service.findReservation(train) == null) {
            Ui.showInfo(this, "Train " + train.getNumber() + " has no platform to release.");
            return;
        }
        service.releaseTrain(train);
    }

    private void viewSelected() {
        Train train = requireSelection();
        if (train == null) {
            return;
        }
        PlatformReservation booking = service.findReservation(train);
        String plan = booking == null
                ? "No platform booked (status " + train.getStatus() + ")."
                : "Platform: " + booking.getPlatform() + "\nOccupies: " + Ui.time(booking.getStart())
                        + " - " + Ui.time(booking.getEnd());
        Ui.showInfo(this, train + "\nTimetable: " + Ui.time(train.getScheduledArrival()) + " - "
                + Ui.time(train.getScheduledDeparture())
                + (train.isDelayed() ? "  (delayed " + train.getDelayMinutes() + " min)" : "")
                + "\nNeeds " + train.getRequiredPlatformLength() + " m, min stop "
                + train.getMinimumDwellMinutes() + " min, priority " + train.getPriority() + "\n" + plan);
    }

    /** @return the selected train, or null (no message) */
    private Train getSelectedTrain() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        // The sorter may reorder rows, so convert the on-screen row to the model row.
        return model.getTrainAt(table.convertRowIndexToModel(viewRow));
    }

    /** @return the selected train, or null after telling the user to select one */
    private Train requireSelection() {
        Train train = getSelectedTrain();
        if (train == null) {
            Ui.showInfo(this, "Select a train in the table first.");
        }
        return train;
    }

    private void select(Train train) {
        if (train == null) {
            return;
        }
        int modelRow = model.rowOf(train);
        if (modelRow >= 0) {
            int viewRow = table.convertRowIndexToView(modelRow);
            table.setRowSelectionInterval(viewRow, viewRow);
            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        }
    }

    /** Colours train types by category and UNALLOCATED status in red. */
    private static final class TypeRenderer extends DefaultTableCellRenderer {
        /** Swing calls this for every cell; we only adjust its colour or font. */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (!selected) {
                if (value instanceof TrainType) {
                    cell.setForeground(Ui.colorFor((TrainType) value));
                } else if (value == TrainStatus.UNALLOCATED) {
                    cell.setForeground(Ui.CONFLICT_COLOR);
                } else {
                    cell.setForeground(table.getForeground());
                }
            }
            return cell;
        }
    }
}
