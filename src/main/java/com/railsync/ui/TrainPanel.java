package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Train;
import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
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
 * Polymorphism (BadgeRenderer is a TableCellRenderer that JTable calls for the type and status columns).</p>
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
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(model));
        // Colour the Type and Status columns so categories and problems stand out.
        table.getColumnModel().getColumn(3).setCellRenderer(new BadgeRenderer());
        table.getColumnModel().getColumn(10).setCellRenderer(new BadgeRenderer());
        // Room for the pills and the names; the narrow number columns give way first.
        int[] widths = {45, 70, 150, 115, 65, 75, 60, 70, 60, 60, 125, 110, 105};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(3).setMinWidth(105);
        table.getColumnModel().getColumn(10).setMinWidth(115);

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

        // Tools in three quiet groups: the main action, editing, then search on the right.
        Ui.primary(add);
        searchField.putClientProperty("JTextField.placeholderText", "Train number");
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tools.add(add);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(edit);
        tools.add(remove);
        tools.add(release);
        tools.add(view);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        search.add(searchField);
        search.add(find);
        JPanel toolRow = new JPanel(new BorderLayout());
        toolRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        toolRow.add(tools, BorderLayout.WEST);
        toolRow.add(search, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.header("Trains",
                "Add, edit and search trains. Priority = base priority + type bonus (express +3, passenger +1, freight 0)."), BorderLayout.NORTH);
        north.add(toolRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(Ui.card(new JScrollPane(table)), BorderLayout.CENTER);
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
}
