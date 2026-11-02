package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TradeProvisioningSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeProvisioningSteps.class);
    private RequestResponseHelper helper;
    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    @Given("^a draft new '([^']*)' trade is provisioned via playwright api$")
    public void provisionPendingDraftTrade(String product) throws Exception {
        saveTradeAsDraft("createdTradeId", "createdTaskId", "trade-create",
                ProductDatFiles.valueOf(product));
    }

    @Given("^a draft pending approval '([^']*)' trade is provisioned via playwright api$")
    public void provisionPendingTrade(String product) throws Exception {
        submitTradeAsPendingApproval("createdTradeId", "createdTaskId", "trade-create",
                ProductDatFiles.valueOf(product));
    }

    @Given("^a draft pending approval '([^']*)' stepin full trade is provisioned via playwright api$")
    public void provisionPendingStepInFullTrade(String product) throws Exception {
        submitTradeAsPendingApproval("createdTradeId", "createdTaskId", "trade-create-stepin-full", ProductDatFiles.valueOf(product));
    }

    @Given("^a draft pending approval '([^']*)' stepin partial trade is provisioned via playwright api$")
    public void provisionPendingStepInPartialTrade(String product) throws Exception {
        submitTradeAsPendingApproval("createdTradeId", "createdTaskId", "trade-create-stepin-partial", ProductDatFiles.valueOf(product));
    }

    @Given("^a live '([^']*)' trade is provisioned via playwright api$")
    public void provisionLiveTradeByProduct(String product) throws Exception {
        provisionLiveTradeAs("createdTradeId", "createdTaskId", "trade-create", ProductDatFiles.valueOf(product));
    }

    @Given("^a '([^']*)' trade with pending cancellation is provisioned via playwright api$")
    public void provisionPendingCancellation(String product) throws Exception {
        provisionLiveTradeAs("createdTradeId", "createdTaskId", "trade-create", ProductDatFiles.valueOf(product));
        provisionLifecycleEvent("createdTradeId", "createdTaskId", "trade-cancellation");
    }

    @Given("^multiple '([^']*)' trades with pending cancellation are provisioned via playwright api$")
    public void provisionMultiplePendingCancellation(String product) throws Exception {
        provisionLiveTradeAs("createdTradeId1", "createdTaskId1", "trade-create", ProductDatFiles.valueOf(product));
        provisionLifecycleEvent("createdTradeId1", "createdTaskId1", "trade-cancellation");
        provisionLiveTradeAs("createdTradeId2", "createdTaskId2", "trade-create", ProductDatFiles.valueOf(product));
        provisionLifecycleEvent("createdTradeId2", "createdTaskId2", "trade-cancellation");
    }

    @Given("^a pending approval amendment '([^']*)' trade is provisioned via playwright api$")
    public void provisionTradeAmendment(String product) throws Exception {
        provisionLiveTradeAs("createdTradeId", "createdTaskId", "trade-create", ProductDatFiles.valueOf(product));
        TradeApiClient.updateTrade(helper, ctx.get("createdTradeId"), "maker@example.com", "trade-amendment");
    }

    @Given("^a pending approval cancellation '([^']*)' trade is provisioned via playwright api$")
    public void provisionTradeCancellation(String product) throws Exception {
        provisionLiveTradeAs("createdTradeId", "createdTaskId", "trade-create", ProductDatFiles.valueOf(product));
        provisionLifecycleEvent("createdTradeId", "createdTaskId", "trade-cancellation");
    }

    @Given("^a dead '([^']*)' trade is provisioned via playwright api$")
    public void provisionDeadTrade(String product) throws Exception {
        provisionTradeCancellation(product);
        TradeApiClient.approveTask(helper, ctx.get("createdTaskId"), "checker@example.com");
    }

    @Given("^a rejected draft '([^']*)' trade is provisioned via playwright api$")
    public void provisionDraftTrade(String product) throws Exception {
        provisionTradeCancellation(product);
        TradeApiClient.rejectTask(helper, ctx.get("createdTaskId"), "checker@example.com");
    }

    private void provisionLiveTradeAs(String tradeIdVar, String taskIdVar, String testDataKey, ProductDatFiles datFile) throws Exception {
        log.info("[TradeProvisioningSteps] Provisioning live trade: tradeIdVar='{}', taskIdVar='{}', testDataKey='{}', datFile='{}'",
                tradeIdVar, taskIdVar, testDataKey, datFile);
        submitTradeAsPendingApproval(tradeIdVar, taskIdVar, testDataKey, datFile);
        String taskId = ctx.get(taskIdVar);
        log.info("[TradeProvisioningSteps] Approving checker task for live trade: taskIdVar='{}', taskId='{}'", taskIdVar, taskId);
        TradeApiClient.approveTask(helper, taskId, "checker@example.com");
        log.info("[TradeProvisioningSteps] Live trade provisioned successfully: tradeId='{}', taskId='{}'",
                ctx.get(tradeIdVar), ctx.get(taskIdVar));
    }

    private void saveTradeAsDraft(String tradeIdVar, String taskIdVar, String testDataKey, ProductDatFiles datFile) throws Exception {
        log.info("[TradeProvisioningSteps] Submitting trade as draft: tradeIdVar='{}', taskIdVar='{}', testDataKey='{}', datFile='{}'",
                tradeIdVar, taskIdVar, testDataKey, datFile);

        JsonNode body = TradeApiClient.saveTrade(helper, "maker@example.com", testDataKey, datFile, datFile.path());
        String tradeId = body.at("/data/trade/id").asText();
        String taskId = body.at("/data/checkerContext/taskId").asText();

        log.info("[TradeProvisioningSteps] Trade saved: extracted tradeId='{}', taskId='{}'", tradeId, taskId);

        ctx.set(tradeIdVar, tradeId);
        ctx.write("Trade created with id: " + tradeId);
        ctx.set(taskIdVar, taskId);
    }

    private void submitTradeAsPendingApproval(String tradeIdVar, String taskIdVar, String testDataKey, ProductDatFiles datFile) throws Exception {
        log.info("[TradeProvisioningSteps] Submitting trade as pending approval: tradeIdVar='{}', taskIdVar='{}', testDataKey='{}', datFile='{}'",
                tradeIdVar, taskIdVar, testDataKey, datFile);

        JsonNode body = submitTrade("maker@example.com", testDataKey, datFile);
        String tradeId = body.at("/data/trade/id").asText();
        String taskId = body.at("/data/checkerContext/taskId").asText();

        log.info("[TradeProvisioningSteps] Trade submitted: extracted tradeId='{}', taskId='{}'", tradeId, taskId);

        ctx.set(tradeIdVar, tradeId);
        ctx.write("Trade created with id: " + tradeId);
        ctx.set(taskIdVar, taskId);
    }

    private void provisionLifecycleEvent(String tradeIdVar, String taskIdVar, String testDataKey) throws Exception {
        String tradeId = ctx.get(tradeIdVar);
        log.info("[TradeProvisioningSteps] Provisioning lifecycle event: tradeIdVar='{}', tradeId='{}', taskIdVar='{}', testDataKey='{}'",
                tradeIdVar, tradeId, taskIdVar, testDataKey);

        JsonNode body = TradeApiClient.triggerEvent(helper, List.of(tradeId), "maker@example.com", testDataKey);
        String taskId = body.at("/data/results/0/checkerTaskId").asText();

        log.info("[TradeProvisioningSteps] Lifecycle event submitted: extracted checkerTaskId='{}'", taskId);

        ctx.set(taskIdVar, taskId);
    }

    private JsonNode submitTrade(String userId, String testDataKey, ProductDatFiles datFile) throws Exception {
        // The case's dat file (datFile of its test data), else the product's default one
        String datPath = datFile.path(ctx.find("_tc.datFile"));
        ctx.write("[dat file] " + datPath);
        return TradeApiClient.submitTrade(helper, userId, testDataKey, datFile, datPath);
    }
}
