package net.openosrs.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Downloads complete artifacts into a versioned cache before publishing a pointer. */
final class Updates
{
    private final Path root;
    private final Consumer<String> status;

    Updates(Path root, Consumer<String> status) throws IOException
    {
        this.root = root;
        this.status = status;
        Files.createDirectories(root);
    }

    Release latest(String repository) throws IOException
    {
        status.accept("Checking " + (repository.equals("OOSRS") ? "client" : "launcher") + " updates…");
        URI uri = URI.create("https://github.com/OOSRS/" + repository + "/releases/latest/download/update.properties");
        IOException failure = null;
        for (int attempt = 0; attempt < 2; attempt++)
        {
            try (InputStream input = open(uri))
            {
                byte[] data = input.readNBytes(16385);
                if (data.length > 16384) { throw new IOException("Release metadata is too large."); }
                return Release.read(repository, new ByteArrayInputStream(data));
            }
            catch (IOException exception) { failure = exception; }
        }
        throw failure;
    }

    Release cached(String repository, String pointer) throws IOException
    {
        Path file = root.resolve(repository + "-" + pointer + ".properties");
        if (!Files.isRegularFile(file)) { return null; }
        try (InputStream input = Files.newInputStream(file))
        {
            Release release = Release.read(repository, input);
            Path jar = path(release);
            return Files.isRegularFile(jar) && sha256(jar).equals(release.sha256()) ? release : null;
        }
    }

    Path path(Release release)
    {
        return root.resolve(release.repository()).resolve(release.version() + "-" + release.sha256().substring(0, 16)).resolve(release.asset());
    }

    Path prepare(Release release) throws IOException
    {
        if (Runtime.version().feature() < release.minimumJava())
        {
            throw new IOException("This release needs Java " + release.minimumJava() + " or newer. Start the launcher with a supported version.");
        }
        Path destination = path(release);
        if (Files.isRegularFile(destination) && sha256(destination).equals(release.sha256()))
        {
            status.accept("Using cached " + release.asset());
            return destination;
        }
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "download-", ".part");
        try
        {
            status.accept("Downloading " + release.asset() + "…");
            try (InputStream input = open(release.downloadUri()); var output = Files.newOutputStream(temporary))
            {
                byte[] buffer = new byte[65536];
                long total = 0;
                int read;
                long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
                while ((read = input.read(buffer)) != -1)
                {
                    total += read;
                    if (total > 512L * 1024 * 1024 || System.nanoTime() > deadline)
                    {
                        throw new IOException("Download exceeded its size or time limit.");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (!sha256(temporary).equals(release.sha256()))
            {
                throw new IOException("Download verification failed. The installed version was preserved.");
            }
            try (JarFile jar = new JarFile(temporary.toFile()))
            {
                if (jar.getManifest() == null || jar.getManifest().getMainAttributes().getValue("Main-Class") == null)
                {
                    throw new IOException("The downloaded file is not a runnable JAR.");
                }
            }
            atomicMove(temporary, destination);
            return destination;
        }
        finally { Files.deleteIfExists(temporary); }
    }

    void remember(Release release, String pointer) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        release.properties().store(output, "OpenOSRS verified release");
        Path temporary = Files.createTempFile(root, "state-", ".part");
        try
        {
            Files.write(temporary, output.toByteArray());
            atomicMove(temporary, root.resolve(release.repository() + "-" + pointer + ".properties"));
        }
        finally { Files.deleteIfExists(temporary); }
    }

    static String sha256(Path file) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file))
            {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) != -1) { digest.update(buffer, 0, read); }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest())
            {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    static void atomicMove(Path source, Path destination) throws IOException
    {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream open(URI uri) throws IOException
    {
        for (int redirects = 0; redirects < 6; redirects++)
        {
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || !(host.equals("github.com")
                || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")))
            {
                throw new IOException("Unexpected release download destination.");
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "OpenOSRS-Launcher/" + Launcher.version());
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400)
            {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) { throw new IOException("The release redirect is missing."); }
                uri = uri.resolve(location);
                continue;
            }
            if (code != 200)
            {
                connection.disconnect();
                throw new IOException(code == 404 ? "No published release is available yet." : "The update service returned HTTP " + code + ".");
            }
            return new java.io.FilterInputStream(connection.getInputStream())
            {
                @Override public void close() throws IOException
                {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        }
        throw new IOException("Too many release redirects.");
    }
}
