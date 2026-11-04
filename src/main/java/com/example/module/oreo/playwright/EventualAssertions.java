package com.example.module.oreo.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Locator assertions that poll until they hold or the timeout expires. */
final class EventualAssertions {

    private final LongSupplier timeoutMillis;

    EventualAssertions(LongSupplier timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    private long timeoutMillis() { return timeoutMillis.getAsLong(); }

    /**
     * <p>Unlike the framework's synchronous {@code assert that 'x' is visible} step,
     * this polls repeatedly until the condition is met or the timeout expires —
     * safe for async UI transitions and animations.
     *
     * @param locator the element to assert
     */
    void assertEventuallyVisible(Locator locator) { assertEventuallyVisible(locator, timeoutMillis()); }

    void assertEventuallyVisible(Locator locator, double timeoutMs) {
        PlaywrightAssertions.assertThat(locator).isVisible(
                new LocatorAssertions.IsVisibleOptions().setTimeout(timeoutMs));
    }

    /**
     * Asserts that {@code locator} is eventually hidden (or detached), with
     * Playwright's built-in polling (default timeout from {@code playwright.timeout}).
     *
     * <p>Unlike the framework's synchronous {@code assert that 'x' is not visible} step,
     * this polls repeatedly until the element disappears or the timeout expires —
     * safe for async UI transitions and animations.
     *
     * @param locator the element to assert
     */
    void assertEventuallyHidden(Locator locator) { assertEventuallyHidden(locator, timeoutMillis()); }

    void assertEventuallyHidden(Locator locator, double timeoutMs) {
        PlaywrightAssertions.assertThat(locator).isHidden(
                new LocatorAssertions.IsHiddenOptions().setTimeout(timeoutMs));
    }

    /**
     * Asserts that {@code locator} is eventually enabled, with Playwright's
     * built-in polling (default timeout from {@code playwright.timeout}).
     *
     * <p>Use this instead of the synchronous {@code element.isEnabled()} check
     * when the element may be temporarily disabled during async operations
     * (e.g. form submission in progress, data loading).
     *
     * @param locator the element to assert
     */
    void assertEventuallyEnabled(Locator locator) { assertEventuallyEnabled(locator, timeoutMillis()); }

    void assertEventuallyEnabled(Locator locator, double timeoutMs) {
        PlaywrightAssertions.assertThat(locator).isEnabled(
                new LocatorAssertions.IsEnabledOptions().setTimeout(timeoutMs));
    }

    /**
     * Asserts that {@code locator} is eventually disabled, with Playwright's
     * built-in polling (default timeout from {@code playwright.timeout}).
     *
     * <p>Use this instead of the synchronous {@code !element.isEnabled()} check
     * when the element may take time to become disabled after a UI state change
     * (e.g. read-only mode applied asynchronously).
     *
     * @param locator the element to assert
     */
    void assertEventuallyDisabled(Locator locator) { assertEventuallyDisabled(locator, timeoutMillis()); }

    void assertEventuallyDisabled(Locator locator, double timeoutMs) {
        PlaywrightAssertions.assertThat(locator).isDisabled(
                new LocatorAssertions.IsDisabledOptions().setTimeout(timeoutMs));
    }

    /**
     * Polls until the element's text content contains {@code text}, or the
     * configured timeout ({@code playwright.timeout}) expires.
     *
     * <p>Use this instead of the synchronous {@code assertElementContains} check
     * when the element text may update asynchronously (e.g. after a page reload
     * where Zustand hydrates from localStorage or DB in the background).
     *
     * @param locator the element to assert
     * @param text    the substring expected in the element's text content
     */
    void assertEventuallyContains(Locator locator, String text) {
        assertEventuallyContains(locator, text, timeoutMillis());
    }

    void assertEventuallyContains(Locator locator, String text, double timeoutMs) {
        if (text.startsWith("${json-unit.regex}")) {
            String regex = text.substring("${json-unit.regex}".length());
            PlaywrightAssertions.assertThat(locator).containsText(
                    Pattern.compile(regex),
                    new LocatorAssertions.ContainsTextOptions().setTimeout(timeoutMs));
        } else {
            PlaywrightAssertions.assertThat(locator).containsText(
                    text,
                    new LocatorAssertions.ContainsTextOptions().setTimeout(timeoutMs));
        }
    }

    /**
     * Polls until {@code locator} matches exactly {@code count} elements; {@code 0} waits until none is on the page,
     * whichever of several matching elements it is (unlike {@code isHidden}, which needs one element).
     */
    void assertEventuallyCount(Locator locator, int count) {
        PlaywrightAssertions.assertThat(locator).hasCount(count,
                new LocatorAssertions.HasCountOptions().setTimeout(timeoutMillis()));
    }

    void assertHasValue(Locator locator, String value) { assertHasValue(locator, value, timeoutMillis()); }

    void assertHasValue(Locator locator, String value, double timeoutMs) {
        PlaywrightAssertions.assertThat(locator).hasValue(
                value,
                new LocatorAssertions.HasValueOptions().setTimeout(timeoutMs));
    }
}
