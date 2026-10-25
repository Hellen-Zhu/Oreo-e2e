package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.RestApiUtil;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Step definitions for websocket notification UI feedback checks.
 *
 * <p>These steps capture notification-center baseline state and verify maker-side
 * feedback appears in a sensible time window with refresh-assisted checks.
 */
public class WebsocketNotificationSteps {

    private static final String NOTIFICATION_UNREAD_BADGE_BASELINE_CTX_KEY = "wsNotificationUnreadBadgeBaseline";
    private static final String NO_UNREAD_BADGE_VALUE = "0";
    private static final Pattern POSITIVE_UNREAD_BADGE_PATTERN = Pattern.compile("^[1-9]\\d*$");
    private static final double NOTIFICATION_UPDATE_TIMEOUT_MILLIS = 10_000D;

    private PlaywrightServiceResolver.Context ctx;
    private RequestResponseHelper helper;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    /** Marks checker notifications as read through RestApiUtil endpoint call. */
    @Given("^checker marks all notifications as read via api$")
    public void markAllNotificationsAsReadForChecker() {
        RestApiUtil.post(helper, "/notifications/read-all", "checker@example.com");
    }

    /** Captures baseline unread badge value before triggering checker action. */
    @Given("^notification counter is captured$")
    public void captureNotificationBaseline() {
        String unreadBadgeBaseline = readUnreadBadgeValueOrZero();
        ctx.set(NOTIFICATION_UNREAD_BADGE_BASELINE_CTX_KEY, unreadBadgeBaseline);
        ctx.write("[ws-notification] Captured unread badge baseline: " + unreadBadgeBaseline);
    }

    /** Verifies unread badge reflects new feedback within sensible timeout. */
    @Then("^notification counter bumps up$")
    public void notificationCenterReflectsNewFeedbackInSensibleTime() {
        String unreadBadgeBaseline = readNotificationUnreadBadgeBaseline();
        String unreadBadgeCurrent = waitUntilUnreadBadgeChangesFromBaseline(unreadBadgeBaseline);
        ctx.write("[ws-notification] Unread badge changed. baseline='" + unreadBadgeBaseline
                + "', current='" + unreadBadgeCurrent + "'.");
    }


    /** Waits until unread badge value changes from baseline and returns current unread badge value. */
    private String waitUntilUnreadBadgeChangesFromBaseline(String baselineUnreadBadge) {
        Locator unreadBadge = ctx.service().getLocator("trade_portal.notification_unread_badge");
        try {
            if (NO_UNREAD_BADGE_VALUE.equals(baselineUnreadBadge)) {
                PlaywrightAssertions.assertThat(unreadBadge).hasText(
                        POSITIVE_UNREAD_BADGE_PATTERN,
                        new LocatorAssertions.HasTextOptions().setTimeout(NOTIFICATION_UPDATE_TIMEOUT_MILLIS));
            } else {
                PlaywrightAssertions.assertThat(unreadBadge).not().hasText(
                        baselineUnreadBadge,
                        new LocatorAssertions.HasTextOptions().setTimeout(NOTIFICATION_UPDATE_TIMEOUT_MILLIS));
                PlaywrightAssertions.assertThat(unreadBadge).hasText(
                        POSITIVE_UNREAD_BADGE_PATTERN,
                        new LocatorAssertions.HasTextOptions().setTimeout(NOTIFICATION_UPDATE_TIMEOUT_MILLIS));
            }
            return readUnreadBadgeValueOrZero();
        } catch (AssertionError e) {
            String currentUnreadBadge = readUnreadBadgeValueOrZero();
            throw new AssertionError(
                    "[WebsocketNotificationSteps] Unread badge did not change in sensible time."
                            + " baselineUnread='" + baselineUnreadBadge + "'"
                            + ", currentUnread='" + currentUnreadBadge + "'"
                            + ", timeoutMs=" + (long) NOTIFICATION_UPDATE_TIMEOUT_MILLIS,
                    e);
        }
    }

    /** Reads unread badge value from UI; returns zero when badge is absent or blank. */
    private String readUnreadBadgeValueOrZero() {
        Locator unreadBadge = ctx.service().getLocator("trade_portal.notification_unread_badge");
        if (unreadBadge.count() == 0 || !unreadBadge.first().isVisible()) {
            return NO_UNREAD_BADGE_VALUE;
        }

        String value = normalizeWhitespace(unreadBadge.first().innerText());
        return value.isBlank() ? NO_UNREAD_BADGE_VALUE : value;
    }

    /** Reads baseline unread badge value from scenario context. */
    private String readNotificationUnreadBadgeBaseline() {
        String rawValue = ctx.get(NOTIFICATION_UNREAD_BADGE_BASELINE_CTX_KEY);
        return Optional.ofNullable(rawValue)
                .orElseThrow(() -> new AssertionError(
                        "[WebsocketNotificationSteps] baseline capture step must run before feedback assertion."));
    }

    /** Normalizes text for robust comparison and diagnostics. */
    private String normalizeWhitespace(String value) {
        return Optional.ofNullable(value).orElse("").replaceAll("\\s+", " ").trim();
    }
}
