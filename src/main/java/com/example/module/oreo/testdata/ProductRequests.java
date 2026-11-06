package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * What the product steps send: the product request of {@code data/setup/product-create}, the event configuration of
 * {@code data/setup/product-event-configure} and the schema files of the cases. {@value #CURRENT_SECONDS} in a
 * product request or a schema file becomes the second given by the caller, so that each run creates a product of its
 * own; {@code {productId}} in an event configuration becomes the product's event ID ({@link #eventId}).
 */
public final class ProductRequests {

    /** Replaced by the second of the run in a product request or a schema file. */
    public static final String CURRENT_SECONDS = "{currentSysSeconds}";
    private static final String PRODUCT_ID = "{productId}";
    private static final String EVENT_CONFIGURATIONS = "productEventConfigurations";

    private ProductRequests() {}

    /** {@code text} with every {@value #CURRENT_SECONDS} replaced by {@code epochSeconds}. */
    public static String withCurrentSeconds(String text, long epochSeconds) {
        return text.replace(CURRENT_SECONDS, String.valueOf(epochSeconds));
    }

    /**
     * The {@code productId} of a product request.
     *
     * @throws AssertionError when the request is not a JSON object or has no {@code productId}
     */
    public static String productIdOf(String requestJson) {
        JsonNode root = parse(requestJson, "the product request");
        if (!(root instanceof ObjectNode)) {
            throw new AssertionError("[ProductRequests] the product request must be a JSON object: " + requestJson);
        }
        String productId = root.path("productId").asText("");
        if (productId.isBlank()) {
            throw new AssertionError("[ProductRequests] the product request has no productId: " + requestJson);
        }
        return productId;
    }

    /** The ID of the product in event IDs: {@code productId} trimmed, every {@code _} a {@code -}. */
    public static String eventId(String productId) {
        return productId.trim().replace('_', '-');
    }

    /**
     * The body that replaces the event configurations of a product: {@code product} (the {@code data} of the product
     * as the API returns it) with {@code id} {@code entityId}, {@code productId} {@code productId} and the
     * {@code productEventConfigurations} of {@code templateJson}, its {@code {productId}} filled in. {@code product}
     * is not changed.
     *
     * @throws AssertionError when {@code product} is not an object or the template has no event configuration
     */
    public static String eventConfigurationUpdate(JsonNode product, String entityId, String productId,
                                                  String templateJson) {
        if (!(product instanceof ObjectNode productObject)) {
            throw new AssertionError("[ProductRequests] the product data must be a JSON object: " + product);
        }
        JsonNode template = parse(templateJson.replace(PRODUCT_ID, eventId(productId)),
                "the event configuration data");
        JsonNode configurations = template.path(EVENT_CONFIGURATIONS);
        if (!configurations.isArray() || configurations.isEmpty()) {
            throw new AssertionError("[ProductRequests] the event configuration data has no " + EVENT_CONFIGURATIONS
                    + ": " + templateJson);
        }
        ObjectNode body = productObject.deepCopy();
        body.put("id", entityId);
        body.put("productId", productId);
        body.set(EVENT_CONFIGURATIONS, configurations.deepCopy());
        return Json.compact(body);
    }

    /**
     * The schema file to upload for the classpath resource {@code classpathFile}: the file itself, or a temporary copy
     * with {@value #CURRENT_SECONDS} filled in when it has one ({@link #renderFile}).
     *
     * @throws AssertionError when the resource does not exist or cannot be read
     */
    public static Path schemaFile(String classpathFile, long epochSeconds) {
        URL resource = ProductRequests.class.getClassLoader().getResource(classpathFile);
        if (resource == null) {
            throw new AssertionError("[ProductRequests] no classpath resource '" + classpathFile + "'");
        }
        try {
            return renderFile(Paths.get(resource.toURI()), epochSeconds);
        } catch (URISyntaxException e) {
            throw new AssertionError("[ProductRequests] invalid URI for classpath resource '" + classpathFile + "': "
                    + e.getMessage(), e);
        }
    }

    /** {@code file}, or a temporary copy, deleted when the JVM exits, with {@value #CURRENT_SECONDS} filled in. */
    static Path renderFile(Path file, long epochSeconds) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.contains(CURRENT_SECONDS)) {
                return file;
            }
            Path copy = Files.createTempFile("schema-upload-", ".json");
            Files.writeString(copy, withCurrentSeconds(content, epochSeconds), StandardCharsets.UTF_8);
            copy.toFile().deleteOnExit();
            return copy;
        } catch (IOException e) {
            throw new AssertionError("[ProductRequests] cannot render the schema file '" + file + "': "
                    + e.getMessage(), e);
        }
    }

    private static JsonNode parse(String json, String what) {
        try {
            return Json.parse(json);
        } catch (IllegalStateException e) {
            throw new AssertionError("[ProductRequests] " + what + " is not valid JSON: " + e.getMessage(), e);
        }
    }
}
