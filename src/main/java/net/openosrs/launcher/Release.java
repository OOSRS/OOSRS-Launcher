package net.openosrs.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

/** The public, versioned contract between a GitHub release and the launcher. */
record Release(String repository, String version, String asset, String sha256, int revision, int javaVersion)
{
    static Release read(String repository, InputStream input) throws IOException
    {
        Properties values = new Properties();
        values.load(input);
        try
        {
            String version = required(values, "version");
            String asset = required(values, "asset");
            String sha = required(values, "sha256");
            if (!version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
                || !asset.matches("openosrs-(client|launcher)-[0-9.]+\\.jar")
                || !sha.matches("[a-f0-9]{64}"))
            {
                throw new IOException("The release metadata is invalid.");
            }
            String kind = repository.equals("OOSRS") ? "client" : "launcher";
            if (!asset.equals("openosrs-" + kind + "-" + version + ".jar"))
            {
                throw new IOException("The release file does not match its version.");
            }
            int revision = Integer.parseInt(values.getProperty("revision", "0"));
            int javaVersion = Integer.parseInt(required(values, "java"));
            if (javaVersion < 21 || javaVersion > 99 || (kind.equals("client") && revision < 1))
            {
                throw new IOException("The release compatibility information is invalid.");
            }
            return new Release(repository, version, asset, sha, revision, javaVersion);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IOException("The release metadata is incomplete.", exception);
        }
    }

    URI downloadUri()
    {
        return URI.create("https://github.com/OOSRS/" + repository + "/releases/download/v" + version + "/" + asset);
    }

    Properties properties()
    {
        Properties values = new Properties();
        values.setProperty("version", version);
        values.setProperty("asset", asset);
        values.setProperty("sha256", sha256);
        values.setProperty("revision", Integer.toString(revision));
        values.setProperty("java", Integer.toString(javaVersion));
        return values;
    }

    static int compareVersions(String left, String right)
    {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++)
        {
            int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (result != 0) { return result; }
        }
        return 0;
    }

    private static String required(Properties values, String key)
    {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(key); }
        return value.trim();
    }
}
