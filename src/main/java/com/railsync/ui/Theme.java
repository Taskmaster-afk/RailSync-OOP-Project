package com.railsync.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

/**
 * The application's visual style in one place: a single calm accent colour,
 * softly rounded corners, slim scrollbars, airy tables, and light or dark mode.
 *
 * <p>Why it exists: every screen looks consistent without each one setting
 * colours or sizes itself. Swing components read these values from UIManager,
 * so changing a value here restyles the whole app.</p>
 *
 * <p>It contains no business rules; it only configures FlatLaf (the look and
 * feel library).</p>
 *
 * <p>Works with: Main (applies the light theme at start), MainFrame (dark mode
 * switch), every screen (they read the colours below).</p>
 */
public final class Theme {

    /** The one accent colour: a muted, classic blue. */
    public static final String ACCENT_HEX = "#3B6FB6";

    private static boolean dark;

    private Theme() {
        // Only static methods.
    }

    /**
     * Installs the light or dark theme with RailSync's settings. Call before
     * creating windows; to switch an open window, call it and then
     * {@code FlatLaf.updateUI()}.
     *
     * @param darkMode true for the dark theme
     */
    public static void apply(boolean darkMode) {
        dark = darkMode;
        // Step 1: the accent colour, picked up by buttons, focus rings, selections and toggles.
        Map<String, String> extras = new HashMap<>();
        extras.put("@accentColor", ACCENT_HEX);
        FlatLaf.setGlobalExtraDefaults(extras);

        // Step 2: install the base look and feel.
        if (darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        // Step 3: shapes and spacing (the same in light and dark).
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("TitlePane.unifiedBackground", true);

        // Step 4: tables. Only horizontal lines, taller rows, padding in every cell.
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.rowHeight", 28);
        UIManager.put("Table.cellMargins", new Insets(2, 10, 2, 10));
        UIManager.put("TableHeader.height", 30);
        UIManager.put("TableHeader.cellMargins", new Insets(2, 10, 2, 10));
        // Every other row very slightly tinted, so long tables are easy to follow.
        UIManager.put("Table.alternateRowColor", shade(UIManager.getColor("Table.background"), darkMode ? 0.04 : -0.025));
    }

    /** @return true if the dark theme is active */
    public static boolean isDark() {
        return dark;
    }

    /** @return the background of the side navigation (a touch different from the content) */
    public static Color sidebarBackground() {
        return shade(UIManager.getColor("Panel.background"), dark ? 0.03 : -0.025);
    }

    /** @return the colour of thin lines and card outlines */
    public static Color lineColor() {
        return UIManager.getColor("Component.borderColor");
    }

    /** @return the colour for secondary text (captions, subtitles) */
    public static Color mutedText() {
        return UIManager.getColor("Label.disabledForeground");
    }

    /** @return the canvas colour for the diagrams (the same as table cells) */
    public static Color canvas() {
        return UIManager.getColor("Table.background");
    }

    /**
     * Makes a colour a little lighter (positive amount) or darker (negative).
     *
     * @param color  the starting colour
     * @param amount from -1.0 (black) to 1.0 (white); small values like 0.03 are typical
     * @return the new colour
     */
    static Color shade(Color color, double amount) {
        int target = amount > 0 ? 255 : 0;
        double t = Math.abs(amount);
        int r = (int) Math.round(color.getRed() + (target - color.getRed()) * t);
        int g = (int) Math.round(color.getGreen() + (target - color.getGreen()) * t);
        int b = (int) Math.round(color.getBlue() + (target - color.getBlue()) * t);
        return new Color(r, g, b);
    }
}
