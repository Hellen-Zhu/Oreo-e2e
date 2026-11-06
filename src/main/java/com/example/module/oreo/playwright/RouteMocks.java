package com.example.module.oreo.playwright;

import com.microsoft.playwright.Route;
import com.example.genie.module.playwright.core.PlaywrightService;

/** Answers browser requests without the backend. */
final class RouteMocks {

    private final PlaywrightService service;

    RouteMocks(PlaywrightService service) {
        this.service = service;
    }

    /**
     * Answers every request whose URL contains {@code urlFragment} with {@code status} and the JSON
     * {@code responseBody}, without reaching the backend. Always pair with {@link #clearMockRoute(String)}.
     */
    void mockRoute(String urlFragment, int status, String responseBody) {
        service.getPage().route(
                "**" + urlFragment + "**",
                route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(status)
                        .setBody(responseBody)
                        .setContentType("application/json"))
        );
    }

    /**
     * {@link #mockRoute} for the rest of the scenario: registered on the browser context, so it answers every page of
     * the scenario and needs no clearing (the context closes with the scenario).
     */
    void mockRouteForScenario(String urlFragment, int status, String responseBody) {
        service.getPage().context().route(
                "**" + urlFragment + "**",
                route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(status)
                        .setBody(responseBody)
                        .setContentType("application/json"))
        );
    }

    /** Removes the mock routes registered for {@code urlFragment}. */
    void clearMockRoute(String urlFragment) { service.getPage().unroute("**" + urlFragment + "**"); }
}
