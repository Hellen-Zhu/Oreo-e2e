package com.example.module.oreo.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.ProductRequests;
import com.example.module.oreo.testdata.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The product endpoints as Java glue calls them: their paths, for the requests here and for the UI steps that wait for
 * a response, and the requests that provision a product from {@code data/setup/} ({@code product-create},
 * {@code product-event-configure}). A path is relative to {@code rest.base.url}; a UI step waits for a response whose
 * URL contains it. API snippets use the built-in REST steps, not this class.
 */
public final class ProductApiClient {

    private static final Logger log = LoggerFactory.getLogger(ProductApiClient.class);

    /** The schema preview of the schema upload page. */
    public static final String SCHEMA_PARSE_PATH = "/products/schema/parse";

    /** A product made by {@link #createProduct}: its product ID, its entity ID and the request that made it. */
    public record CreatedProduct(String productId, String entityId, String request) {}

    private ProductApiClient() {
        // Utility class
    }

    /** The product as the compose page loads it. */
    public static String productPath(String productId) {
        return "/products/" + productId;
    }

    public static String metadataPath(String productId) {
        return "/products/" + productId + "/metadata";
    }

    /** {@code entityId} is the product's entity ID ({@code data.id}), not its product ID. */
    public static String updatePath(String entityId) {
        return "/products/" + entityId + "/update";
    }

    private static String schemaCommitPath(String productId) {
        return "/products/" + productId + "/schema/commit";
    }

    private static String byProductIdPath(String productId) {
        return "/products/productId/" + productId;
    }

    /**
     * Commits the schema of setup {@code product-create}, its {@value ProductRequests#CURRENT_SECONDS} filled in with
     * this second, and reads the product back for its entity ID.
     */
    public static CreatedProduct createProduct(RequestResponseHelper helper, String userId) {
        String request = ProductRequests.withCurrentSeconds(TestData.setup("product-create").request(),
                DateUtil.now().getEpochSecond());
        String requestedId = ProductRequests.productIdOf(request);
        log.info("[ProductApiClient] createProduct: productId='{}', userId='{}'", requestedId, userId);
        JsonNode created = RestApiUtil.post(helper, schemaCommitPath(requestedId), userId, request);
        String productId = created.at("/data/productId").asText();
        String entityId = getProduct(helper, productId, userId).path("data").path("id").asText();
        return new CreatedProduct(productId, entityId, request);
    }

    /** The product with product ID {@code productId}; its fields are under {@code data}. */
    public static JsonNode getProduct(RequestResponseHelper helper, String productId, String userId) {
        return RestApiUtil.get(helper, byProductIdPath(productId), userId);
    }

    /**
     * Replaces the event configurations of the product with those of setup {@code product-event-configure}
     * ({@link ProductRequests#eventConfigurationUpdate}), keeping its other fields as the API returns them.
     */
    public static JsonNode seedEventConfigurations(RequestResponseHelper helper, String productId, String entityId,
                                                   String userId) {
        JsonNode response = getProduct(helper, productId, userId);
        JsonNode product = response.path("data");
        if (product.isMissingNode() || product.isNull()) {
            throw new AssertionError("[ProductApiClient] no product data for productId '" + productId
                    + "'; the response: " + response);
        }
        String body = ProductRequests.eventConfigurationUpdate(product, entityId, productId,
                TestData.setup("product-event-configure").request());
        log.info("[ProductApiClient] seedEventConfigurations: productId='{}', entityId='{}', userId='{}'",
                productId, entityId, userId);
        return RestApiUtil.post(helper, updatePath(entityId), userId, body);
    }
}
