package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Train;
import com.railsync.service.SchedulingService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the list of trains to a JTable: one row per train.
 *
 * <p>Why it exists: Swing tables read data through a TableModel. This class
 * only READS trains and their bookings; it never changes them.</p>
 *
 * <p>OOP concepts: Inheritance (extends AbstractTableModel) and Polymorphism
 * (JTable calls the overridden getValueAt(), getRowCount() and so on).</p>
 *
 * <p>Works with: TrainPanel, SchedulingService, Train.</p>
 */
public class TrainTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"ID", "Number", "Name", "Type", "Arrival", "Departure",
        "Dwell", "Base prio", "Priority", "Delay", "Status", "Platform", "Platform start"};

    private final SchedulingService service;
    private List<Train> rows = new ArrayList<>();

    /**
     * Creates the model.
     *
     * @param service where the trains are read from
     */
    public TrainTableModel(SchedulingService service) {
        this.service = service;
    }

    /** Re-reads the trains from the service and tells the table to redraw. */
    public void reload() {
        rows = new ArrayList<>(service.getTrainManager().getAllTrains());
        fireTableDataChanged();
    }

    /**
     * Returns the train shown in a model row.
     *
     * @param row model row index
     * @return the train
     */
    public Train getTrainAt(int row) {
        return rows.get(row);
    }

    /**
     * Finds the model row of a train.
     *
     * @param train the train
     * @return the row, or -1 if it is not shown
     */
    public int rowOf(Train train) {
        return rows.indexOf(train);
    }

    /** @return how many rows the table shows */
    @Override
    public int getRowCount() {
        return rows.size();
    }

    /** @return how many columns the table shows */
    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /** @return the heading of the given column */
    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /** Returns what one cell shows. Read-only: nothing in the model is changed. */
    @Override
    public Object getValueAt(int row, int column) {
        Train train = rows.get(row);
        PlatformReservation booking = service.findReservation(train);
        Platform platform = train.getAssignedPlatform();
        switch (column) {
            case 0: return train.getId();
            case 1: return train.getNumber();
            case 2: return train.getName();
            case 3: return train.getType();
            case 4: return Ui.time(train.getScheduledArrival());
            case 5: return Ui.time(train.getScheduledDeparture());
            case 6: return train.getDwellTime().toMinutes() + " min";
            case 7: return train.getBasePriority();
            // [POLYMORPHISM] the effective priority comes from the real subclass.
            case 8: return train.getPriority();
            case 9: return train.isDelayed() ? "+" + train.getDelayMinutes() + " min" : "";
            case 10: return train.getStatus();
            case 11: return platform == null ? "-" : platform.toString();
            default: return booking == null ? "-" : Ui.time(booking.getStart());
        }
    }
}
