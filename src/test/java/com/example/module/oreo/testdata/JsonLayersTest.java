package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonLayersTest {

    private static ObjectNode object(String json) {
        return (ObjectNode) Json.parse(json);
    }

    @Test
    public void deepMergesObjects() {
        ObjectNode merged = JsonLayers.merge(object("{\"basic\":{\"a\":1,\"b\":2}}"), object("{\"basic\":{\"b\":3}}"));
        assertEquals(Json.parse("{\"basic\":{\"a\":1,\"b\":3}}"), merged);
    }

    @Test
    public void nullDeletesKey() {
        ObjectNode merged = JsonLayers.merge(
                object("{\"basic\":{\"premiumAmount\":10000,\"x\":1}}"),
                object("{\"basic\":{\"premiumAmount\":null}}"));
        assertEquals(Json.parse("{\"basic\":{\"x\":1}}"), merged);
    }

    @Test
    public void arraysReplaceWhole() {
        ObjectNode merged = JsonLayers.merge(object("{\"udf\":[1,2]}"), object("{\"udf\":[3]}"));
        assertEquals(Json.parse("{\"udf\":[3]}"), merged);
    }

    @Test
    public void doesNotMutateInputs() {
        ObjectNode base = object("{\"basic\":{\"a\":1,\"b\":2}}");
        ObjectNode patch = object("{\"basic\":{\"b\":null,\"c\":{\"d\":4}}}");
        JsonNode baseBefore = base.deepCopy();
        JsonNode patchBefore = patch.deepCopy();

        ObjectNode merged = JsonLayers.merge(base, patch);
        ((ObjectNode) merged.get("basic").get("c")).put("d", 5);

        assertEquals(baseBefore, base);
        assertEquals(patchBefore, patch);
    }

    @Test
    public void appendAddsToTheInheritedList() {
        ObjectNode base = object("{\"data\":[{\"key\":\"a\"}],\"x\":1}");
        ObjectNode merged = JsonLayers.merge(base, object("{\"data\":{\"_append\":[{\"key\":\"b\"}]}}"));
        assertEquals(Json.parse("{\"data\":[{\"key\":\"a\"},{\"key\":\"b\"}],\"x\":1}"), merged);
        assertEquals(Json.parse("{\"data\":[{\"key\":\"a\"}],\"x\":1}"), base);
    }

    @Test
    public void appendWithoutAnInheritedListNamesTheKey() {
        try {
            JsonLayers.merge(object("{\"basic\":{}}"), object("{\"basic\":{\"udf\":{\"_append\":[1]}}}"));
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("basic.udf: "));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void appendOfAKeyTheListHasIsAnError() {
        JsonLayers.merge(object("{\"data\":[{\"key\":\"a\",\"value\":1}]}"),
                object("{\"data\":{\"_append\":[{\"key\":\"a\",\"value\":2}]}}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void appendInsideAListIsRejected() {
        JsonLayers.checkAppends(Json.parse("{\"data\":[{\"_append\":[]}]}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void appendBesideOtherKeysIsRejected() {
        JsonLayers.checkAppends(Json.parse("{\"data\":{\"_append\":[],\"x\":1}}"));
    }

    @Test
    public void dropsCommentsAtEveryDepth() {
        JsonNode cleaned = JsonLayers.withoutComments(
                Json.parse("{\"_comment\":\"x\",\"a\":{\"_comment\":\"y\",\"b\":[{\"_comment\":\"z\",\"c\":1}]}}"));
        assertEquals(Json.parse("{\"a\":{\"b\":[{\"c\":1}]}}"), cleaned);
    }
}
