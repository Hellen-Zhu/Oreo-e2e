package com.example.module.oreo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.config.TestUser;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The trade endpoints as Java glue calls them, with those of the maker-checker flow around a trade (checker tasks,
 * the blotter's trades, notifications, lifecycle events): their paths, for the requests here and for the UI steps
 * that wait for a response; the requests that provision a trade from {@code data/setup/} (the {@code provision...}
 * methods, for the API and the UI provisioning steps alike); what their responses say, so that no step reads a
 * response path. It holds no scenario state: a method returns the IDs it created and the caller stores them. A path
 * is relative to {@code rest.base.url}; a UI step waits for a response whose URL contains it. API snippets use the
 * built-in REST steps, not this class.
 */
public final class TradeApiClient {

    private static final Logger log = LoggerFactory.getLogger(TradeApiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** An amendment's response names its checker task only in its message: "... TaskId: <id>". */
    private static final Pattern TASK_ID_IN_MSG = Pattern.compile("TaskId:\\s*(\\S+)");

    /** Creates a trade, with {@code ?tradeAction=SUBMIT} or {@code SAVE}: the new trade page sends one of them. */
    public static final String CREATE_TRADE_PATH = "/trades/create";
    public static final String SUBMIT_TRADE_PATH = CREATE_TRADE_PATH + "?tradeAction=SUBMIT";
    public static final String SAVE_TRADE_PATH = CREATE_TRADE_PATH + "?tradeAction=SAVE";
    /** The risk of the trade on the new trade page, before it exists. */
    public static final String CALCULATE_RISK_FOR_NEW_PATH = "/trades/calculate-risk-for-new";
    /** The trades of a blotter: the trade portal loads them after a sign-in, a refresh and a change to a trade. */
    public static final String BLOTTER_TRADES_PATH = "/blotter/trades";
    /** The start of every checker task action: approve or reject, one task or bulk. */
    public static final String CHECKER_TASKS_PATH = "/checker/tasks/";
    /** Triggers a lifecycle event (cancellation, allocation, novation, ...) on one or more trades. */
    public static final String TRIGGER_EVENT_PATH = "/trades/trigger-event";
    private static final String READ_ALL_NOTIFICATIONS_PATH = "/notifications/read-all";

    /**
     * A provisioned trade: its ID and the ID of its latest checker task, blank when it has none. After
     * {@link #submitTrade} or {@link #saveTrade} they are {@code data.trade.id} and {@code data.checkerContext.taskId}.
     */
    public record CreatedTrade(String tradeId, String taskId) {}

    /**
     * One result of a lifecycle event, in the response's order: the trade it ran on ({@code tradeId}), the checker
     * task it created ({@code checkerTaskId}, blank when it executed at once) and the trades it created
     * ({@code childTradeIds}: allocation, novation; empty when none).
     */
    public record EventResult(String tradeId, String checkerTaskId, List<String> childTradeIds) {}

    private TradeApiClient() {
        // Utility class
    }

    /** The trade, as the trade detail page loads it. */
    public static String tradePath(String tradeId) {
        return "/trades/" + tradeId;
    }

    public static String updatePath(String tradeId) {
        return tradePath(tradeId) + "/update";
    }

    /** {@link #updatePath} as the trade detail page books the change. */
    public static String submitUpdatePath(String tradeId) {
        return updatePath(tradeId) + "?tradeAction=SUBMIT";
    }

    /** {@link #updatePath} as the trade detail page saves the change as a draft. */
    public static String saveUpdatePath(String tradeId) {
        return updatePath(tradeId) + "?tradeAction=SAVE";
    }

    /** The risk metrics of the trade, which the trade detail page loads after the trade. */
    public static String riskMetricsPath(String tradeId) {
        return tradePath(tradeId) + "/risk-metrics";
    }

    public static String calculateRiskPath(String tradeId) {
        return tradePath(tradeId) + "/calculate-risk";
    }

    public static String calculatePartialRiskPath(String tradeId) {
        return tradePath(tradeId) + "/calculate-partial-novation-risk";
    }

    public static String calculateAllocationRiskPath(String tradeId) {
        return tradePath(tradeId) + "/calculate-allocation-risk";
    }

    public static String targetGainPath(String tradeId) {
        return tradePath(tradeId) + "/target-gain";
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

    // ── Lifecycle events ─────────────────────────────────────────────────

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
        log.info("[TradeApiClient] triggerEvent: tradeIds='{}', userId='{}', testDataKey='{}'",
                tradeIds, userId, testDataKey);
        JsonNode response = RestApiUtil.post(helper, TRIGGER_EVENT_PATH, userId, OBJECT_MAPPER.writeValueAsString(body));
        return eventResults(response, testDataKey + " on " + tradeIds);
    }

    /**
     * The results of a lifecycle event's response, in its order; {@code what} names the event in the error.
     *
     * @throws AssertionError when the response has no result
     */
    public static List<EventResult> eventResults(JsonNode response, String what) {
        JsonNode results = response.at("/data/results");
        if (!results.isArray() || results.isEmpty()) {
            throw new AssertionError("[TradeApiClient] " + what + " has no results: " + response);
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

    // ── Provisioning: maker submits, checker decides ─────────────────────

    /** The setup body {@code testDataKey} of {@code product} submitted by the maker: a trade pending approval. */
    public static CreatedTrade provisionPendingTrade(RequestResponseHelper helper, String testDataKey,
                                                     ProductDatFiles product, String datPath) throws URISyntaxException {
        return submitTrade(helper, TestUser.MAKER.email(), testDataKey, product, datPath);
    }

    /** The setup body {@code testDataKey} of {@code product} saved by the maker: a draft. */
    public static CreatedTrade provisionDraftTrade(RequestResponseHelper helper, String testDataKey,
                                                   ProductDatFiles product, String datPath) throws URISyntaxException {
        return saveTrade(helper, TestUser.MAKER.email(), testDataKey, product, datPath);
    }

    /** {@link #provisionPendingTrade} approved by the checker: a live trade; its task is the approved one. */
    public static CreatedTrade provisionLiveTrade(RequestResponseHelper helper, String testDataKey,
                                                  ProductDatFiles product, String datPath) throws URISyntaxException {
        CreatedTrade trade = provisionPendingTrade(helper, testDataKey, product, datPath);
        approveTask(helper, trade.taskId(), TestUser.CHECKER.email());
        log.info("[TradeApiClient] live trade provisioned: tradeId='{}'", trade.tradeId());
        return trade;
    }

    /**
     * The event of the setup body {@code testDataKey} triggered by the maker on trade {@code tradeId}, pending the
     * checker: the trade with the event's checker task.
     *
     * @throws AssertionError when the event created no checker task
     */
    public static CreatedTrade provisionPendingEvent(RequestResponseHelper helper, String tradeId, String testDataKey)
            throws JsonProcessingException {
        String taskId = triggerEvent(helper, List.of(tradeId), TestUser.MAKER.email(),
                testDataKey).get(0).checkerTaskId();
        if (taskId.isBlank()) {
            throw new AssertionError("[TradeApiClient] setup " + testDataKey + " on " + tradeId
                    + " created no checker task");
        }
        return new CreatedTrade(tradeId, taskId);
    }

    /**
     * The amendment of the setup body {@code testDataKey} submitted by the maker on trade {@code tradeId}, pending the
     * checker: the trade with the amendment's checker task, read from the response's {@code msg}.
     *
     * @throws AssertionError when the message names no task
     */
    public static CreatedTrade provisionPendingAmendment(RequestResponseHelper helper, String tradeId,
                                                         String testDataKey) {
        JsonNode response = updateTrade(helper, tradeId, TestUser.MAKER.email(), testDataKey);
        String msg = response.path("msg").asText(response.at("/data/msg").asText(""));
        Matcher taskId = TASK_ID_IN_MSG.matcher(msg);
        if (!taskId.find()) {
            throw new AssertionError("[TradeApiClient] setup " + testDataKey + " on " + tradeId
                    + " names no checker task in msg: " + response);
        }
        return new CreatedTrade(tradeId, taskId.group(1).replaceAll("\\.$", ""));
    }

    public static JsonNode approveTask(RequestResponseHelper helper, String taskId, String userId) {
        log.info("[TradeApiClient] approveTask: taskId='{}', userId='{}'", taskId, userId);
        return RestApiUtil.post(helper, CHECKER_TASKS_PATH + taskId + "/approve", userId);
    }

    public static JsonNode rejectTask(RequestResponseHelper helper, String taskId, String userId) {
        log.info("[TradeApiClient] rejectTask: taskId='{}', userId='{}'", taskId, userId);
        return RestApiUtil.post(helper, CHECKER_TASKS_PATH + taskId + "/reject", userId);
    }

    /** Marks every notification of {@code userId} as read. */
    public static JsonNode markAllNotificationsRead(RequestResponseHelper helper, String userId) {
        log.info("[TradeApiClient] markAllNotificationsRead: userId='{}'", userId);
        return RestApiUtil.post(helper, READ_ALL_NOTIFICATIONS_PATH, userId);
    }
}
