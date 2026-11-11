package com.example.module.oreo.glue.api;

import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.utils.TradeApiClient.CreatedTrade;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

import java.util.List;

/**
 * The trade preconditions of API scenarios, provisioned through {@link TradeApiClient}'s {@code provision...} methods
 * (which the UI provisioning steps share) and stored for the steps that follow: the trade as {@code createdTradeId},
 * its latest checker task as {@code createdTaskId}, unless the step names other keys.
 */
public class ApiTradeProvisioningSteps {

    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    @Given("^a '([^']*)' trade is submitted to pending approval via api$")
    public void pendingTrade(String product) throws Exception {
        pendingTradeStoredAs(product, "createdTradeId", "createdTaskId");
    }

    @Given("^a '([^']*)' trade pending checker approval is created with trade stored as '([^']*)' and task stored as '([^']*)'$")
    public void pendingTradeStoredAs(String product, String tradeKey, String taskKey) throws Exception {
        ProductDatFiles p = product(product);
        store(tradeKey, taskKey, TradeApiClient.provisionPendingTrade(ctx.helper(), "trade-create", p, datPath(p)));
    }

    @Given("^a live '([^']*)' trade is created via api$")
    public void liveTrade(String product) throws Exception {
        liveTradeStoredAs(product, "createdTradeId", "createdTaskId");
    }

    @Given("^a live '([^']*)' trade is created via api with trade stored as '([^']*)' and task stored as '([^']*)'$")
    public void liveTradeStoredAs(String product, String tradeKey, String taskKey) throws Exception {
        live(product, tradeKey, taskKey);
    }

    @Given("^a dead '([^']*)' trade is created via api$")
    public void deadTrade(String product) throws Exception {
        CreatedTrade trade = pendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.approveTask(ctx.helper(), trade.taskId(), TestUser.CHECKER.email());
    }

    /** A live trade whose cancellation the checker rejected. */
    @Given("^a draft '([^']*)' trade is created via api$")
    public void draftTrade(String product) throws Exception {
        CreatedTrade trade = pendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.rejectTask(ctx.helper(), trade.taskId(), TestUser.CHECKER.email());
    }

    @Given("^a live '([^']*)' trade with a pending Cancellation exists$")
    public void pendingCancellation(String product) throws Exception {
        pendingCancellation(product, "createdTradeId", "createdTaskId");
    }

    @Given("^multiple live '([^']*)' trades have pending cancellation requests$")
    public void pendingCancellations(String product) throws Exception {
        pendingCancellation(product, "createdTradeId1", "createdTaskId1");
        pendingCancellation(product, "createdTradeId2", "createdTaskId2");
    }

    @Given("^a live '([^']*)' trade with a pending Amendment exists$")
    public void pendingAmendment(String product) throws Exception {
        CreatedTrade live = live(product, "createdTradeId", "createdTaskId");
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionPendingAmendment(ctx.helper(), live.tradeId(), "trade-amendment"));
    }

    /** A live FX_TRF trade allocated by setup allocation-create; the allocated trade is {@code allocatedParentTradeId}. */
    @Given("^a trade is allocated with 2-way equal split via api$")
    public void allocatedTrade() throws Exception {
        CreatedTrade live = live("FX_TRF", "createdTradeId", "createdTaskId");
        TradeApiClient.EventResult result = TradeApiClient.triggerEvent(ctx.helper(),
                List.of(live.tradeId()), TestUser.MAKER.email(), "allocation-create").get(0);
        ctx.set("allocatedParentTradeId", result.tradeId());
        ctx.write(String.format("[provisioned] allocatedParentTradeId = %s, children = %s", result.tradeId(),
                result.childTradeIds()));
    }

    private CreatedTrade live(String product, String tradeKey, String taskKey) throws Exception {
        ProductDatFiles p = product(product);
        return store(tradeKey, taskKey, TradeApiClient.provisionLiveTrade(ctx.helper(), "trade-create", p, datPath(p)));
    }

    private CreatedTrade pendingCancellation(String product, String tradeKey, String taskKey) throws Exception {
        CreatedTrade live = live(product, tradeKey, taskKey);
        return store(tradeKey, taskKey,
                TradeApiClient.provisionPendingEvent(ctx.helper(), live.tradeId(), "trade-cancellation"));
    }

    /** The registered composition, written to the report with its product ID. */
    private ProductDatFiles product(String product) {
        ProductDatFiles composition = ProductDatFiles.of(product);
        ctx.write("[" + composition.describe() + "]");
        return composition;
    }

    /** The case's dat file (datFile of its test data), else the composition's default one. */
    private String datPath(ProductDatFiles product) {
        return product.path(ctx.find("_tc.datFile"));
    }

    private CreatedTrade store(String tradeKey, String taskKey, CreatedTrade trade) {
        ctx.set(tradeKey, trade.tradeId());
        ctx.set(taskKey, trade.taskId());
        ctx.write(String.format("[provisioned] %s = %s, %s = %s", tradeKey, trade.tradeId(), taskKey, trade.taskId()));
        return trade;
    }
}
