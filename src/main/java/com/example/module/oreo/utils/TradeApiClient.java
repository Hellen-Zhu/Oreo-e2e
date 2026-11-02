package com.example.module.oreo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.testdata.TradeRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

/**
 * The trade endpoints as Java glue calls them: their paths, for the requests here and for the UI steps that wait for
 * a response, and the requests that provision a trade from {@code data/setup/}. API snippets use the built-in REST
 * steps, not this class.
 */
public final class TradeApiClient {

    private static final Logger log = LoggerFactory.getLogger(TradeApiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String SUBMIT_TRADE_PATH = "/trades/create?tradeAction=SUBMIT";
    public static final String SAVE_TRADE_PATH = "/trades/create?tradeAction=SAVE";
    public static final String TRIGGER_EVENT_PATH = "/trades/trigger-event";

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

    /** Submits the setup body {@code testDataKey} for {@code product} with the dat file at classpath {@code datPath}. */
    public static JsonNode submitTrade(RequestResponseHelper helper, String userId, String testDataKey,
                                       ProductDatFiles product, String datPath) throws URISyntaxException {
        return createTrade(helper, SUBMIT_TRADE_PATH, userId, testDataKey, product, datPath);
    }

    /** Saves the setup body {@code testDataKey} as a draft of {@code product}. */
    public static JsonNode saveTrade(RequestResponseHelper helper, String userId, String testDataKey,
                                     ProductDatFiles product, String datPath) throws URISyntaxException {
        return createTrade(helper, SAVE_TRADE_PATH, userId, testDataKey, product, datPath);
    }

    private static JsonNode createTrade(RequestResponseHelper helper, String path, String userId, String testDataKey,
                                        ProductDatFiles product, String datPath) throws URISyntaxException {
        // The setup body carries no product: it is set from the dat file's product
        String tradeJson = TradeRequests.withProductId(TestData.setup(testDataKey).request(), product.name());
        URL resource = TradeApiClient.class.getClassLoader().getResource(datPath);
        if (resource == null) {
            throw new AssertionError("[TradeApiClient] dat file '" + datPath + "' not found on classpath.");
        }
        log.info("[TradeApiClient] createTrade: path='{}', userId='{}', testDataKey='{}', datFile='{}'",
                path, userId, testDataKey, datPath);
        return RestApiUtil.postMultipart(helper, path, userId, "trade", tradeJson, "datFile",
                Paths.get(resource.toURI()));
    }

    public static JsonNode updateTrade(RequestResponseHelper helper, String tradeId, String userId, String testDataKey) {
        log.info("[TradeApiClient] updateTrade: tradeId='{}', userId='{}', testDataKey='{}'", tradeId, userId, testDataKey);
        return RestApiUtil.post(helper, updatePath(tradeId), userId, TestData.setup(testDataKey).request());
    }

    /** Triggers the event of the setup body {@code testDataKey} on {@code tradeIds}. */
    public static JsonNode triggerEvent(RequestResponseHelper helper, List<String> tradeIds, String userId,
                                        String testDataKey) throws JsonProcessingException {
        ObjectNode body = (ObjectNode) OBJECT_MAPPER.readTree(TestData.setup(testDataKey).request());
        ArrayNode ids = OBJECT_MAPPER.createArrayNode();
        tradeIds.forEach(ids::add);
        body.set("tradeIds", ids);
        log.info("[TradeApiClient] triggerEvent: tradeIds='{}', userId='{}', testDataKey='{}'", tradeIds, userId, testDataKey);
        return RestApiUtil.post(helper, TRIGGER_EVENT_PATH, userId, OBJECT_MAPPER.writeValueAsString(body));
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
