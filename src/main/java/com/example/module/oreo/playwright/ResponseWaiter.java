package com.example.module.oreo.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.example.genie.module.playwright.core.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Runs a UI action and waits for the API calls it causes, in order: for each call, the first matching request sent
 * after the previous call was answered (the first: after the action started), then that request's response with its
 * whole body. A matching request already in flight before the action is not taken for it. Each call has the whole
 * timeout, counted from the previous answer. A timeout or a wrong status fails with what was awaited instead of
 * Playwright's bare "Timeout exceeded".
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
        runAndWaitForResponses(action, List.of(new Call(httpMethod, urlFragment)));
    }

    /** Runs {@code action}, asserts its response is HTTP 200 or 201 and returns the body. */
    String runAndCaptureResponse(Runnable action, String urlFragment, String httpMethod) {
        Call call = new Call(httpMethod, urlFragment);
        Response response = awaitResponses(action, List.of(call)).get(0);
        if (response.status() != 200 && response.status() != 201) {
            throw statusError(call, "2xx", response);
        }
        return response.text();
    }

    /** Runs {@code action}, waits for {@code calls} in order and asserts each response is HTTP 200. */
    List<Response> runAndWaitForResponses(Runnable action, List<Call> calls) {
        List<Response> responses = awaitResponses(action, calls);
        for (int i = 0; i < calls.size(); i++) {
            expectStatus200(calls.get(i), responses.get(i));
        }
        return responses;
    }

    /**
     * Listeners registered before the action record the requests as their events arrive (a request event always comes
     * before its response event, so a fast or mocked response is not missed) and their responses; the call after
     * them is waited for once the previous one is answered, each up to the whole timeout.
     */
    private List<Response> awaitResponses(Runnable action, List<Call> calls) {
        int count = calls.size();
        long timeoutMs = timeoutMillis.getAsLong();
        Page page = service.getPage();
        log.info("[ResponseWaiter] waiting up to {} ms for each of {}", timeoutMs, calls);
        Request[] sent = new Request[count];
        Response[] answered = new Response[count];
        Consumer<Request> recordRequest = r -> {
            int next = 0;
            while (next < count && sent[next] != null) next++;
            if (next < count && (next == 0 || answered[next - 1] != null) && calls.get(next).matches(r)) {
                sent[next] = r;
            }
        };
        Consumer<Response> recordResponse = r -> {
            for (int i = 0; i < count; i++) {
                if (sent[i] != null && answered[i] == null && r.request() == sent[i]) {
                    answered[i] = r;
                }
            }
        };
        long start = System.nanoTime();
        page.onRequest(recordRequest);
        page.onResponse(recordResponse);
        try {
            for (int i = 0; i < count; i++) {
                int call = i;
                try {
                    if (call == 0) action.run();
                    waitUntil(page, () -> answered[call] != null, timeoutMs);
                } catch (TimeoutError e) {
                    throw timedOut(calls.get(call), sent[call], call == 0 ? null : calls.get(call - 1), timeoutMs, e);
                }
            }
        } finally {
            page.offRequest(recordRequest);
            page.offResponse(recordResponse);
        }
        for (int i = 0; i < count; i++) {
            expectBody(calls.get(i), answered[i]);
        }
        log.info("[ResponseWaiter] {} answered in {} ms", calls, (System.nanoTime() - start) / 1_000_000);
        return List.of(answered);
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
