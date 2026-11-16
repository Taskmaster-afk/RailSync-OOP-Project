package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.model.Train;
import com.railsync.model.TrainFactory;
import com.railsync.model.TrainType;
import com.railsync.service.AllocationResult;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.LocalDateTime;

/**
 * A pop-up form to add a new train or edit an existing one.
 *
 * <p>Calls: SchedulingService.addTrain() / updateTrain(), TrainFactory.create().
 * The form only collects input; every rule (times, priority range, unique
 * number, minimum dwell) is checked by the model and services, and their
 * friendly message is shown if something is wrong. The dialog then stays
 * open so the user can fix it.</p>
 *
 * <p>When editing, the type, number and carriage count are fixed (they are
 * final in Train); name, priority and times can change.</p>
 */
public class TrainFormDialog extends JDialog {

    private final SchedulingService service;
    private final Train existing;
    private final JComboBox<TrainType> typeBox = new JComboBox<>(TrainType.values());
    private final JTextField numberField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField arrivalField = new JTextField("10:00");
    private final JTextField departureField = new JTextField("10:10");
    private final JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(3, Train.MIN_BASE_PRIORITY, Train.MAX_BASE_PRIORITY, 1));
    private final JSpinner carriageSpinner = new JSpinner(new SpinnerNumberModel(10, 1, Train.MAX_CARRIAGES, 1));

    /**
     * Creates the dialog.
     *
     * @param owner    the window it belongs to
     * @param service  the scheduling service
     * @param existing the train to edit, or null to add a new one
     */
    public TrainFormDialog(Window owner, SchedulingService service, Train existing) {
        super(owner, existing == null ? "Add train" : "Edit train " + existing.getNumber(), ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.existing = existing;

        // Step 1: the form fields, as label/field pairs.
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        addRow(form, "Type", typeBox);
        addRow(form, "Train number", numberField);
        addRow(form, "Name", nameField);
        addRow(form, "Arrival (HH:mm)", arrivalField);
        addRow(form, "Departure (HH:mm)", departureField);
        addRow(form, "Base priority (1-5)", prioritySpinner);
        addRow(form, "Coaches / wagons", carriageSpinner);
        JLabel hint = new JLabel("Min stop: express 5, passenger 3, freight 20 min.");
        form.add(new JLabel());
        form.add(hint);

        // Step 2: when editing, fill in the current values and lock the fixed ones.
        if (existing != null) {
            typeBox.setSelectedItem(existing.getType());
            numberField.setText(existing.getNumber());
            nameField.setText(existing.getName());
            arrivalField.setText(Ui.time(existing.getScheduledArrival()));
            departureField.setText(Ui.time(existing.getScheduledDeparture()));
            prioritySpinner.setValue(existing.getBasePriority());
            carriageSpinner.setValue(existing.getCarriageCount());
            typeBox.setEnabled(false);
            numberField.setEnabled(false);
            carriageSpinner.setEnabled(false);
        }

        // Step 3: Save / Cancel buttons.
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

    /** Reads the fields and asks the service to add or update the train. */
    private void save() {
        try {
            // Step 1: turn the text into times (format errors give a friendly message).
            LocalDateTime arrival = Ui.parseTime(arrivalField.getText(), "Arrival");
            LocalDateTime departure = Ui.parseTime(departureField.getText(), "Departure");
            int priority = (Integer) prioritySpinner.getValue();
            // Step 2: add or update through the service; the model validates the values.
            if (existing == null) {
                Train train = TrainFactory.create((TrainType) typeBox.getSelectedItem(),
                        service.getTrainManager().nextTrainId(), numberField.getText(), nameField.getText(),
                        arrival, departure, priority, (Integer) carriageSpinner.getValue());
                service.addTrain(train);
            } else {
                AllocationResult result = service.updateTrain(existing, nameField.getText(), priority, arrival, departure);
                if (result != null) {
                    Ui.showInfo(this, "The train held a platform, so it was placed again:\n" + result.getMessage());
                }
            }
            dispose();
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private static void addRow(JPanel form, String label, Component field) {
        form.add(new JLabel(label));
        form.add(field);
    }
}
