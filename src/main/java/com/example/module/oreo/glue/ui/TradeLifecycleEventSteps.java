package com.example.module.oreo.glue.ui;

import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeLifecycleEventSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeLifecycleEventSteps.class);
    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    // ── Amendment steps ─────────────────────────────────────────────

    /**
     * React controlled {@code <input type="date">} ignores Playwright {@code fill()} — the save
     * button's {@code hasChanges} state stays false. {@link PlaywrightServiceResolver.Context#fillDateInput}
     * handles the workaround.
     */
    @When("^fill date field '([^']*)' with stored variable '([^']*)'$")
    public void fillDateFieldWithStoredVariable(String elementKey, String ctxKey) {
        String value = ctx.get(ctxKey);
        log.info("[TradeLifecycleEventSteps] Filling date field '{}' with ctx['{}'] = '{}'", elementKey, ctxKey, value);
        ctx.fillDateInput(elementKey, value);
    }

    @When("^click amend using configured risk engine mode for trade stored as '([^']*)' from trade details page$")
    public void clickAmendUsingConfiguredRiskEngineModeFromTradeDetails(String ctxKey) {
        ctx.clickUsingRiskEngineMode("trade_detail.amend_btn",
                TradeApiClient.calculateRiskPath(ctx.get(ctxKey)), "risk-calc-success-mock");
    }

    /**
     * Clicks the Confirm button in the TradeChangeConfirmation dialog and waits for
     * {@code POST /trades/{tradeId}/update} to complete.
     */
    @When("^click amendment confirm and wait for trade update for trade stored as '([^']*)'$")
    public void clickAmendmentConfirmAndWaitForUpdate(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        log.info("[TradeLifecycleEventSteps] Clicking amendment confirm and waiting for update for trade='{}'", tradeId);
        ctx.clickAndWaitForResponse("trade_change_confirmation.confirm_btn", TradeApiClient.updatePath(tradeId), "POST");
    }

    @When("^confirm checker action on trade detail and wait for page ready$")
    public void confirmCheckerActionAndWait() {
        log.info("[TradeLifecycleEventSteps] Confirming checker action on trade detail; waiting for /checker/tasks/ response");
        ctx.clickAndWaitForResponse("trade_detail.checker_confirm_btn", "/checker/tasks/", "POST");
    }

    // ── Lifecycle event steps ───────────────────────────────────────

    /**
     * Clicks Confirm in any dynamic-action event form dialog and waits for the
     * calculate-risk POST to complete. Applies to PartialTermination, EarlyTermination,
     * PortfolioReassignment, or any other event that uses the dynamic-action dialog.
     * On success the TradeChangeConfirmation dialog opens automatically.
     */
    @When("^confirm dynamic action form using configured risk engine mode for trade stored as '([^']*)'$")
    public void confirmDynamicActionFormUsingConfiguredRiskEngineMode(String ctxKey) {
        ctx.clickUsingRiskEngineMode("dynamic_action.confirm_btn",
                TradeApiClient.calculateRiskPath(ctx.get(ctxKey)), "risk-calc-success-mock");
    }

    @When("^confirm dynamic action form using configured partial risk engine mode for trade stored as '([^']*)'$")
    public void confirmDynamicActionFormUsingConfiguredPartialRiskEngineMode(String ctxKey) {
        ctx.clickUsingRiskEngineMode("dynamic_action.confirm_btn",
                TradeApiClient.calculatePartialRiskPath(ctx.get(ctxKey)), "risk-calc-partial-success-mock");
    }

    /**
     * Clicks Confirm & Save in the TradeChangeConfirmation dialog and waits for the
     * trigger-event POST to complete. Used by any lifecycle event routed through
     * POST /trades/trigger-event (PartialTermination, EarlyTermination, PortfolioReassignment, etc.).
     */
    @When("^confirm lifecycle event trade change and wait for page ready$")
    public void confirmLifecycleEventTradeChangeAndWait() {
        log.info("[TradeLifecycleEventSteps] Confirming trade change confirmation; waiting for trigger-event response");
        ctx.clickAndWaitForResponse("trade_change_confirmation.confirm_btn", TradeApiClient.TRIGGER_EVENT_PATH, "POST");
    }

    /**
     * Clicks Confirm & Save in the TradeChangeConfirmation dialog and follows what the page does: waits for
     * {@code POST /trades/trigger-event}, then for the blotter reload the page sends after it
     * ({@code POST /api/v1/blotter/trades}), then for blotter {@code blotterId} to finish refreshing.
     */
    @When("^confirm lifecycle event trade change and wait for blotter '([^']*)' to reload$")
    public void confirmLifecycleEventTradeChangeAndWaitForBlotterReload(String blotterId) {
        confirmTradeChangeAndWaitForBlotterReload(blotterId);
    }

    /** As above, and stores the trigger-event response body under {@code responseCtxKey}. */
    @When("^confirm lifecycle event trade change, capture response as '([^']*)' and wait for blotter '([^']*)' to reload$")
    public void confirmLifecycleEventTradeChangeCaptureAndWaitForBlotterReload(String responseCtxKey, String blotterId) {
        ctx.set(responseCtxKey, confirmTradeChangeAndWaitForBlotterReload(blotterId));
    }

    private String confirmTradeChangeAndWaitForBlotterReload(String blotterId) {
        return clickAndWaitForBlotterReload("trade_change_confirmation.confirm_btn", TradeApiClient.TRIGGER_EVENT_PATH,
                blotterId);
    }

    /**
     * Clicks Confirm in the checker action dialog opened from the blotter (approve or reject, one trade or bulk) and
     * follows what the page does: waits for {@code POST /checker/tasks/...}, then for the blotter reload the page
     * sends after it ({@code POST /api/v1/blotter/trades}), then for blotter {@code blotterId} to finish refreshing.
     */
    @When("^confirm checker action from the blotter and wait for blotter '([^']*)' to reload$")
    public void confirmCheckerActionAndWaitForBlotterReload(String blotterId) {
        clickAndWaitForBlotterReload("checker_action.confirm_btn", "/checker/tasks/", blotterId);
    }

    /** Clicks {@code locatorKey}, waits for {@code POST urlFragment} and the blotter reload after it; returns the first body. */
    private String clickAndWaitForBlotterReload(String locatorKey, String urlFragment, String blotterId) {
        String response = ctx.clickAndWaitForResponses(locatorKey, urlFragment, "POST", "/api/v1/blotter/trades", "POST");
        ctx.assertEventuallyHidden(ctx.getLocatorWithParam("trade_portal.blotter_view_refreshing_state", blotterId));
        return response;
    }

    /**
     * Clicks Confirm & Save in the TradeChangeConfirmation dialog, waits for
     * {@code POST /trades/trigger-event} to complete, and stores the response body
     * in scenario context under {@code responseCtxKey}.
     */
    @When("^confirm lifecycle event trade change and capture response as '([^']*)'$")
    public void confirmLifecycleEventTradeChangeAndCaptureResponse(String responseCtxKey) {
        log.info("[TradeLifecycleEventSteps] Confirming trade change confirmation; capturing trigger-event response as '{}'", responseCtxKey);
        String responseBody = ctx.clickAndCaptureResponse(
                "trade_change_confirmation.confirm_btn",
                TradeApiClient.TRIGGER_EVENT_PATH,
                "POST"
        );
        ctx.set(responseCtxKey, responseBody);
    }
}
