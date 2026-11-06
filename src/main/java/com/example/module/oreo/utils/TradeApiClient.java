package com.example.module.oreo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.testdata.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The trade endpoints as Java glue calls them: their paths, for the requests here and for the UI steps that wait for
 * a response; the requests that provision a trade from {@code data/setup/}; what their responses say, so that no step
 * reads a response path. API snippets use the built-in REST steps, not this class.
 */
public final class TradeApiClient {

    private static final Logger log = LoggerFactory.getLogger(TradeApiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String SUBMIT_TRADE_PATH = "/trades/create?tradeAction=SUBMIT";
    public static final String SAVE_TRADE_PATH = "/trades/create?tradeAction=SAVE";
    public static final String TRIGGER_EVENT_PATH = "/trades/trigger-event";

    /**
     * A trade that {@link #submitTrade} or {@link #saveTrade} created: its ID ({@code data.trade.id}) and the ID of its
     * checker task ({@code data.checkerContext.taskId}), blank when the response has none.
     */
    public record CreatedTrade(String tradeId, String taskId) {}

    private TradeApiClient() {
        // Utility class
    }

    public static String updatePath(String tradeId) {
        return "/trades/" + tradeId + "/update";
    }

    public static String calculateRiskPath(String tradeId) {
        return "/trades/" + tradeId + "/calculate-risk";
    }

    public static String calculatePartialRiskPath(String tradeId) {
        return "/trades/" + tradeId + "/calculate-partial-novation-risk";
    }

    public static String calculateAllocationRiskPath(String tradeId) {
        return "/trades/" + tradeId + "/calculate-allocation-risk";
    }

    public static String targetGainPath(String tradeId) {
        return "/trades/" + tradeId + "/target-gain";
    }

    /** Submits the setup body {@code testDataKey} for {@code product} with the dat file at classpath {@code datPath}. */
    public static CreatedTrade submitTrade(RequestResponseHelper helper, String userId, String testDataKey,
                                           ProductDatFiles product, String datPath) throws URISyntaxException {
        return createdTrade(createTrade(helper, SUBMIT_TRADE_PATH, userId, testDataKey, product, datPath), testDataKey);
    }

    /** Saves the setup body {@code testDataKey} as a draft of {@code product}. */
    public static CreatedTrade saveTrade(RequestResponseHelper helper, String userId, String testDataKey,
                                         ProductDatFiles product, String datPath) throws URISyntaxException {
        return createdTrade(createTrade(helper, SAVE_TRADE_PATH, userId, testDataKey, product, datPath), testDataKey);
    }

    /** @throws AssertionError when the response names no trade */
    private static CreatedTrade createdTrade(JsonNode response, String testDataKey) {
        String tradeId = response.at("/data/trade/id").asText("");
        if (tradeId.isBlank()) {
            throw new AssertionError("[TradeApiClient] setup " + testDataKey + " created no trade: " + response);
        }
        return new CreatedTrade(tradeId, response.at("/data/checkerContext/taskId").asText(""));
    }

    private static JsonNode createTrade(RequestResponseHelper helper, String path, String userId, String testDataKey,
                                        ProductDatFiles product, String datPath) throws URISyntaxException {
        // The setup body carries no product: it is set from the dat file's product
        String tradeJson = withProductId(TestData.setup(testDataKey).request(), product.name());
        URL resource = TradeApiClient.class.getClassLoader().getResource(datPath);
        if (resource == null) {
            throw new AssertionError("[TradeApiClient] dat file '" + datPath + "' not found on classpath.");
        }
        log.info("[TradeApiClient] createTrade: path='{}', userId='{}', testDataKey='{}', datFile='{}'",
                path, userId, testDataKey, datPath);
        return RestApiUtil.postMultipart(helper, path, userId, "trade", tradeJson, "datFile",
                Paths.get(resource.toURI()));
    }

    /**
     * {@code requestJson} with {@code basic.productId} set to {@code productId} ({@code basic} is created when
     * absent): a trade request in the data never names its product, the step sets it. Numbers are kept exactly as
     * written.
     *
     * @throws AssertionError when the request or its {@code basic} is not a JSON object
     */
    public static String withProductId(String requestJson, String productId) {
        JsonNode root;
        try {
            root = Json.parse(requestJson);
        } catch (IllegalStateException e) {
            throw new AssertionError("[TradeApiClient] Failed to inject basic.productId: " + e.getMessage(), e);
        }
        if (!(root instanceof ObjectNode rootObject)) {
            throw new AssertionError("[TradeApiClient] request must be a JSON object.");
        }
        JsonNode basic = rootObject.get("basic");
        ObjectNode basicObject;
        if (basic == null || basic.isNull()) {
            basicObject = rootObject.putObject("basic");
        } else if (basic instanceof ObjectNode object) {
            basicObject = object;
        } else {
            throw new AssertionError("[TradeApiClient] request.basic must be a JSON object.");
        }
        basicObject.put("productId", productId);
        return Json.compact(rootObject);
    }

    public static JsonNode updateTrade(RequestResponseHelper helper, String tradeId, String userId, String testDataKey) {
        log.info("[TradeApiClient] updateTrade: tradeId='{}', userId='{}', testDataKey='{}'", tradeId, userId, testDataKey);
        return RestApiUtil.post(helper, updatePath(tradeId), userId, TestData.setup(testDataKey).request());
    }

    /**
     * Triggers the event of the setup body {@code testDataKey} on {@code tradeIds}; returns the checker task IDs of the
     * response's results ({@code data.results[].checkerTaskId}), in its order.
     *
     * @throws AssertionError when the response has no result
     */
    public static List<String> triggerEvent(RequestResponseHelper helper, List<String> tradeIds, String userId,
                                            String testDataKey) throws JsonProcessingException {
        ObjectNode body = (ObjectNode) OBJECT_MAPPER.readTree(TestData.setup(testDataKey).request());
        ArrayNode ids = OBJECT_MAPPER.createArrayNode();
        tradeIds.forEach(ids::add);
        body.set("tradeIds", ids);
        log.info("[TradeApiClient] triggerEvent: tradeIds='{}', userId='{}', testDataKey='{}'", tradeIds, userId, testDataKey);
        JsonNode response = RestApiUtil.post(helper, TRIGGER_EVENT_PATH, userId,
                OBJECT_MAPPER.writeValueAsString(body));
        JsonNode results = response.at("/data/results");
        if (!results.isArray() || results.isEmpty()) {
            throw new AssertionError("[TradeApiClient] setup " + testDataKey + " on " + tradeIds + " has no results: "
                    + response);
        }
        List<String> taskIds = new ArrayList<>();
        results.forEach(result -> taskIds.add(result.path("checkerTaskId").asText("")));
        return taskIds;
    }

    public static JsonNode approveTask(RequestResponseHelper helper, String taskId, String userId) {
        log.info("[TradeApiClient] approveTask: taskId='{}', userId='{}'", taskId, userId);
        return RestApiUtil.post(helper, "/checker/tasks/" + taskId + "/approve", userId);
    }

    public static JsonNode rejectTask(RequestResponseHelper helper, String taskId, String userId) {
        log.info("[TradeApiClient] rejectTask: taskId='{}', userId='{}'", taskId, userId);
        return RestApiUtil.post(helper, "/checker/tasks/" + taskId + "/reject", userId);
    }
}
