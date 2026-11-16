package com.railsync.ui;

import com.railsync.model.Platform;
import com.railsync.model.PlatformReservation;
import com.railsync.model.TrainType;
import com.railsync.service.SchedulingService;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the station's platforms to a JTable: one row per platform, with its
 * status, accepted categories and how busy it is.
 *
 * <p>OOP concepts: Inheritance (extends AbstractTableModel), Polymorphism
 * (JTable calls the overridden methods). Read-only: it never changes a platform.</p>
 *
 * <p>Works with: PlatformPanel, SchedulingService, Platform.</p>
 */
public class PlatformTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"ID", "Number", "Length (m)", "Status", "Accepts",
        "Bookings", "First booking", "Last free from"};

    private final SchedulingService service;
    private List<Platform> rows = new ArrayList<>();

    /**
     * Creates the model.
     *
     * @param service where the platforms are read from
     */
    public PlatformTableModel(SchedulingService service) {
        this.service = service;
    }

    /** Re-reads the platforms and redraws. */
    public void reload() {
        rows = new ArrayList<>(service.getStation().getPlatforms());
        fireTableDataChanged();
    }

    /**
     * Returns the platform in a model row.
     *
     * @param row model row index
     * @return the platform
     */
    public Platform getPlatformAt(int row) {
        return rows.get(row);
    }

    /**
     * Finds the model row of a platform.
     *
     * @param platform the platform
     * @return the row, or -1
     */
    public int rowOf(Platform platform) {
        return rows.indexOf(platform);
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
        Platform platform = rows.get(row);
        List<PlatformReservation> bookings = service.getSchedule().getReservationsOn(platform);
        switch (column) {
            case 0: return platform.getId();
            case 1: return platform.getNumber();
            case 2: return platform.getLengthMeters();
            case 3: return platform.getStatus();
            case 4: return describeTypes(platform);
            case 5: return bookings.size();
            case 6: return bookings.isEmpty() ? "-" : Ui.time(bookings.get(0).getStart());
            default: return bookings.isEmpty() ? "all day" : Ui.time(bookings.get(bookings.size() - 1).getEnd());
        }
    }

    /** @return e.g. "EXPRESS, PASSENGER" in enum order (sets have no fixed order) */
    static String describeTypes(Platform platform) {
        StringBuilder text = new StringBuilder();
        for (TrainType type : TrainType.values()) {
            if (platform.supports(type)) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(type);
            }
        }
        return text.toString();
    }
}
