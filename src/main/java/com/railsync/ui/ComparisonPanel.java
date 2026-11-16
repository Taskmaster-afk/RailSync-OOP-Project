package com.railsync.ui;

import com.railsync.exception.RailSyncException;
import com.railsync.service.Scenario;
import com.railsync.service.ScenarioGenerator;
import com.railsync.service.ScheduleMetrics;
import com.railsync.service.SchedulingService;
import com.railsync.service.StrategyArena;
import com.railsync.strategy.StrategyCatalog;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Module F2 screen: the strategy comparison arena. Runs the same trains
 * through all four strategies on copies (the live schedule is never changed)
 * and shows the metrics side by side, with the best value in each column in bold.
 *
 * <p>The scenario is either a copy of the live one, or one made by the seeded
 * generator (seed and train count are editable, so every run can be repeated).</p>
 *
 * <p>Calls: StrategyArena.compare(), ScenarioGenerator.generate(),
 * Scenario.fromLive(), SchedulingService.loadScenario() (only when the user
 * asks to load the generated scenario). Observes: nothing; it runs on demand.</p>
 *
 * <p>OOP concepts: [STRATEGY] (a list of four objects behind one interface),
 * Inheritance (the table renderer overrides a Swing method).</p>
 */
public class ComparisonPanel extends JPanel {

    private static final String[] COLUMNS = {"Strategy", "Allocated", "Unallocated", "Conflicts",
        "Avg wait (min)", "Avg turnaround (min)", "Avg utilization", "Platforms used", "Needed for zero wait", "Run time"};
    /** For each column: true if a LOWER value is better, false if HIGHER is better, null if not ranked. */
    private static final Boolean[] LOWER_IS_BETTER = {null, false, true, true, true, true, false, true, null, null};

    private final SchedulingService service;
    private final StrategyArena arena = new StrategyArena();
    private final JRadioButton liveSource = new JRadioButton("Copy of the live scenario", true);
    private final JRadioButton generatedSource = new JRadioButton("Generated scenario:");
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(2026, 0, Integer.MAX_VALUE, 1));
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(120, ScenarioGenerator.MIN_TRAINS, ScenarioGenerator.MAX_TRAINS, 10));
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        /** @return false: comparison results cannot be edited */
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea notes = new JTextArea(5, 40);
    /** Best value per column of the last run, used by the renderer for bold text. */
    private final double[] bestValues = new double[COLUMNS.length];

    /**
     * Builds the arena screen.
     *
     * @param service the live service (read for copying; changed only by "Load into app")
     */
    public ComparisonPanel(SchedulingService service) {
        super(new BorderLayout(10, 10));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Step 1: choose where the trains come from.
        // Show the seed as a plain number (2026, not "2,026").
        seedSpinner.setEditor(new JSpinner.NumberEditor(seedSpinner, "#"));
        ButtonGroup sourceGroup = new ButtonGroup();
        sourceGroup.add(liveSource);
        sourceGroup.add(generatedSource);
        JButton run = new JButton("Run comparison");
        run.addActionListener(e -> runComparison());
        JButton load = new JButton("Load generated scenario into the app");
        load.addActionListener(e -> loadGenerated());
        JPanel source = new JPanel(new FlowLayout(FlowLayout.LEFT));
        source.add(liveSource);
        source.add(generatedSource);
        source.add(new JLabel("seed"));
        source.add(seedSpinner);
        source.add(new JLabel("trains (10-500)"));
        source.add(countSpinner);
        source.add(run);
        source.add(load);

        // Step 2: the results table; the best value in each column is shown in bold.
        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setDefaultRenderer(Object.class, new BestValueRenderer());

        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setText("Press \"Run comparison\". Each strategy runs on its own copy, so the live schedule never changes.");
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setBorder(BorderFactory.createTitledBorder("How to read this"));

        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Strategy Comparison Arena"), BorderLayout.NORTH);
        north.add(source, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(notesScroll, BorderLayout.SOUTH);
    }

    /** Runs all four strategies on the chosen scenario and fills the table. */
    private void runComparison() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            // Step 1: build the scenario (never the live objects themselves).
            Scenario scenario = liveSource.isSelected() ? Scenario.fromLive(service) : generatedScenario();
            if (scenario.getTrains().isEmpty()) {
                Ui.showInfo(this, "The live scenario has no trains. Load the demo or choose a generated scenario.");
                return;
            }
            // Step 2: run the arena with the live settings, so the comparison matches what the app does.
            List<StrategyArena.Entry> entries = arena.compare(scenario, StrategyCatalog.createAll(),
                    service.getSchedule().getClearanceBufferMinutes(), service.getMaxWaitMinutes(),
                    service.isPreemptionEnabled());
            // Step 3: show one row per strategy.
            fillTable(entries);
            notes.setText(buildNotes(scenario, entries));
        } catch (RailSyncException e) {
            Ui.showError(this, e.getMessage());
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void loadGenerated() {
        if (!Ui.confirm(this, "Replace the live trains and platforms with the generated scenario (seed "
                + seedSpinner.getValue() + ", " + countSpinner.getValue() + " trains)?")) {
            return;
        }
        service.loadScenario(generatedScenario());
        Ui.showInfo(this, "Loaded. Use the Allocation screen to allocate it, then view it in the diagrams.");
    }

    private Scenario generatedScenario() {
        return ScenarioGenerator.generate(((Number) seedSpinner.getValue()).longValue(), (Integer) countSpinner.getValue());
    }

    private void fillTable(List<StrategyArena.Entry> entries) {
        tableModel.setRowCount(0);
        for (StrategyArena.Entry entry : entries) {
            ScheduleMetrics m = entry.getMetrics();
            tableModel.addRow(new Object[]{entry.getStrategyName(), m.getAllocatedTrains(), m.getUnallocatedTrains(),
                m.getConflictsDetected(), round(m.getAverageWaitingMinutes()), round(m.getAverageTurnaroundMinutes()),
                Math.round(m.getAverageUtilization() * 100) + " %", m.getPlatformsUsed(),
                m.getMinimumPlatformsNeeded(), entry.getRunMillis() + " ms"});
        }
        // Remember the best number in each ranked column, for bold highlighting.
        for (int column = 1; column < COLUMNS.length; column++) {
            if (LOWER_IS_BETTER[column] == null) {
                continue;
            }
            double best = LOWER_IS_BETTER[column] ? Double.MAX_VALUE : -Double.MAX_VALUE;
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                double value = numberIn(tableModel.getValueAt(row, column));
                best = LOWER_IS_BETTER[column] ? Math.min(best, value) : Math.max(best, value);
            }
            bestValues[column] = best;
        }
    }

    private static String buildNotes(Scenario scenario, List<StrategyArena.Entry> entries) {
        ScheduleMetrics first = entries.get(0).getMetrics();
        return scenario.getTrains().size() + " trains, " + scenario.getPlatforms().size() + " platforms. "
                + "Bold = best in that column. Read \"Avg wait\" together with \"Unallocated\": averages only cover "
                + "allocated trains, so a strategy that drops a train can look faster than it is. "
                + "\"Needed for zero wait\" (" + first.getMinimumPlatformsNeeded() + ") comes from the sweep line and "
                + "is the same for every strategy; it ignores categories and lengths, so it is a yardstick, not a "
                + "target. None of these heuristics is guaranteed optimal.";
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Reads the number at the start of a cell such as "38 %" or "12 ms". */
    private static double numberIn(Object cell) {
        String text = String.valueOf(cell).trim();
        int end = 0;
        while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.')) {
            end++;
        }
        return end == 0 ? 0 : Double.parseDouble(text.substring(0, end));
    }

    /** Draws the best value of each ranked column in bold. */
    private final class BestValueRenderer extends DefaultTableCellRenderer {
        /** Swing calls this for every cell; we only adjust its colour or font. */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            boolean ranked = LOWER_IS_BETTER[column] != null;
            boolean best = ranked && numberIn(value) == bestValues[column];
            cell.setFont(cell.getFont().deriveFont(best ? Font.BOLD : Font.PLAIN));
            return cell;
        }
    }
}
