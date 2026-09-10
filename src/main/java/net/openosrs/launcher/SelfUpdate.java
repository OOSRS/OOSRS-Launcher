package net.openosrs.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Runs from the new cached JAR, after the installed launcher has exited. */
final class SelfUpdate
{
    private SelfUpdate() { }

    static void apply(String[] args) throws Exception
    {
        if (args.length != 5) { throw new IllegalArgumentException("Invalid launcher update arguments."); }
        long parent = Long.parseLong(args[1]);
        Path target = Path.of(args[2]).toAbsolutePath().normalize();
        Path source = Path.of(args[3]).toAbsolutePath().normalize();
        String expectedSha = args[4];
        if (!expectedSha.matches("[a-f0-9]{64}") || !Updates.sha256(source).equals(expectedSha)
            || !Files.isRegularFile(target) || Files.isSymbolicLink(target) || target.equals(source))
        {
            throw new IOException("Launcher update verification failed.");
        }
        ProcessHandle.of(parent).ifPresent(process -> {
            try { process.onExit().get(60, TimeUnit.SECONDS); }
            catch (Exception exception) { throw new IllegalStateException("The old launcher did not close.", exception); }
        });
        Path backup = target.resolveSibling(target.getFileName() + ".previous");
        Path temporary = Files.createTempFile(target.getParent(), ".openosrs-launcher-", ".jar");
        try
        {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!Updates.sha256(temporary).equals(expectedSha)) { throw new IOException("Staged launcher checksum mismatch."); }
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            Updates.atomicMove(temporary, target);
            Path logs = Path.of(System.getProperty("user.home"), ".openosrs", "launcher", "logs");
            Files.createDirectories(logs);
            Process restarted;
            try
            {
                restarted = new ProcessBuilder(Launcher.javaExecutable(), "-Duser.home=" + System.getProperty("user.home"), "-jar", target.toString())
                    .redirectErrorStream(true).redirectOutput(logs.resolve("launcher-restart.log").toFile()).start();
                if (restarted.waitFor(3, TimeUnit.SECONDS) && restarted.exitValue() != 0)
                {
                    throw new IOException("Updated launcher exited immediately.");
                }
            }
            catch (Exception failure)
            {
                Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
                Updates.atomicMove(temporary, target);
                throw new IOException("The previous launcher was restored. Open it again to retry.", failure);
            }
        }
        finally { Files.deleteIfExists(temporary); }
    }
}
