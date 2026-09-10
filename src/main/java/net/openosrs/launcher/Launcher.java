package net.openosrs.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** A small desktop launcher with independent client and launcher release channels. */
public final class Launcher
{
    private static final Color BACKGROUND = new Color(15, 23, 27);
    private static final Color SURFACE = new Color(24, 37, 42);
    private static final Color ACCENT = new Color(106, 231, 177);
    private static final Color TEXT = new Color(232, 241, 238);
    private final Path cache = Path.of(System.getProperty("user.home"), ".openosrs", "launcher");
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private JFrame frame;
    private JLabel status;
    private JLabel versions;
    private JButton launch;
    private JButton check;
    private JButton selfUpdate;
    private JProgressBar progress;
    private Updates updates;
    private volatile Release clientRelease;
    private volatile Release launcherRelease;

    private Launcher() { }

    public static void main(String[] args) throws Exception
    {
        if (args.length > 0 && args[0].equals("--apply-update"))
        {
            SelfUpdate.apply(args);
            return;
        }
        if (List.of(args).contains("--version"))
        {
            System.out.println("OpenOSRS Launcher " + version());
            return;
        }
        Launcher application = new Launcher();
        Files.createDirectories(application.cache.resolve("logs"));
        if (List.of(args).contains("--prepare"))
        {
            application.updates = new Updates(application.cache, System.out::println);
            try (FileChannel channel = application.lockChannel(); FileLock ignored = application.lock(channel))
            {
                for (String repository : List.of("OOSRS-Launcher", "OOSRS"))
                {
                    Release release = application.updates.latest(repository);
                    Path jar = application.updates.prepare(release);
                    application.updates.remember(release, "current");
                    System.out.println("Verified " + release.version() + ": " + jar);
                }
            }
            application.worker.shutdown();
            return;
        }
        if (args.length != 0) { throw new IllegalArgumentException("Usage: java -jar launcher.jar [--version|--prepare]"); }
        SwingUtilities.invokeLater(application::show);
    }

    static String version()
    {
        String value = Launcher.class.getPackage().getImplementationVersion();
        return value == null ? "1.0.0" : value;
    }

    static String javaExecutable()
    {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }

    private void show()
    {
        frame = new JFrame("OpenOSRS Launcher");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(640, 420));
        frame.setSize(700, 450);
        frame.setLocationRelativeTo(null);
        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(30, 34, 28, 34));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel eyebrow = label("YOUR CLIENT. YOUR PLUGINS.", 11, ACCENT);
        JLabel title = label("OpenOSRS", 42, TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        heading.add(eyebrow);
        heading.add(Box.createVerticalStrut(10));
        heading.add(title);
        heading.add(Box.createVerticalStrut(8));
        heading.add(label("Ready for your next session.", 16, new Color(150, 174, 167)));
        content.add(heading, BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        versions = label("Launcher " + version(), 13, TEXT);
        status = label("Checking for updates…", 13, new Color(166, 187, 179));
        progress = new JProgressBar();
        progress.setForeground(ACCENT);
        progress.setBackground(BACKGROUND);
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(400, 4));
        card.add(versions, BorderLayout.NORTH);
        card.add(status, BorderLayout.CENTER);
        card.add(progress, BorderLayout.SOUTH);
        content.add(card, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton logs = button("Logs", false);
        logs.addActionListener(event -> openLogs());
        check = button("Check updates", false);
        check.addActionListener(event -> refresh());
        selfUpdate = button("Update launcher", false);
        selfUpdate.setVisible(false);
        selfUpdate.addActionListener(event -> updateLauncher());
        launch = button("Launch OpenOSRS", true);
        launch.setEnabled(false);
        launch.addActionListener(event -> startClient());
        actions.add(logs);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(check);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(selfUpdate);
        actions.add(Box.createHorizontalGlue());
        actions.add(launch);
        content.add(actions, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.addWindowListener(new WindowAdapter()
        {
            @Override public void windowClosed(WindowEvent event) { worker.shutdown(); }
        });
        frame.setVisible(true);
        refresh();
    }

    private void refresh()
    {
        busy(true);
        worker.submit(() -> {
            String notice = "Ready to launch.";
            try (FileChannel channel = lockChannel(); FileLock ignored = lock(channel))
            {
                updates = new Updates(cache, this::status);
                launcherRelease = null;
                try { launcherRelease = updates.latest("OOSRS-Launcher"); }
                catch (IOException exception) { notice = "Launcher check unavailable. " + exception.getMessage(); }

                Release latest = null;
                Release selected = null;
                try
                {
                    latest = updates.latest("OOSRS");
                    updates.prepare(latest);
                    updates.remember(latest, "current");
                    selected = latest;
                }
                catch (IOException exception)
                {
                    for (String pointer : List.of("good", "current"))
                    {
                        try
                        {
                            Release cached = updates.cached("OOSRS", pointer);
                            if (cached != null && (latest == null || cached.revision() == latest.revision()))
                            {
                                selected = cached;
                                break;
                            }
                        }
                        catch (IOException ignoredCache) { }
                    }
                    if (selected == null) { throw exception; }
                    notice = "Using cached client " + selected.version() + "; update check/download failed.";
                    log(exception);
                }
                clientRelease = selected;
                status(notice);
                SwingUtilities.invokeLater(() -> versions.setText("Client " + clientRelease.version() + "  ·  Revision " + clientRelease.revision() + "  ·  Launcher " + version()));
            }
            catch (Exception exception) { clientRelease = null; error(exception); }
            finally { SwingUtilities.invokeLater(() -> busy(false)); }
        });
    }

    private void startClient()
    {
        Release release = clientRelease;
        if (release == null) { return; }
        busy(true);
        worker.submit(() -> {
            try
            {
                Path jar;
                try (FileChannel channel = lockChannel(); FileLock ignored = lock(channel)) { jar = updates.prepare(release); }
                Path log = cache.resolve("logs/client-" + Instant.now().toEpochMilli() + ".log");
                Path ready = cache.resolve("client-ready-" + java.util.UUID.randomUUID());
                Process process = new ProcessBuilder(javaExecutable(), "-Duser.home=" + System.getProperty("user.home"),
                    "-Dopenosrs.launcher.ready=" + ready, "-jar", jar.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                status("Starting OpenOSRS…");
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(90).toNanos();
                try
                {
                    while (process.isAlive() && !Files.isRegularFile(ready) && System.nanoTime() < deadline)
                    {
                        Thread.sleep(250);
                    }
                    if (!process.isAlive()) { throw new IOException("Client exited before startup completed. Open Logs for details."); }
                    if (!Files.isRegularFile(ready))
                    {
                        throw new IOException("Client startup has not completed yet. Its window remains open; check Logs before retrying.");
                    }
                    try (FileChannel channel = lockChannel(); FileLock ignored = lock(channel)) { updates.remember(release, "good"); }
                    status("OpenOSRS is running. You can close this launcher.");
                }
                finally { Files.deleteIfExists(ready); }
            }
            catch (Exception exception) { error(exception); }
            finally { SwingUtilities.invokeLater(() -> busy(false)); }
        });
    }

    private void updateLauncher()
    {
        Release release = launcherRelease;
        if (release == null) { return; }
        busy(true);
        worker.submit(() -> {
            try
            {
                try (FileChannel channel = lockChannel(); FileLock ignored = lock(channel))
                {
                    Path staged = updates.prepare(release);
                    Path target = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    if (!Files.isRegularFile(target) || !target.getFileName().toString().endsWith(".jar"))
                    {
                        throw new IOException("Self-update is available when running the release JAR.");
                    }
                    if (!Files.isWritable(target.getParent())) { throw new IOException("Move the launcher to a writable folder before updating."); }
                    Path helperLog = cache.resolve("logs/launcher-update-" + Instant.now().toEpochMilli() + ".log");
                    new ProcessBuilder(javaExecutable(), "-Duser.home=" + System.getProperty("user.home"), "-jar", staged.toString(),
                        "--apply-update", Long.toString(ProcessHandle.current().pid()), target.toString(), staged.toString(), release.sha256())
                        .redirectErrorStream(true).redirectOutput(helperLog.toFile()).start();
                }
                System.exit(0);
            }
            catch (Exception exception) { error(exception); SwingUtilities.invokeLater(() -> busy(false)); }
        });
    }

    private FileChannel lockChannel() throws IOException
    {
        Files.createDirectories(cache);
        return FileChannel.open(cache.resolve("update.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    private FileLock lock(FileChannel channel) throws IOException
    {
        FileLock lock = channel.tryLock();
        if (lock == null) { throw new IOException("Another launcher is updating. Wait for it to finish, then retry."); }
        return lock;
    }

    private void busy(boolean value)
    {
        progress.setIndeterminate(value);
        check.setEnabled(!value);
        launch.setEnabled(!value && clientRelease != null);
        selfUpdate.setEnabled(!value);
        selfUpdate.setVisible(launcherRelease != null && Release.compareVersions(launcherRelease.version(), version()) > 0);
    }

    private void status(String message) { SwingUtilities.invokeLater(() -> status.setText(message)); }
    private void error(Exception exception)
    {
        log(exception);
        status(exception.getMessage() == null ? "Launch failed. Open Logs for details." : exception.getMessage());
    }

    private void log(Exception exception)
    {
        try
        {
            Files.writeString(cache.resolve("logs/launcher.log"), Instant.now() + " " + exception.getClass().getSimpleName() + ": "
                + exception.getMessage() + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ignored) { }
    }

    private void openLogs()
    {
        try { Desktop.getDesktop().open(cache.resolve("logs").toFile()); }
        catch (Exception exception) { JOptionPane.showMessageDialog(frame, cache.resolve("logs").toString(), "OpenOSRS logs", JOptionPane.INFORMATION_MESSAGE); }
    }

    private static JLabel label(String text, int size, Color color)
    {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        return label;
    }

    private static JButton button(String title, boolean primary)
    {
        JButton button = new JButton(title);
        button.setBackground(primary ? ACCENT : SURFACE);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return button;
    }
}
