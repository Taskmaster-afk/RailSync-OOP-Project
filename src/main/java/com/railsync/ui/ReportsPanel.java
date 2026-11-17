package com.railsync.ui;

import com.railsync.service.AllocationResult;
import com.railsync.service.ReportGenerator;
import com.railsync.service.SchedulingService;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Module F screen: the full text report of the live schedule (metrics,
 * per-platform utilization, a text Gantt chart and the last allocation log).
 *
 * <p>Calls: SchedulingService.calculateMetrics(), ReportGenerator (text only).
 * Observes: every event, so the report is always current.</p>
 *
 * <p>OOP concepts: Inheritance (extends RefreshablePanel), [OBSERVER],
 * SOLID-S (formatting is ReportGenerator's job; this panel only displays it).</p>
 */
public class ReportsPanel extends RefreshablePanel {

    private final ReportGenerator reports = new ReportGenerator();
    private final JTextArea text = new JTextArea();

    /**
     * Builds the reports screen.
     *
     * @param service the scheduling service
     */
    public ReportsPanel(SchedulingService service) {
        super(service);
        text.setEditable(false);
        text.setFont(Ui.monoFont());
        JButton copy = new JButton("Copy report");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.getText()), null);
            Ui.showInfo(this, "Report copied to the clipboard.");
        });
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.add(copy);
        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.header("Reports",
                "The full text report of the live schedule, ready to copy."), BorderLayout.NORTH);
        north.add(tools, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(text), BorderLayout.CENTER);
        refresh();
    }

    /** Rebuilds the whole report text. */
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
    }
}
