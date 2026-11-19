package com.example.module.oreo.glue.ui;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.utils.RestApiUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.playwright.PlaywrightServiceResolver.Context.RiskCall;
import com.microsoft.playwright.Locator;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class TradeLifecycleEventSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeLifecycleEventSteps.class);
    private PlaywrightServiceResolver.Context ctx;
    private RequestResponseHelper helper;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

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

    // ── Event amounts from the trade's notional ───────────────────

    /**
     * The notionals a partial novation remaining leaves, from the trade's own notional ({@code basic.notionalAmount},
     * read through the API) and the remaining notional the case gives: stores {@code originalNotional},
     * {@code newNotional} (original - remaining: the novated trade's) and {@code equationLineValue} (the event form's
     * line, {@code Notional: <original> - <remaining> = <new>}), each as the portal shows amounts ({@code 69,000.00}:
     * en-US, the preference maker and checker sign in with).
     *
     * <pre>When the novated notional of trade 'createdTradeId' is computed from the remaining notional 'remainingNotional'</pre>
     */
    @Given("^the novated notional of trade '([^']*)' is computed from the remaining notional '([^']*)'$")
    public void computeNovatedNotional(String tradeVar, String remainingVar) {
        String tradeId = ctx.find(tradeVar);
        BigDecimal original = tradeNotional(tradeId);
        Object remainingValue = ctx.find(remainingVar);
        if (remainingValue == null) {
            throw new AssertionError("[TradeLifecycleEventSteps] no '" + remainingVar + "' in the case data of "
                    + ctx.find("_tc.key"));
        }
        BigDecimal remaining = new BigDecimal(String.valueOf(remainingValue).replace(",", "").trim());
        BigDecimal novated = original.subtract(remaining);
        ctx.set("originalNotional", amount(original));
        ctx.set("newNotional", amount(novated));
        ctx.set("equationLineValue", "Notional: " + amount(original) + " - " + amount(remaining) + " = "
                + amount(novated));
        ctx.write("[notional] trade " + tradeId + ": " + amount(original) + " - " + amount(remaining) + " = "
                + amount(novated));
    }

    /**
     * An amount of the case data that may be a share of the trade's notional: {@code '<shareVar>'} is {@code "50%"}
     * (computed from the trade's {@code basic.notionalAmount}, read through the API, rounded to the cent) or an amount
     * as it is ({@code "5000"}). Stores it as {@code '<amountVar>'}, as typed into a form ({@code 3468.75}), and as
     * {@code '<amountVar>.display'}, as the portal shows it ({@code 3,468.75}).
     *
     * <p>A case whose data has no {@code '<shareVar>'} (or a blank one) stores nothing.
     *
     * <pre>When the share 'newNotional' of the notional of trade 'createdTradeId' is stored as 'newNotionalAmount'</pre>
     */
    @Given("^the share '([^']*)' of the notional of trade '([^']*)' is stored as '([^']*)'$")
    public void storeShareOfNotional(String shareVar, String tradeVar, String amountVar) {
        Object given = ctx.find(shareVar);
        if (given == null || String.valueOf(given).isBlank()) {
            ctx.write("[notional] no '" + shareVar + "' in the case data: '" + amountVar + "' not stored");
            return;
        }
        String text = String.valueOf(given).trim();
        BigDecimal amount;
        if (text.endsWith("%")) {
            String tradeId = ctx.find(tradeVar);
            BigDecimal percent = new BigDecimal(text.substring(0, text.length() - 1).trim());
            amount = tradeNotional(tradeId).multiply(percent).divide(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            ctx.write("[notional] " + text + " of trade " + tradeId + " = " + amount(amount));
        } else {
            amount = new BigDecimal(text.replace(",", ""));
        }
        ctx.set(amountVar, amount.stripTrailingZeros().toPlainString());
        ctx.set(amountVar + ".display", amount(amount));
    }

    /** The trade's {@code data.trade.basic.notionalAmount}, read through the API as the maker. */
    private BigDecimal tradeNotional(String tradeId) {
        JsonNode notional = RestApiUtil.get(helper, TradeApiClient.tradePath(tradeId), TestUser.MAKER.email())
                .path("data").path("trade").path("basic").path("notionalAmount");
        if (!notional.isNumber()) {
            throw new AssertionError("[TradeLifecycleEventSteps] trade " + tradeId
                    + " has no numeric data.trade.basic.notionalAmount: " + notional);
        }
        return notional.decimalValue();
    }

    /** An amount as the portal shows it with the en-US number format: grouped, two decimals. */
    static String amount(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
