package com.example.module.oreo.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.example.genie.module.playwright.core.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

/**
 * Runs a UI action and waits for the API call it causes: the first request the action sends whose URL contains
 * {@code urlFragment} and whose method is {@code httpMethod}, then that request's response with its whole body. A
 * matching request already in flight before the action is not taken for it. A timeout or a wrong status fails with
 * what was awaited instead of Playwright's bare "Timeout exceeded".
 */
final class ResponseWaiter {

    private static final Logger log = LoggerFactory.getLogger(ResponseWaiter.class);
    private static final int BODY_IN_MESSAGE = 500;

    private final PlaywrightService service;
    private final LongSupplier timeoutMillis;

    ResponseWaiter(PlaywrightService service, LongSupplier timeoutMillis) {
        this.service = service;
        this.timeoutMillis = timeoutMillis;
    }

    /** Runs {@code action} and asserts its response is HTTP 200. */
    void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod) {
        Response response = awaitResponse(action, urlFragment, httpMethod);
        if (response.status() != 200) {
            throw statusError(httpMethod + " " + urlFragment, "200", response);
        }
    }

    /** Runs {@code action}, asserts its response is HTTP 200 or 201 and returns the body. */
    String runAndCaptureResponse(Runnable action, String urlFragment, String httpMethod) {
        Response response = awaitResponse(action, urlFragment, httpMethod);
        if (response.status() != 200 && response.status() != 201) {
            throw statusError(httpMethod + " " + urlFragment, "2xx", response);
        }
        return response.text();
    }

    /**
     * The inner wait finds the request the action sends; the outer one, which shares the same timeout, takes only
     * that request's response.
     */
    private Response awaitResponse(Runnable action, String urlFragment, String httpMethod) {
        String awaited = httpMethod + " " + urlFragment;
        long timeoutMs = timeoutMillis.getAsLong();
        Page page = service.getPage();
        log.info("[ResponseWaiter] waiting up to {} ms for {}", timeoutMs, awaited);
        Request[] sent = {null};
        long start = System.nanoTime();
        Response response;
        try {
            response = page.waitForResponse(
                    r -> r.request() == sent[0],
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> sent[0] = page.waitForRequest(
                            r -> r.url().contains(urlFragment) && r.method().equals(httpMethod),
                            new Page.WaitForRequestOptions().setTimeout(timeoutMs),
                            action));
        } catch (TimeoutError e) {
            String message = (sent[0] == null
                    ? "No " + awaited + " sent within " + timeoutMs + " ms (" + firstLine(e.getMessage()) + ")"
                    : "No response to " + awaited + " within " + timeoutMs + " ms after it was sent"
                            + " (see the trace's Network tab)")
                    + ". Page: " + page.url();
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
}
