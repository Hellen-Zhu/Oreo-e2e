package com.example.module.oreo.glue.ui;

import com.example.module.oreo.utils.OreoEndpoints;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeDetailSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeDetailSteps.class);

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    @When("^navigate to trade details for stored trade '([^']*)'$")
    public void navigateToTradeDetails(String ctxKey){
        String tradeId = ctx.get(ctxKey);
        ctx.write("Navigating to trade details for tradeId: " + tradeId);
        log.info("Navigating to trade details for tradeId: {}", tradeId);
        ctx.navigateToPath("/trade/" + tradeId);
    }

    @When("^confirm book trade from detail page for trade '([^']*)'$")
    public void captureTradeDetailBookTradeCreationResponse(String tradeIdKey) {
        String tradeId = ctx.get(tradeIdKey);
        log.info("[TradeDetailSteps] Confirming trade change confirmation; wait for /trades/{}/update?tradeAction=SUBMIT response", tradeId);
        ctx.clickAndWaitForResponse(
                "trade_change_confirmation.confirm_btn",
                tradeUpdateUrl(tradeId) + "?tradeAction=SUBMIT",
                "POST"
        );
    }

    @When("^confirm save trade from detail page for trade '([^']*)'$")
    public void captureTradeDetailSaveTradeCreationResponse(String tradeIdKey) {
        String tradeId = ctx.get(tradeIdKey);
        log.info("[TradeDetailSteps] Confirming trade change confirmation; wait for /trades/{}/update?tradeAction=SAVE response", tradeId);
        ctx.clickAndWaitForResponse(
                "trade_change_confirmation.confirm_btn",
                tradeUpdateUrl(tradeId) + "?tradeAction=SAVE",
                "POST"
        );
    }

    @When("^click save using configured risk engine mode for new trade '([^']*)' creation from detail page$")
    public void clickTradeDetailSaveUsingConfiguredRiskEngineModeForNewTrade(String tradeIdKey) {
        clickUsingConfiguredRiskEngineModeForNewTrade(tradeIdKey, "trade_detail.save_btn");
    }

    @When("^click book using configured risk engine mode for new trade '([^']*)' creation from detail page$")
    public void clickTradeDetailBookUsingConfiguredRiskEngineModeForNewTrade(String tradeIdKey) {
        clickUsingConfiguredRiskEngineModeForNewTrade(tradeIdKey, "trade_detail.book_btn");
    }

    private void clickUsingConfiguredRiskEngineModeForNewTrade(String ctxKey, String buttonLocator) {
        ctx.clickUsingRiskEngineMode(buttonLocator, TradeApiClient.calculateRiskPath(ctx.get(ctxKey)), "risk-calc-success-mock");
    }

    private static String tradeUpdateUrl(String tradeId) {
        return OreoEndpoints.API_PREFIX + TradeApiClient.updatePath(tradeId);
    }
}
