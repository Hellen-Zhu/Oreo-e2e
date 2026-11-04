package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Stores the sub-trade IDs of the allocation response in stored variable {@code responseCtxKey}
     * ({@code data.results[0].childTradeIds}) as {@code allocatedTradeId1}, {@code allocatedTradeId2}, ...
     */
    @When("^store allocated sub-trade ids from stored variable '([^']*)'$")
    public void storeAllocatedSubTradeIds(String responseCtxKey) {
        String responseBody = ctx.get(responseCtxKey);
        JsonNode ids;
        try {
            ids = MAPPER.readTree(responseBody).path("data").path("results").path(0).path("childTradeIds");
        } catch (JsonProcessingException e) {
            throw new AssertionError("Stored variable '" + responseCtxKey + "' is not a JSON response.", e);
        }
        if (!ids.isArray() || ids.isEmpty()) {
            throw new AssertionError("No data.results[0].childTradeIds in stored variable '" + responseCtxKey + "'.");
        }
        for (int i = 0; i < ids.size(); i++) {
            ctx.set("allocatedTradeId" + (i + 1), ids.get(i).asText());
            ctx.write("[stored] allocatedTradeId" + (i + 1) + " = " + ids.get(i).asText());
        }
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

    @When("^user fills (\\d+) way sub-trade allocation info$")
    public void fillSubTradeRows(int size) {
        JsonNode subTrades = ctx.get("_tc.request.subTrades");

        if (subTrades.isMissingNode() || !subTrades.isArray() || subTrades.isEmpty()) {
            throw new AssertionError(
                    "[AllocationSteps] 'subTrades' array is missing or empty "
                    + "for key '" + ctx.get("_tc.key") + "'.");
        }
        if (size > subTrades.size()) {
            throw new AssertionError(
                    "[AllocationSteps] Requested size " + size + " exceeds 'subTrades' length "
                    + subTrades.size() + " for key '" + ctx.get("_tc.key") + "'.");
        }

        Locator dialog = ctx.service().getLocator("dynamic_action.dialog");

        for (int i = 0; i < size; i++) {
            JsonNode leg              = subTrades.get(i);
            String   counterpartyName = optionalLegField(leg, "counterpartyName");
            String   percentage       = optionalLegField(leg, "percentage");

            log.info("[AllocationSteps] Row {} — counterparty='{}'{}",
                    i + 1,
                    counterpartyName,
                    percentage.isEmpty() ? "" : ", percentage='" + percentage + "'");

            Locator row = dialog.locator("tbody tr").nth(i);
            if (!counterpartyName.isEmpty()) {
                selectCounterparty(row, counterpartyName);
            }
            if (!percentage.isEmpty()) {
                fillSubTradePercentage(row, percentage);
            }

        }
        log.info("[AllocationSteps] All {} sub-trade row(s) filled.", size);
    }


    // ── Private helpers ──────────────────────────────────────────────

    private String optionalLegField(JsonNode leg, String fieldName) {
        return leg.path(fieldName).asText("").trim();
    }

    private void selectCounterparty(Locator row, String optionName) {
        row.locator("[data-testid='allocation-sub-trade-counterparty-select']").click();
        ctx.clickOption(optionName);
    }

    private void fillSubTradePercentage(Locator row, String percentage) {
        ctx.innerInput(row.locator("[data-testid='allocation-sub-trade-percentage-input']")).fill(percentage);
    }
}
