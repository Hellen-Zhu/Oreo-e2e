package com.example.module.oreo.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.testdata.DateUtil;
import com.example.testdata.Json;
import com.example.testdata.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The product endpoints as Java glue calls them, and what the product steps send: the paths, for the requests here and
 * for the UI steps that wait for a response; the requests that provision a product from {@code data/setup/}
 * ({@code product-create}, {@code product-event-configure}); the schema files to upload. A path is relative to
 * {@code rest.base.url}; a UI step waits for a response whose URL contains it. {@value #CURRENT_SECONDS} in a product
 * request or a schema file becomes a second of the run, so that each run creates a product of its own;
 * {@code {productId}} in an event configuration becomes the product's event ID ({@link #eventId}). API snippets use
 * the built-in REST steps, not this class.
 */
public final class ProductApiClient {

    private static final Logger log = LoggerFactory.getLogger(ProductApiClient.class);

    /** The schema preview of the schema upload page. */
    public static final String SCHEMA_PARSE_PATH = "/products/schema/parse";

    /** Replaced by a second of the run in a product request or a schema file. */
    public static final String CURRENT_SECONDS = "{currentSysSeconds}";
    private static final String PRODUCT_ID = "{productId}";
    private static final String EVENT_CONFIGURATIONS = "productEventConfigurations";

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

    /** The product by its product ID, as {@link #getProduct} reads it and a product page loads it. */
    public static String byProductIdPath(String productId) {
        return "/products/productId/" + productId;
    }

    /** The ID of the product in event IDs: {@code productId} trimmed, every {@code _} a {@code -}. */
    public static String eventId(String productId) {
        return productId.trim().replace('_', '-');
    }

    /**
     * Commits the schema of setup {@code product-create}, its {@value #CURRENT_SECONDS} filled in with this second,
     * and reads the product back for its entity ID. The request is sent as the setup data gives it.
     */
    public static CreatedProduct createProduct(RequestResponseHelper helper, String userId) {
        String request = withCurrentSeconds(TestData.setup("product-create").request(),
                DateUtil.now().getEpochSecond());
        String requestedId = productIdOf(request);
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
     * Replaces the event configurations of the product with the {@code productEventConfigurations} of setup
     * {@code product-event-configure}, its {@code {productId}} filled in; the product's other fields are sent back as
     * the API returns them, with {@code id} {@code entityId} and {@code productId} {@code productId}.
     */
    public static JsonNode seedEventConfigurations(RequestResponseHelper helper, String productId, String entityId,
                                                   String userId) {
        JsonNode response = getProduct(helper, productId, userId);
        JsonNode product = response.path("data");
        if (!(product instanceof ObjectNode productObject)) {
            throw new AssertionError("[ProductApiClient] no product data for productId '" + productId
                    + "'; the response: " + response);
        }
        String template = TestData.setup("product-event-configure").request();
        JsonNode configurations = parse(template.replace(PRODUCT_ID, eventId(productId)), "product-event-configure")
                .path(EVENT_CONFIGURATIONS);
        if (!configurations.isArray() || configurations.isEmpty()) {
            throw new AssertionError("[ProductApiClient] setup product-event-configure has no " + EVENT_CONFIGURATIONS
                    + ": " + template);
        }
        ObjectNode body = productObject.deepCopy();
        body.put("id", entityId);
        body.put("productId", productId);
        body.set(EVENT_CONFIGURATIONS, configurations.deepCopy());
        log.info("[ProductApiClient] seedEventConfigurations: productId='{}', entityId='{}', userId='{}'",
                productId, entityId, userId);
        return RestApiUtil.post(helper, updatePath(entityId), userId, Json.compact(body));
    }

    /**
     * The schema file to upload for the classpath resource {@code classpathFile}: the file itself, or a temporary copy,
     * deleted when the JVM exits, with {@value #CURRENT_SECONDS} replaced by {@code epochSeconds}.
     *
     * @throws AssertionError when the resource does not exist or cannot be read
     */
    public static Path schemaFile(String classpathFile, long epochSeconds) {
        URL resource = ProductApiClient.class.getClassLoader().getResource(classpathFile);
        if (resource == null) {
            throw new AssertionError("[ProductApiClient] no classpath resource '" + classpathFile + "'");
        }
        try {
            Path file = Paths.get(resource.toURI());
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.contains(CURRENT_SECONDS)) {
                return file;
            }
            Path copy = Files.createTempFile("schema-upload-", ".json");
            Files.writeString(copy, withCurrentSeconds(content, epochSeconds), StandardCharsets.UTF_8);
            copy.toFile().deleteOnExit();
            return copy;
        } catch (URISyntaxException | IOException e) {
            throw new AssertionError("[ProductApiClient] cannot read the schema file '" + classpathFile + "': "
                    + e.getMessage(), e);
        }
    }

    private static String withCurrentSeconds(String text, long epochSeconds) {
        return text.replace(CURRENT_SECONDS, String.valueOf(epochSeconds));
    }

    private static String productIdOf(String request) {
        JsonNode root = parse(request, "product-create");
        String productId = root.path("productId").asText("");
        if (!root.isObject() || productId.isBlank()) {
            throw new AssertionError("[ProductApiClient] setup product-create has no productId: " + request);
        }
        return productId;
    }

    private static JsonNode parse(String json, String setupName) {
        try {
            return Json.parse(json);
        } catch (IllegalStateException e) {
            throw new AssertionError("[ProductApiClient] setup " + setupName + " is not valid JSON: "
                    + e.getMessage(), e);
        }
    }
}
