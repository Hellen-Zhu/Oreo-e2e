package com.example.module.oreo.config;

import com.example.genie.module.GenieModule;
import com.example.genie.module.configuration.BaseGenieModuleConfiguration;
import com.example.genie.module.configuration.ConfigurationDescriptor;
import com.example.genie.module.configuration.DefaultConfigurationDescriptor;
import com.example.genie.type.ValueConverter;

import java.util.Locale;

public final class OreoModuleConfiguration extends BaseGenieModuleConfiguration {

    public final ConfigurationDescriptor<Mode> RISK_ENGINE_MODE = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "risk.engine.mode",
            Mode.REAL,
            "Risk engine mode. Use MOCK to call mock APIs.",
            ValueConverter.createEnum(Mode.class)
    );

    public final ConfigurationDescriptor<TraceMode> PLAYWRIGHT_TRACE_MODE = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "playwright.trace.mode",
            TraceMode.RETAIN_ON_FAILURE,
            "Playwright trace mode. Use ON, OFF, or RETAIN_ON_FAILURE.",
            ValueConverter.createEnum(TraceMode.class)
    );

    /** The e-mail of each {@link TestUser}: {@code oreo.user.<role>.email}, set in genie.properties. */
    public final ConfigurationDescriptor<String> MAKER_EMAIL = userSetting(TestUser.MAKER, "email");
    public final ConfigurationDescriptor<String> CHECKER_EMAIL = userSetting(TestUser.CHECKER, "email");
    public final ConfigurationDescriptor<String> ADMIN_EMAIL = userSetting(TestUser.ADMIN, "email");
    public final ConfigurationDescriptor<String> USER_EMAIL = userSetting(TestUser.USER, "email");

    /** The password of each {@link TestUser}: {@code oreo.user.<role>.password}, set in genie.properties. */
    public final ConfigurationDescriptor<String> MAKER_PASSWORD = userSetting(TestUser.MAKER, "password");
    public final ConfigurationDescriptor<String> CHECKER_PASSWORD = userSetting(TestUser.CHECKER, "password");
    public final ConfigurationDescriptor<String> ADMIN_PASSWORD = userSetting(TestUser.ADMIN, "password");
    public final ConfigurationDescriptor<String> USER_PASSWORD = userSetting(TestUser.USER, "password");

    public OreoModuleConfiguration(GenieModule genieModule) {
        super(genieModule);
        addDescriptor(RISK_ENGINE_MODE);
        addDescriptor(PLAYWRIGHT_TRACE_MODE);
        addDescriptor(MAKER_EMAIL);
        addDescriptor(CHECKER_EMAIL);
        addDescriptor(ADMIN_EMAIL);
        addDescriptor(USER_EMAIL);
        addDescriptor(MAKER_PASSWORD);
        addDescriptor(CHECKER_PASSWORD);
        addDescriptor(ADMIN_PASSWORD);
        addDescriptor(USER_PASSWORD);
    }

    /** The configured e-mail of a user, blank when none is set. */
    public String email(TestUser user) {
        ConfigurationDescriptor<String> descriptor = switch (user) {
            case MAKER -> MAKER_EMAIL;
            case CHECKER -> CHECKER_EMAIL;
            case ADMIN -> ADMIN_EMAIL;
            case USER -> USER_EMAIL;
        };
        return descriptor.getValue();
    }

    /** The configured password of a user, blank when none is set. */
    public String password(TestUser user) {
        ConfigurationDescriptor<String> descriptor = switch (user) {
            case MAKER -> MAKER_PASSWORD;
            case CHECKER -> CHECKER_PASSWORD;
            case ADMIN -> ADMIN_PASSWORD;
            case USER -> USER_PASSWORD;
        };
        return descriptor.getValue();
    }

    private ConfigurationDescriptor<String> userSetting(TestUser user, String setting) {
        return new DefaultConfigurationDescriptor<>(
                getGenieModule(),
                "user." + user.role() + "." + setting,
                "",
                "The " + setting + " of the " + user.role() + " test user.",
                ValueConverter.STRING
        );
    }


    public enum Mode {
        MOCK,
        REAL;

        public static Mode from(String rawValue) {
            String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "MOCK" -> MOCK;
                case "REAL" -> REAL;
                default -> throw new IllegalArgumentException(
                        "Unsupported risk.engine.mode value '" + rawValue + "'. Expected one of: MOCK, REAL."
                );
            };
        }
    }

    public enum TraceMode {
        OFF,
        ON,
        RETAIN_ON_FAILURE
    }
}
