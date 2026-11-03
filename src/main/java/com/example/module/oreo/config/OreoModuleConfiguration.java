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
    public final ConfigurationDescriptor<String> MAKER_EMAIL = userEmail(TestUser.MAKER);
    public final ConfigurationDescriptor<String> CHECKER_EMAIL = userEmail(TestUser.CHECKER);
    public final ConfigurationDescriptor<String> ADMIN_EMAIL = userEmail(TestUser.ADMIN);
    public final ConfigurationDescriptor<String> USER_EMAIL = userEmail(TestUser.USER);

    public OreoModuleConfiguration(GenieModule genieModule) {
        super(genieModule);
        addDescriptor(RISK_ENGINE_MODE);
        addDescriptor(PLAYWRIGHT_TRACE_MODE);
        addDescriptor(MAKER_EMAIL);
        addDescriptor(CHECKER_EMAIL);
        addDescriptor(ADMIN_EMAIL);
        addDescriptor(USER_EMAIL);
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

    private ConfigurationDescriptor<String> userEmail(TestUser user) {
        return new DefaultConfigurationDescriptor<>(
                getGenieModule(),
                "user." + user.role() + ".email",
                "",
                "E-mail of the " + user.role() + " test user: its X-User-Id and UI login.",
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
