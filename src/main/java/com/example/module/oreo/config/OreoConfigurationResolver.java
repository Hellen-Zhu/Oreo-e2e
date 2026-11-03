package com.example.module.oreo.config;

import com.example.genie.model.GenieScenario;
import com.example.module.oreo.OreoModule;
import cucumber.api.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The configured modes of a scenario: the {@link OreoModuleConfiguration} value and what overrides it. A bad risk
 * engine mode fails the scenario; a bad trace mode only warns, as tracing must not affect the test result.
 */
public final class OreoConfigurationResolver {

    private static final Logger log = LoggerFactory.getLogger(OreoConfigurationResolver.class);
    private static final String RISK_ENGINE_MODE_ATTRIBUTE = "risk.engine.mode";
    private static final String TRACE_MODE_PROPERTY = "playwright.trace.mode";
    private static final String TRACE_ENABLED_PROPERTY = "playwright.trace.enabled";

    private OreoConfigurationResolver() {
    }

    /** The risk engine mode, allowing a scenario attribute to override the configured one. */
    public static OreoModuleConfiguration.Mode riskEngineMode(GenieScenario scenario) {
        String scenarioMode = scenario.getAttribute(RISK_ENGINE_MODE_ATTRIBUTE);
        if (scenarioMode != null && !scenarioMode.isBlank()) {
            return OreoModuleConfiguration.Mode.from(scenarioMode);
        }

        return configuration(scenario).RISK_ENGINE_MODE.getValue();
    }

    /** The configuration Genie loaded for the run: genie.properties, overridden by the environment file. */
    public static OreoModuleConfiguration configuration(GenieScenario scenario) {
        OreoModule module = scenario.getRuntime().getModule(OreoModule.class);
        if (module == null) {
            throw new IllegalStateException("OreoModule is not active in the current Genie runtime");
        }
        return module.getConfiguration();
    }

    /**
     * The trace mode: the JVM property {@code playwright.trace.mode}, else {@code playwright.trace.enabled}, else the
     * configured one, else retain-on-failure.
     */
    public static OreoModuleConfiguration.TraceMode traceMode(Scenario scenario) {
        String modeOverride = System.getProperty(TRACE_MODE_PROPERTY);
        if (modeOverride != null && !modeOverride.isBlank()) {
            try {
                return OreoModuleConfiguration.TraceMode.valueOf(
                        modeOverride.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                log.warn("[OreoConfigurationResolver] Invalid {}='{}'. Using retain-on-failure.",
                        TRACE_MODE_PROPERTY, modeOverride);
                return OreoModuleConfiguration.TraceMode.RETAIN_ON_FAILURE;
            }
        }

        String enabledOverride = System.getProperty(TRACE_ENABLED_PROPERTY);
        if (enabledOverride != null && !enabledOverride.isBlank()) {
            return Boolean.parseBoolean(enabledOverride)
                    ? OreoModuleConfiguration.TraceMode.ON
                    : OreoModuleConfiguration.TraceMode.OFF;
        }

        try {
            OreoModule module = ((GenieScenario) scenario).getRuntime().getModule(OreoModule.class);
            if (module != null) {
                OreoModuleConfiguration.TraceMode configuredMode =
                        module.getConfiguration().PLAYWRIGHT_TRACE_MODE.getValue();
                if (configuredMode != null) {
                    return configuredMode;
                }
            }
        } catch (RuntimeException e) {
            log.warn("[OreoConfigurationResolver] Could not read oreo.playwright.trace.mode: {}",
                    e.getMessage());
        }
        return OreoModuleConfiguration.TraceMode.RETAIN_ON_FAILURE;
    }
}
