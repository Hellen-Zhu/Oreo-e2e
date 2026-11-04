package com.example.module.oreo.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.example.genie.model.GenieScenario;
import com.example.genie.module.playwright.core.PlaywrightService;
import com.example.module.oreo.glue.ScenarioContext;
import com.example.module.oreo.config.OreoConfigurationResolver;
import com.example.module.oreo.config.OreoModuleConfiguration;
import com.example.module.oreo.testdata.TestData;
import cucumber.api.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class PlaywrightServiceResolver {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightServiceResolver.class);

    private PlaywrightServiceResolver() {
    }

    /**
     * Resolves Playwright infrastructure for the given scenario.
     *
     * @throws RuntimeException if {@code playwrightService} is absent
     *                          (scenario not tagged {@code @playwright})
     */
    public static Context from(Scenario scenario) {
        GenieScenario genieScenario = (GenieScenario) scenario;
        PlaywrightService service = genieScenario.getRuntime().getAttribute("playwrightService");
        if (service == null) {
            throw new RuntimeException(
                    "PlaywrightService not initialized. Make sure your scenario is tagged with @playwright");
        }
        return new Context(genieScenario, service);
    }

    public static final class Context extends ScenarioContext {

        private static final long DEFAULT_TIMEOUT_MILLIS = 50_000L;
        private final GenieScenario genieScenario;
        private final PlaywrightService service;
        private final ElementLookup elements;
        private final ElementActions actions;
        private final ResponseWaiter responses;
        private final RouteMocks mocks;
        private final EventualAssertions assertions;

        private Context(GenieScenario genieScenario, PlaywrightService service) {
            super(genieScenario);
            this.genieScenario = genieScenario;
            this.service = service;
            this.elements = new ElementLookup(service);
            this.actions = new ElementActions(service, this::timeoutMillis);
            this.responses = new ResponseWaiter(service, this::timeoutMillis);
            this.mocks = new RouteMocks(service);
            this.assertions = new EventualAssertions(this::timeoutMillis);
        }

        /**
         * The Playwright service for browser interaction.
         */
        public PlaywrightService service() { return service; }

        /**
         * Returns the configured risk engine mode, allowing a scenario attribute to override it.
         */
        public OreoModuleConfiguration.RiskEngineMode riskEngineMode() {
            return OreoConfigurationResolver.riskEngineMode(genieScenario);
        }

        /**
         * Default timeout used by Context helpers, aligned with playwright.timeout.
         */
        public long timeoutMillis() {
            long configuredTimeout = service.getConfiguration().getTimeout();
            return configuredTimeout > 0 ? configuredTimeout : DEFAULT_TIMEOUT_MILLIS;
        }

        public void navigateToPath(String path) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must not be null/blank");
            }
            if (!path.startsWith("/")) {
                throw new IllegalArgumentException("path must start with '/', but was: " + path);
            }

            String baseUrl = service.getConfiguration().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("playwright.base.url is not configured");
            }

            String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String url = normalizedBase + path;

            service.getPage().navigate(
                    url,
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(timeoutMillis())
            );
        }

        public Locator getLocatorWithParam(String name, String value) {
            return elements.getLocatorWithParam(name, value);
        }

        /**
         * The toast recorder of this scenario's page ({@link ToastRecorder}): installed on the first call for the
         * browser context, emptied on the first call of every scenario. Toasts are the {@code toast.notification}
         * elements.
         */
        public ToastRecorder toastRecorder() {
            return ToastRecorder.attach(service.getPage(), genieScenario, elements.testIdSelector("toast.notification"));
        }

        /** {@link #getLocatorWithParam} with the value of stored variable {@code ctxKey}, which the report shows. */
        public Locator getLocatorFromStoredVariable(String name, String ctxKey) {
            String value = get(ctxKey);
            write("Dynamic value from scenario context variable '" + ctxKey + "' is: " + value);
            return getLocatorWithParam(name, value);
        }

        // ── Element actions (ElementActions) ───────────────────────────
        public Locator innerInput(Locator element) { return ElementActions.innerInput(element); }
        public Locator innerInput(String elementKey) { return innerInput(service.getLocator(elementKey)); }
        public void waitForVisible(Locator element) { actions.waitForVisible(element); }
        public void fillDateInput(String elementKey, String value) { actions.fillDateInput(elementKey, value); }
        public void clickOption(String name) { actions.clickOption(name); }

        // ── Table rows and cells (ElementLookup, ElementActions) ───────
        public Locator rowWithCell(String rowKey, String cellKey, String column, String text) {
            return elements.rowWithCell(rowKey, cellKey, column, text);
        }
        public Locator cellOf(Locator row, String cellKey, String column) {
            return elements.cellOf(row, cellKey, column);
        }
        /** The trimmed visible text of {@code element}, read once when the element exists. */
        public String text(Locator element) { return actions.text(element); }

        // ── Actions that wait for their response (ResponseWaiter) ──────
        // Each waits for the request the action sends and its whole response, and asserts HTTP 200.
        public void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            clickAndWaitForResponse(service.getLocator(locatorKey), urlFragment, httpMethod);
        }
        public void clickAndWaitForResponse(Locator locator, String urlFragment, String httpMethod) {
            responses.runAndWaitForResponse(locator::click, urlFragment, httpMethod);
        }
        public void doubleClickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            Locator locator = service.getLocator(locatorKey);
            responses.runAndWaitForResponse(locator::dblclick, urlFragment, httpMethod);
        }
        public void refreshAndWaitForResponse(String urlFragment, String httpMethod) {
            responses.runAndWaitForResponse(() -> service.getPage().reload(), urlFragment, httpMethod);
        }
        /**
         * Clicks {@code locatorKey}, waits for the {@code httpMethod urlFragment} response the click causes, then for the
         * {@code thenHttpMethod thenUrlFragment} response the page sends after it (a reload the UI does by itself), each
         * with the whole timeout; asserts HTTP 200 on both and returns the first body.
         */
        public String clickAndWaitForResponses(String locatorKey, String urlFragment, String httpMethod,
                                               String thenUrlFragment, String thenHttpMethod) {
            Locator locator = service.getLocator(locatorKey);
            return responses.runAndWaitForResponses(locator::click, List.of(
                    new ResponseWaiter.Call(httpMethod, urlFragment),
                    new ResponseWaiter.Call(thenHttpMethod, thenUrlFragment))).get(0).text();
        }
        /** Like {@link #clickAndWaitForResponse(String, String, String)}, accepting 200 or 201; returns the body. */
        public String clickAndCaptureResponse(String locatorKey, String urlFragment, String httpMethod) {
            Locator locator = service.getLocator(locatorKey);
            return responses.runAndCaptureResponse(locator::click, urlFragment, httpMethod);
        }

        /**
         * Clicks {@code locatorKey} and waits for the risk calculation {@code POST riskUrl}: REAL lets the risk engine
         * answer, MOCK answers with the response of setup file {@code mockSetup}.
         */
        public void clickUsingRiskEngineMode(String locatorKey, String riskUrl, String mockSetup) {
            OreoModuleConfiguration.RiskEngineMode mode = riskEngineMode();
            log.info("[PlaywrightServiceResolver] clicking '{}' using configured risk engine mode='{}', url='{}'",
                    locatorKey, mode, riskUrl);
            Runnable click = () -> service.getLocator(locatorKey).click();
            switch (mode) {
                case MOCK:
                    mocks.mockRoute(riskUrl, 200, TestData.setup(mockSetup).response());
                    try {
                        responses.runAndWaitForResponse(click, riskUrl, "POST");
                    } finally {
                        mocks.clearMockRoute(riskUrl);
                    }
                    return;
                case REAL:
                    responses.runAndWaitForResponse(click, riskUrl, "POST");
                    return;
                default:
                    throw new IllegalStateException("Unhandled risk engine mode: " + mode);
            }
        }

        // ── Assertions (EventualAssertions) ────────────────────────────
        public void assertEventuallyVisible(Locator locator) { assertions.assertEventuallyVisible(locator); }
        public void assertEventuallyHidden(Locator locator) { assertions.assertEventuallyHidden(locator); }
        public void assertEventuallyEnabled(Locator locator) { assertions.assertEventuallyEnabled(locator); }
        public void assertEventuallyDisabled(Locator locator) { assertions.assertEventuallyDisabled(locator); }
        public void assertEventuallyContains(Locator locator, String text) {
            assertions.assertEventuallyContains(locator, text);
        }
        public void assertHasValue(Locator locator, String value) { assertions.assertHasValue(locator, value); }
    }
}
