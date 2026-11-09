package com.example.module.oreo.glue.ui;

import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class NewTradeSteps {

    private PlaywrightServiceResolver.Context ctx;
    private static final Logger log = LoggerFactory.getLogger(NewTradeSteps.class);

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    // ── File upload ─────────────────────────────────────────────────

    /** The case's dat file ({@code datFile} of its test data), else the composition's default one. */
    @When("^upload '([^']*)' dat file$")
    public void uploadDatFile(String productType) {
        ProductDatFiles composition = ProductDatFiles.of(productType);
        ctx.write("[" + composition.describe() + "]");
        uploadDatFileByPath(composition.path(ctx.find("_tc.datFile")));
    }

    /** The product ID registered for the composition, for the new trade page's product list. */
    @When("^store the product ID of '([^']*)' as '([^']*)'$")
    public void storeProductId(String composition, String key) {
        ctx.set(key, ProductDatFiles.of(composition).productId());
    }

    @When("^click save using configured risk engine mode for new trade creation$")
    public void clickSaveUsingConfiguredRiskEngineModeForNewTrade() {
        ctx.clickUsingRiskEngineMode("new_trade.save_btn", TradeApiClient.CALCULATE_RISK_FOR_NEW_PATH,
                "risk-calc-success-mock");
    }

    @When("^click book using configured risk engine mode for new trade creation$")
    public void clickBookUsingConfiguredRiskEngineModeForNewTrade() {
        ctx.clickUsingRiskEngineMode("new_trade.book_btn", TradeApiClient.CALCULATE_RISK_FOR_NEW_PATH,
                "risk-calc-success-mock");
    }

    @When("^confirm new trade and wait for page ready$")
    public void waitForNewTradeCreatedReady() {
        ctx.clickAndWaitForResponse("trade_change_confirmation.confirm_btn", TradeApiClient.BLOTTER_TRADES_PATH,
                "POST");
    }

    @When("^confirm new trade creation and capture response as '([^']*)'$")
    public void captureNewTradeCreationResponse(String responseCtxKey) {
        log.info("[NewTradeSteps] Confirming trade change confirmation; capturing /trades/create response as '{}'", responseCtxKey);
        String responseBody = ctx.clickAndCaptureResponse(
                "trade_change_confirmation.confirm_btn",
                TradeApiClient.CREATE_TRADE_PATH,
                "POST"
        );
        ctx.set(responseCtxKey, responseBody);
    }

    // ── Private helpers ─────────────────────────────────────────────

    private void uploadDatFileByPath(String relativeFilePath) {
        URL resource = getClass().getClassLoader().getResource(relativeFilePath);
        if (resource == null) {
            throw new AssertionError(
                    "[NewTradeSteps] Cannot find resource '" + relativeFilePath + "' on classpath.");
        }
        try {
            ctx.service().getLocator("new_trade.file_input")
                    .setInputFiles(Paths.get(resource.toURI()));
            ctx.write("[dat file] " + relativeFilePath);
        } catch (URISyntaxException e) {
            throw new AssertionError(
                    "[NewTradeSteps] Invalid URI for resource '" + relativeFilePath + "': " + e.getMessage());
        }
    }
}
