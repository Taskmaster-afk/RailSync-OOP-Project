package com.railsync.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * A rounded white (or dark) surface with a thin outline: the building block of
 * every screen. Sections, number tiles and tables all sit on cards placed on
 * the soft grey page.
 *
 * <p>Why it exists: one class draws every card, so they all share the same
 * radius, padding and colours, and follow light/dark mode automatically.</p>
 *
 * <p>When content is added, plain panels inside it are made see-through so the
 * card's surface shows behind them, and scroll panes lose their own border
 * (the card already has one).</p>
 *
 * <p>OOP concepts: Inheritance (extends JPanel, overrides paintComponent and addImpl).</p>
 *
 * <p>Works with: Ui.section(), Ui.card(), DashboardPanel, every screen.</p>
 */
public class Card extends JPanel {

    /** Corner radius in pixels. */
    public static final int ARC = 14;

    /**
     * Creates a card with the given layout and the standard padding.
     *
     * @param layout the layout manager
     */
    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    /** Creates a card with a BorderLayout. */
    public Card() {
        this(new BorderLayout(0, 8));
    }

    /** Paints the rounded surface and its outline in the current theme's colours. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.cardBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.setColor(Theme.lineColor());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.dispose();
    }

    /** Makes newly added content blend into the card. */
    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        blend(component);
        super.addImpl(component, constraints, index);
    }

    /**
     * Aligns a table's column titles to the left, like the values under them.
     * Wraps the look and feel's own header renderer, so the header keeps its style.
     *
     * @param table the table
     */
    static void leftAlignHeader(javax.swing.JTable table) {
        javax.swing.table.TableCellRenderer base = table.getTableHeader().getDefaultRenderer();
        if (base instanceof LeftHeader) {
            return;
        }
        table.getTableHeader().setDefaultRenderer(new LeftHeader(base));
        if (table.getTableHeader().getClientProperty(LeftHeader.class) == null) {
            // A theme switch installs the new look and feel's renderer; wrap that one again.
            table.getTableHeader().putClientProperty(LeftHeader.class, Boolean.TRUE);
            table.getTableHeader().addPropertyChangeListener("UI",
                    e -> javax.swing.SwingUtilities.invokeLater(() -> leftAlignHeader(table)));
        }
    }

    /** A header renderer that left-aligns the text of the renderer it wraps. */
    private static final class LeftHeader implements javax.swing.table.TableCellRenderer, javax.swing.plaf.UIResource {
        private final javax.swing.table.TableCellRenderer base;

        LeftHeader(javax.swing.table.TableCellRenderer base) {
            this.base = base;
        }

        /** Lets the wrapped renderer draw the header cell, aligned to the left. */
        @Override
        public Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component cell = base.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (cell instanceof JLabel) {
                ((JLabel) cell).setHorizontalAlignment(JLabel.LEADING);
            }
            return cell;
        }
    }

    /**
     * Walks a component tree and makes plain containers transparent, so the
     * card's surface shows through. Tables, lists and text areas keep their
     * own (identical) background.
     *
     * @param component the root of the tree
     */
    static void blend(Component component) {
        if (component instanceof Card) {
            return; // a nested card draws itself
        }
        if (component instanceof JScrollPane) {
            JScrollPane scroll = (JScrollPane) component;
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setViewportBorder(null);
            if (scroll.getViewport().getView() instanceof javax.swing.JTable) {
                leftAlignHeader((javax.swing.JTable) scroll.getViewport().getView());
            }
            if (scroll.getViewport().getView() instanceof JTextArea) {
                ((JTextArea) scroll.getViewport().getView()).setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            }
            return;
        }
        if (component instanceof JSplitPane || component instanceof JTabbedPane) {
            return;
        }
        if (component instanceof JPanel || component instanceof JLabel || component instanceof AbstractButton
                && !(component instanceof javax.swing.JButton) && !(component instanceof javax.swing.JToggleButton)) {
            ((JComponent) component).setOpaque(false);
        }
        if (component instanceof JPanel) {
            for (Component child : ((Container) component).getComponents()) {
                blend(child);
            }
        }
    }
}
