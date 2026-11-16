package com.railsync.ui;

import com.railsync.strategy.PlatformAllocationStrategy;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/**
 * Shows strategy objects by their name in drop-down lists.
 *
 * <p>OOP concepts: Inheritance and Polymorphism (overrides
 * getListCellRendererComponent(), which Swing calls for every item).</p>
 *
 * <p>Used by: AllocationPanel, YardPanel.</p>
 */
public class StrategyListRenderer extends DefaultListCellRenderer {

    /** Swing calls this for every list item; we only replace the text shown. */
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean selected, boolean focused) {
        super.getListCellRendererComponent(list, value, index, selected, focused);
        if (value instanceof PlatformAllocationStrategy) {
            setText(((PlatformAllocationStrategy) value).getName());
        }
        return this;
    }
}
