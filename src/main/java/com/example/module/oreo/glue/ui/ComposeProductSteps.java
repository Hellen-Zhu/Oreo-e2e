package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Locator;
import com.example.genie.module.database.glue.DatabaseGlue;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.utils.CleanupTestDataUtil;
import com.example.module.oreo.utils.ProductApiClient;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.config.TestUser;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;

public class ComposeProductSteps {

    private PlaywrightServiceResolver.Context ctx;
    private DatabaseGlue databaseGlue;
    private RequestResponseHelper helper;

    @Before(value = "@playwright", order = 1)
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
        this.databaseGlue = new DatabaseGlue();
        this.databaseGlue.before(scenario);
    }

    @After(value = "@ADO#compose_product", order = 2)
    public void deleteProductAfterTest() {
        String productId = ctx.get("createdProductId");
        CleanupTestDataUtil.cleanupTestProduct(databaseGlue, productId);
    }

    @When("^save product edit for metadata of product '([^']*)' and wait for page ready$")
    public void saveProductDetailPageForCatalogueBasic(String productIdKey) {
        String productId = ctx.get(productIdKey);
        ctx.clickAndWaitForResponse("product_edit.save_btn", ProductApiClient.metadataPath(productId), "PUT");
    }

    @When("^save product edit for product '([^']*)' and wait for page ready$")
    public void saveProductDetailPageForEventConfiguration(String productIdKey) {
        String productEntityId = ctx.get(productIdKey);
        ctx.set("CreatedProductEntityId", productEntityId);
        ctx.clickAndWaitForResponse("product_edit.save_btn", ProductApiClient.updatePath(productEntityId), "POST");
    }

    @When("^add event '([^']*)' into canvas$")
    public void addEventOntoCanvas(String element) {
        Locator toolboxItem = ctx.service().getLocator(element);
        Locator dropArea = ctx.service().getLocator("product_edit.canvas_drop_area");
        ctx.waitForVisible(toolboxItem);
        toolboxItem.dragTo(dropArea);
    }

    @Given("^a product is provisioned via playwright api$")
    public void provisionProduct() {
        ProductApiClient.CreatedProduct product = ProductApiClient.createProduct(helper, TestUser.MAKER.email());
        ctx.write("Payload as:" + product.request());
        ctx.set("CreatedProductEntityId", product.entityId());
        ctx.set("createdProductId", product.productId());
        ctx.set("reformattedProductId", ProductApiClient.eventId(product.productId()));
        ctx.write("Created product with productId: " + product.productId() + ", entityId: " + product.entityId());
    }

    @When("^seed compose product with initial EarlyTermination event configuration$")
    public void seedComposeProductWithInitialEarlyTerminationEventConfiguration() {
        String productId = ctx.get("createdProductId");
        String productEntityId = ctx.get("CreatedProductEntityId");
        ProductApiClient.seedEventConfigurations(helper, productId, productEntityId, TestUser.MAKER.email());
        ctx.refreshAndWaitForResponse(ProductApiClient.productPath(productId), "GET");
    }
}
