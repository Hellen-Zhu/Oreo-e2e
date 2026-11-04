package com.example.module.oreo.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.example.genie.module.playwright.core.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Runs a UI action and waits for the API call it causes: the first matching request sent after the action started,
 * then that request's response with its whole body; optionally then a follow-up call the page sends once the first is
 * answered (a reload). A matching request already in flight before the action is not taken for it. Each call has the
 * whole timeout. A timeout or a wrong status fails with what was awaited instead of Playwright's bare
 * "Timeout exceeded".
 */
final class ResponseWaiter {

    private static final Logger log = LoggerFactory.getLogger(ResponseWaiter.class);
    private static final int BODY_IN_MESSAGE = 500;

    /** An API call the action is expected to cause: a request whose URL contains {@code urlFragment}. */
    record Call(String httpMethod, String urlFragment) {
        boolean matches(Request request) {
            return request.url().contains(urlFragment) && request.method().equals(httpMethod);
        }

        @Override
        public String toString() { return httpMethod + " " + urlFragment; }
    }

    private final PlaywrightService service;
    private final LongSupplier timeoutMillis;

    ResponseWaiter(PlaywrightService service, LongSupplier timeoutMillis) {
        this.service = service;
        this.timeoutMillis = timeoutMillis;
    }

    /** Runs {@code action} and asserts its response is HTTP 200. */
    void runAndWaitForResponse(Runnable action, String urlFragment, String httpMethod) {
        Call call = new Call(httpMethod, urlFragment);
        expectStatus200(call, awaitResponses(action, call, null)[0]);
    }

    /** Runs {@code action}, asserts its response is HTTP 200 or 201 and returns the body. */
    String runAndCaptureResponse(Runnable action, String urlFragment, String httpMethod) {
        Call call = new Call(httpMethod, urlFragment);
        Response response = awaitResponses(action, call, null)[0];
        if (response.status() != 200 && response.status() != 201) {
            throw statusError(call, "2xx", response);
        }
        return response.text();
    }

    /**
     * Runs {@code action}, waits for {@code first}, then for {@code then} sent after {@code first} was answered;
     * asserts HTTP 200 on both and returns the body of {@code first}.
     */
    String runAndWaitForResponseThen(Runnable action, Call first, Call then) {
        Response[] responses = awaitResponses(action, first, then);
        expectStatus200(first, responses[0]);
        expectStatus200(then, responses[1]);
        return responses[0].text();
    }

    /**
     * Listeners registered before the action record the requests as their events arrive (a request event always comes
     * before its response event, so a fast or mocked response is not missed) and their responses. {@code then} may be
     * {@code null}.
     */
    private Response[] awaitResponses(Runnable action, Call first, Call then) {
        long timeoutMs = timeoutMillis.getAsLong();
        Page page = service.getPage();
        log.info("[ResponseWaiter] waiting up to {} ms for {}{}", timeoutMs, first, then == null ? "" : ", then " + then);
        Request[] sent = new Request[2];
        Response[] answered = new Response[2];
        Consumer<Request> recordRequest = r -> {
            if (sent[0] == null) {
                if (first.matches(r)) sent[0] = r;
            } else if (then != null && sent[1] == null && answered[0] != null && then.matches(r)) {
                sent[1] = r;
            }
        };
        Consumer<Response> recordResponse = r -> {
            if (answered[0] == null && r.request() == sent[0]) {
                answered[0] = r;
            } else if (sent[1] != null && answered[1] == null && r.request() == sent[1]) {
                answered[1] = r;
            }
        };
        long start = System.nanoTime();
        page.onRequest(recordRequest);
        page.onResponse(recordResponse);
        try {
            try {
                action.run();
                waitUntil(page, () -> answered[0] != null, timeoutMs);
            } catch (TimeoutError e) {
                throw timedOut(first, sent[0], null, timeoutMs, e);
            }
            if (then != null) {
                try {
                    waitUntil(page, () -> answered[1] != null, timeoutMs);
                } catch (TimeoutError e) {
                    throw timedOut(then, sent[1], first, timeoutMs, e);
                }
            }
        } finally {
            page.offRequest(recordRequest);
            page.offResponse(recordResponse);
        }
        expectBody(first, answered[0]);
        if (then != null) expectBody(then, answered[1]);
        log.info("[ResponseWaiter] {}{} answered in {} ms", first, then == null ? "" : ", then " + then,
                (System.nanoTime() - start) / 1_000_000);
        return answered;
    }

    private static void waitUntil(Page page, BooleanSupplier condition, long timeoutMs) {
        page.waitForCondition(condition, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
    }

    private AssertionError timedOut(Call call, Request sent, Call after, long timeoutMs, TimeoutError e) {
        String message = (sent != null
                ? "No response to " + call + " within " + timeoutMs + " ms after it was sent"
                        + " (see the trace's Network tab)"
                : after != null
                ? "No " + call + " sent within " + timeoutMs + " ms after " + after + " was answered"
                : "No " + call + " sent within " + timeoutMs + " ms (" + firstLine(e.getMessage()) + ")")
                + ". Page: " + service.getPage().url();
        log.error("[ResponseWaiter] {}", message);
        return new AssertionError(message, e);
    }

    private static void expectBody(Call call, Response response) {
        String failure = response.finished();
        if (failure != null) {
            String message = "The body of " + call + " did not arrive: " + failure;
            log.error("[ResponseWaiter] {}", message);
            throw new AssertionError(message);
        }
        log.info("[ResponseWaiter] {} -> {} ({})", call, response.status(), response.url());
    }

    private static void expectStatus200(Call call, Response response) {
        if (response.status() != 200) {
            throw statusError(call, "200", response);
        }
    }

    private static AssertionError statusError(Call call, String expectedStatus, Response response) {
        String message = "Expected " + call + " to return " + expectedStatus + " but got " + response.status()
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
