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
import java.util.stream.Collectors;

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

    /**
     * An API call the action is expected to cause: a request whose URL contains {@code urlFragment}, answered with one
     * of {@code statuses} (200 unless {@link #orCreated()}).
     */
    record Call(String httpMethod, String urlFragment, List<Integer> statuses) {
        Call(String httpMethod, String urlFragment) { this(httpMethod, urlFragment, List.of(200)); }

        /** This call, also accepting 201 Created. */
        Call orCreated() { return new Call(httpMethod, urlFragment, List.of(200, 201)); }

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

    /**
     * Runs {@code action}, waits for {@code calls} in order and asserts each status; returns the responses in the
     * order of {@code calls}. Each call is the first matching request sent after the previous one was answered (so a
     * reload is told apart from an older request still in flight), each up to the whole timeout.
     */
    List<Response> await(Runnable action, Call... calls) {
        return await(action, List.of(calls), true);
    }

    /**
     * Like {@link #await(Runnable, Call...)}, in any order: each request sent after the action started is taken by the
     * first of {@code calls} it matches and that has no request yet, even while an earlier one is still unanswered. For
     * calls the page may send together, such as the risk calculations of a lifecycle event.
     */
    List<Response> awaitInAnyOrder(Runnable action, Call... calls) {
        return await(action, List.of(calls), false);
    }

    /**
     * Listeners registered before the action record the requests as their events arrive (a request event always comes
     * before its response event, so a fast or mocked response is not missed) and their responses.
     */
    private List<Response> await(Runnable action, List<Call> calls, boolean ordered) {
        int count = calls.size();
        long timeoutMs = timeoutMillis.getAsLong();
        Page page = service.getPage();
        log.info("[ResponseWaiter] waiting up to {} ms for each of {}{}", timeoutMs, calls, ordered ? "" : ", in any order");
        Request[] sent = new Request[count];
        Response[] answered = new Response[count];
        Consumer<Request> recordRequest = r -> {
            if (ordered) {
                int next = 0;
                while (next < count && sent[next] != null) next++;
                if (next < count && (next == 0 || answered[next - 1] != null) && calls.get(next).matches(r)) {
                    sent[next] = r;
                }
                return;
            }
            for (int i = 0; i < count; i++) {
                if (sent[i] == null && calls.get(i).matches(r)) {
                    sent[i] = r;
                    return;
                }
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
            try {
                action.run();
            } catch (TimeoutError e) {
                throw timedOut(calls.get(0), null, null, timeoutMs, e);
            }
            for (int i = 0; i < count; i++) {
                int done = i + 1;
                try {
                    waitUntil(page, () -> answeredCount(answered) >= done, timeoutMs);
                } catch (TimeoutError e) {
                    int call = ordered ? i : firstUnanswered(answered);
                    Call after = ordered && call > 0 ? calls.get(call - 1) : null;
                    throw timedOut(calls.get(call), sent[call], after, timeoutMs, e);
                }
            }
        } finally {
            page.offRequest(recordRequest);
            page.offResponse(recordResponse);
        }
        for (int i = 0; i < count; i++) {
            expectBody(calls.get(i), answered[i]);
            expectStatus(calls.get(i), answered[i]);
        }
        log.info("[ResponseWaiter] {} answered in {} ms", calls, (System.nanoTime() - start) / 1_000_000);
        return List.of(answered);
    }

    private static int answeredCount(Response[] answered) {
        int n = 0;
        for (Response r : answered) if (r != null) n++;
        return n;
    }

    private static int firstUnanswered(Response[] answered) {
        for (int i = 0; i < answered.length; i++) if (answered[i] == null) return i;
        return answered.length - 1;
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

    private static void expectStatus(Call call, Response response) {
        if (call.statuses().contains(response.status())) return;
        String expected = call.statuses().stream().map(String::valueOf).collect(Collectors.joining(" or "));
        String message = "Expected " + call + " to return " + expected + " but got " + response.status()
                + " from " + response.url() + ". Body: " + body(response);
        log.error("[ResponseWaiter] {}", message);
        throw new AssertionError(message);
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
