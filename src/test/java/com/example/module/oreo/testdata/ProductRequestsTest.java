package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProductRequestsTest {

    private static final String TEMPLATE =
            "{\"productEventConfigurations\":[{\"eventId\":\"{productId}-ET\",\"rate\":1.50}]}";

    private static void assertFails(Runnable action, String messagePart) {
        try {
            action.run();
            fail("expected an AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    @Test
    public void everyCurrentSecondsIsTheGivenSecond() {
        assertEquals("{\"productId\":\"P_42\",\"name\":\"n 42\"}", ProductRequests.withCurrentSeconds(
                "{\"productId\":\"P_{currentSysSeconds}\",\"name\":\"n {currentSysSeconds}\"}", 42));
        assertEquals("{\"a\":1}", ProductRequests.withCurrentSeconds("{\"a\":1}", 42));
    }

    @Test
    public void aProductRequestNamesItsProduct() {
        assertEquals("P_1", ProductRequests.productIdOf("{\"productId\":\"P_1\"}"));
        assertFails(() -> ProductRequests.productIdOf("{\"productId\":\" \"}"), "no productId");
        assertFails(() -> ProductRequests.productIdOf("[1]"), "must be a JSON object");
        assertFails(() -> ProductRequests.productIdOf("{"), "not valid JSON");
    }

    @Test
    public void anEventIdHasDashesForUnderscores() {
        assertEquals("FX-TRF-AUTO-1", ProductRequests.eventId(" FX_TRF_AUTO_1 "));
    }

    @Test
    public void theEventConfigurationsOfTheTemplateReplaceThoseOfTheProduct() {
        JsonNode product = Json.parse("{\"id\":\"old\",\"productId\":\"P_1\",\"description\":\"d\","
                + "\"productEventConfigurations\":[{\"eventId\":\"x\"}]}");
        String before = Json.compact(product);

        String body = ProductRequests.eventConfigurationUpdate(product, "E9", "P_1", TEMPLATE);

        assertEquals("{\"id\":\"E9\",\"productId\":\"P_1\",\"description\":\"d\","
                + "\"productEventConfigurations\":[{\"eventId\":\"P-1-ET\",\"rate\":1.50}]}", body);
        assertEquals("the product data is not changed", before, Json.compact(product));
    }

    @Test
    public void anEventConfigurationNeedsATemplateWithConfigurationsAndAProductObject() {
        JsonNode product = Json.parse("{\"id\":\"E9\"}");
        assertFails(() -> ProductRequests.eventConfigurationUpdate(product, "E9", "P_1",
                "{\"productEventConfigurations\":[]}"), "has no productEventConfigurations");
        assertFails(() -> ProductRequests.eventConfigurationUpdate(product, "E9", "P_1", "{}"),
                "has no productEventConfigurations");
        assertFails(() -> ProductRequests.eventConfigurationUpdate(Json.parse("[]"), "E9", "P_1", TEMPLATE),
                "must be a JSON object");
    }

    @Test
    public void aSchemaFileWithoutCurrentSecondsIsUploadedAsItIs() throws IOException {
        Path file = Files.createTempFile("schema-", ".json");
        Files.writeString(file, "{\"version\":\"1\"}", StandardCharsets.UTF_8);
        assertEquals(file, ProductRequests.renderFile(file, 42));
    }

    @Test
    public void aSchemaFileWithCurrentSecondsIsACopyWithTheSecond() throws IOException {
        Path file = Files.createTempFile("schema-", ".json");
        Files.writeString(file, "{\"productId\":\"P_{currentSysSeconds}\"}", StandardCharsets.UTF_8);

        Path rendered = ProductRequests.renderFile(file, 42);

        assertNotEquals(file, rendered);
        assertEquals("{\"productId\":\"P_42\"}", Files.readString(rendered, StandardCharsets.UTF_8));
        assertEquals("the source is not changed", "{\"productId\":\"P_{currentSysSeconds}\"}",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void aMissingSchemaFileFails() {
        assertFails(() -> ProductRequests.schemaFile("data/no-such-schema.json", 42), "no classpath resource");
    }
}
