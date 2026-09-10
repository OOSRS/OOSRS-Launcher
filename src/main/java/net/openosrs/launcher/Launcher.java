package net.openosrs.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** A small desktop launcher with independent client and launcher release channels. */
public final class Launcher
{
    private final Path cache = Path.of(System.getProperty("user.home"), ".openosrs", "launcher");
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private LauncherWindow window;
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
        return value == null ? "1.0.3" : value;
    }

    static String javaExecutable()
    {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }

    private void show()
    {
        window = new LauncherWindow(this::startClient, this::refresh, this::updateLauncher, this::openLogs, worker::shutdown);
        window.setVisible(true);
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
                SwingUtilities.invokeLater(() -> window.setClientVersion(clientRelease.version(), clientRelease.revision()));
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
                }
                finally { Files.deleteIfExists(ready); }
                SwingUtilities.invokeLater(window::dispose);
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
        window.setBusy(value, clientRelease != null,
            launcherRelease != null && Release.compareVersions(launcherRelease.version(), version()) > 0);
    }

    private void status(String message) { SwingUtilities.invokeLater(() -> window.setStatus(message, false)); }
    private void error(Exception exception)
    {
        log(exception);
        SwingUtilities.invokeLater(() -> window.setStatus(
            exception.getMessage() == null ? "Launch failed. Open Logs for details." : exception.getMessage(), true));
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
        catch (Exception exception) { JOptionPane.showMessageDialog(window, cache.resolve("logs").toString(), "OpenOSRS logs", JOptionPane.INFORMATION_MESSAGE); }
    }

}
