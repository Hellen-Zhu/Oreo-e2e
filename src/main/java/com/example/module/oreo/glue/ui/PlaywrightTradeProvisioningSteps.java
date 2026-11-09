package com.example.module.oreo.glue.ui;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.utils.TradeApiClient.CreatedTrade;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

/**
 * The trade preconditions of UI scenarios, provisioned through the API ({@link TradeApiClient}'s
 * {@code provision...} methods, which the API steps share) and stored for the UI steps. They neither log in nor
 * navigate.
 */
public class PlaywrightTradeProvisioningSteps {

    private RequestResponseHelper helper;
    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    @Given("^a draft new '([^']*)' trade is provisioned via playwright api$")
    public void provisionDraftTrade(String product) throws Exception {
        ProductDatFiles p = composition(product);
        store("createdTradeId", "createdTaskId", TradeApiClient.provisionDraftTrade(helper, "trade-create", p, p.path()));
    }

    /** Submitted by the maker: a trade pending the checker's approval. */
    @Given("^a Draft Pending Approval '([^']*)' trade exists in the blotter$")
    public void provisionPendingTrade(String product) throws Exception {
        provisionPending(product, "trade-create");
    }

    @Given("^a Draft Pending Approval '([^']*)' StepIn Full trade exists in the blotter$")
    public void provisionPendingStepInFullTrade(String product) throws Exception {
        provisionPending(product, "trade-create-stepin-full");
    }

    @Given("^a Draft Pending Approval '([^']*)' StepIn Partial trade exists in the blotter$")
    public void provisionPendingStepInPartialTrade(String product) throws Exception {
        provisionPending(product, "trade-create-stepin-partial");
    }

    /** Submitted by the maker, approved by the checker. */
    @Given("^a Live '([^']*)' trade exists in the blotter$")
    public void provisionLiveTrade(String product) throws Exception {
        provisionLive(product, "createdTradeId", "createdTaskId");
    }

    @Given("^a '([^']*)' trade with pending cancellation is provisioned via playwright api$")
    public void provisionPendingCancellation(String product) throws Exception {
        provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
    }

    @Given("^multiple '([^']*)' trades with pending cancellation are provisioned via playwright api$")
    public void provisionMultiplePendingCancellation(String product) throws Exception {
        provisionPendingCancellation(product, "createdTradeId1", "createdTaskId1");
        provisionPendingCancellation(product, "createdTradeId2", "createdTaskId2");
    }

    @Given("^a pending approval amendment '([^']*)' trade is provisioned via playwright api$")
    public void provisionPendingAmendment(String product) throws Exception {
        CreatedTrade live = provisionLive(product, "createdTradeId", "createdTaskId");
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionPendingAmendment(helper, live.tradeId(), "trade-amendment"));
    }

    @Given("^a pending approval cancellation '([^']*)' trade is provisioned via playwright api$")
    public void provisionTradeCancellation(String product) throws Exception {
        provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
    }

    /** A live trade whose cancellation the checker approved. */
    @Given("^a Dead '([^']*)' trade exists in the blotter$")
    public void provisionDeadTrade(String product) throws Exception {
        CreatedTrade trade = provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.approveTask(helper, trade.taskId(), TestUser.CHECKER.email());
    }

    /** A live trade whose cancellation the checker rejected. */
    @Given("^a Draft '([^']*)' trade exists in the blotter$")
    public void provisionRejectedDraftTrade(String product) throws Exception {
        CreatedTrade trade = provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.rejectTask(helper, trade.taskId(), TestUser.CHECKER.email());
    }

    private void provisionPending(String product, String setupName) throws Exception {
        ProductDatFiles p = composition(product);
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionPendingTrade(helper, setupName, p, p.path(ctx.find("_tc.datFile"))));
    }

    private CreatedTrade provisionLive(String product, String tradeKey, String taskKey) throws Exception {
        ProductDatFiles p = composition(product);
        return store(tradeKey, taskKey,
                TradeApiClient.provisionLiveTrade(helper, "trade-create", p, p.path(ctx.find("_tc.datFile"))));
    }

    private CreatedTrade provisionPendingCancellation(String product, String tradeKey, String taskKey)
            throws Exception {
        CreatedTrade live = provisionLive(product, tradeKey, taskKey);
        return store(tradeKey, taskKey,
                TradeApiClient.provisionPendingEvent(helper, live.tradeId(), "trade-cancellation"));
    }

    private CreatedTrade store(String tradeKey, String taskKey, CreatedTrade trade) {
        ctx.set(tradeKey, trade.tradeId());
        ctx.set(taskKey, trade.taskId());
        ctx.write(String.format("[provisioned] %s = %s, %s = %s", tradeKey, trade.tradeId(), taskKey, trade.taskId()));
        return trade;
    }

    /** The registered composition, written to the report with its trade type and product ID. */
    private ProductDatFiles composition(String product) {
        ProductDatFiles composition = ProductDatFiles.of(product);
        ctx.write("[" + composition.describe() + "]");
        return composition;
    }
}
