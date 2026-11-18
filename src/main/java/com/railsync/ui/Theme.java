package com.railsync.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

/**
 * The application's visual system in one place: the "RailSync Signal"
 * palette (navy, steel blue and signal amber), light and dark modes, rounded
 * cards on a soft page background, airy tables and the icon set.
 *
 * <p>Why it exists: every screen looks consistent without each one setting
 * colours or sizes itself. Swing components read these values from UIManager,
 * so changing a value here restyles the whole app.</p>
 *
 * <p>It contains no business rules; it only configures FlatLaf (the look and
 * feel library) and hands out colours and icons.</p>
 *
 * <p>Works with: Main (applies the light theme at start), MainFrame (dark mode
 * switch), Ui (cards, badges), every screen (they read the colours below).</p>
 */
public final class Theme {

    /** Interactive accent (buttons, selection, focus): steel blue. */
    public static final String ACCENT_HEX = "#2E6F9E";
    /** The accent as a Color. */
    public static final Color ACCENT = new Color(0x2E6F9E);
    /** The brand's signal amber (logo, selected navigation item, highlights). */
    public static final Color SIGNAL = new Color(0xF2A33A);
    /** Navy used for the sidebar in light mode. */
    public static final Color NAVY = new Color(0x14213D);
    /** Success / allocated (green). */
    public static final Color GOOD = new Color(0x2F8A57);
    /** Warning / waiting (amber-brown, readable on white). */
    public static final Color WARN = new Color(0xB7791F);
    /** Danger / conflict / failure (red). */
    public static final Color BAD = new Color(0xC0392B);

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

        // Step 3: surfaces. The page is a soft grey; cards, tables and text areas are a clean surface on top of it.
        Color page = pageBackground();
        Color surface = cardBackground();
        Color text = darkMode ? new Color(0xE6EAF0) : new Color(0x1B2430);
        put("Panel.background", page);
        put("RootPane.background", page);
        put("OptionPane.background", page);
        put("Viewport.background", surface);
        put("Table.background", surface);
        put("List.background", surface);
        put("TextArea.background", surface);
        put("EditorPane.background", surface);
        // Read-only text areas (reports, results) use the "inactive" colour; keep it the card surface too.
        put("TextArea.inactiveBackground", surface);
        put("EditorPane.inactiveBackground", surface);
        put("TextPane.inactiveBackground", surface);
        put("Label.foreground", text);
        put("Component.borderColor", lineColor());
        put("Separator.foreground", lineColor());
        put("ScrollPane.background", surface);

        // Step 4: shapes and spacing (the same in light and dark).
        put("Component.arc", 10);
        put("Button.arc", 10);
        put("TextComponent.arc", 8);
        put("CheckBox.arc", 5);
        put("Component.focusWidth", 1);
        put("Component.innerFocusWidth", 0);
        put("Button.margin", new Insets(5, 14, 5, 14));
        put("Button.toolbar.margin", new Insets(5, 10, 5, 10));
        put("Button.default.boldText", true);
        put("ScrollBar.showButtons", false);
        put("ScrollBar.width", 10);
        put("ScrollBar.thumbArc", 999);
        put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        put("ScrollBar.track", surface);
        put("SplitPane.dividerSize", 10);
        put("SplitPaneDivider.style", "plain");
        put("TitlePane.unifiedBackground", true);
        put("TabbedPane.showTabSeparators", false);
        put("TabbedPane.tabHeight", 34);
        put("TabbedPane.selectedBackground", surface);
        put("ToolTip.border", new javax.swing.border.EmptyBorder(6, 10, 6, 10));
        put("Spinner.buttonStyle", "button");

        // Step 5: tables. Only horizontal lines, tall rows, bold quiet headers, a soft selection.
        put("Table.showVerticalLines", false);
        put("Table.showHorizontalLines", true);
        put("Table.gridColor", shade(surface, darkMode ? 0.06 : -0.06));
        put("Table.intercellSpacing", new Dimension(0, 1));
        put("Table.rowHeight", 32);
        put("Table.cellMargins", new Insets(2, 12, 2, 12));
        put("Table.selectionBackground", darkMode ? new Color(0x23415C) : new Color(0xDCEAF6));
        put("Table.selectionForeground", text);
        put("Table.selectionInactiveBackground", darkMode ? new Color(0x26303F) : new Color(0xE9EEF4));
        put("Table.selectionInactiveForeground", text);
        put("Table.alternateRowColor", shade(surface, darkMode ? 0.025 : -0.018));
        put("TableHeader.height", 34);
        put("TableHeader.cellMargins", new Insets(2, 12, 2, 12));
        put("TableHeader.background", surface);
        put("TableHeader.foreground", mutedText());
        put("TableHeader.separatorColor", surface);
        put("TableHeader.bottomSeparatorColor", lineColor());
        put("TableHeader.font", UIManager.getFont("defaultFont").deriveFont(java.awt.Font.BOLD));
        put("List.selectionBackground", UIManager.get("Table.selectionBackground"));
        put("List.selectionForeground", text);
    }

    /**
     * Stores a theme value, marked as a look-and-feel resource (ColorUIResource and
     * friends). Swing only replaces a component's colour on a theme switch when it
     * is such a resource; a plain Color would stay behind from the old theme.
     */
    private static void put(String key, Object value) {
        Object resource = value;
        if (value instanceof Color && !(value instanceof javax.swing.plaf.UIResource)) {
            resource = new javax.swing.plaf.ColorUIResource((Color) value);
        } else if (value instanceof Insets && !(value instanceof javax.swing.plaf.UIResource)) {
            Insets i = (Insets) value;
            resource = new javax.swing.plaf.InsetsUIResource(i.top, i.left, i.bottom, i.right);
        } else if (value instanceof Dimension && !(value instanceof javax.swing.plaf.UIResource)) {
            Dimension d = (Dimension) value;
            resource = new javax.swing.plaf.DimensionUIResource(d.width, d.height);
        } else if (value instanceof java.awt.Font && !(value instanceof javax.swing.plaf.UIResource)) {
            resource = new javax.swing.plaf.FontUIResource((java.awt.Font) value);
        }
        UIManager.put(key, resource);
    }

    /** @return true if the dark theme is active */
    public static boolean isDark() {
        return dark;
    }

    /** @return the page background behind the cards */
    public static Color pageBackground() {
        return dark ? new Color(0x10151D) : new Color(0xF2F4F8);
    }

    /** @return the surface of cards, tables and text areas */
    public static Color cardBackground() {
        return dark ? new Color(0x19202B) : Color.WHITE;
    }

    /** @return the background of the side navigation (always navy) */
    public static Color sidebarBackground() {
        return dark ? new Color(0x0B111C) : NAVY;
    }

    /** @return the colour of thin lines and card outlines */
    public static Color lineColor() {
        return dark ? new Color(0x2A3341) : new Color(0xE1E6EE);
    }

    /** @return the colour for secondary text (captions, subtitles) */
    public static Color mutedText() {
        return dark ? new Color(0x8D99AB) : new Color(0x667085);
    }

    /** @return the canvas colour for the diagrams (the same as cards) */
    public static Color canvas() {
        return cardBackground();
    }

    /**
     * A soft tint of a colour for badge and icon backgrounds.
     *
     * @param color the strong colour
     * @return a pale (light mode) or deep (dark mode) version of it
     */
    public static Color tint(Color color) {
        return dark ? mix(color, cardBackground(), 0.75) : mix(color, Color.WHITE, 0.87);
    }

    /**
     * Loads one of RailSync's SVG icons, drawn in a single colour.
     *
     * @param name  file name without ".svg", e.g. "train"
     * @param size  width and height in pixels
     * @param color the colour to draw it in
     * @return the icon
     */
    public static Icon icon(String name, int size, Color color) {
        FlatSVGIcon svg = new FlatSVGIcon("com/railsync/ui/icons/" + name + ".svg", size, size);
        svg.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return svg;
    }

    /**
     * Loads an icon drawn in the normal text colour (follows light and dark mode).
     *
     * @param name file name without ".svg"
     * @param size width and height in pixels
     * @return the icon
     */
    public static Icon icon(String name, int size) {
        FlatSVGIcon svg = new FlatSVGIcon("com/railsync/ui/icons/" + name + ".svg", size, size);
        svg.setColorFilter(new FlatSVGIcon.ColorFilter(c -> UIManager.getColor("Label.foreground")));
        return svg;
    }

    /**
     * Makes a colour a little lighter (positive amount) or darker (negative).
     *
     * @param color  the starting colour
     * @param amount from -1.0 (black) to 1.0 (white); small values like 0.03 are typical
     * @return the new colour
     */
    static Color shade(Color color, double amount) {
        return mix(color, amount > 0 ? Color.WHITE : Color.BLACK, Math.abs(amount));
    }

    /** @return a blend: 0 gives a, 1 gives b */
    static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
