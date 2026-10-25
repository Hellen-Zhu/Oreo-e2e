package com.example.module.oreo.glue.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.example.genie.module.database.glue.DatabaseGlue;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.utils.CleanupTestDataUtil;
import com.example.module.oreo.utils.RestApiUtil;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;

public class ComposeProductSteps {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
            deleteProduct(productId);
    }

    @When("^save product edit for metadata of product '([^']*)' and wait for page ready$")
    public void saveProductDetailPageForCatalogueBasic(String productIdKey) {
        String productId = ctx.get(productIdKey);
        ctx.clickAndWaitForResponse("product_edit.save_btn", "/api/v1/products/" + productId + "/metadata", "PUT");
    }

    @When("^save product edit for product '([^']*)' and wait for page ready$")
    public void saveProductDetailPageForEventConfiguration(String productIdKey) {
        String productEntityId = ctx.get(productIdKey);
        ctx.set("CreatedProductEntityId", productEntityId);
        ctx.clickAndWaitForResponse("product_edit.save_btn", "/api/v1/products/" + productEntityId + "/update", "POST");
    }

    @When("^add event '([^']*)' into canvas$")
    public void addEventOntoCanvas(String element) {
        Locator toolboxItem = ctx.service().getLocator(element);
        Locator dropArea = ctx.service().getLocator("product_edit.canvas_drop_area");
        toolboxItem.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(ctx.timeoutMillis()));
        toolboxItem.dragTo(dropArea);
    }

    @Given("^a product is provisioned via playwright api$")
    public void provisionProduct() {
        JsonNode body = createProduct("product-create", "maker@example.com");
        String productId = body.at("/data/productId").asText();
        JsonNode getProductResponse = RestApiUtil.get(helper, "/products/productId/" + productId, "maker@example.com");
        String entityId = getProductResponse.path("data").path("id").asText();
        ctx.set("CreatedProductEntityId", entityId);
        ctx.set("createdProductId", productId);
        ctx.set("reformattedProductId", reformatProductIdForEventId(productId));
        ctx.write("Created product with productId: " + productId + ", entityId: " + entityId);
    }

    private JsonNode createProduct(String testDataKey, String userId) {
        String raw = TestData.setup(testDataKey).request();
        try {
            String currentSysSeconds = String.valueOf(System.currentTimeMillis() / 1000);
            String renderedRaw = raw.replace("{currentSysSeconds}", currentSysSeconds);
            ObjectNode body = (ObjectNode) OBJECT_MAPPER.readTree(renderedRaw);
            String productId = body.path("productId").asText();
            if (productId == null || productId.isBlank()) {
                throw new AssertionError("Missing required productId in request payload");
            }
            ctx.write("Payload as:" + body);
            return RestApiUtil.post(helper, "/products/" + productId + "/schema/commit", userId,
                    OBJECT_MAPPER.writeValueAsString(body));
        } catch (Exception e) {
            throw new AssertionError("Failed to build product payload: " + e.getMessage(), e);
        }
    }

    private void deleteProduct(String productId) {
        CleanupTestDataUtil.cleanupTestProduct(databaseGlue, productId);
    }

    @When("^seed compose product with initial EarlyTermination event configuration$")
    public void seedComposeProductWithInitialEarlyTerminationEventConfiguration() {
        String productId = ctx.get("createdProductId");
        String productEntityId = ctx.get("CreatedProductEntityId");

        JsonNode productResponse = RestApiUtil.get(helper, "/products/productId/" + productId, "maker@example.com");
        JsonNode currentProductData = productResponse.path("data");
        if (currentProductData == null || currentProductData.isMissingNode() || currentProductData.isNull()) {
            throw new AssertionError("Failed to load product data for productId: " + productId
                    + ". Endpoint response: " + productResponse);
        }

        String rawTemplate = TestData.setup("product-event-configure").request();
        String renderedTemplate = rawTemplate.replace("{productId}", reformatProductIdForEventId(productId));
        try {
            JsonNode templatePayload = OBJECT_MAPPER.readTree(renderedTemplate);
            JsonNode initialEventConfigurations = templatePayload.path("productEventConfigurations");
            if (!initialEventConfigurations.isArray() || initialEventConfigurations.isEmpty()) {
                throw new AssertionError("No productEventConfigurations found in product-event-configure test data.");
            }

            ObjectNode updateBody = currentProductData.deepCopy();
            updateBody.put("id", productEntityId);
            updateBody.put("productId", productId);
            updateBody.set("productEventConfigurations", initialEventConfigurations.deepCopy());
            RestApiUtil.post(helper, "/products/" + productEntityId + "/update", "maker@example.com", OBJECT_MAPPER.writeValueAsString(updateBody));

            ctx.refreshAndWaitForResponse("/api/v1/products/" + productId, "GET");
        } catch (Exception e) {
            throw new AssertionError("Failed to seed initial EarlyTermination event configuration: " + e.getMessage(), e);
        }
    }

    private static String reformatProductIdForEventId(String productId) {
        return productId.trim().replace('_', '-');
    }
}
