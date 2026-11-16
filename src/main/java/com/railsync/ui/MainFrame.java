package com.railsync.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.railsync.exception.RailSyncException;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.Scenario;
import com.railsync.service.ScenarioCsv;
import com.railsync.service.SchedulingService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The main window: a toolbar on top, a sidebar of screens on the left, the
 * selected screen in the middle (CardLayout) and the status bar at the bottom.
 *
 * <p>Why it exists: it only arranges the screens. Each screen is its own
 * class, so there is no giant GUI class, and no screen contains business rules.</p>
 *
 * <p>Toolbar: Undo (Ctrl+Z, calls SchedulingService.undoLast()), Open/Save CSV
 * (ScenarioCsv), Load demo (DemoScenarioLoader), Dark mode.
 * Calls: DemoScenarioLoader (toolbar button). Owns the one SimulationClock
 * shared by the diagram and yard screens. Observes: nothing itself; the screens and
 * the status bar observe events.</p>
 *
 * <p>Works with: every screen class, StatusBar, SchedulingService.</p>
 */
public class MainFrame extends JFrame {

    private final SchedulingService service;
    private final SimulationClock clock = new SimulationClock(Ui.SERVICE_DAY.atTime(7, 50));
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel sidebar = new JPanel();
    private final ButtonGroup navGroup = new ButtonGroup();
    private final List<JToggleButton> navButtons = new ArrayList<>();
    private final List<String> screenNames = new ArrayList<>();
    private final JButton undoButton = new JButton("Undo");

    /**
     * Builds the window and all screens.
     *
     * @param service the scheduling service shared by every screen
     */
    public MainFrame(SchedulingService service) {
        super("RailSync - Railway Platform Allocation & Scheduling Simulator");
        this.service = service;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1360, 820);
        setLocationRelativeTo(null);

        // Step 1: the three fixed parts of the window.
        add(buildToolbar(), BorderLayout.NORTH);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(new StatusBar(service), BorderLayout.SOUTH);

        // Step 2: one sidebar entry per screen.
        addScreen("Dashboard", new DashboardPanel(service));
        addScreen("Trains", new TrainPanel(service));
        addScreen("Platforms", new PlatformPanel(service));
        addScreen("Allocation", new AllocationPanel(service));
        addScreen("Schedule", new SchedulePanel(service));
        addScreen("Time-Distance", new TimeDistancePanel(service, clock));
        addScreen("Delay & Breakdown", new DisruptionPanel(service, clock));
        addScreen("Yard View", new YardPanel(service, clock));
        addScreen("Comparison", new ComparisonPanel(service));
        addScreen("Reports", new ReportsPanel(service));
        showScreen("Dashboard");
    }

    /**
     * Adds a screen to the sidebar and the card area.
     *
     * @param name   label shown in the sidebar
     * @param screen the panel
     */
    public final void addScreen(String name, Component screen) {
        content.add(screen, name);
        JToggleButton button = new JToggleButton(name);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setHorizontalAlignment(JToggleButton.LEFT);
        button.addActionListener(e -> showScreen(name));
        navGroup.add(button);
        navButtons.add(button);
        screenNames.add(name);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(4));
    }

    /**
     * Switches to a screen by name and highlights its sidebar button.
     *
     * @param name the screen name
     */
    public final void showScreen(String name) {
        cards.show(content, name);
        int index = screenNames.indexOf(name);
        if (index >= 0) {
            navButtons.get(index).setSelected(true);
        }
    }

    /** @return the shared simulation clock (used by tools that drive the window) */
    public SimulationClock getClock() {
        return clock;
    }

    /** @return the screen names in sidebar order */
    public List<String> getScreenNames() {
        return new ArrayList<>(screenNames);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        JLabel title = new JLabel("RailSync");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("   platforms = CPUs, trains = processes, allocator = OS scheduler");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(title);
        left.add(subtitle);

        // Undo button (also Ctrl+Z). Its label and enabled state follow every schedule event.
        undoButton.addActionListener(e -> undo());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl Z"), "undo");
        getRootPane().getActionMap().put("undo", new AbstractAction() {
            /** Ctrl+Z runs the same undo as the toolbar button. */
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });
        // [OBSERVER] a one-method observer written as a lambda: refresh the Undo button after any change.
        service.getEvents().subscribe(event -> updateUndoButton());
        updateUndoButton();

        JButton open = new JButton("Open CSV...");
        open.addActionListener(e -> openCsv());
        JButton save = new JButton("Save CSV...");
        save.addActionListener(e -> saveCsv());
        JButton loadDemo = new JButton("Load demo scenario");
        loadDemo.addActionListener(e -> loadDemo());
        JCheckBox dark = new JCheckBox("Dark mode");
        dark.addActionListener(e -> switchTheme(dark.isSelected()));
        JPanel right = new JPanel();
        right.add(undoButton);
        right.add(open);
        right.add(save);
        right.add(loadDemo);
        right.add(dark);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Undoes the last allocation or delay through the service. */
    private void undo() {
        String undone = service.undoLast();
        if (undone == null) {
            Ui.showInfo(this, "Nothing to undo. Undo covers allocations and delays since the last other change.");
        }
    }

    private void updateUndoButton() {
        String next = service.getUndoDescription();
        undoButton.setEnabled(next != null);
        undoButton.setText(next == null ? "Undo" : "Undo " + next);
        undoButton.setToolTipText(next == null ? "Nothing to undo" : "Ctrl+Z: undo the " + next);
    }

    /** Saves the live trains and platforms (not the bookings) to a CSV file. */
    private void saveCsv() {
        JFileChooser chooser = csvChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists() && !Ui.confirm(this, file.getName() + " already exists. Overwrite it?")) {
            return;
        }
        try {
            ScenarioCsv.save(Scenario.fromLive(service), file.toPath());
            Ui.showInfo(this, "Saved " + service.getTrainManager().getAllTrains().size() + " trains and "
                    + service.getStation().getPlatformCount() + " platforms to " + file.getName()
                    + ".\n(Bookings are not saved; allocate again after opening.)");
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    /** Loads trains and platforms from a CSV file, replacing the live scenario only if the whole file is valid. */
    private void openCsv() {
        JFileChooser chooser = csvChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            // Step 1: read and check the whole file first; a bad line stops here with its line number.
            Scenario scenario = ScenarioCsv.load(chooser.getSelectedFile().toPath());
            // Step 2: only then ask before replacing the live data.
            if (Ui.confirm(this, "Replace the current scenario with " + scenario.getTrains().size() + " trains and "
                    + scenario.getPlatforms().size() + " platforms from " + chooser.getSelectedFile().getName() + "?")) {
                service.loadScenario(scenario);
            }
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        }
    }

    private static JFileChooser csvChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("RailSync scenario (*.csv)", "csv"));
        return chooser;
    }

    private void loadDemo() {
        boolean hasData = !service.getTrainManager().getAllTrains().isEmpty();
        if (hasData && !Ui.confirm(this, "Replace all current trains, platforms and bookings with the demo scenario?")) {
            return;
        }
        DemoScenarioLoader.load(service);
        Ui.showInfo(this, "Demo scenario loaded: 6 platforms, 12 trains, nothing allocated yet.\n"
                + "Go to Allocation, pick a strategy and press \"Allocate all pending trains\".");
    }

    /** Switches between FlatLaf light and dark themes and repaints every component. */
    private void switchTheme(boolean darkMode) {
        if (darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI();
    }
}
