package com.example.module.oreo.config;

import com.example.genie.module.GenieModule;
import com.example.genie.module.configuration.BaseGenieModuleConfiguration;
import com.example.genie.module.configuration.ConfigurationDescriptor;
import com.example.genie.module.configuration.DefaultConfigurationDescriptor;
import com.example.genie.type.ValueConverter;

public final class OreoModuleConfiguration extends BaseGenieModuleConfiguration {

    public final ConfigurationDescriptor<RiskEngineMode> RISK_ENGINE_MODE = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "risk.engine.mode",
            RiskEngineMode.REAL,
            "Risk engine mode. Use MOCK to call mock APIs.",
            ValueConverter.createEnum(RiskEngineMode.class)
    );

    public final ConfigurationDescriptor<TraceMode> PLAYWRIGHT_TRACE_MODE = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "playwright.trace.mode",
            TraceMode.RETAIN_ON_FAILURE,
            "Playwright trace mode. Use ON, OFF, or RETAIN_ON_FAILURE.",
            ValueConverter.createEnum(TraceMode.class)
    );

    public final ConfigurationDescriptor<String> MAKER_EMAIL = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.maker.email",
            "",
            "E-mail of the maker test user: its X-User-Id and UI login.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> CHECKER_EMAIL = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.checker.email",
            "",
            "E-mail of the checker test user: its X-User-Id and UI login.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> ADMIN_EMAIL = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.admin.email",
            "",
            "E-mail of the admin test user: its X-User-Id and UI login.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> USER_EMAIL = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.user.email",
            "",
            "E-mail of the user test user: its X-User-Id and UI login.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> MAKER_PASSWORD = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.maker.password",
            "",
            "Password of the maker test user: its UI login. -Doreo.user.maker.password overrides it.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> CHECKER_PASSWORD = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.checker.password",
            "",
            "Password of the checker test user: its UI login. -Doreo.user.checker.password overrides it.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> ADMIN_PASSWORD = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.admin.password",
            "",
            "Password of the admin test user: its UI login. -Doreo.user.admin.password overrides it.",
            ValueConverter.STRING
    );

    public final ConfigurationDescriptor<String> USER_PASSWORD = new DefaultConfigurationDescriptor<>(
            getGenieModule(),
            "user.user.password",
            "",
            "Password of the user test user: its UI login. -Doreo.user.user.password overrides it.",
            ValueConverter.STRING
    );

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

    public enum RiskEngineMode {
        MOCK,
        REAL
    }

    public enum TraceMode {
        OFF,
        ON,
        RETAIN_ON_FAILURE
    }
}
