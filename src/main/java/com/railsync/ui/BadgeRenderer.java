package com.railsync.ui;

import com.railsync.model.TrainStatus;
import com.railsync.model.TrainType;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

/**
 * Shows a table cell as a coloured pill: train types (EXPRESS, PASSENGER,
 * FREIGHT), train and platform statuses, and audit-log event types.
 *
 * <p>Why it exists: a colour plus a word is read faster than a word alone,
 * and the same colours are used in the diagrams, so the screens agree.</p>
 *
 * <p>OOP concepts: implements Swing's TableCellRenderer interface ([POLYMORPHISM]:
 * JTable calls our version for these columns).</p>
 *
 * <p>Works with: TrainPanel, PlatformPanel, SchedulePanel, ReportsPanel.</p>
 */
public class BadgeRenderer implements TableCellRenderer {

    private final JPanel holder = new JPanel(new BorderLayout());
    private final Ui.Badge badge = new Ui.Badge();

    /** Creates the renderer. */
    public BadgeRenderer() {
        badge.setHorizontalAlignment(SwingConstants.LEFT);
        holder.add(badge, BorderLayout.CENTER);
        holder.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 4));
    }

    /**
     * Returns the pill for one cell.
     *
     * @return the component Swing paints for this cell
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                   int row, int column) {
        String text = value == null ? "" : value.toString();
        badge.setText(text);
        badge.setForeground(colorFor(text));
        badge.setVisible(!text.isEmpty() && !text.equals("-"));
        Color background = selected ? table.getSelectionBackground()
                : (row % 2 == 1 && javax.swing.UIManager.getColor("Table.alternateRowColor") != null
                ? javax.swing.UIManager.getColor("Table.alternateRowColor") : table.getBackground());
        holder.setBackground(background);
        return holder;
    }

    /**
     * Picks the colour for a word.
     *
     * @param text the cell text
     * @return green for good states, amber for waiting or changes, red for problems, blue otherwise
     */
    static Color colorFor(String text) {
        String word = text.trim().toUpperCase();
        for (TrainType type : TrainType.values()) {
            if (type.name().equals(word)) {
                return Ui.colorFor(type);
            }
        }
        for (TrainStatus status : TrainStatus.values()) {
            if (status.name().equals(word)) {
                return Ui.colorFor(status);
            }
        }
        switch (word) {
            case "OPERATIONAL":
            case "ALLOCATION":
            case "UNDO":
                return Theme.GOOD;
            case "FAILED":
            case "CONFLICT":
            case "BREAKDOWN":
                return Theme.BAD;
            case "DELAY":
            case "REASSIGNMENT":
            case "SCHEDULE_RESET":
                return Theme.WARN;
            default:
                return Theme.ACCENT;
        }
    }
}
