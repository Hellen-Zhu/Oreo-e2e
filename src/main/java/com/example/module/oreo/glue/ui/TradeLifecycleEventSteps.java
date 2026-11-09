package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.playwright.PlaywrightServiceResolver.Context.RiskCall;
import com.microsoft.playwright.Locator;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
        ctx.clickAndWaitForResponse("trade_detail.checker_confirm_btn", TradeApiClient.CHECKER_TASKS_PATH, "POST");
    }

    // ── Lifecycle event steps ───────────────────────────────────────

    /**
     * Clicks Cancellation, from the blotter row action menu or the trade detail More Actions menu, and waits for the
     * one {@code calculate-risk} it causes before the TradeChangeConfirmation dialog opens; MOCK answers it with setup
     * {@code risk-calc-success-mock}.
     */
    @When("^trigger cancellation for trade using configured risk engine mode from (trade portal|trade detail page) for trade stored as '([^']*)'$")
    public void triggerCancellationUsingConfiguredRiskEngineMode(String from, String ctxKey) {
        String button = from.equals("trade portal") ? "trade_portal.row_action_cancel" : "trade_detail.cancel_btn";
        ctx.clickUsingRiskEngineMode(button, List.of(calculateRisk(ctx.get(ctxKey))));
    }

    /**
     * Opens the Novation Remaining form from the blotter row action and waits for {@code GET /trades/<id>/target-gain},
     * which the form sends while it loads; MOCK answers it with setup {@code target-gain-success-mock}.
     */
    @When("^click novation remaining action using configured risk engine mode for trade stored as '([^']*)'$")
    public void clickNovationRemainingActionUsingConfiguredRiskEngineMode(String ctxKey) {
        ctx.clickUsingRiskEngineMode("trade_portal.row_action_novationremaining", List.of(
                new RiskCall("GET", TradeApiClient.targetGainPath(ctx.get(ctxKey)), "target-gain-success-mock")));
    }

    /** The number of the case's sub-trades ({@code subTrades} of its data): the allocation dialog's split number. */
    @When("^the number of sub-trades of the case is stored as '([^']*)'$")
    public void storeSubTradeCount(String key) {
        int legs = caseSubTrades().size();
        ctx.set(key, String.valueOf(legs));
        ctx.write("Split into " + legs + " sub-trades");
    }

    /**
     * Fills the sub-trade rows of the allocation dialog, which the split number has made, one per item of the case's
     * {@code subTrades}: the item's {@code counterpartyName} is selected and its {@code percentage} typed, each when
     * the item has it. The report shows each row.
     */
    @When("^user fills the sub-trade rows of the case$")
    public void fillSubTradeRows() {
        JsonNode subTrades = caseSubTrades();
        int legs = subTrades.size();
        Locator counterparties = ctx.service().getLocator("allocation_dialog.subtrade_counterparty_select");
        Locator percentages = ctx.service().getLocator("allocation_dialog.subtrade_percentage_input");
        for (int i = 0; i < legs; i++) {
            String counterparty = text(subTrades.get(i), "counterpartyName");
            String percentage = text(subTrades.get(i), "percentage");
            if (!counterparty.isEmpty()) {
                counterparties.nth(i).click();
                ctx.clickOption(counterparty);
            }
            if (!percentage.isEmpty()) {
                ctx.innerInput(percentages.nth(i)).fill(percentage);
            }
            ctx.write("Sub-trade " + (i + 1) + ": counterparty '" + counterparty + "', percentage '" + percentage + "'");
        }
    }

    /** The case's {@code subTrades}: one item per sub-trade, at least two. */
    private JsonNode caseSubTrades() {
        JsonNode subTrades = ctx.find("_tc.request.subTrades");
        if (subTrades == null || !subTrades.isArray() || subTrades.size() < 2) {
            throw new AssertionError("Case " + ctx.find("_tc.key") + " needs its 'subTrades' in its test data,"
                    + " one item per sub-trade (at least two), has: " + subTrades);
        }
        return subTrades;
    }

    private static String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }

    /**
     * Clicks Confirm in the allocation form split into the case's sub-trades and waits for
     * {@code calculate-allocation-risk}, then calculate-risk. The page rejects an allocation risk whose legs are not
     * those of the form ("Risk calculation failed"), so MOCK answers with the fixed answer of that many legs: setup
     * {@code risk-calc-allocation-<legs>-legs-success-mock}, which must exist for the case's number of sub-trades.
     */
    @When("^confirm allocation form using configured risk engine mode for trade stored as '([^']*)'$")
    public void confirmAllocationFormUsingConfiguredRiskEngineMode(String ctxKey) {
        int legs = caseSubTrades().size();
        String tradeId = ctx.get(ctxKey);
        ctx.clickUsingRiskEngineMode("dynamic_action.confirm_btn", List.of(
                new RiskCall(TradeApiClient.calculateAllocationRiskPath(tradeId),
                        "risk-calc-allocation-" + legs + "-legs-success-mock"),
                calculateRisk(tradeId)));
    }

    /**
     * Clicks Confirm in the dynamic-action form of {@code event} and waits for the risk calculations it causes, in
     * order, before the TradeChangeConfirmation dialog is used ({@link #riskCallsOf}). MOCK answers those it can.
     */
    @When("^confirm '(novation remaining|partial novation remaining|step out full|step out partial|early termination|partial termination|portfolio reassignment)' event form using configured risk engine mode for trade stored as '([^']*)'$")
    public void confirmEventFormUsingConfiguredRiskEngineMode(String event, String ctxKey) {
        ctx.clickUsingRiskEngineMode("dynamic_action.confirm_btn", riskCallsOf(event, ctx.get(ctxKey)));
    }

    /**
     * The risk calculations the Confirm of an event form causes, in the order the page sends them: each after the
     * previous one is answered (with a {@code GET /trades/<id>} between the event's own calculation and
     * calculate-risk). Novation remaining sends {@code target-gain} when its form opens, not on Confirm
     * ({@link #clickNovationRemainingActionUsingConfiguredRiskEngineMode}).
     */
    static List<RiskCall> riskCallsOf(String event, String tradeId) {
        RiskCall risk = calculateRisk(tradeId);
        return switch (event) {
            case "step out full", "step out partial", "early termination", "partial termination",
                 "portfolio reassignment", "novation remaining" -> List.of(risk, risk);
            case "partial novation remaining" -> List.of(
                    new RiskCall(TradeApiClient.calculatePartialRiskPath(tradeId), "risk-calc-partial-success-mock"), risk);
            default -> throw new IllegalArgumentException("No risk calculations known for event '" + event + "'");
        };
    }

    private static RiskCall calculateRisk(String tradeId) {
        return new RiskCall(TradeApiClient.calculateRiskPath(tradeId), "risk-calc-success-mock");
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
        clickAndWaitForBlotterReload("checker_action.confirm_btn", TradeApiClient.CHECKER_TASKS_PATH, blotterId);
    }

    /** Clicks {@code locatorKey}, waits for {@code POST urlFragment} and the blotter reload after it; returns the first body. */
    private String clickAndWaitForBlotterReload(String locatorKey, String urlFragment, String blotterId) {
        String response = ctx.clickAndWaitForResponses(locatorKey, urlFragment, "POST",
                TradeApiClient.BLOTTER_TRADES_PATH, "POST");
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
