package com.example.module.oreo.glue.ui;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ToastSteps {

    private static final Logger log = LoggerFactory.getLogger(ToastSteps.class);
    private static final String JSON_UNIT_REGEX_PREFIX = "${json-unit.regex}";
    private static final long POLL_INTERVAL_MILLIS = 200L;

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    /**
     * Starts recording toasts before the first step ({@link ToastRecorder}); order 20000 runs after the hooks with
     * the default order. Without a page yet, recording starts at the first toast assertion instead.
     */
    @Before(value = "@playwright", order = 20000)
    public void startRecordingToasts() {
        try {
            ctx.toastRecorder();
        } catch (RuntimeException e) {
            log.warn("[ToastSteps] toast recording not started before the first step: {}", e.toString());
            ctx.write("[toast] recording not started before the first step (" + e
                    + "); it starts at the first toast assertion");
        }
    }

    @Then("^assert toast notification with type '([^']*)' and message '([^']*)'$")
    public void assertToastNotificationWithTypeAndMessage(String expectedType, String expectedMessage) {
        assertToastField(expectedType, expectedMessage, ToastRecorder.Toast::description, "description");
    }

    @Then("^assert toast notification with type '([^']*)' and message from stored variable '([^']*)'$")
    public void assertToastNotificationWithTypeAndMessageFromStoredVariable(String expectedType, String messageVarName) {
        String expectedMessage = ctx.get(messageVarName);
        if (expectedMessage == null || expectedMessage.isBlank()) {
            throw new AssertionError(
                    "[ToastSteps] stored variable '" + messageVarName
                            + "' is null/blank — cannot assert toast notification description.");
        }
        assertToastField(expectedType, expectedMessage, ToastRecorder.Toast::description, "description");
    }

    @Then("^assert toast notification with type '([^']*)' and title as '([^']*)'$")
    public void assertToastNotificationWithTypeAndTitle(String expectedType, String expectedTitle) {
        assertToastField(expectedType, expectedTitle, ToastRecorder.Toast::title, "title");
    }

    @Then("^assert toast notification with type '([^']*)' and title from stored variable '([^']*)'$")
    public void assertToastNotificationWithTypeAndTitleFromStoredVariable(String expectedType, String titleVarName) {
        String expectedTitle = ctx.get(titleVarName);
        if (expectedTitle == null || expectedTitle.isBlank()) {
            throw new AssertionError(
                    "[ToastSteps] stored variable '" + titleVarName
                            + "' is null/blank — cannot assert toast notification title.");
        }
        assertToastField(expectedType, expectedTitle, ToastRecorder.Toast::title, "title");
    }

    /**
     * Passes when a toast with that type and field was shown in this scenario, before or during this step, and no
     * earlier assertion took it ({@link ToastRecorder}): the toast need not be on screen any more. Waits up to the
     * Playwright timeout for it. A toast with the expected field but another type fails at once.
     */
    private void assertToastField(String expectedType, String expectedValue,
                                  Function<ToastRecorder.Toast, String> field, String fieldName) {
        ToastRecorder recorder = ctx.toastRecorder();
        Predicate<ToastRecorder.Toast> fieldMatches =
                toast -> messageMatches(normalizeWhitespace(field.apply(toast)), expectedValue);

        long start = System.currentTimeMillis();
        long deadline = start + ctx.timeoutMillis();
        while (true) {
            Optional<ToastRecorder.Toast> matched =
                    recorder.take(fieldMatches.and(toast -> toast.type().contains(expectedType)));
            if (matched.isPresent()) {
                ctx.write("[toast] matched toast: " + matched.get());
                log.info("[ToastSteps] matched toast with type='{}' and {}='{}': {}",
                        expectedType, fieldName, expectedValue, matched.get());
                return;
            }

            Optional<ToastRecorder.Toast> otherType = recorder.find(fieldMatches);
            if (otherType.isPresent()) {
                throw new AssertionError(
                        "[ToastSteps] Found toast containing expected " + fieldName + ", but type mismatch."
                                + " expectedType='" + expectedType + "', actualType='" + otherType.get().type()
                                + "', toast: " + otherType.get());
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            // Not Thread.sleep: Playwright delivers the recorder's reports only while a Playwright call runs
            ctx.service().getPage().waitForTimeout(POLL_INTERVAL_MILLIS);
        }

        throw new AssertionError(
                "[ToastSteps] No toast with type='" + expectedType + "' and " + fieldName + " containing '"
                        + expectedValue + "' was shown in this scenario (waited from " + ToastRecorder.time(start)
                        + " to " + ToastRecorder.time(System.currentTimeMillis()) + "). Toasts recorded in this scenario: "
                        + recorder.describe());
    }

    private boolean messageMatches(String actualMessage, String expectedMessage) {
        if (expectedMessage.startsWith(JSON_UNIT_REGEX_PREFIX)) {
            String regex = normalizeWhitespace(expectedMessage.substring(JSON_UNIT_REGEX_PREFIX.length()));
            return Pattern.compile(regex).matcher(actualMessage).find();
        }

        String normalizedExpected = normalizeWhitespace(expectedMessage);
        return actualMessage.contains(normalizedExpected);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }
}
