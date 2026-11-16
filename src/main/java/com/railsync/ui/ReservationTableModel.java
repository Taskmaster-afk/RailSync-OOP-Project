package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.Train;
import com.railsync.service.MetricsCalculator;
import com.railsync.service.SchedulingService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts platform bookings to a JTable: one row per booking, ordered by
 * platform number and then by start time. Can be limited to one platform.
 *
 * <p>OOP concepts: Inheritance (extends AbstractTableModel), Polymorphism.
 * Waiting minutes come from MetricsCalculator, so the definition matches the
 * Reports screen exactly.</p>
 *
 * <p>Works with: SchedulePanel, SchedulingService, MetricsCalculator.</p>
 */
public class ReservationTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Platform", "Train", "Name", "Type", "Timetable arr.",
        "Platform from", "Platform until", "Dwell", "Waited", "Delay"};

    private final SchedulingService service;
    private final MetricsCalculator metrics = new MetricsCalculator();
    private List<PlatformReservation> rows = new ArrayList<>();

    /**
     * Creates the model.
     *
     * @param service where the bookings are read from
     */
    public ReservationTableModel(SchedulingService service) {
        this.service = service;
    }

    /**
     * Re-reads the bookings.
     *
     * @param onlyPlatform show only this platform, or null for all
     */
    public void reload(Platform onlyPlatform) {
        rows = new ArrayList<>();
        // Station keeps platforms sorted and getReservationsOn() sorts by time,
        // so the rows come out in platform order, then time order.
        for (Platform platform : service.getStation().getPlatforms()) {
            if (onlyPlatform == null || onlyPlatform == platform) {
                rows.addAll(service.getSchedule().getReservationsOn(platform));
            }
        }
        fireTableDataChanged();
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
        PlatformReservation booking = rows.get(row);
        Train train = booking.getTrain();
        switch (column) {
            case 0: return booking.getPlatform().toString();
            case 1: return train.getNumber();
            case 2: return train.getName();
            case 3: return train.getType();
            case 4: return Ui.time(train.getScheduledArrival());
            case 5: return Ui.time(booking.getStart());
            case 6: return Ui.time(booking.getEnd());
            case 7: return booking.getDuration().toMinutes() + " min";
            case 8: return metrics.waitingMinutes(train, booking) + " min";
            default: return train.isDelayed() ? "+" + train.getDelayMinutes() + " min" : "";
        }
    }
}
