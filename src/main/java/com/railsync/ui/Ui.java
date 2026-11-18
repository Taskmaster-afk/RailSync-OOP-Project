package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.TrainType;
import com.railsync.service.DemoScenarioLoader;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
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

    /** Colour for express trains (steel blue), used in tables and diagrams. */
    public static final Color EXPRESS_COLOR = new Color(0x2E6F9E);
    /** Colour for passenger trains (green). */
    public static final Color PASSENGER_COLOR = new Color(0x2F8A57);
    /** Colour for freight trains (signal amber, deepened to read on white). */
    public static final Color FREIGHT_COLOR = new Color(0xD08A24);
    /** Colour for conflicts and failures (red). */
    public static final Color CONFLICT_COLOR = new Color(0xC0392B);

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
     * Creates the header at the top of every screen: a clear title with one
     * quiet line underneath saying what the screen is for.
     *
     * @param title    the screen title, e.g. "Allocation"
     * @param subtitle one short sentence in plain words
     * @return the header component
     */
    public static JComponent header(String title, String subtitle) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.putClientProperty("FlatLaf.style", "font: bold +9");
        JLabel subtitleLabel = new MutedLabel(subtitle);
        subtitleLabel.putClientProperty("FlatLaf.style", "font: +1");
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitleLabel);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 14, 0));
        return panel;
    }

    /**
     * Wraps content in a plain card (no title), e.g. a table.
     *
     * @param body the content
     * @return the card
     */
    public static Card card(JComponent body) {
        Card card = new Card();
        card.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /**
     * Creates a small coloured pill such as "ALLOCATED" or "EXPRESS".
     *
     * @param text  the text
     * @param color the strong colour (text); the pill is a pale tint of it
     * @return the badge label
     */
    public static JLabel badge(String text, Color color) {
        JLabel label = new Badge();
        label.setText(text);
        label.setForeground(color);
        return label;
    }

    /** @return the badge colour for a train's status */
    public static Color colorFor(com.railsync.model.TrainStatus status) {
        switch (status) {
            case ALLOCATED:
                return Theme.GOOD;
            case UNALLOCATED:
                return Theme.BAD;
            default:
                return Theme.WARN;
        }
    }

    /** A label drawn as a rounded pill in a tint of its text colour. */
    static final class Badge extends JLabel {
        Badge() {
            setOpaque(false);
            setHorizontalAlignment(CENTER);
            putClientProperty("FlatLaf.style", "font: bold -2");
            setBorder(BorderFactory.createEmptyBorder(2, 9, 2, 9));
        }

        /** In dark mode the text is a lighter shade of the badge colour, so it stays easy to read. */
        @Override
        public Color getForeground() {
            Color color = super.getForeground();
            return Theme.isDark() && color != null ? Theme.mix(color, Color.WHITE, 0.4) : color;
        }

        /** Paints the pill behind the text. */
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int w = Math.min(getWidth() - 2, fm.stringWidth(getText()) + 18);
            int h = Math.min(getHeight() - 2, fm.getHeight() + 4);
            int x = getHorizontalAlignment() == LEFT ? 0 : (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            g2.setColor(Theme.tint(super.getForeground()));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A label in the secondary text colour that stays correct when the theme changes. */
    static final class MutedLabel extends JLabel {
        MutedLabel(String text) {
            super(text);
        }

        /** Re-reads the muted colour whenever the look and feel is (re)installed. */
        @Override
        public void updateUI() {
            super.updateUI();
            setForeground(Theme.mutedText());
        }
    }

    /**
     * Gives the main action of a screen the accent colour (like a "default"
     * button), so the eye finds it first. Use at most one per area.
     *
     * @param button the button to emphasise
     * @return the same button
     */
    public static JButton primary(JButton button) {
        // Filled with the accent colour, white bold text, a little darker on hover and press.
        String accent = Theme.ACCENT_HEX;
        button.putClientProperty("FlatLaf.style", "background: " + accent + "; foreground: #ffffff;"
                + " borderColor: " + accent + "; focusedBorderColor: " + accent + "; focusedBackground: " + accent + ";"
                + " hoverBackground: darken(" + accent + ",6%); pressedBackground: darken(" + accent + ",12%);"
                + " font: bold");
        return button;
    }

    /**
     * Puts a small bold label above a component, to group related controls.
     * Replaces the old etched "titled box" borders with a lighter look.
     *
     * @param title the section name
     * @param body  the content
     * @return a panel holding both
     */
    public static JPanel section(String title, JComponent body) {
        JLabel label = new JLabel(title);
        label.putClientProperty("FlatLaf.style", "font: bold +1");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        Card card = new Card();
        card.add(label, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** @return a monospaced font for report text (Consolas where available) */
    public static Font monoFont() {
        Font consolas = new Font("Consolas", Font.PLAIN, 13);
        return consolas.getFamily().equals("Consolas") ? consolas : new Font(Font.MONOSPACED, Font.PLAIN, 13);
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
