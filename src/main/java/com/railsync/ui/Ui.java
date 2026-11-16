package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.TrainType;
import com.railsync.service.DemoScenarioLoader;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Small shared helpers for every screen: time formatting and parsing,
 * colours, fonts and message dialogs.
 *
 * <p>Why it exists: without it, each panel would repeat the same formatting
 * and dialog code. It holds NO business rules; parsing "08:15" into a time is
 * input handling, while checking that departure is after arrival stays in Train.</p>
 *
 * <p>Works with: every panel and dialog in this package.</p>
 */
public final class Ui {

    /** The day all GUI times belong to (the app simulates one service day). */
    public static final LocalDate SERVICE_DAY = DemoScenarioLoader.SERVICE_DAY;

    /** Colour for express trains (blue), used in tables and diagrams. */
    public static final Color EXPRESS_COLOR = new Color(52, 120, 220);
    /** Colour for passenger trains (green). */
    public static final Color PASSENGER_COLOR = new Color(40, 160, 90);
    /** Colour for freight trains (amber). */
    public static final Color FREIGHT_COLOR = new Color(210, 140, 30);
    /** Colour for conflicts and failures (red). */
    public static final Color CONFLICT_COLOR = new Color(215, 60, 60);

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private Ui() {
        // Only static helpers.
    }

    /**
     * Formats a time as "08:15".
     *
     * @param time the time (may be null)
     * @return "HH:mm", or "-" for null
     */
    public static String time(LocalDateTime time) {
        return time == null ? "-" : time.format(HH_MM);
    }

    /**
     * Reads a time typed as "HH:mm" on the service day.
     *
     * @param text      what the user typed
     * @param fieldName name shown in the error message
     * @return the time on SERVICE_DAY
     * @throws RailSyncException with a friendly message if the format is wrong
     */
    public static LocalDateTime parseTime(String text, String fieldName) {
        try {
            return SERVICE_DAY.atTime(LocalTime.parse(text.trim(), HH_MM));
        } catch (DateTimeParseException e) {
            throw new RailSyncException(fieldName + " must be a time like 08:15 (24-hour HH:mm).");
        }
    }

    /**
     * Returns the colour used for a train category.
     *
     * @param type the category
     * @return its colour
     */
    public static Color colorFor(TrainType type) {
        switch (type) {
            case EXPRESS:
                return EXPRESS_COLOR;
            case PASSENGER:
                return PASSENGER_COLOR;
            default:
                return FREIGHT_COLOR;
        }
    }

    /**
     * Creates a bold section heading.
     *
     * @param text heading text
     * @return the label
     */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return label;
    }

    /** @return a monospaced font for report text */
    public static Font monoFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, 13);
    }

    /**
     * Shows an error message (for example a validation failure).
     *
     * @param parent  the component the dialog is centred on
     * @param message plain-English message
     */
    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "RailSync", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Shows an information message.
     *
     * @param parent  the component the dialog is centred on
     * @param message plain-English message
     */
    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "RailSync", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Asks a yes/no question (used before every delete).
     *
     * @param parent   the component the dialog is centred on
     * @param question the question
     * @return true if the user chose Yes
     */
    public static boolean confirm(Component parent, String question) {
        int answer = JOptionPane.showConfirmDialog(parent, question, "Please confirm", JOptionPane.YES_NO_OPTION);
        return answer == JOptionPane.YES_OPTION;
    }
}
