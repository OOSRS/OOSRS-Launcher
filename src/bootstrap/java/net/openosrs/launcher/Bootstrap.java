package net.openosrs.launcher;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.swing.JOptionPane;

/** Starts on Java 11 or newer and selects the installed Java 21 runtime. */
public final class Bootstrap
{
    private Bootstrap() { }

    public static void main(String[] args) throws Exception
    {
        if (Runtime.version().feature() == 21)
        {
            // Keep Java 21 classes out of the older VM's class loading path.
            try { Class.forName("net.openosrs.launcher.Launcher").getMethod("main", String[].class).invoke(null, (Object) args); }
            catch (InvocationTargetException exception)
            {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception) { throw (Exception) cause; }
                if (cause instanceof Error) { throw (Error) cause; }
                throw exception;
            }
            return;
        }

        try
        {
            if (Boolean.getBoolean("openosrs.launcher.runtimeSelected"))
            {
                throw new IOException("The selected runtime is not Java 21. Check OPENOSRS_JAVA_HOME.");
            }
            Path java = findJava();
            List<String> command = new ArrayList<>();
            command.add(java.toString());
            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
            {
                if (argument.startsWith("-D")) { command.add(argument); }
            }
            command.add("-Dopenosrs.launcher.runtimeSelected=true");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(Bootstrap.class.getName());
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).inheritIO().start();
            // Release the installed JAR before the desktop launcher can self-update it.
            if (args.length != 0) { System.exit(process.waitFor()); }
        }
        catch (IOException exception)
        {
            String message = exception.getMessage();
            System.err.println("OpenOSRS: " + message);
            if (args.length == 0 && !GraphicsEnvironment.isHeadless())
            {
                JOptionPane.showMessageDialog(null, message, "OpenOSRS Launcher", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }

    private static Path findJava() throws IOException
    {
        Set<Path> homes = new LinkedHashSet<>();
        for (String home : new String[]{System.getProperty("openosrs.java.home"), System.getenv("OPENOSRS_JAVA_HOME"),
            System.getenv("JAVA_HOME"), System.getProperty("java.home")})
        {
            if (home != null && !home.isBlank()) { homes.add(Path.of(home)); }
        }
        List<Path> roots = new ArrayList<>();
        Path current = Path.of(System.getProperty("java.home")).getParent();
        if (current != null) { roots.add(current); }
        String userHome = System.getProperty("user.home");
        roots.add(Path.of(userHome, ".jdks"));
        roots.add(Path.of(userHome, ".sdkman", "candidates", "java"));
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows"))
        {
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null)
            {
                for (String vendor : List.of("Java", "Eclipse Adoptium", "Microsoft", "Amazon Corretto"))
                {
                    roots.add(Path.of(programFiles, vendor));
                }
            }
        }
        else if (os.startsWith("Mac"))
        {
            roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
            roots.add(Path.of(userHome, "Library", "Java", "JavaVirtualMachines"));
        }
        else
        {
            roots.add(Path.of("/usr/lib/jvm"));
            roots.add(Path.of("/usr/java"));
            roots.add(Path.of("/opt/java"));
        }
        for (Path root : roots)
        {
            try (var children = Files.list(root)) { children.sorted().forEach(homes::add); }
            catch (IOException ignored) { }
        }
        for (Path home : homes)
        {
            if (Files.isDirectory(home.resolve("Contents/Home"))) { home = home.resolve("Contents/Home"); }
            Path executable = home.resolve("bin").resolve(os.startsWith("Windows") ? "java.exe" : "java");
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) { continue; }
            try (InputStream input = Files.newInputStream(home.resolve("release")))
            {
                Properties release = new Properties();
                release.load(input);
                String version = release.getProperty("JAVA_VERSION", "").replace("\"", "");
                if (version.matches("21(?:[.\\-+].*)?")) { return executable; }
            }
            catch (IOException ignored) { }
        }
        throw new IOException("Java 21 was not found. Install Java 21, or set OPENOSRS_JAVA_HOME to its folder.\n"
            + "Your system's default Java does not need to change.");
    }
}
