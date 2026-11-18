package com.railsync.ui;

import com.railsync.persistence.AuditEntry;
import com.railsync.persistence.AuditLog;
import com.railsync.service.AllocationResult;
import com.railsync.service.ReportGenerator;
import com.railsync.service.SchedulingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Module F screen: the full text report of the live schedule (metrics,
 * per-platform utilization, a text Gantt chart and the last allocation log),
 * and the permanent audit log read from the database.
 *
 * <p>Calls: SchedulingService.calculateMetrics(), ReportGenerator (text only),
 * AuditLog.recent(). Observes: every event, so both tabs are always current.</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * SOLID-S (formatting is ReportGenerator's job; this panel only displays it),
 * SOLID-D (reads the audit log through the AuditLog interface, not SQLite).</p>
 */
public class ReportsPanel extends RefreshablePanel {

    private static final int AUDIT_ROWS = 500;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");

    private final ReportGenerator reports = new ReportGenerator();
    private final JTextArea text = new JTextArea();
    private final AuditLog auditLog;
    private final DefaultTableModel auditModel = new DefaultTableModel(new Object[] {"#", "When", "Event", "What happened"}, 0) {
        /** The audit log is read-only. */
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel auditCount = new Ui.MutedLabel("");

    /**
     * Builds the reports screen without an audit log.
     *
     * @param service the scheduling service
     */
    public ReportsPanel(SchedulingService service) {
        this(service, null);
    }

    /**
     * Builds the reports screen.
     *
     * @param service  the scheduling service
     * @param auditLog the database audit log, or null when running without a database
     */
    public ReportsPanel(SchedulingService service, AuditLog auditLog) {
        super(service);
        this.auditLog = auditLog;
        add(Ui.header("Reports", "The full report of the live schedule, and the permanent audit log of every change."),
                BorderLayout.NORTH);

        // Tab 1: the text report.
        text.setEditable(false);
        text.setFont(Ui.monoFont());
        text.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JButton copy = new JButton("Copy report");
        copy.setIcon(Theme.icon("reports", 16));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.getText()), null);
            Ui.showInfo(this, "Report copied to the clipboard.");
        });
        JPanel reportTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        reportTools.add(copy);
        JPanel reportTab = new JPanel(new BorderLayout(0, 8));
        reportTab.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        reportTab.add(reportTools, BorderLayout.NORTH);
        reportTab.add(Ui.card(new JScrollPane(text)), BorderLayout.CENTER);

        // Tab 2: the audit log from the database.
        JTable auditTable = new JTable(auditModel);
        auditTable.setFillsViewportHeight(true);
        auditTable.getColumnModel().getColumn(0).setMaxWidth(70);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(170);
        auditTable.getColumnModel().getColumn(1).setMaxWidth(200);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        auditTable.getColumnModel().getColumn(2).setMaxWidth(200);
        auditTable.getColumnModel().getColumn(2).setCellRenderer(new BadgeRenderer());
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(800);
        JPanel auditTools = new JPanel(new BorderLayout());
        auditTools.add(auditCount, BorderLayout.WEST);
        JPanel auditTab = new JPanel(new BorderLayout(0, 8));
        auditTab.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        auditTab.add(auditTools, BorderLayout.NORTH);
        auditTab.add(Ui.card(new JScrollPane(auditTable)), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.addTab("Schedule report", Theme.icon("reports", 16), reportTab);
        tabs.addTab("Audit log (database)", Theme.icon("database", 16), auditTab);
        add(tabs, BorderLayout.CENTER);
        refresh();
    }

    /** Rebuilds the report text and re-reads the audit log. */
    @Override
    public void refresh() {
        StringBuilder report = new StringBuilder();
        report.append(reports.buildSummaryReport("Live schedule - " + service.getStrategy().getName(),
                service.calculateMetrics()));
        report.append("\nPlatform occupancy (text Gantt chart):\n");
        report.append(reports.buildOccupancyListing(service.getStation(), service.getSchedule()));
        report.append("\nLast allocation run:\n");
        if (service.getLastResults().isEmpty()) {
            report.append("  (none yet)\n");
        }
        for (AllocationResult result : service.getLastResults()) {
            report.append("  ").append(result.getMessage()).append('\n');
        }
        report.append("\nDefinitions: waiting = platform start - timetable arrival; turnaround = waiting + dwell;\n")
                .append("utilization = occupied minutes / observed window; averages cover allocated trains only.\n");
        text.setText(report.toString());
        text.setCaretPosition(0);
        // The auto-saver writes the audit entry for this same event after the screens are told,
        // so read the log once every observer has finished.
        SwingUtilities.invokeLater(this::refreshAudit);
    }

    private void refreshAudit() {
        auditModel.setRowCount(0);
        if (auditLog == null) {
            auditCount.setText("No database: the audit log is not available in this run.");
            return;
        }
        try {
            List<AuditEntry> entries = auditLog.recent(AUDIT_ROWS);
            for (AuditEntry entry : entries) {
                String message = entry.getMessage();
                int newline = message.indexOf('\n');
                auditModel.addRow(new Object[] {entry.getId(), entry.getLoggedAt().format(STAMP), entry.getEventType(),
                        newline >= 0 ? message.substring(0, newline) + " ..." : message});
            }
            auditCount.setText(auditLog.count() + " entries in total, newest first (showing up to " + AUDIT_ROWS
                    + "). Entries are only ever added, never changed.");
        } catch (RuntimeException e) {
            auditCount.setText("Could not read the audit log: " + e.getMessage());
        }
    }
}
