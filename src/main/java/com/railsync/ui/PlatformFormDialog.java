package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Platform;
import com.railsync.model.TrainType;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.EnumSet;
import java.util.Set;

/**
 * A pop-up form to add a platform or change an existing one.
 *
 * <p>Calls: SchedulingService.addPlatform() / updatePlatform(). The form only
 * collects input; PlatformManager refuses changes that would break an
 * existing booking, and its message is shown.</p>
 */
public class PlatformFormDialog extends JDialog {

    private final SchedulingService service;
    private final Platform existing;
    private final JSpinner numberSpinner;
    private final JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(400, 1, Platform.MAX_LENGTH_METERS, 10));
    private final JCheckBox expressBox = new JCheckBox("Express");
    private final JCheckBox passengerBox = new JCheckBox("Passenger");
    private final JCheckBox freightBox = new JCheckBox("Freight");

    /**
     * Creates the dialog.
     *
     * @param owner    the window it belongs to
     * @param service  the scheduling service
     * @param existing the platform to edit, or null to add one
     */
    public PlatformFormDialog(Window owner, SchedulingService service, Platform existing) {
        super(owner, existing == null ? "Add platform" : "Edit " + existing, ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.existing = existing;
        int suggestedNumber = service.getPlatformManager().nextPlatformNumber();
        numberSpinner = new JSpinner(new SpinnerNumberModel(suggestedNumber, 1, 99, 1));

        // Step 1: the fields.
        JPanel types = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        types.add(expressBox);
        types.add(passengerBox);
        types.add(freightBox);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        form.add(new JLabel("Platform number"));
        form.add(numberSpinner);
        form.add(new JLabel("Length (m)"));
        form.add(lengthSpinner);
        form.add(new JLabel("Accepts"));
        form.add(types);

        // Step 2: when editing, show current values; the number cannot change.
        if (existing != null) {
            numberSpinner.setValue(existing.getNumber());
            numberSpinner.setEnabled(false);
            lengthSpinner.setValue(existing.getLengthMeters());
            expressBox.setSelected(existing.supports(TrainType.EXPRESS));
            passengerBox.setSelected(existing.supports(TrainType.PASSENGER));
            freightBox.setSelected(existing.supports(TrainType.FREIGHT));
        } else {
            passengerBox.setSelected(true);
        }

        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(save);
        buttons.add(cancel);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Reads the fields and asks the service to add or update the platform. */
    private void save() {
        Set<TrainType> types = EnumSet.noneOf(TrainType.class);
        if (expressBox.isSelected()) {
            types.add(TrainType.EXPRESS);
        }
        if (passengerBox.isSelected()) {
            types.add(TrainType.PASSENGER);
        }
        if (freightBox.isSelected()) {
            types.add(TrainType.FREIGHT);
        }
        int length = (Integer) lengthSpinner.getValue();
        try {
            if (existing == null) {
                String id = service.getPlatformManager().nextPlatformId();
                service.addPlatform(new Platform(id, (Integer) numberSpinner.getValue(), length, types));
            } else {
                service.updatePlatform(existing, length, types);
            }
            dispose();
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }
}
