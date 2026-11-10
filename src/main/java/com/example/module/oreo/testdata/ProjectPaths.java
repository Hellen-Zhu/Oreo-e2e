package com.example.module.oreo.testdata;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Finds the {@code oreo-e2e} folder (the one holding {@code pom.xml}) from the classpath, so that files under
 * {@code src/test/resources} can be read and written whatever the working directory of a forked JVM.
 */
public final class ProjectPaths {

    public static final String PROPERTY = "oreo.project.dir";

    private ProjectPaths() {}

    /** {@code -Doreo.project.dir} when set; else derived from {@code target/test-classes/data} on the classpath. */
    public static Path projectRoot() {
        String dir = System.getProperty(PROPERTY);
        if (dir != null && !dir.isBlank()) {
            return Paths.get(dir.trim()).toAbsolutePath().normalize();
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = ProjectPaths.class.getClassLoader();
        List<String> seen = new ArrayList<>();
        try {
            Enumeration<URL> urls = loader.getResources("data");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                seen.add(url.toString());
                if (!"file".equals(url.getProtocol())) continue;
                Path dataDir = Paths.get(url.toURI());
                if (fits(dataDir)) return fromClasspathDataDir(dataDir);
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("[ProjectPaths] cannot read the classpath: " + e.getMessage()
                    + "; set -D" + PROPERTY + "=<oreo-e2e folder>", e);
        }
        throw new IllegalStateException("[ProjectPaths] no target/test-classes/data folder on the classpath "
                + seen + "; set -D" + PROPERTY + "=<oreo-e2e folder>");
    }

    /** {@code <root>/target/test-classes/data} -> {@code <root>}. */
    public static Path fromClasspathDataDir(Path dataDir) {
        Path dir = dataDir.normalize();
        if (!fits(dir)) {
            throw new IllegalStateException("[ProjectPaths] " + dataDir + " is not <project>/target/test-classes/data;"
                    + " set -D" + PROPERTY + "=<oreo-e2e folder>");
        }
        return dir.getParent().getParent().getParent();
    }

    /** Normalized only, not made absolute: the caller passes an absolute path (a drive letter must not be added). */
    private static boolean fits(Path dataDir) {
        Path dir = dataDir.normalize();
        Path testClasses = dir.getParent();
        Path target = testClasses == null ? null : testClasses.getParent();
        return target != null && target.getParent() != null
                && "data".equals(String.valueOf(dir.getFileName()))
                && "test-classes".equals(String.valueOf(testClasses.getFileName()))
                && "target".equals(String.valueOf(target.getFileName()));
    }

    /** The test data settings of the project, a properties file: see {@link #settings(Path)}. */
    public static final String SETTINGS_FILE = "src/test/resources/test-data.properties";

    /**
     * The test data settings of {@code project} ({@link #SETTINGS_FILE}), empty when it has none: decisions kept
     * out of the code, such as the setup files it names but does not have yet ({@code check.knownMissingSetups}).
     */
    public static Properties settings(Path project) {
        Properties settings = new Properties();
        Path file = project.resolve(SETTINGS_FILE);
        if (!java.nio.file.Files.isRegularFile(file)) return settings;
        try (java.io.Reader in = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            settings.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("[ProjectPaths] cannot read " + file + ": " + e.getMessage(), e);
        }
        return settings;
    }

    /** The comma-separated values of {@code key} in {@code settings}, trimmed; empty when it is not set. */
    public static List<String> listSetting(Properties settings, String key) {
        List<String> values = new ArrayList<>();
        for (String value : settings.getProperty(key, "").split(",")) {
            if (!value.isBlank()) values.add(value.trim());
        }
        return values;
    }
}
