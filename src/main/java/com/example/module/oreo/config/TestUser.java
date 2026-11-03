package com.example.module.oreo.config;

import com.example.module.oreo.testdata.ProjectPaths;

import java.io.IOException;
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
 *   <li>e-mail: {@code oreo.user.<role>.email} of {@link OreoModuleConfiguration}, loaded by Genie like
 *       {@code oreo.risk.engine.mode}: genie.properties holds the default, an environment file overrides it. A hook
 *       hands the configuration over before each scenario ({@link #configure})</li>
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

    static final String LOCAL_FILE = "users.local.properties";
    private static volatile OreoModuleConfiguration configuration;
    private static Properties local;

    /** Called before each scenario with the configuration Genie loaded for the run. */
    public static void configure(OreoModuleConfiguration loaded) {
        configuration = loaded;
    }

    public String role() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String email() {
        OreoModuleConfiguration loaded = configuration;
        if (loaded == null) {
            throw new IllegalStateException("[TestUser] no configuration yet: the e-mails are read once a scenario"
                    + " has started");
        }
        String value = loaded.email(this);
        if (blank(value)) {
            throw new IllegalStateException("[TestUser] no e-mail for " + role() + ": set oreo.user." + role()
                    + ".email in genie.properties or the environment file");
        }
        return value.trim();
    }

    public String password() {
        String key = "test.user." + role() + ".password";
        String env = "OREO_USER_" + name() + "_PASSWORD";
        String value = System.getProperty(key);
        if (blank(value)) value = System.getenv(env);
        if (blank(value)) value = local().getProperty(key);
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Read once per JVM, only when a password is not given otherwise, so a run with -D needs no project folder. */
    private static synchronized Properties local() {
        if (local == null) {
            local = new Properties();
            Path file = ProjectPaths.projectRoot().resolve(LOCAL_FILE);
            if (Files.isRegularFile(file)) {
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    local.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("[TestUser] cannot read " + file + ": " + e.getMessage(), e);
                }
            }
        }
        return local;
    }
}
