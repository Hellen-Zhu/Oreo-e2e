package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.microsoft.playwright.Locator;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllocationSteps {

    private static final Logger log = LoggerFactory.getLogger(AllocationSteps.class);

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
    }

    // ── Confirm allocation + wait for trades API ────────────────────────

    /**
     * Clicks the confirm button on the trade-change-confirmation dialog and waits for
     * {@code POST /trades/trigger-event} to return HTTP 200. The body is not kept.
     */
    @When("^user confirms allocation and waits for allocation response$")
    public void confirmAllocationAndWaitForResponse() {
        ctx.clickAndWaitForResponse(
                "trade_change_confirmation.confirm_btn", TradeApiClient.TRIGGER_EVENT_PATH, "POST");
        log.info("[AllocationSteps] Allocation confirmed — POST /trades/trigger-event completed.");
    }

    // ── Trades snapshot capture ────────────────────────────────────────

    /**
     * Clicks the Refresh button on the Trade Portal, waits for {@code GET /api/v1/trades}
     * to return 200, and stores the full response body under {@code ctxKey}.
     *
     * <p>Call this once before opening the allocation dialog to capture the pre-allocation
     * state. The body is later retrieved by {@code verifyAllocationDiff}.
     */
    @When("^capture trades snapshot as '([^']*)'$")
    public void captureTradesSnapshot(String ctxKey) {
        String tradesJson = ctx.clickAndCaptureResponse(
                "trade_portal.refresh_btn", "/api/v1/blotter/trades", "POST");
        ctx.set(ctxKey, tradesJson);
        log.info("[AllocationSteps] Trades snapshot captured as '{}'.", ctxKey);
    }

    /**
     * Fills the first {@code legs} sub-trade rows of the allocation dialog, which the split number has made, from the
     * case data {@code subTrades}: the item's {@code counterpartyName} is selected and its {@code percentage} typed,
     * each when the item has it. The report shows each row.
     *
     * <pre>And user fills 3 way sub-trade allocation info</pre>
     */
    @When("^user fills (\\d+) way sub-trade allocation info$")
    public void fillSubTradeRows(int legs) {
        JsonNode subTrades = caseSubTrades(legs);
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

    /** The case's {@code subTrades}, which must have at least {@code legs} items. */
    private JsonNode caseSubTrades(int legs) {
        JsonNode subTrades = ctx.find("_tc.request.subTrades");
        if (subTrades == null || !subTrades.isArray() || subTrades.size() < legs) {
            throw new AssertionError("Case " + ctx.find("_tc.key") + " needs at least " + legs
                    + " items in its 'subTrades' for a " + legs + "-way allocation, has: " + subTrades);
        }
        return subTrades;
    }

    private static String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }
}
