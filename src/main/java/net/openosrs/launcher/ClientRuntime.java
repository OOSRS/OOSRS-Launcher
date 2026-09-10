package net.openosrs.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** Installs a pinned, private JVM without changing system Java or active clients. */
final class ClientRuntime
{
    private static final long MAX_ARCHIVE = 128L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 512L * 1024 * 1024;

    private ClientRuntime() { }

    static Path prepare(Path cache, Consumer<String> status) throws IOException
    {
        String platform = platform();
        Properties runtimes = new Properties();
        try (InputStream input = ClientRuntime.class.getResourceAsStream("runtimes.properties"))
        {
            if (input == null) { throw new IOException("Runtime download information is missing."); }
            runtimes.load(input);
        }
        String url = runtimes.getProperty(platform + ".url");
        String sha = runtimes.getProperty(platform + ".sha256");
        if (url == null || sha == null) { throw new IOException("No client runtime is available for " + platform + "."); }
        Path root = cache.resolve("runtime").resolve(platform).toAbsolutePath();
        Files.createDirectories(root);
        Path pointer = root.resolve("current.properties");
        if (Files.isRegularFile(pointer))
        {
            Properties current = new Properties();
            try (InputStream input = Files.newInputStream(pointer)) { current.load(input); }
            String relative = current.getProperty("java");
            if (sha.equals(current.getProperty("sha256")) && relative != null)
            {
                Path executable = inside(root, relative);
                if (Files.isRegularFile(executable) && probe(executable, cache))
                {
                    status.accept("Using cached Java 11.");
                    return executable;
                }
            }
        }

        Path install = Files.createTempDirectory(root, "java11-");
        boolean installed = false;
        try
        {
            Path archive = install.resolve("runtime.download");
            status.accept("Setting up Java 11 · downloading runtime…");
            try (InputStream input = Updates.open(URI.create(url)); var output = Files.newOutputStream(archive))
            {
                byte[] buffer = new byte[65536];
                long total = 0;
                int read;
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
                while ((read = input.read(buffer)) != -1)
                {
                    total += read;
                    if (total > MAX_ARCHIVE || System.nanoTime() > deadline) { throw new IOException("Java download exceeded its size or time limit."); }
                    output.write(buffer, 0, read);
                }
            }
            if (!Updates.sha256(archive).equals(sha)) { throw new IOException("Java download checksum mismatch. Retry the launch."); }
            status.accept("Setting up Java 11 · verifying runtime…");
            Path contents = Files.createDirectory(install.resolve("contents"));
            extract(archive, contents, "zip".equals(runtimes.getProperty(platform + ".format")));
            Files.delete(archive);
            String executableName = platform.startsWith("windows-") ? "java.exe" : "java";
            Path executable;
            try (var paths = Files.walk(contents, 6))
            {
                executable = paths.filter(path -> path.getFileName().toString().equals(executableName)
                    && path.getParent().getFileName().toString().equals("bin") && Files.isRegularFile(path))
                    .findFirst().orElseThrow(() -> new IOException("The Java archive is missing its executable."));
            }
            if (!probe(executable, cache)) { throw new IOException("The downloaded Java 11 runtime could not start. See runtime-check.log in Logs."); }
            Properties current = new Properties();
            current.setProperty("sha256", sha);
            current.setProperty("version", runtimes.getProperty("version"));
            current.setProperty("java", root.relativize(executable).toString());
            Path temporary = install.resolve("current.properties");
            try (var output = Files.newOutputStream(temporary)) { current.store(output, "OpenOSRS verified client runtime"); }
            Updates.atomicMove(temporary, pointer);
            installed = true;
            status.accept("Java 11 is ready.");
            return executable;
        }
        finally
        {
            if (!installed)
            {
                try (var paths = Files.walk(install))
                {
                    for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    }
                }
            }
        }
    }

    private static boolean probe(Path executable, Path cache) throws IOException
    {
        Path log = cache.resolve("logs/runtime-check.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        Process process;
        try
        {
            Path launcher = Path.of(ClientRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            process = new ProcessBuilder(executable.toString(), "-cp", launcher.toString(), RuntimeProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        }
        catch (java.net.URISyntaxException | IOException exception) { return false; }
        try
        {
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
            return process.exitValue() == 0;
        }
        catch (InterruptedException exception)
        {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Java runtime check was interrupted.", exception);
        }
    }

    private static String platform() throws IOException
    {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) { arch = "x64"; }
        if (arch.equals("arm64")) { arch = "aarch64"; }
        if (os.startsWith("windows"))
        {
            // Windows 11 on ARM can run the x64 JVM under its built-in emulation.
            if (arch.equals("aarch64")) { arch = "x64"; }
            os = "windows";
        }
        else if (os.startsWith("mac")) { os = "mac"; }
        else if (os.startsWith("linux")) { os = Files.exists(Path.of("/etc/alpine-release")) ? "alpine-linux" : "linux"; }
        else { throw new IOException("Unsupported operating system: " + System.getProperty("os.name")); }
        return os + "-" + arch;
    }

    private static void extract(Path archive, Path root, boolean zip) throws IOException
    {
        long[] bytes = {0};
        if (zip)
        {
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive)))
            {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null)
                {
                    Path path = inside(root, entry.getName());
                    if (entry.isDirectory()) { Files.createDirectories(path); }
                    else { copyEntry(input, path, bytes); }
                }
            }
        }
        else
        {
            List<TarArchiveEntry> links = new ArrayList<>();
            try (TarArchiveInputStream input = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive))))
            {
                TarArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null)
                {
                    Path path = inside(root, entry.getName());
                    if (entry.isDirectory()) { Files.createDirectories(path); }
                    else if (entry.isSymbolicLink() || entry.isLink()) { links.add(entry); }
                    else if (entry.isFile())
                    {
                        copyEntry(input, path, bytes);
                        if ((entry.getMode() & 0111) != 0 && !path.toFile().setExecutable(true, false))
                        {
                            throw new IOException("Could not make Java executable.");
                        }
                    }
                    else { throw new IOException("Unsupported Java archive entry."); }
                }
            }
            // Links are created last so no archive file can escape through a symlink.
            for (TarArchiveEntry entry : links)
            {
                Path path = inside(root, entry.getName());
                Files.createDirectories(path.getParent());
                Path target = entry.isSymbolicLink() ? path.getParent().resolve(entry.getLinkName()).normalize()
                    : inside(root, entry.getLinkName());
                if (!target.startsWith(root) || Path.of(entry.getLinkName()).isAbsolute()) { throw new IOException("Invalid Java archive link."); }
                if (entry.isSymbolicLink()) { Files.createSymbolicLink(path, Path.of(entry.getLinkName())); }
                else { Files.createLink(path, target); }
            }
        }
    }

    private static Path inside(Path root, String name) throws IOException
    {
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || Path.of(name).isAbsolute()) { throw new IOException("Invalid Java archive path."); }
        return path;
    }

    private static void copyEntry(InputStream input, Path path, long[] total) throws IOException
    {
        Files.createDirectories(path.getParent());
        try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                total[0] += read;
                if (total[0] > MAX_EXTRACTED) { throw new IOException("Java archive is too large."); }
                output.write(buffer, 0, read);
            }
        }
    }
}
