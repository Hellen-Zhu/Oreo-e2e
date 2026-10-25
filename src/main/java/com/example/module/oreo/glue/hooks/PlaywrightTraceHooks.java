package com.example.module.oreo.glue.hooks;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Tracing;
import com.example.genie.model.GenieScenario;
import com.example.module.oreo.OreoModule;
import com.example.module.oreo.OreoModuleConfiguration;
import com.example.module.oreo.glue.ui.PlaywrightServiceResolver;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures Playwright traces for UI scenarios without exposing tracing to feature files.
 *
 * <p>The mode is read from the {@code oreo.playwright.trace.mode} Genie module descriptor.
 * The JVM property {@code -Dplaywright.trace.mode=...} takes precedence. If neither is
 * configured, the default is {@code retain-on-failure}.
 */
public class PlaywrightTraceHooks {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTraceHooks.class);
    private static final String TRACE_MODE_PROPERTY = "playwright.trace.mode";
    private static final String TRACE_ENABLED_PROPERTY = "playwright.trace.enabled";
    private static final DateTimeFormatter TRACE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final AtomicLong TRACE_SEQUENCE = new AtomicLong();

    private final ThreadLocal<TraceSession> traceSession = new ThreadLocal<>();

    /**
     * Starts tracing after the existing Playwright setup hooks have resolved the service,
     * and before any scenario step runs.
     */
    @Before(value = "@playwright", order = 3)
    public void startTrace(Scenario scenario) {
        OreoModuleConfiguration.TraceMode mode = configuredTraceMode(scenario);
        if (mode == OreoModuleConfiguration.TraceMode.OFF) {
            return;
        }

        try {
            BrowserContext context = PlaywrightServiceResolver.from(scenario)
                    .service()
                    .getPage()
                    .context();
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true));
            traceSession.set(new TraceSession(context, mode));
            log.info("[PlaywrightTraceHooks] Trace started for '{}' with mode={}", scenario.getName(), mode);
        } catch (RuntimeException e) {
            // Tracing is diagnostic support and must not hide the actual test result.
            traceSession.remove();
            log.warn("[PlaywrightTraceHooks] Could not start trace for '{}': {}",
                    scenario.getName(), e.getMessage());
        }
    }

    /**
     * Stops tracing before lower-order after hooks and framework cleanup close the browser context.
     * Cucumber executes higher-order after hooks first.
     */
    @After(value = "@playwright", order = Integer.MAX_VALUE)
    public void stopTrace(Scenario scenario) {
        TraceSession session = traceSession.get();
        if (session == null) {
            return;
        }

        try {
            boolean keepTrace = session.mode == OreoModuleConfiguration.TraceMode.ON || scenario.isFailed();
            Tracing.StopOptions stopOptions = new Tracing.StopOptions();
            if (keepTrace) {
                Path tracePath = tracePath(scenario);
                Files.createDirectories(tracePath.getParent());
                stopOptions.setPath(tracePath);
                session.context.tracing().stop(stopOptions);
                log.info("[PlaywrightTraceHooks] Trace saved to {}", tracePath.toAbsolutePath());
            } else {
                // Stop without a path to discard the successful trace in retain-on-failure mode.
                session.context.tracing().stop(stopOptions);
                log.debug("[PlaywrightTraceHooks] Successful trace discarded for '{}'", scenario.getName());
            }
        } catch (Exception e) {
            // Do not replace a scenario failure with a trace-file or browser-cleanup failure.
            log.warn("[PlaywrightTraceHooks] Could not stop trace for '{}': {}",
                    scenario.getName(), e.getMessage());
        } finally {
            traceSession.remove();
        }
    }

    private static OreoModuleConfiguration.TraceMode configuredTraceMode(Scenario scenario) {
        String modeOverride = System.getProperty(TRACE_MODE_PROPERTY);
        if (modeOverride != null && !modeOverride.isBlank()) {
            try {
                return OreoModuleConfiguration.TraceMode.valueOf(
                        modeOverride.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                log.warn("[PlaywrightTraceHooks] Invalid {}='{}'. Using retain-on-failure.",
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
            log.warn("[PlaywrightTraceHooks] Could not read oreo.playwright.trace.mode: {}",
                    e.getMessage());
        }
        return OreoModuleConfiguration.TraceMode.RETAIN_ON_FAILURE;
    }

    private static Path tracePath(Scenario scenario) {
        String scenarioName = scenario.getName() == null ? "unnamed-scenario" : scenario.getName();
        String safeScenarioName = scenarioName
                .replaceAll("[^a-zA-Z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safeScenarioName.isBlank()) {
            safeScenarioName = "unnamed-scenario";
        }
        if (safeScenarioName.length() > 120) {
            safeScenarioName = safeScenarioName.substring(0, 120);
        }

        String uniqueSuffix = TRACE_TIMESTAMP.format(LocalDateTime.now())
                + "-thread-" + Thread.currentThread().threadId()
                + "-" + TRACE_SEQUENCE.incrementAndGet()
                + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        return resolveTraceDirectory().resolve(safeScenarioName + "-" + uniqueSuffix + ".zip");
    }

    private static Path resolveTraceDirectory() {
        try {
            URL log4j2 = PlaywrightTraceHooks.class.getClassLoader().getResource("log4j2.xml");
            if (log4j2 != null) {
                Path targetDirectory = Paths.get(log4j2.toURI()).getParent().getParent();
                return targetDirectory.resolve("traces");
            }
        } catch (Exception e) {
            log.debug("[PlaywrightTraceHooks] Could not resolve target directory from log4j2.xml: {}",
                    e.getMessage());
        }
        return Paths.get("target", "traces").toAbsolutePath();
    }

    private record TraceSession(BrowserContext context, OreoModuleConfiguration.TraceMode mode) {
    }
}
