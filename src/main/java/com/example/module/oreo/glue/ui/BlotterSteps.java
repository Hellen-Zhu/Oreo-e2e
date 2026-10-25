package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.CleanupTestDataUtil;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.testdata.TestData;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlotterSteps {

    private static final Logger log = LoggerFactory.getLogger(BlotterSteps.class);
    private static final String PORTAL_USER = "admin@example.com";

    private PlaywrightServiceResolver.Context ctx;
    private RequestResponseHelper helper;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    @After(value = "@playwright and @ADO#trade_blotter", order = 2)
    public void deleteBlotterAfterTest() {
        String blotterId = ctx.find("newBlotterId");
        CleanupTestDataUtil.deleteBlotter(helper, blotterId, PORTAL_USER);

        String importedId = ctx.find("importedBlotterId");
        CleanupTestDataUtil.deleteBlotter(helper, importedId, "user@example.com");
    }

    @Given("^a blotter named '([^']*)' is provisioned via playwright api$")
    public void blotterIsProvisionedForPortalUser(String nameCtxKey) {
        String blotterName = ctx.get(nameCtxKey);

        String createRequest = TestData.setup("trade-blotter").request()
                .replace("${blotterName}", blotterName);
        log.info("[NewBlotterSteps] Provisioning a blotter with name '{}' and request '{}' via playwright api", blotterName, createRequest);
        JsonNode createResponse = RestApiUtil.post(helper, "/uc/blotters/create", PORTAL_USER, createRequest);

        String id = requiredText(createResponse, "/data/id");
        String name = requiredText(createResponse, "/data/name");

        ctx.set("newBlotterId", id);
        ctx.write("Provisioned a blotter with name '" + name + "' (id='" + id + "') via playwright api.");

    }

    @Given("^a public blotter named '([^']*)' is provisioned via playwright api$")
    public void marketplaceBlotterIsProvisionedForPortalUser(String nameCtxKey) {
        String blotterName = ctx.get(nameCtxKey);

        String createRequest = TestData.setup("trade-blotter-public").request()
                .replace("${blotterName}", blotterName);
        log.info("[NewBlotterSteps] Provisioning a public blotter with name '{}' and request '{}' via playwright api", blotterName, createRequest);
        JsonNode createResponse = RestApiUtil.post(helper, "/uc/blotters/create", PORTAL_USER, createRequest);

        String id = requiredText(createResponse, "/data/id");
        String publishedId = requiredText(createResponse, "/data/publishedId");
        String name = requiredText(createResponse, "/data/name");

        ctx.set("newBlotterId", id);
        ctx.set("newMarketplaceBlotterId", publishedId);
        ctx.write("Provisioned a public blotter with name '" + name + "' (id='" + id + "', publishedId='" + publishedId + "') via playwright api.");

    }

    @When("^confirm new blotter creation and capture response as '([^']*)'$")
    public void captureNewBlotterCreationResponse(String responseCtxKey) {
        log.info("[NewBlotterSteps] Confirming new blotter creation; capturing /blotters/create response as '{}'", responseCtxKey);
        String responseBody = ctx.clickAndCaptureResponse(
                "custom_blotter_dialog.confirm_btn",
                "/uc/blotters/create",
                "POST"
        );
        ctx.set(responseCtxKey, responseBody);
        ctx.write("Captured /blotters/create response as '" + responseCtxKey + "': " + responseBody);
    }

    @When("^confirm to import '([^']*)' blotter and capture response as '([^']*)'$")
    public void captureImportBlotterResponse(String blotterIdCtxKey, String responseCtxKey) {
        String blotterId = ctx.get(blotterIdCtxKey);
        log.info("[NewBlotterSteps] Confirming blotter import response as '{}'", responseCtxKey);
        String responseBody = ctx.clickAndCaptureResponse(
                "blotter_marketplace_dialog.import_btn",
                "/uc/blotters/" + blotterId+ "/import",
                "POST"
        );
        ctx.set(responseCtxKey, responseBody);
        ctx.write("Captured /blotters/" + blotterId + "/import response as '" + responseCtxKey + "': " + responseBody);
    }

    private String requiredText(JsonNode response, String pointer) {
        JsonNode node = response.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            throw new AssertionError("Missing required field '" + pointer + "' in response: " + response);
        }
        return node.asText();
    }
}
