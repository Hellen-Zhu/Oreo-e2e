package com.example.module.oreo.config;

import com.example.module.oreo.testdata.ProjectPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * The accounts the suite acts as. Features, snippets and Java name a role ({@code maker}); its e-mail is the
 * {@code X-User-Id} of an API request and the login of the UI.
 * <ul>
 *   <li>e-mail {@code test.user.<role>.email}: the environment file, else {@code genie.properties}, the default
 *       for every environment. The environment file is the one Genie runs against: {@code -Dgenie.properties=<path>}
 *       ({@code ./src/test/resources/sit.properties}), else {@code qa.properties}, the default of {@code OreoTest}</li>
 *   <li>password {@code test.user.<role>.password}: {@code -D}, else the environment variable
 *       {@code OREO_USER_<ROLE>_PASSWORD}, else {@code users.local.properties} in the project folder (git-ignored);
 *       never a committed file; the same in every environment</li>
 * </ul>
 */
public enum TestUser {
    MAKER,
    CHECKER,
    ADMIN,
    USER;

    /** Set to pick the environment file, as for Genie itself. */
    static final String ENVIRONMENT_PROPERTY = "genie.properties";
    static final String DEFAULT_ENVIRONMENT_FILE = "qa.properties";
    static final String SHARED_FILE = "genie.properties";
    static final String LOCAL_FILE = "users.local.properties";

    public String role() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String email() {
        String key = key("email");
        String value = Holder.ENVIRONMENT.getProperty(key);
        if (blank(value)) value = Holder.SHARED.getProperty(key);
        if (blank(value)) {
            throw new IllegalStateException("[TestUser] no e-mail for " + role() + ": set " + key + " in "
                    + Holder.ENVIRONMENT_NAME + " or " + SHARED_FILE);
        }
        return value.trim();
    }

    public String password() {
        String key = key("password");
        String env = "OREO_USER_" + name() + "_PASSWORD";
        String value = System.getProperty(key);
        if (blank(value)) value = System.getenv(env);
        if (blank(value)) value = Holder.local().getProperty(key);
        if (blank(value)) {
            throw new IllegalStateException("[TestUser] no password for " + role() + ": set -D" + key
                    + ", the environment variable " + env + " or " + key + " in " + LOCAL_FILE
                    + " (git-ignored); never in a committed file");
        }
        return value;
    }

    /** The {@code X-User-Id} for a step argument: a role's e-mail, or an e-mail as given. */
    public static String userId(String roleOrEmail) {
        if (roleOrEmail != null && roleOrEmail.contains("@")) return roleOrEmail.trim();
        return of(roleOrEmail).email();
    }

    public static TestUser of(String role) {
        String name = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        for (TestUser user : values()) {
            if (user.name().equals(name)) return user;
        }
        throw new IllegalArgumentException("[TestUser] unknown role '" + role + "'; expected maker, checker, admin"
                + " or user");
    }

    private String key(String field) {
        return "test.user." + role() + "." + field;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Read once per JVM, on first use. */
    private static final class Holder {
        static final String ENVIRONMENT_NAME;
        static final Properties ENVIRONMENT;
        static final Properties SHARED = classpath(SHARED_FILE);
        private static Properties local;

        static {
            String path = System.getProperty(ENVIRONMENT_PROPERTY);
            if (blank(path)) {
                ENVIRONMENT_NAME = DEFAULT_ENVIRONMENT_FILE;
                ENVIRONMENT = classpath(DEFAULT_ENVIRONMENT_FILE);
            } else {
                Path file = environmentFile(path.trim());
                ENVIRONMENT_NAME = file.toString();
                ENVIRONMENT = file(file);
            }
        }

        /**
         * As given, else against the project folder: a forked JVM may run in another working directory. A file that
         * is not found fails, as the accounts of another environment would be used.
         */
        private static Path environmentFile(String path) {
            Path given = Path.of(path);
            if (Files.isRegularFile(given)) return given;
            Path inProject = ProjectPaths.projectRoot().resolve(path).normalize();
            if (Files.isRegularFile(inProject)) return inProject;
            throw new IllegalStateException("[TestUser] -D" + ENVIRONMENT_PROPERTY + "=" + path + ": no such file, nor "
                    + inProject);
        }

        /** Read only when a password is not given otherwise, so a run with -D needs no project folder. */
        static synchronized Properties local() {
            if (local == null) {
                Path file = ProjectPaths.projectRoot().resolve(LOCAL_FILE);
                local = Files.isRegularFile(file) ? file(file) : new Properties();
            }
            return local;
        }

        private static Properties file(Path file) {
            Properties properties = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("[TestUser] cannot read " + file + ": " + e.getMessage(), e);
            }
            return properties;
        }

        private static Properties classpath(String name) {
            Properties properties = new Properties();
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = TestUser.class.getClassLoader();
            try (InputStream in = loader.getResourceAsStream(name)) {
                if (in != null) properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("[TestUser] cannot read " + name + ": " + e.getMessage(), e);
            }
            return properties;
        }
    }
}
