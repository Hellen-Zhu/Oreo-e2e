package com.example.module.oreo.glue.ui;

import com.example.module.oreo.OreoModuleConfiguration;
import com.example.module.oreo.testdata.TestData;
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
        ctx.clickAndWaitForResponse("trade_change_confirmation.confirm_btn", "/trades/trigger-event", "POST");
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
        switch (resolveRiskEngineMode()) {
            case MOCK:
                confirmCancellationFormAndMockSuccessfulRiskCalculationResponse("trade_portal.row_action_cancel", ctxKey);
                return;
            case REAL:
                confirmCancellationFormAndWaitForRealRiskCalculationResponse("trade_portal.row_action_cancel", ctxKey);
                return;
            default:
                throw new IllegalStateException("Unhandled risk engine mode");
        }
    }

    @When("^trigger cancellation for trade using configured risk engine mode from trade detail page for trade stored as '([^']*)'$")
    public void confirmCancellationFromDetailsUsingConfiguredRiskEngineMode(String ctxKey) {
        switch (resolveRiskEngineMode()) {
            case MOCK:
                confirmCancellationFormAndMockSuccessfulRiskCalculationResponse("trade_detail.cancel_btn", ctxKey);
                return;
            case REAL:
                confirmCancellationFormAndWaitForRealRiskCalculationResponse("trade_detail.cancel_btn", ctxKey);
                return;
            default:
                throw new IllegalStateException("Unhandled risk engine mode");
        }
    }

    public void confirmCancellationFormAndWaitForRealRiskCalculationResponse(String elementKey, String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        ctx.runAndWaitForResponse(
                () -> ctx.service().getLocator(elementKey).click(),
                tradeRiskCalculationUrl(tradeId),
                "POST"
        );
    }

    public void confirmCancellationFormAndMockSuccessfulRiskCalculationResponse(String elementKey, String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        String responseBody = TestData.setup("risk-calc-success-mock").response();
        String urlFragment = tradeRiskCalculationUrl(tradeId);
        ctx.clickWithMockedResponse(
                elementKey,
                urlFragment,
                "POST",
                200,
                responseBody
        );
    }
    /**
     * Confirms the cancellation dialog on the Trade Detail page and waits for the trigger-event
     * API call to complete before returning.
     */
    @When("^confirm cancellation from trade detail and wait for page ready$")
    public void confirmCancellationFromTradeDetailAndWait() {
        log.info("[TradeCancellationSteps] Confirming cancel dialog on trade detail and waiting for trigger-event response");
        ctx.clickAndWaitForResponse("trade_detail.cancel_dialog_confirm_btn", "/trades/trigger-event", "POST");
    }

    private OreoModuleConfiguration.Mode resolveRiskEngineMode() {
        return ctx.riskEngineMode();
    }


    private String tradeRiskCalculationUrl(String tradeId) {
        return "/trades/" + tradeId + "/calculate-risk";
    }
}
