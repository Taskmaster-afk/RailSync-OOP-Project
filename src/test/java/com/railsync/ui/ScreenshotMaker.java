package com.railsync.ui;

import com.railsync.model.Schedule;
import com.railsync.model.Station;
import com.railsync.observer.ScheduleEventManager;
import com.railsync.service.DemoScenarioLoader;
import com.railsync.service.SchedulingService;
import com.railsync.strategy.FcfsStrategy;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Developer tool (not part of the app): opens the real main window, loads the
 * demo, allocates with FCFS and saves a PNG of every screen into
 * docs/screenshots. It then clicks the real "Demo: delay" button and captures
 * the animation part-way and after it ends, clicks Undo, and finally a breakdown.
 *
 * <p>Run: java -cp "target/classes;target/test-classes;target/lib/*" com.railsync.ui.ScreenshotMaker [prefix]</p>
 */
public final class ScreenshotMaker {

    private static MainFrame frame;
    private static SchedulingService service;

    private ScreenshotMaker() {
    }

    /**
     * Captures every screen and the disruption animation.
     *
     * @param args optional file name prefix, e.g. "stage6"
     * @throws Exception if the window cannot be built or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        String prefix = args.length > 0 ? args[0] : "screen";
        Theme.apply(args.length > 1 && args[1].equals("dark"));

        // Step 1: build the window, load the demo, allocate with FCFS, capture every screen.
        SwingUtilities.invokeAndWait(() -> {
            service = new SchedulingService(new Station("RailSync Central"), new Schedule(),
                    new FcfsStrategy(), new ScheduleEventManager());
            frame = new MainFrame(service);
            DemoScenarioLoader.load(service);
            service.allocatePendingTrains();
            // 08:12: three trains are queuing at the outer signal, so the yard view has something to show.
            frame.getClock().setTime(DemoScenarioLoader.SERVICE_DAY.atTime(8, 12));
            frame.setVisible(true);
        });
        for (String name : frame.getScreenNames()) {
            onEdt(() -> {
                frame.showScreen(name);
                if (name.equals("Comparison")) {
                    clickButtonStartingWith(frame.getContentPane(), "Run comparison");
                }
                capture(name, prefix + "-" + fileName(name));
            });
        }

        // Step 2: click the real scripted-delay button and capture the slide part-way and at the end.
        onEdt(() -> {
            frame.showScreen("Delay & Breakdown");
            clickButtonStartingWith(frame.getContentPane(), "Demo: delay");
        });
        Thread.sleep(200);
        onEdt(() -> capture("Delay & Breakdown", prefix + "-delay-animating"));
        Thread.sleep(700);
        onEdt(() -> capture("Delay & Breakdown", prefix + "-delay-after"));

        // Step 2b: the toolbar now offers "Undo delay of 12951 by 5 min"; click the real Undo button.
        onEdt(() -> clickButtonStartingWith(frame.getContentPane(), "Undo"));
        Thread.sleep(300);
        onEdt(() -> capture("Time-Distance", prefix + "-after-undo"));

        // Step 3: break down PF-2 (through the service, to avoid the confirmation dialog) and capture the diagram.
        onEdt(() -> service.reportBreakdown(service.getPlatformManager().findById(DemoScenarioLoader.BREAKDOWN_PLATFORM_ID)));
        Thread.sleep(700);
        onEdt(() -> capture("Time-Distance", prefix + "-breakdown-after"));

        onEdt(() -> frame.dispose());
        System.exit(0);
    }

    /** A step that may throw IOException, run on the Swing thread. */
    private interface SwingStep {
        void run() throws IOException;
    }

    private static void onEdt(SwingStep step) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                step.run();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void capture(String screen, String fileName) throws IOException {
        frame.showScreen(screen);
        JRootPane root = frame.getRootPane();
        root.validate();
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        root.paint(g);
        g.dispose();
        File folder = new File("docs/screenshots");
        folder.mkdirs();
        File file = new File(folder, fileName + ".png");
        ImageIO.write(image, "png", file);
        System.out.println("Saved " + file.getPath());
    }

    private static String fileName(String screen) {
        return screen.toLowerCase().replace(" & ", "-").replace(' ', '-');
    }

    /** Finds a visible button whose text starts with the given words and clicks it. */
    private static boolean clickButtonStartingWith(Container container, String start) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton && child.isShowing()
                    && ((AbstractButton) child).getText().startsWith(start)) {
                ((AbstractButton) child).doClick();
                return true;
            }
            if (child instanceof Container && clickButtonStartingWith((Container) child, start)) {
                return true;
            }
        }
        return false;
    }
}
