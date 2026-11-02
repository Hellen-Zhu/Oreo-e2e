package com.example.module.oreo.glue.ui;

import com.example.module.oreo.utils.TradeApiClient;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeCancellationSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeCancellationSteps.class);

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
    }

    @When("^select cancel reason from stored variable '([^']*)'$")
    public void selectCancelReason(String ctxKey) {
        String reason = ctx.get(ctxKey);
        log.info("[TradeCancellationSteps] Selecting cancel reason '{}' from ctx['{}']", reason, ctxKey);
        ctx.write("[TradeCancellationSteps] Selecting cancel reason: '" + reason + "'");
        ctx.service().getLocator("trade_change_confirmation.reason_select").click();
        ctx.clickOption(reason);
    }

    @When("^confirm cancel trade and wait for page ready$")
    public void confirmCancelTradeAndWait() {
        log.info("[TradeCancellationSteps] Clicking cancel trade confirm button and waiting for trades reload");
        ctx.clickAndWaitForResponse("trade_change_confirmation.confirm_btn", TradeApiClient.TRIGGER_EVENT_PATH, "POST");
    }

    /**
     * Selects the cancel reason stored under {@code ctxKey} from the Trade Detail page cancel dialog.
     */
    @When("^select cancel detail reason from stored variable '([^']*)'$")
    public void selectCancelDetailReason(String ctxKey) {
        String reason = ctx.get(ctxKey);
        log.info("[TradeCancellationSteps] Selecting detail cancel reason '{}' from ctx['{}']", reason, ctxKey);
        ctx.service().getLocator("trade_detail.cancel_reason_select").click();
        ctx.clickOption(reason);
    }

    @When("^trigger cancellation for trade using configured risk engine mode from trade portal for trade stored as '([^']*)'$")
    public void confirmCancellationFromPortalUsingConfiguredRiskEngineMode(String ctxKey) {
        ctx.clickUsingRiskEngineMode("trade_portal.row_action_cancel",
                TradeApiClient.calculateRiskPath(ctx.get(ctxKey)), "risk-calc-success-mock");
    }

    @When("^trigger cancellation for trade using configured risk engine mode from trade detail page for trade stored as '([^']*)'$")
    public void confirmCancellationFromDetailsUsingConfiguredRiskEngineMode(String ctxKey) {
        ctx.clickUsingRiskEngineMode("trade_detail.cancel_btn",
                TradeApiClient.calculateRiskPath(ctx.get(ctxKey)), "risk-calc-success-mock");
    }

    /**
     * Confirms the cancellation dialog on the Trade Detail page and waits for the trigger-event
     * API call to complete before returning.
     */
    @When("^confirm cancellation from trade detail and wait for page ready$")
    public void confirmCancellationFromTradeDetailAndWait() {
        log.info("[TradeCancellationSteps] Confirming cancel dialog on trade detail and waiting for trigger-event response");
        ctx.clickAndWaitForResponse("trade_detail.cancel_dialog_confirm_btn", TradeApiClient.TRIGGER_EVENT_PATH, "POST");
    }
}
