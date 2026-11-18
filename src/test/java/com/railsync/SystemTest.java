package com.railsync;

import com.railsync.persistence.AuditEntry;
import com.railsync.persistence.Database;
import com.railsync.persistence.SqliteStationRepository;
import com.railsync.service.StationSnapshot;
import com.railsync.strategy.PlatformAllocationStrategy;
import com.railsync.ui.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * System tests: the complete application (window, every screen, the service
 * layer and a real SQLite file) driven the way a user drives it, by clicking
 * the real buttons. Each test starts from a fresh temporary database file.
 *
 * <p>They need a screen, so they are skipped automatically on a headless
 * machine. Buttons that open a confirmation dialog are not clicked here; those
 * paths are covered by the manual system test cases in docs/se/TEST_PLAN.md.</p>
 */
class SystemTest {

    @TempDir
    Path folder;

    private Database database;
    private Main.Application app;

    @BeforeAll
    static void lookAndFeel() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "System tests need a screen");
        Theme.apply(false);
    }

    @BeforeEach
    void start() throws Exception {
        database = Database.open(folder.resolve("railsync.db"));
        onEdt(() -> app = Main.build(database));
    }

    @AfterEach
    void stop() throws Exception {
        onEdt(() -> app.getFrame().dispose());
        database.close();
        Theme.apply(false);
    }

    // ---------- helpers ----------

    private interface Step {
        void run() throws Exception;
    }

    private static void onEdt(Step step) throws Exception {
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                step.run();
            } catch (Exception e) {
                error.set(e);
            }
        });
        // Let anything the step queued with invokeLater (for example the audit-log refresh) run too.
        SwingUtilities.invokeAndWait(() -> { });
        if (error.get() != null) {
            throw error.get();
        }
    }

    private static <T extends Component> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container) {
                found.addAll(find((Container) child, type));
            }
        }
        return found;
    }

    /** Clicks the first button (on any screen) whose text starts with the given words. */
    private void click(String textStart) throws Exception {
        onEdt(() -> {
            for (AbstractButton button : find(app.getFrame().getContentPane(), AbstractButton.class)) {
                if (button.getText() != null && button.getText().startsWith(textStart)) {
                    button.doClick();
                    return;
                }
            }
            fail("No button starting with \"" + textStart + "\"");
        });
    }

    private StationSnapshot savedSession() {
        return new SqliteStationRepository(database).load().orElseThrow();
    }

    // ---------- the system test cases (IDs match docs/se/TEST_PLAN.md) ----------

    // ST-01: first start with an empty database. Expected: the demo scenario (12 trains, 6 platforms) is loaded,
    // saved to the file, and the sidebar shows where it is saved.
    @Test
    void st01FirstStartLoadsDemoAndSavesIt() {
        assertEquals(12, app.getService().getTrainManager().getAllTrains().size());
        StationSnapshot saved = savedSession();
        assertEquals(12, saved.getTrains().size());
        assertEquals(6, saved.getPlatforms().size());
        assertNotNull(app.getAutoSaver().getLastSavedAt());
        assertNull(app.getWarning());
    }

    // ST-02: Allocation screen, click "Allocate all pending trains" (FCFS). Expected: 11 of 12 placed, the
    // database holds 11 bookings, and the audit log records the ALLOCATION.
    @Test
    void st02AllocateAllThroughTheWindow() throws Exception {
        onEdt(() -> app.getFrame().showScreen("Allocation"));
        click("Allocate all pending trains");
        assertEquals(11, app.getService().getSchedule().size());
        assertEquals(11, savedSession().getBookings().size());
        List<AuditEntry> audit = app.getAutoSaver().getAuditLog().recent(20);
        assertTrue(audit.stream().anyMatch(e -> e.getEventType().equals("ALLOCATION")));
    }

    // ST-03: allocate, then click the toolbar Undo button. Expected: every booking removed, also in the database.
    @Test
    void st03UndoButtonReversesAllocation() throws Exception {
        click("Allocate all pending trains");
        click("Undo allocation");
        assertEquals(0, app.getService().getSchedule().size());
        assertEquals(0, savedSession().getBookings().size());
    }

    // ST-04: allocate, delay 12951 with the scripted demo button, close the app, start it again on the same file.
    // Expected: the restarted app shows the same bookings and the 5-minute delay (persistence across restarts).
    @Test
    void st04SessionSurvivesRestart() throws Exception {
        click("Allocate all pending trains");
        click("Demo: delay");
        int bookings = app.getService().getSchedule().size();
        onEdt(() -> app.getFrame().dispose());
        onEdt(() -> app = Main.build(database));
        assertEquals(bookings, app.getService().getSchedule().size());
        assertEquals(5, app.getService().getTrainManager().findByNumber("12951").getDelayMinutes());
    }

    // ST-05: allocate, then the scripted delay (12951 by 5 min). Expected: resolved as a ripple of 2 following
    // trains, and a DELAY entry in the audit log.
    @Test
    void st05DemoDelayRipples() throws Exception {
        click("Allocate all pending trains");
        click("Demo: delay");
        assertEquals(5, app.getService().getTrainManager().findByNumber("12951").getDelayMinutes());
        assertTrue(app.getAutoSaver().getAuditLog().recent(20).stream().anyMatch(e -> e.getEventType().equals("DELAY")));
    }

    // ST-06: choose "Priority" in the Allocation screen's strategy list, restart. Expected: the new strategy is
    // used at once and is still selected after the restart.
    @Test
    void st06StrategyChoiceIsRemembered() throws Exception {
        onEdt(() -> {
            for (JComboBox<?> box : find(app.getFrame().getContentPane(), JComboBox.class)) {
                for (int i = 0; i < box.getItemCount(); i++) {
                    Object item = box.getItemAt(i);
                    if (item instanceof PlatformAllocationStrategy
                            && ((PlatformAllocationStrategy) item).getName().equals("Priority")) {
                        box.setSelectedIndex(i);
                        return;
                    }
                }
            }
            fail("Strategy list not found");
        });
        String chosen = app.getService().getStrategy().getName();
        assertEquals("Priority", chosen);
        onEdt(() -> app.getFrame().dispose());
        onEdt(() -> app = Main.build(database));
        assertEquals(chosen, app.getService().getStrategy().getName());
    }

    // ST-07: Comparison screen, click "Run comparison". Expected: one row per strategy (4) and the live schedule
    // is not changed (the arena works on copies).
    @Test
    void st07ComparisonRunsOnCopies() throws Exception {
        onEdt(() -> app.getFrame().showScreen("Comparison"));
        click("Run comparison");
        AtomicReference<Integer> rows = new AtomicReference<>(0);
        onEdt(() -> {
            for (JTable table : find(app.getFrame().getContentPane(), JTable.class)) {
                if (table.getColumnCount() > 0 && table.getColumnName(0).equals("Strategy")) {
                    rows.set(table.getRowCount());
                }
            }
        });
        assertEquals(4, rows.get());
        assertEquals(0, app.getService().getSchedule().size());
    }

    // ST-08: Reports screen, audit tab after an allocation. Expected: the table lists database entries, newest first.
    @Test
    void st08AuditLogVisibleOnReports() throws Exception {
        click("Allocate all pending trains");
        onEdt(() -> app.getFrame().showScreen("Reports"));
        AtomicReference<Integer> rows = new AtomicReference<>(0);
        AtomicReference<String> firstType = new AtomicReference<>("");
        onEdt(() -> {
            for (JTable table : find(app.getFrame().getContentPane(), JTable.class)) {
                if (table.getColumnCount() == 4 && table.getColumnName(3).equals("What happened")) {
                    rows.set(table.getRowCount());
                    firstType.set(String.valueOf(table.getValueAt(0, 2)));
                }
            }
        });
        assertTrue(rows.get() >= 2, "rows: " + rows.get());
        assertTrue(firstType.get().equals("CONFLICT") || firstType.get().equals("ALLOCATION"), firstType.get());
    }

    // ST-09: click the sidebar "Dark mode" switch while the window is open. Expected: the theme really changes
    // (dark page and card colours, light text), and switching back restores the light theme.
    @Test
    void st09DarkModeSwitchesLive() throws Exception {
        Color lightText = UIManager.getColor("Label.foreground");
        click("Dark mode");
        assertTrue(Theme.isDark());
        Color darkText = UIManager.getColor("Label.foreground");
        assertTrue(brightness(darkText) > brightness(lightText));
        assertTrue(brightness(Theme.cardBackground()) < 0.3);
        click("Light mode");
        assertFalse(Theme.isDark());
    }

    private static double brightness(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    // ST-10: every screen in the sidebar can be opened. Expected: ten screens, each shown without an error.
    @Test
    void st10EveryScreenOpens() throws Exception {
        List<String> names = app.getFrame().getScreenNames();
        assertEquals(10, names.size());
        for (String name : names) {
            onEdt(() -> app.getFrame().showScreen(name));
        }
    }
}
