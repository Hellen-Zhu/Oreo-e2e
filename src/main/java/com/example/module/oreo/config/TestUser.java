package com.example.module.oreo.config;

import java.util.Locale;

/**
 * The accounts the suite acts as. Features, snippets and Java name a role ({@code maker}); its e-mail is the
 * {@code X-User-Id} of an API request and the login of the UI.
 * <ul>
 *   <li>e-mail: {@code oreo.user.<role>.email} of {@link OreoModuleConfiguration}, loaded by Genie like
 *       {@code oreo.risk.engine.mode}: genie.properties holds the default, an environment file overrides it. A hook
 *       hands the configuration over before each scenario ({@link #configure})</li>
 *   <li>password: {@code -Doreo.user.<role>.password}, else {@code oreo.user.<role>.password} of the same
 *       configuration (genie.properties)</li>
 * </ul>
 */
public enum TestUser {
    MAKER,
    CHECKER,
    ADMIN,
    USER;

    private static volatile OreoModuleConfiguration configuration;

    /** Called before each scenario with the configuration Genie loaded for the run. */
    public static void configure(OreoModuleConfiguration loaded) {
        configuration = loaded;
    }

    public String role() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String email() {
        String value = loaded().email(this);
        if (blank(value)) {
            throw new IllegalStateException("[TestUser] no e-mail for " + role() + ": set oreo.user." + role()
                    + ".email in genie.properties or the environment file");
        }
        return value.trim();
    }

    public String password() {
        String key = "oreo.user." + role() + ".password";
        String value = System.getProperty(key);
        if (blank(value)) value = loaded().password(this);
        if (blank(value)) {
            throw new IllegalStateException("[TestUser] no password for " + role() + ": set " + key
                    + " in genie.properties or pass -D" + key);
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

    private static OreoModuleConfiguration loaded() {
        OreoModuleConfiguration loaded = configuration;
        if (loaded == null) {
            throw new IllegalStateException("[TestUser] no configuration yet: the users are read once a scenario"
                    + " has started");
        }
        return loaded;
    }
}
