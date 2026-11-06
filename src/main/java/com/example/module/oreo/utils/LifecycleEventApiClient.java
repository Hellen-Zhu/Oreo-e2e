package com.example.module.oreo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The lifecycle event endpoint ({@code POST /trades/trigger-event}) as Java glue calls it: its path, for the request
 * here and for the UI steps that wait for its response; the request that triggers an event from {@code data/setup/};
 * what its response says, so that no step reads a response path. It holds no scenario state: the caller stores what
 * it needs. API snippets use the built-in REST steps, not this class.
 */
public final class LifecycleEventApiClient {

    private static final Logger log = LoggerFactory.getLogger(LifecycleEventApiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String TRIGGER_EVENT_PATH = "/trades/trigger-event";

    /**
     * One result of an event, in the response's order: the trade it ran on ({@code tradeId}), the checker task it
     * created ({@code checkerTaskId}, blank when it executed at once) and the trades it created
     * ({@code childTradeIds}: allocation, novation; empty when none).
     */
    public record EventResult(String tradeId, String checkerTaskId, List<String> childTradeIds) {}

    private LifecycleEventApiClient() {
        // Utility class
    }

    /**
     * Triggers the event of the setup body {@code testDataKey} on {@code tradeIds} as {@code userId}.
     *
     * @throws AssertionError when the response has no result
     */
    public static List<EventResult> triggerEvent(RequestResponseHelper helper, List<String> tradeIds, String userId,
                                                 String testDataKey) throws JsonProcessingException {
        ObjectNode body = (ObjectNode) OBJECT_MAPPER.readTree(TestData.setup(testDataKey).request());
        ArrayNode ids = OBJECT_MAPPER.createArrayNode();
        tradeIds.forEach(ids::add);
        body.set("tradeIds", ids);
        log.info("[LifecycleEventApiClient] triggerEvent: tradeIds='{}', userId='{}', testDataKey='{}'",
                tradeIds, userId, testDataKey);
        JsonNode response = RestApiUtil.post(helper, TRIGGER_EVENT_PATH, userId, OBJECT_MAPPER.writeValueAsString(body));
        return results(response, testDataKey + " on " + tradeIds);
    }

    /** @throws AssertionError when the response has no result */
    static List<EventResult> results(JsonNode response, String what) {
        JsonNode results = response.at("/data/results");
        if (!results.isArray() || results.isEmpty()) {
            throw new AssertionError("[LifecycleEventApiClient] " + what + " has no results: " + response);
        }
        List<EventResult> out = new ArrayList<>();
        for (JsonNode result : results) {
            List<String> children = new ArrayList<>();
            result.path("childTradeIds").forEach(child -> children.add(child.asText()));
            out.add(new EventResult(result.path("tradeId").asText(""), result.path("checkerTaskId").asText(""),
                    List.copyOf(children)));
        }
        return out;
    }
}
