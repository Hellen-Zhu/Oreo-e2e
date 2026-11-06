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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


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
        // Each runs the action and waits for the request it sends and its whole response (ResponseWaiter.await):
        // HTTP 200 unless said otherwise.
        public void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            clickAndWaitForResponse(service.getLocator(locatorKey), urlFragment, httpMethod);
        }
        public void clickAndWaitForResponse(Locator locator, String urlFragment, String httpMethod) {
            responses.await(locator::click, new ResponseWaiter.Call(httpMethod, urlFragment));
        }
        public void doubleClickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            responses.await(service.getLocator(locatorKey)::dblclick, new ResponseWaiter.Call(httpMethod, urlFragment));
        }
        public void refreshAndWaitForResponse(String urlFragment, String httpMethod) {
            responses.await(() -> service.getPage().reload(), new ResponseWaiter.Call(httpMethod, urlFragment));
        }
        /**
         * Clicks {@code locatorKey}, waits for the {@code httpMethod urlFragment} response the click causes, then for the
         * {@code thenHttpMethod thenUrlFragment} response the page sends after it (a reload the UI does by itself), each
         * with the whole timeout; returns the first body.
         */
        public String clickAndWaitForResponses(String locatorKey, String urlFragment, String httpMethod,
                                               String thenUrlFragment, String thenHttpMethod) {
            return responses.await(service.getLocator(locatorKey)::click,
                    new ResponseWaiter.Call(httpMethod, urlFragment),
                    new ResponseWaiter.Call(thenHttpMethod, thenUrlFragment)).get(0).text();
        }
        /** Like {@link #clickAndWaitForResponse(String, String, String)}, accepting 200 or 201; returns the body. */
        public String clickAndCaptureResponse(String locatorKey, String urlFragment, String httpMethod) {
            return responses.await(service.getLocator(locatorKey)::click,
                    new ResponseWaiter.Call(httpMethod, urlFragment).orCreated()).get(0).text();
        }

        /**
         * A risk calculation an action causes: {@code httpMethod urlFragment} ({@code POST} unless given), answered in
         * MOCK mode with the response of setup file {@code mockSetup}; a null {@code mockSetup} lets the backend answer
         * in both modes.
         */
        public record RiskCall(String httpMethod, String urlFragment, String mockSetup) {
            public RiskCall(String urlFragment, String mockSetup) { this("POST", urlFragment, mockSetup); }
        }

        /** {@link #clickUsingRiskEngineMode(String, List)} for an action that causes one risk calculation. */
        public void clickUsingRiskEngineMode(String locatorKey, String riskUrl, String mockSetup) {
            clickUsingRiskEngineMode(locatorKey, List.of(new RiskCall(riskUrl, mockSetup)));
        }

        /**
         * Clicks {@code locatorKey} and waits for the risk calculations of {@code riskCalls} in their order, each sent
         * after the previous one was answered (a URL listed twice is awaited twice): REAL lets the risk engine answer,
         * MOCK answers each URL that has a setup file with it until all are answered.
         */
        public void clickUsingRiskEngineMode(String locatorKey, List<RiskCall> riskCalls) {
            OreoModuleConfiguration.RiskEngineMode mode = riskEngineMode();
            log.info("[PlaywrightServiceResolver] clicking '{}' using configured risk engine mode='{}', calls={}",
                    locatorKey, mode, riskCalls);
            Runnable click = () -> service.getLocator(locatorKey).click();
            ResponseWaiter.Call[] calls = riskCalls.stream()
                    .map(c -> new ResponseWaiter.Call(c.httpMethod(), c.urlFragment()))
                    .toArray(ResponseWaiter.Call[]::new);
            switch (mode) {
                case MOCK:
                    Map<String, String> mockByUrl = new LinkedHashMap<>();
                    riskCalls.stream().filter(c -> c.mockSetup() != null)
                            .forEach(c -> mockByUrl.putIfAbsent(c.urlFragment(), c.mockSetup()));
                    mockByUrl.forEach((url, setup) -> mocks.mockRoute(url, 200, TestData.setup(setup).response()));
                    try {
                        responses.await(click, calls);
                    } finally {
                        mockByUrl.keySet().forEach(mocks::clearMockRoute);
                    }
                    return;
                case REAL:
                    responses.await(click, calls);
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
        /** Polls until {@code locator} matches exactly {@code count} elements. */
        public void assertEventuallyCount(Locator locator, int count) {
            assertions.assertEventuallyCount(locator, count);
        }
        /**
         * Polls until the whole text of {@code locator} matches {@code pattern}, for at most {@code timeoutMillis}: a
         * step that proves a change within a time of its own, not within {@link #timeoutMillis()}.
         */
        public void assertEventuallyMatches(Locator locator, Pattern pattern, long timeoutMillis) {
            assertions.assertEventuallyMatches(locator, pattern, timeoutMillis);
        }
        /** Polls until the whole text of {@code locator} is not {@code text}, for at most {@code timeoutMillis}. */
        public void assertEventuallyNotText(Locator locator, String text, long timeoutMillis) {
            assertions.assertEventuallyNotText(locator, text, timeoutMillis);
        }
    }
}
