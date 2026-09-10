package net.openosrs.launcher;

/** Runs inside the downloaded JVM before any game classes can be loaded. */
public final class RuntimeProbe
{
    private RuntimeProbe() { }

    public static void main(String[] args) throws Exception
    {
        if (Runtime.version().feature() != 11) { throw new IllegalStateException("Client runtime must be Java 11"); }
        Class.forName("java.applet.AppletStub", false, RuntimeProbe.class.getClassLoader());
        Class.forName("java.applet.Applet", false, RuntimeProbe.class.getClassLoader());
        Class.forName("javax.swing.JFrame", false, RuntimeProbe.class.getClassLoader());
        System.out.println("OpenOSRS runtime OK: " + System.getProperty("java.version") + " / " + System.getProperty("os.arch"));
    }
}
