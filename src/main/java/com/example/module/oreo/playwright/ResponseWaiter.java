package com.example.module.oreo.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.example.genie.module.playwright.core.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

/** Runs a UI action and waits for the API call it causes; a timeout or a wrong status fails with what was awaited. */
final class ResponseWaiter {

    private static final Logger log = LoggerFactory.getLogger(ResponseWaiter.class);
    private static final int BODY_IN_MESSAGE = 500;

    private final PlaywrightService service;
    private final LongSupplier timeoutMillis;

    ResponseWaiter(PlaywrightService service, LongSupplier timeoutMillis) {
        this.service = service;
        this.timeoutMillis = timeoutMillis;
    }

    private long timeoutMillis() { return timeoutMillis.getAsLong(); }

    void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod) {
        runAndWaitForResponse(action, urlFragment, httpMethod, timeoutMillis());
    }

    void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod, double timeoutMs) {
        Response response = awaitResponse(action, urlFragment, httpMethod, timeoutMs);
        if (response.status() != 200) {
            throw statusError(httpMethod + " " + urlFragment, "200", response);
        }
    }

    /**
     * Runs {@code action} and returns the response to the first request it sends whose URL contains
     * {@code urlFragment} and whose method is {@code httpMethod}, once its body has arrived. A matching request
     * already in flight before the action is not taken for it, and the wait does not end at the response headers.
     * A timeout fails with what was awaited, how long, the page URL and whether the action itself finished (a click
     * that never happened), the request was not sent, or it was not answered, instead of Playwright's bare
     * "Timeout exceeded".
     */
    private Response awaitResponse(Runnable action, String urlFragment, String httpMethod, double timeoutMs) {
        String awaited = httpMethod + " " + urlFragment;
        log.info("[ResponseWaiter] waiting up to {} ms for {}", (long) timeoutMs, awaited);
        boolean[] acted = {false};
        Request[] sent = {null};
        long start = System.nanoTime();
        Response response;
        try {
            response = service.getPage().waitForResponse(
                    r -> sent[0] != null && r.request() == sent[0],
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> sent[0] = service.getPage().waitForRequest(
                            r -> r.url().contains(urlFragment) && r.method().equals(httpMethod),
                            new Page.WaitForRequestOptions().setTimeout(timeoutMs),
                            () -> {
                                action.run();
                                acted[0] = true;
                            }
                    )
            );
        } catch (TimeoutError e) {
            String message = (!acted[0]
                    ? "The action before " + awaited + " did not finish within " + (long) timeoutMs + " ms: "
                            + firstLine(e.getMessage())
                    : sent[0] == null
                    ? "No " + awaited + " sent within " + (long) timeoutMs + " ms after the action"
                    : "No response to " + awaited + " within " + (long) timeoutMs + " ms after the action sent it"
                            + " (see the trace's Network tab)")
                    + ". Page: " + service.getPage().url();
            log.error("[ResponseWaiter] {}", message);
            throw new AssertionError(message, e);
        }
        String failure = response.finished();
        if (failure != null) {
            String message = "The body of " + awaited + " did not arrive: " + failure;
            log.error("[ResponseWaiter] {}", message);
            throw new AssertionError(message);
        }
        log.info("[ResponseWaiter] {} -> {} in {} ms ({})", awaited, response.status(),
                (System.nanoTime() - start) / 1_000_000, response.url());
        return response;
    }

    private static AssertionError statusError(String awaited, String expectedStatus, Response response) {
        String message = "Expected " + awaited + " to return " + expectedStatus + " but got " + response.status()
                + " from " + response.url() + ". Body: " + body(response);
        log.error("[ResponseWaiter] {}", message);
        return new AssertionError(message);
    }

    private static String body(Response response) {
        try {
            String text = response.text();
            return text.length() > BODY_IN_MESSAGE ? text.substring(0, BODY_IN_MESSAGE) + "..." : text;
        } catch (RuntimeException e) {
            return "(not readable: " + firstLine(e.getMessage()) + ")";
        }
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> service.getLocator(locatorKey).click(),
                urlFragment,
                httpMethod
        );
    }

    void clickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod, double timeoutMs) {
        runAndWaitForResponse(
                () -> service.getLocator(locatorKey).click(),
                urlFragment,
                httpMethod,
                timeoutMs
        );
    }

    void clickAndWaitForResponse(Locator locator, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                locator::click,
                urlFragment,
                httpMethod
        );
    }

    void doubleClickAndWaitForResponse(String locatorKey, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> service.getLocator(locatorKey).dblclick(),
                urlFragment,
                httpMethod
        );
    }

    void fillAndWaitForResponse(String locatorKey, String value, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> service.getLocator(locatorKey).fill(value),
                urlFragment,
                httpMethod
        );
    }

    void fillAndWaitForResponse(Locator locator, String value, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> locator.fill(value),
                urlFragment,
                httpMethod
        );
    }

    void fillInnerInputAndWaitForResponse(String locatorKey, String value, String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> ElementActions.innerInput(service.getLocator(locatorKey)).fill(value),
                urlFragment,
                httpMethod
        );
    }

    void refreshAndWaitForResponse(String urlFragment, String httpMethod) {
        runAndWaitForResponse(
                () -> service.getPage().reload(),
                urlFragment,
                httpMethod
        );
    }

    /**
     * Clicks {@code locatorKey}, waits for the specified API response, asserts HTTP 200,
     * and returns the full response body as a string for snapshot comparison.
     *
     * <p>Identical contract to {@link #clickAndWaitForResponse} except the response body
     * is returned instead of discarded — use this when callers need to diff before/after states.
     *
     * @param locatorKey  element repository key of the element to click
     * @param urlFragment URL substring to match
     * @param httpMethod  HTTP method to match
     * @return response body as a UTF-8 string
     */
    String clickAndCaptureResponse(String locatorKey, String urlFragment, String httpMethod) {
        Response response = awaitResponse(() -> service.getLocator(locatorKey).click(), urlFragment, httpMethod,
                timeoutMillis());
        int status = response.status();
        if (!(status == 200 || status == 201)) {
            throw statusError(httpMethod + " " + urlFragment, "2xx", response);
        }
        return response.text();
    }

    /** Runs {@code action}, asserts HTTP 200 on the response and returns its body. */
    String runAndCaptureResponse(Runnable action, String urlFragment, String httpMethod) {
        Response response = awaitResponse(action, urlFragment, httpMethod, timeoutMillis());
        if (response.status() != 200) {
            throw statusError(httpMethod + " " + urlFragment, "200", response);
        }
        return response.text();
    }

    Request clickAndCaptureRequest(String locatorKey, String urlFragment, String httpMethod) {
        return service.getPage().waitForRequest(
                r -> r.url().contains(urlFragment) && r.method().equals(httpMethod),
                new Page.WaitForRequestOptions().setTimeout(timeoutMillis()),
                () -> service.getLocator(locatorKey).click()
        );
    }
}
