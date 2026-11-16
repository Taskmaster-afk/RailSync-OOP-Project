package com.railsync.ui;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * A reusable strip of simulation-clock controls: Play/Pause, speed
 * (1x/10x/60x), Jump to start, and the current simulated time.
 *
 * <p>Why it exists: the Time-Distance screen and the Yard view both need the
 * same controls for the same shared clock. Writing them once avoids
 * duplicate code, and because each strip listens to the clock, pressing Play
 * on one screen updates the button on the other.</p>
 *
 * <p>OOP concepts: Composition (screens contain a ClockControls), [OBSERVER]
 * (implements SimulationClock.Listener).</p>
 */
public class ClockControls extends JPanel implements SimulationClock.Listener {

    private final SimulationClock clock;
    private final JButton playButton = new JButton("Play");
    private final JComboBox<String> speedBox = new JComboBox<>(new String[]{"1x", "10x", "60x"});
    private final JLabel timeLabel = new JLabel();

    /**
     * Builds the controls.
     *
     * @param clock     the shared clock
     * @param startTime gives the time "Jump to start" should go to (asked each time it is pressed)
     */
    public ClockControls(SimulationClock clock, Supplier<LocalDateTime> startTime) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.clock = clock;
        playButton.addActionListener(e -> {
            if (clock.isPlaying()) {
                clock.pause();
            } else {
                clock.play();
            }
        });
        speedBox.setSelectedItem(clock.getSpeed() + "x");
        speedBox.addActionListener(e -> {
            String chosen = (String) speedBox.getSelectedItem();
            clock.setSpeed(Integer.parseInt(chosen.substring(0, chosen.length() - 1)));
            updateTimeLabel(clock.getTime());
        });
        JButton toStart = new JButton("Jump to start");
        toStart.addActionListener(e -> clock.setTime(startTime.get()));
        add(playButton);
        add(new JLabel("Speed:"));
        add(speedBox);
        add(toStart);
        add(timeLabel);
        clock.addListener(this);
        onClockChanged(clock.getTime());
    }

    /**
     * Keeps the time label and the Play/Pause text in step with the clock. (The
     * speed box is not set here: setting it would fire its own listener again.)
     *
     * @param now the simulated time
     */
    @Override
    public void onClockChanged(LocalDateTime now) {
        playButton.setText(clock.isPlaying() ? "Pause" : "Play");
        updateTimeLabel(now);
    }

    private void updateTimeLabel(LocalDateTime now) {
        timeLabel.setText("   Simulated time: " + Ui.time(now) + "  (" + clock.getSpeed() + "x)");
    }
}
