package com.example.module.oreo.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.example.genie.module.playwright.core.PlaywrightService;

import java.util.function.LongSupplier;

/** Answers browser requests without the backend. */
final class RouteMocks {

    private final PlaywrightService service;
    private final LongSupplier timeoutMillis;

    RouteMocks(PlaywrightService service, LongSupplier timeoutMillis) {
        this.service = service;
        this.timeoutMillis = timeoutMillis;
    }

    private long timeoutMillis() { return timeoutMillis.getAsLong(); }

    /**
     * Registers a mock route that intercepts every request whose URL contains
     * {@code urlFragment} and fulfills it with {@code status} and a JSON body,
     * without ever reaching the real backend.
     *
     * <p>Always pair with {@link #clearMockRoute(String)} after the interaction:
     * <pre>
     *   ctx.mockRoute("/api/v1/trades", 200, "{\"id\":\"TRD-999\"}");
     *   ctx.clickAndWaitForResponse("btn-book", "/api/v1/trades", "POST");
     *   ctx.clearMockRoute("/api/v1/trades");
     * </pre>
     *
     * @param urlFragment  URL substring to intercept
     * @param status       HTTP status code to return (e.g. {@code 200}, {@code 422})
     * @param responseBody response body string (JSON recommended)
     */
    void mockRoute(String urlFragment, int status, String responseBody) {
        mockRoute(urlFragment, status, responseBody, "application/json");
    }

    /**
     * Registers a mock route that fulfills after {@code delayMillis}, allowing callers
     * to simulate slow backend responses without blocking the route handler thread.
     */
    void mockRouteWithDelay(String urlFragment, int status, String responseBody) {
        mockRouteWithDelay(urlFragment, status, responseBody, "application/json");
    }

    /**
     * Registers a mock route with an explicit {@code contentType}.
     * @param contentType  value for the {@code Content-Type} header
     */
    void mockRoute(String urlFragment, int status, String responseBody, String contentType) {
        service.getPage().route(
                "**" + urlFragment + "**",
                route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(status)
                        .setBody(responseBody)
                        .setContentType(contentType))
        );
    }

    /**
     * Registers a delayed mock route with an explicit {@code contentType}.
     */
    void mockRouteWithDelay(String urlFragment, int status, String responseBody,
                                   String contentType) {
        service.getPage().route(
                "**" + urlFragment + "**",
                route -> Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(timeoutMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    route.fulfill(new Route.FulfillOptions()
                            .setStatus(status)
                            .setBody(responseBody)
                            .setContentType(contentType));
                })
        );
    }

    /**
     * Removes all mock routes registered for {@code urlFragment}.
     *
     * <p>Call this after each test interaction to prevent leaking mocks into
     * subsequent scenarios.
     *
     * @param urlFragment URL substring passed to {@link #mockRoute}
     */
    void clearMockRoute(String urlFragment) { service.getPage().unroute("**" + urlFragment + "**"); }

    /**
     * Convenience method: registers a mock route, clicks {@code locatorKey},
     * waits for the mocked response to be received, then removes the mock.
     *
     * <p>Equivalent to calling {@link #mockRoute}, {@link #clickAndWaitForResponse},
     * and {@link #clearMockRoute} in sequence:
     * <pre>
     *   // Error scenario — no round-trip to the real backend
     *   ctx.clickWithMockedResponse("btn-book", "/api/v1/trades", "POST",
     *           422, "{\"error\":\"Limit breached\"}");
     *   ctx.assertEventuallyVisible(page.getByTestId("toast-error"));
     * </pre>
     *
     * @param locatorKey   element repository key of the element to click
     * @param urlFragment  URL substring to intercept and match
     * @param httpMethod   HTTP method to match (e.g. {@code "POST"})
     * @param status       HTTP status code the mock returns
     * @param responseBody response body the mock returns (JSON)
     */
    void clickWithMockedResponse(String locatorKey, String urlFragment,
                                        String httpMethod, int status, String responseBody) {
        mockRoute(urlFragment, status, responseBody);
        try {
            Response response = service.getPage().waitForResponse(
                    r -> r.url().contains(urlFragment) && r.request().method().equals(httpMethod),
                    new Page.WaitForResponseOptions().setTimeout(timeoutMillis()),
                    () -> service.getLocator(locatorKey).click()
            );
            if (response.status() != status) {
                throw new AssertionError(
                        "Mock returned unexpected status: expected " + status + " but got " + response.status());
            }
        } finally {
            clearMockRoute(urlFragment);
        }
    }
}
