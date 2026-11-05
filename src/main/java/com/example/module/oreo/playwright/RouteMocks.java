package com.example.module.oreo.playwright;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.example.genie.module.playwright.core.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/** Answers browser requests without the backend. */
final class RouteMocks {

    private static final Logger log = LoggerFactory.getLogger(RouteMocks.class);

    private final PlaywrightService service;

    RouteMocks(PlaywrightService service) {
        this.service = service;
    }

    /**
     * Answers every request whose URL contains {@code urlFragment} with {@code status} and the JSON
     * {@code responseBody}, without reaching the backend. Always pair with {@link #clearMockRoute(String)}.
     */
    void mockRoute(String urlFragment, int status, String responseBody) {
        mockRoute(urlFragment, status, request -> responseBody);
    }

    /**
     * Like {@link #mockRoute(String, int, String)}, with the body built from each request. When building it fails,
     * the request is answered 500 with the reason, so the step waiting for it fails with that message.
     */
    void mockRoute(String urlFragment, int status, Function<Request, String> responseBody) {
        service.getPage().route("**" + urlFragment + "**", route -> {
            int answerStatus = status;
            String body;
            try {
                body = responseBody.apply(route.request());
            } catch (RuntimeException e) {
                log.error("[RouteMocks] no mock answer for {}: {}", route.request().url(), e.getMessage(), e);
                answerStatus = 500;
                body = "{\"mockError\":" + quote(String.valueOf(e.getMessage())) + "}";
            }
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(answerStatus)
                    .setBody(body)
                    .setContentType("application/json"));
        });
    }

    /** Removes the mock routes registered for {@code urlFragment}. */
    void clearMockRoute(String urlFragment) { service.getPage().unroute("**" + urlFragment + "**"); }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
    }
}
