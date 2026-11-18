package com.railsync.ui;

import com.formdev.flatlaf.FlatLaf;
import com.railsync.exception.RailSyncException;
import com.railsync.persistence.AutoSaver;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.Scenario;
import com.railsync.service.ScenarioCsv;
import com.railsync.service.SchedulingService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The main window: a navy sidebar of screens on the left (with the RailSync
 * logo, the storage indicator and the light/dark switch), an action bar on
 * top, the selected screen in the middle (CardLayout) and the status bar at
 * the bottom.
 *
 * <p>Why it exists: it only arranges the screens. Each screen is its own
 * class, so there is no giant GUI class, and no screen contains business rules.</p>
 *
 * <p>Action bar: Undo (Ctrl+Z, calls SchedulingService.undoLast()), Open/Save CSV
 * (ScenarioCsv), Load demo (DemoScenarioLoader). Owns the one SimulationClock
 * shared by the diagram and yard screens. Observes: schedule events (for the
 * Undo button) and the AutoSaver (for the "Saved" indicator).</p>
 *
 * <p>Works with: every screen class, StatusBar, SchedulingService, AutoSaver.</p>
 */
public class MainFrame extends JFrame {

    private static final Color SIDEBAR_TEXT = new Color(0xB9C3D3);
    private static final Color SIDEBAR_MUTED = new Color(0x7D8AA0);
    private static final DateTimeFormatter SAVED_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SchedulingService service;
    private final AutoSaver autoSaver;
    private final String databaseLocation;
    private final SimulationClock clock = new SimulationClock(Ui.SERVICE_DAY.atTime(7, 50));
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel nav = new JPanel();
    private final ButtonGroup navGroup = new ButtonGroup();
    private final List<JToggleButton> navButtons = new ArrayList<>();
    private final List<String> screenNames = new ArrayList<>();
    private final JButton undoButton = new JButton("Undo");
    private final JLabel storageLabel = new JLabel();

    /**
     * Builds the window and all screens, without a database (used by tools and tests).
     *
     * @param service the scheduling service shared by every screen
     */
    public MainFrame(SchedulingService service) {
        this(service, null, null);
    }

    /**
     * Builds the window with database saving shown in the sidebar and the
     * audit log available on the Reports screen.
     *
     * @param service          the scheduling service shared by every screen
     * @param autoSaver        saves every change (null when running without a database)
     * @param databaseLocation where the database file is, shown to the user (may be null)
     */
    public MainFrame(SchedulingService service, AutoSaver autoSaver, String databaseLocation) {
        super("RailSync - Railway Platform Allocation & Scheduling Simulator");
        this.service = service;
        this.autoSaver = autoSaver;
        this.databaseLocation = databaseLocation;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Open at a comfortable size, but never larger than the usable screen (taskbar excluded);
        // on smaller screens (for example a 1366x768 laptop) open maximised.
        java.awt.Rectangle usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1100, usable.width), Math.min(680, usable.height)));
        setSize(Math.min(1440, usable.width), Math.min(880, usable.height));
        setLocationRelativeTo(null);
        if (usable.width < 1440 || usable.height < 880) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        // Step 1: the fixed parts of the window: sidebar, action bar, content area, status bar.
        add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(buildActionBar(), BorderLayout.NORTH);
        content.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
        main.add(content, BorderLayout.CENTER);
        main.add(new StatusBar(service), BorderLayout.SOUTH);
        add(main, BorderLayout.CENTER);

        // Step 2: one sidebar entry per screen, grouped under small section labels.
        addGroup("Overview");
        addScreen("Dashboard", "dashboard", new DashboardPanel(service));
        addGroup("Data");
        addScreen("Trains", "train", new TrainPanel(service));
        addScreen("Platforms", "platform", new PlatformPanel(service));
        addGroup("Planning");
        addScreen("Allocation", "allocation", new AllocationPanel(service));
        addScreen("Schedule", "schedule", new SchedulePanel(service));
        addScreen("Delay & Breakdown", "delay", new DisruptionPanel(service, clock));
        addGroup("Visualise");
        addScreen("Time-Distance", "timeline", new TimeDistancePanel(service, clock));
        addScreen("Yard View", "yard", new YardPanel(service, clock));
        addGroup("Analysis");
        addScreen("Comparison", "compare", new ComparisonPanel(service));
        addScreen("Reports", "reports", new ReportsPanel(service, autoSaver == null ? null : autoSaver.getAuditLog()));
        showScreen("Dashboard");

        // Step 3: the storage indicator follows every save.
        if (autoSaver != null) {
            autoSaver.addSaveListener((ok, message) -> updateStorageLabel(ok, message));
        }
        updateStorageLabel(true, null);
    }

    // ------------------------------------------------------------------
    // Sidebar.
    // ------------------------------------------------------------------

    private JComponent buildSidebar() {
        JPanel sidebar = new SidebarPanel();
        sidebar.setLayout(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(232, 10));

        // The brand: three signal lights and the name.
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(20, 20, 18, 16));
        JLabel name = new JLabel("RailSync");
        name.setForeground(Color.WHITE);
        name.putClientProperty("FlatLaf.style", "font: bold +7");
        JLabel tagline = new JLabel("Platform scheduling");
        tagline.setForeground(SIDEBAR_MUTED);
        tagline.putClientProperty("FlatLaf.style", "font: -1");
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(name);
        words.add(tagline);
        brand.add(new SignalLights(), BorderLayout.WEST);
        brand.add(words, BorderLayout.CENTER);
        sidebar.add(brand, BorderLayout.NORTH);

        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        JPanel navHolder = new JPanel(new BorderLayout());
        navHolder.setOpaque(false);
        navHolder.add(nav, BorderLayout.NORTH);
        sidebar.add(navHolder, BorderLayout.CENTER);

        // Bottom: where the data is saved, and the theme switch.
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 12, 16, 12));
        storageLabel.setIcon(Theme.icon("database", 16, SIDEBAR_TEXT));
        storageLabel.setIconTextGap(10);
        storageLabel.setForeground(SIDEBAR_TEXT);
        storageLabel.putClientProperty("FlatLaf.style", "font: -1");
        storageLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 8));
        storageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        NavButton darkSwitch = new NavButton("Dark mode", Theme.icon("moon", 18, SIDEBAR_TEXT),
                Theme.icon("sun", 18, Color.WHITE));
        darkSwitch.setToolTipText("Switch between the light and dark theme");
        darkSwitch.addActionListener(e -> {
            darkSwitch.setText(darkSwitch.isSelected() ? "Light mode" : "Dark mode");
            switchTheme(darkSwitch.isSelected());
        });
        bottom.add(storageLabel);
        bottom.add(darkSwitch);
        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }

    /**
     * Adds a small, quiet section label to the sidebar (e.g. "PLANNING").
     *
     * @param title the section name
     */
    private void addGroup(String title) {
        JLabel label = new JLabel(title.toUpperCase());
        label.putClientProperty("FlatLaf.style", "font: bold -3");
        label.setForeground(SIDEBAR_MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(screenNames.isEmpty() ? 4 : 16, 12, 6, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(label);
    }

    /**
     * Adds a screen with a plain text entry (kept for tools that add extra screens).
     *
     * @param name   label shown in the sidebar
     * @param screen the panel
     */
    public final void addScreen(String name, Component screen) {
        addScreen(name, "layers", screen);
    }

    /**
     * Adds a screen to the sidebar and the card area.
     *
     * @param name     label shown in the sidebar
     * @param iconName the sidebar icon (an SVG in the icons folder)
     * @param screen   the panel
     */
    public final void addScreen(String name, String iconName, Component screen) {
        content.add(screen, name);
        NavButton button = new NavButton(name, Theme.icon(iconName, 18, SIDEBAR_TEXT),
                Theme.icon(iconName, 18, Color.WHITE));
        button.addActionListener(e -> showScreen(name));
        navGroup.add(button);
        navButtons.add(button);
        screenNames.add(name);
        nav.add(button);
        nav.add(Box.createVerticalStrut(2));
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

    // ------------------------------------------------------------------
    // Action bar.
    // ------------------------------------------------------------------

    private JComponent buildActionBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 22, 6, 18));

        // Left: the station and the simulated service day.
        JLabel station = new JLabel(service.getStation().getName());
        station.putClientProperty("FlatLaf.style", "font: bold +1");
        station.setIcon(Theme.icon("platform", 18, Theme.ACCENT));
        station.setIconTextGap(8);
        JLabel day = Ui.badge("Service day " + Ui.SERVICE_DAY.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                Theme.ACCENT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(station);
        left.add(Box.createHorizontalStrut(12));
        left.add(day);

        // Undo button (also Ctrl+Z). Its label and enabled state follow every schedule event.
        undoButton.addActionListener(e -> undo());
        undoButton.setIcon(Theme.icon("undo", 16));
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

        JButton open = new JButton("Open CSV");
        open.setIcon(Theme.icon("open", 16));
        open.setToolTipText("Replace the scenario with trains and platforms from a CSV file");
        open.addActionListener(e -> openCsv());
        JButton save = new JButton("Save CSV");
        save.setIcon(Theme.icon("save", 16));
        save.setToolTipText("Export the trains and platforms to a CSV file");
        save.addActionListener(e -> saveCsv());
        JButton loadDemo = Ui.primary(new JButton("Load demo scenario"));
        loadDemo.setIcon(Theme.icon("demo", 16, Color.WHITE));
        loadDemo.addActionListener(e -> loadDemo());
        for (JButton button : new JButton[] {undoButton, open, save}) {
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setIconTextGap(7);
            button.setFocusable(false);
        }
        loadDemo.setIconTextGap(7);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(undoButton);
        right.add(new JSeparator(JSeparator.VERTICAL) {
            /** A short divider line, about the height of a toolbar button. */
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(9, 22);
            }
        });
        right.add(open);
        right.add(save);
        right.add(Box.createHorizontalStrut(8));
        right.add(loadDemo);

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

    private void updateStorageLabel(boolean ok, String message) {
        if (autoSaver == null) {
            storageLabel.setText("Not saved (no database)");
            return;
        }
        String file = databaseLocation == null ? "" : new File(databaseLocation).getName();
        if (ok && autoSaver.getLastSavedAt() != null) {
            storageLabel.setText("<html>SQLite · " + file + "<br><span style='color:#7D8AA0'>Saved "
                    + autoSaver.getLastSavedAt().format(SAVED_TIME) + "</span></html>");
        } else if (ok) {
            storageLabel.setText("<html>SQLite · " + file + "<br><span style='color:#7D8AA0'>Auto-save on</span></html>");
        } else {
            storageLabel.setText("<html>SQLite · " + file + "<br><span style='color:#E57373'>Save failed</span></html>");
        }
        storageLabel.setToolTipText(ok ? "Every change is saved automatically to " + databaseLocation : message);
    }

    // ------------------------------------------------------------------
    // CSV, demo and theme.
    // ------------------------------------------------------------------

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
                    + ".\n(Bookings are kept in the database; a CSV file holds the scenario only.)");
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

    /** Switches between the light and dark themes and repaints every component. */
    private void switchTheme(boolean darkMode) {
        Theme.apply(darkMode);
        FlatLaf.updateUI();
        undoButton.setIcon(Theme.icon("undo", 16));
        repaint();
    }

    // ------------------------------------------------------------------
    // Small painted components of the sidebar.
    // ------------------------------------------------------------------

    /** The navy sidebar background. */
    private static final class SidebarPanel extends JPanel {
        /** Paints the sidebar colour of the current theme. */
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.sidebarBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** The logo mark: three signal lights (amber, green, red), as on the slides and the report. */
    private static final class SignalLights extends JComponent {
        SignalLights() {
            setPreferredSize(new Dimension(18, 40));
        }

        /** Paints three small circles stacked like a signal head. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color[] lights = {Theme.SIGNAL, new Color(0x4F8A5B), new Color(0xC0392B)};
            int size = 10;
            int top = (getHeight() - (3 * size + 2 * 3)) / 2;
            for (int i = 0; i < lights.length; i++) {
                g2.setColor(lights[i]);
                g2.fillOval(4, top + i * (size + 3), size, size);
            }
            g2.dispose();
        }
    }

    /**
     * A sidebar entry: icon and text on the navy background. The selected
     * entry gets a soft highlight and an amber "signal" bar on its left edge.
     */
    private static final class NavButton extends JToggleButton {
        NavButton(String text, Icon icon, Icon selectedIcon) {
            super(text, icon);
            setSelectedIcon(selectedIcon);
            setRolloverSelectedIcon(selectedIcon);
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(12);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setPreferredSize(new Dimension(200, 38));
            setMargin(new Insets(0, 12, 0, 8));
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
            addItemListener(e -> updateLook());
            updateLook();
        }

        /** Keeps the sidebar colours when the theme changes. */
        @Override
        public void updateUI() {
            super.updateUI();
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
            setForeground(isSelected() ? Color.WHITE : SIDEBAR_TEXT);
        }

        /** Paints the highlight and the amber bar, then the icon and text. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Theme.SIGNAL);
                g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        /** White bold text when selected, soft grey otherwise. */
        private void updateLook() {
            setForeground(isSelected() ? Color.WHITE : SIDEBAR_TEXT);
            putClientProperty("FlatLaf.style", isSelected() ? "font: bold" : "font: plain");
        }
    }
}
