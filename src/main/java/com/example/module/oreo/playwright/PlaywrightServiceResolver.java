package com.example.module.oreo.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.example.genie.model.GenieScenario;
import com.example.genie.module.playwright.core.PlaywrightService;
import com.example.module.oreo.glue.ScenarioContext;
import com.example.module.oreo.config.OreoConfigurationResolver;
import com.example.module.oreo.config.OreoModuleConfiguration;
import com.example.module.oreo.testdata.TestData;
import cucumber.api.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            this.mocks = new RouteMocks(service, this::timeoutMillis);
            this.assertions = new EventualAssertions(this::timeoutMillis);
        }

        /**
         * The Playwright service for browser interaction.
         */
        public PlaywrightService service() { return service; }

        /**
         * Returns the configured risk engine mode, allowing a scenario attribute to override it.
         */
        public OreoModuleConfiguration.Mode riskEngineMode() {
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

        public void waitForFunction(String expression, Object arg) {
            waitForFunction(expression, arg, timeoutMillis());
        }

        public void waitForFunction(String expression, Object arg, double timeoutMs) {
            service.getPage().waitForFunction(
                    expression,
                    arg,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs)
            );
        }

        // ── Actions that wait for a response (ResponseWaiter) ──────────
        public void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod) {
            responses.runAndWaitForResponse(action, urlFragment, httpMethod);
        }
        public void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod, double timeoutMs) {
            responses.runAndWaitForResponse(action, urlFragment, httpMethod, timeoutMs);
        }
        public void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            responses.clickAndWaitForResponse(locatorKey, urlFragment, httpMethod);
        }
        public void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod, double timeoutMs) {
            responses.clickAndWaitForResponse(locatorKey, urlFragment, httpMethod, timeoutMs);
        }
        public void clickAndWaitForResponse(Locator locator, String urlFragment, String httpMethod) {
            responses.clickAndWaitForResponse(locator, urlFragment, httpMethod);
        }
        public void doubleClickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
            responses.doubleClickAndWaitForResponse(locatorKey, urlFragment, httpMethod);
        }
        public void fillAndWaitForResponse(String locatorKey, String value, String urlFragment, String httpMethod) {
            responses.fillAndWaitForResponse(locatorKey, value, urlFragment, httpMethod);
        }
        public void fillAndWaitForResponse(Locator locator, String value, String urlFragment, String httpMethod) {
            responses.fillAndWaitForResponse(locator, value, urlFragment, httpMethod);
        }
        public void fillInnerInputAndWaitForResponse(String locatorKey, String value, String urlFragment, String httpMethod) {
            responses.fillInnerInputAndWaitForResponse(locatorKey, value, urlFragment, httpMethod);
        }
        public void refreshAndWaitForResponse(String urlFragment, String httpMethod) {
            responses.refreshAndWaitForResponse(urlFragment, httpMethod);
        }
        public String clickAndCaptureResponse(String locatorKey, String urlFragment, String httpMethod) {
            return responses.clickAndCaptureResponse(locatorKey, urlFragment, httpMethod);
        }
        public Request clickAndCaptureRequest(String locatorKey, String urlFragment, String httpMethod) {
            return responses.clickAndCaptureRequest(locatorKey, urlFragment, httpMethod);
        }

        /**
         * Navigates to {@code path} (relative to the configured base URL), waits for the
         * specified API response, asserts HTTP 200, and returns the response body.
         *
         * @param path        path relative to base URL, e.g. {@code "/trade/TRD-001"}
         * @param urlFragment URL substring to match (e.g. {@code "/api/v1/trades/TRD-001"})
         * @param httpMethod  HTTP method to match (e.g. {@code "GET"})
         * @return response body as a UTF-8 string
         */
        public String navigateAndCaptureResponse(String path, String urlFragment, String httpMethod) {
            return responses.runAndCaptureResponse(() -> navigateToPath(path), urlFragment, httpMethod);
        }

        // ── Mock routes (RouteMocks) ───────────────────────────────────
        public void mockRoute(String urlFragment, int status, String responseBody) {
            mocks.mockRoute(urlFragment, status, responseBody);
        }
        public void mockRoute(String urlFragment, int status, String responseBody, String contentType) {
            mocks.mockRoute(urlFragment, status, responseBody, contentType);
        }
        public void mockRouteWithDelay(String urlFragment, int status, String responseBody) {
            mocks.mockRouteWithDelay(urlFragment, status, responseBody);
        }
        public void mockRouteWithDelay(String urlFragment, int status, String responseBody, String contentType) {
            mocks.mockRouteWithDelay(urlFragment, status, responseBody, contentType);
        }
        public void clearMockRoute(String urlFragment) { mocks.clearMockRoute(urlFragment); }

        public void clickWithMockedResponse(String locatorKey, String urlFragment,
                                            String httpMethod, int status, String responseBody) {
            mocks.clickWithMockedResponse(locatorKey, urlFragment, httpMethod, status, responseBody);
        }

        /**
         * Clicks {@code locatorKey} and waits for the risk calculation {@code POST riskUrl}: REAL lets the risk engine
         * answer, MOCK answers with the response of setup file {@code mockSetup}.
         */
        public void clickUsingRiskEngineMode(String locatorKey, String riskUrl, String mockSetup) {
            OreoModuleConfiguration.Mode mode = riskEngineMode();
            log.info("[PlaywrightServiceResolver] clicking '{}' using configured risk engine mode='{}', url='{}'",
                    locatorKey, mode, riskUrl);
            Runnable click = () -> service.getLocator(locatorKey).click();
            switch (mode) {
                case MOCK:
                    mockRoute(riskUrl, 200, TestData.setup(mockSetup).response());
                    try {
                        runAndWaitForResponse(click, riskUrl, "POST");
                    } finally {
                        clearMockRoute(riskUrl);
                    }
                    return;
                case REAL:
                    runAndWaitForResponse(click, riskUrl, "POST");
                    return;
                default:
                    throw new IllegalStateException("Unhandled risk engine mode: " + mode);
            }
        }

        // ── Assertions (EventualAssertions) ────────────────────────────
        public void assertEventuallyVisible(Locator locator) { assertions.assertEventuallyVisible(locator); }
        public void assertEventuallyVisible(Locator locator, double timeoutMs) {
            assertions.assertEventuallyVisible(locator, timeoutMs);
        }
        public void assertEventuallyHidden(Locator locator) { assertions.assertEventuallyHidden(locator); }
        public void assertEventuallyHidden(Locator locator, double timeoutMs) {
            assertions.assertEventuallyHidden(locator, timeoutMs);
        }
        public void assertEventuallyEnabled(Locator locator) { assertions.assertEventuallyEnabled(locator); }
        public void assertEventuallyEnabled(Locator locator, double timeoutMs) {
            assertions.assertEventuallyEnabled(locator, timeoutMs);
        }
        public void assertEventuallyDisabled(Locator locator) { assertions.assertEventuallyDisabled(locator); }
        public void assertEventuallyDisabled(Locator locator, double timeoutMs) {
            assertions.assertEventuallyDisabled(locator, timeoutMs);
        }
        public void assertEventuallyContains(Locator locator, String text) {
            assertions.assertEventuallyContains(locator, text);
        }
        public void assertEventuallyContains(Locator locator, String text, double timeoutMs) {
            assertions.assertEventuallyContains(locator, text, timeoutMs);
        }
        public void assertHasValue(Locator locator, String value) { assertions.assertHasValue(locator, value); }
        public void assertHasValue(Locator locator, String value, double timeoutMs) {
            assertions.assertHasValue(locator, value, timeoutMs);
        }
    }
}
