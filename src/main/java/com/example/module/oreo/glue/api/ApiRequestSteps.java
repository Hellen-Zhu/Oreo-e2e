package com.example.module.oreo.glue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.SetupData;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.utils.DateUtil;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiRequestSteps {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestSteps.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** A share of the trade's notional in a case request's data value: {@code 50%}. */
    private static final Pattern SHARE = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*%\\s*$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    /**
     * Extracts a value from the response body by JSONPath and stores it as a scenario variable.
     *
     * <pre>Given store response field 'data.id' as 'reasonId'</pre>
     */
    @Given("^store response field '([^']*)' as '([^']*)'$")
    public void storeResponseField(String jsonPath, String varName) {
        Object value = ctx.helper().response().extract().path(jsonPath);
        if (value == null) {
            throw new AssertionError(
                    "[ApiRequestSteps] JSONPath '" + jsonPath + "' returned null — cannot store as '" + varName + "'");
        }
        String stringValue = String.valueOf(value);
        ctx.set(varName, stringValue);
        ctx.write("[stored] " + varName + " = " + stringValue);
        log.info("[ApiRequestSteps] stored '{}' = '{}'", varName, stringValue);
    }

    /**
     * Stores a literal value as a scenario variable.
     *
     * <pre>Given store api variable value 'NON-EXISTENT' as 'nonExistentTradeId'</pre>
     */
    @Given("^store api variable value '([^']*)' as '([^']*)'$")
    public void storeLiteralValue(String value, String varName) {
        ctx.set(varName, value);
        ctx.write("[stored] " + varName + " = " + value);
        log.info("[ApiRequestSteps] stored '{}' = '{}'", varName, value);
    }

    @Given("^store dynamic api timestamp with prefix '([^']*)' as '([^']*)'$")
    public void storeDynamicApiTimestamp(String prefix, String varName) {
        String value = prefix + "-" + DateUtil.format(DateUtil.now(), "MMddHHmmss");
        ctx.set(varName, value);
        ctx.write("[stored] " + varName + " = " + value);
        log.info("[ApiRequestSteps] stored dynamic timestamp '{}' = '{}' using prefix='{}'",
                varName, value, prefix);
    }

    /**
     * Sets the X-User-Id header of the request being built to the e-mail of a role ({@code maker}, {@code checker},
     * {@code admin}, {@code user}; see {@code TestUser}) or to an e-mail as given. Called by
     * {@code start a new api request as user '...'}.
     *
     * <pre>And set the user header for 'checker'</pre>
     */
    @Given("^set the user header for '([^']*)'$")
    public void setUserHeader(String roleOrEmail) {
        ctx.write("[user] " + RestApiUtil.setUserHeader(ctx.helper(), roleOrEmail));
    }

    /**
     * Stores the e-mail of a role as a scenario variable, so SQL can reference it as {@code \${name}}; the e-mail is
     * the one of the environment the run uses.
     *
     * <pre>And store the e-mail of user 'maker' as 'blotterUserEmail'</pre>
     */
    @Given("^store the e-mail of user '([^']*)' as '([^']*)'$")
    public void storeUserEmail(String role, String varName) {
        String email = TestUser.of(role).email();
        ctx.set(varName, email);
        ctx.write("[stored] " + varName + " = " + email);
        log.info("[ApiRequestSteps] stored '{}' = '{}'", varName, email);
    }

    /**
     * Sets the X-Request-Id header of the request being built to a new UUID and stores it as {@code requestId}, so a
     * failure can be traced in the server logs. Called by {@code start a new api request as user '...'}.
     *
     * <pre>And set a new request id header</pre>
     */
    @Given("^set a new request id header$")
    public void setNewRequestIdHeader() {
        String requestId = RestApiUtil.setNewRequestId(ctx.helper());
        ctx.set("requestId", requestId);
        ctx.write("[request id] " + requestId);
    }

    @Given("^set path parameter '([^']*)' from stored variable '([^']*)'$")
    public void setPathParamFromStoredVariable(String paramName, String varName) {
        String value = ctx.get(varName);
        setPathParam(paramName, value, "stored variable '" + varName + "'");
    }

    @Given("^set path parameter '([^']*)' to '([^']*)'$")
    public void setPathParamToValue(String paramName, String value) {
        setPathParam(paramName, value, "literal value");
    }

    /**
     * The case's product ID ({@code productId} of its test data), else the composition's default one
     * ({@link ProductDatFiles#productId(String)}), as a path parameter.
     */
    @Given("^set path parameter '([^']*)' to the product ID of '([^']*)'$")
    public void setPathParamToProductId(String paramName, String composition) {
        setPathParam(paramName, ProductDatFiles.of(composition).productId(ctx.find("_tc.productId")),
                "product ID of '" + composition + "'");
    }

    @Given("^set query parameter '([^']*)' as '([^']*)'$")
    public void setQueryParamAsValue(String paramName, String value) {
        setQueryParam(paramName, value, "literal value");
    }

    @Given("^set query parameter '([^']*)' from stored variable '([^']*)'$")
    public void setQueryParamFromStoredVariable(String paramName, String varName) {
        String value = ctx.get(varName);
        setQueryParam(paramName, value, "stored variable '" + varName + "'");
    }

    /**
     * Promotes a stored scenario variable into a DatabaseGlue query token
     * ({@code queryToken.<name>}) so it can be referenced as {@code ${name}} in SQL DataTables.
     *
     * <pre>Given set query token 'tradeId' from stored variable 'createdTradeId'</pre>
     */
    @Given("^set query token '([^']*)' from stored variable '([^']*)'$")
    public void setQueryTokenFromStoredVariable(String tokenName, String varName) {
        String value = ctx.get(varName);
        ctx.set("queryToken." + tokenName, value);
        ctx.write("[query token] " + tokenName + " = " + value);
        log.info("[ApiRequestSteps] set query token '{}' = '{}'", tokenName, value);
    }

    @Given("^set request body from test data '([^']*)'$")
    public void setRequestBodyFromTestData(String key) {
        setRequestBody(
                requestBodyFromTestData(key),
                "test data '" + key + "'"
        );
    }

    @Given("^set json request body from test data '([^']*)' at json path '([^']*)' from stored variable '([^']*)'$")
    public void setJsonRequestBodyFromTestDataAtPath(String key, String jsonPath, String varName) {
        setJsonRequestBodyAtPath(
                requestBodyFromTestData(key),
                jsonPath,
                ctx.get(varName),
                "setup data '" + key + "'",
                varName
        );
    }

    @Given("^set json request body from current scenario at json path '([^']*)' from stored variable '([^']*)'$")
    public void setJsonRequestBodyFromCurrentScenarioAtPath(String jsonPath, String varName) {
        setJsonRequestBodyAtPath(
                requestBodyFromCurrentScenario(),
                jsonPath,
                ctx.get(varName),
                currentScenarioLabel(),
                varName
        );
    }

    @Given("^set request body from current scenario$")
    public void setRequestBodyFromCurrentScenario() {
        setRequestBody(
                requestBodyFromCurrentScenario(),
                currentScenarioLabel()
        );
    }

    /**
     * When the case request gives an amount as a share of the trade's notional ({@code "50%"}, {@code "[60%, 30%,
     * 10%]"} in a {@code data} value), reads the notional of the stored trade ({@code data.trade.basic.notionalAmount})
     * for the body step to compute the amounts from; else does nothing. A step of its own, before the request is
     * started: the read is a request too.
     *
     * <pre>Given the notional of the trade stored as 'createdTradeId' is read when the request gives shares of it</pre>
     */
    @Given("^the notional of the trade stored as '([^']*)' is read when the request gives shares of it$")
    public void readNotionalForShares(String varName) throws Exception {
        String request = ctx.find("_tc.request");
        if (request == null || request.isBlank() || !hasShares(MAPPER.readTree(request))) {
            return;
        }
        String tradeId = ctx.get(varName);
        JsonNode notional = RestApiUtil.get(ctx.helper(), TradeApiClient.tradePath(tradeId), TestUser.MAKER.email())
                .path("data").path("trade").path("basic").path("notionalAmount");
        if (!notional.isNumber()) {
            throw new AssertionError("[ApiRequestSteps] the request of " + ctx.get("_tc.key") + " gives shares of the"
                    + " notional, and trade " + tradeId + " has no numeric data.trade.basic.notionalAmount: " + notional);
        }
        ctx.set(notionalKey(tradeId), notional.decimalValue().toPlainString());
        ctx.write("[request] notional of trade " + tradeId + " = " + notional.decimalValue().toPlainString());
    }

    @Given("^set json request body from current scenario with trade id from stored variable '([^']*)'$")
    public void setEventRequestBodyFromCurrentScenarioWithTradeId(String varName) {
        setJsonRequestBodyWithTradeId(
                requestBodyFromCurrentScenario(),
                ctx.get(varName),
                currentScenarioLabel(),
                varName
        );
    }

    @Given("^set json request body from test data '([^']*)' with trade id from stored variable '([^']*)'$")
    public void setEventRequestBodyFromTestDataWithTradeId(String key, String varName) {
        setJsonRequestBodyWithTradeId(
                TestData.setup(key).request(),
                ctx.get(varName),
                "test data '" + key + "'",
                varName
        );
    }

    @Given("^set json request body from current scenario with task ids from stored variables '([^']*)' and '([^']*)'$")
    public void setJsonRequestBodyFromCurrentScenarioWithTaskIds(String varName1, String varName2) {
        String requestBody = requestBodyFromCurrentScenario();
        String taskId1 = ctx.get(varName1);
        String taskId2 = ctx.get(varName2);
        try {
            ObjectNode body = (ObjectNode) MAPPER.readTree(requestBody);
            body.putArray("taskIds").add(taskId1).add(taskId2);
            ctx.helper().request().body(MAPPER.writeValueAsString(body));
            log.info("[ApiRequestSteps] set bulk task request body from current scenario ('{}') with taskIds from '{}' and '{}'",
                    ctx.get("_tc.key"), varName1, varName2);
        } catch (Exception e) {
            throw new AssertionError(
                    "[ApiRequestSteps] Failed to inject task IDs into bulk request body for scenario key '"
                    + ctx.get("_tc.key") + "': " + e.getMessage(), e);
        }
    }

    private void setPathParam(String paramName, String value, String valueSourceLabel) {
        if (value == null || value.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] " + valueSourceLabel
                    + " is null/blank — cannot set path parameter '" + paramName + "'");
        }
        ctx.write("[request:path] " + paramName + " = " + value);
        ctx.helper().request().pathParam(paramName, value);
        log.info("[ApiRequestSteps] set path param '{}' = '{}'", paramName, value);
    }

    private void setQueryParam(String paramName, String value, String valueSourceLabel) {
        if (value == null || value.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] " + valueSourceLabel
                    + " is null/blank — cannot set query parameter '" + paramName + "'");
        }
        ctx.write("[request:query] " + paramName + " = " + value);
        ctx.helper().request().queryParam(paramName, value);
        log.info("[ApiRequestSteps] set query param '{}' = '{}'", paramName, value);
    }

    /** The {@code request} of setup file {@code data/setup/<key>.json}. */
    private String requestBodyFromTestData(String key) {
        SetupData setup = TestData.setup(key);
        if (!setup.hasRequest()) {
            throw new AssertionError("[ApiRequestSteps] Setup data '" + key + "' has no request.");
        }
        return requireRequestBody(setup.request(), "setup data '" + key + "'");
    }

    /** The merged request of the case ({@code _tc.request}, set by the hook only when the case has one). */
    private String requestBodyFromCurrentScenario() {
        String tcKey = ctx.get("_tc.key");
        String requestBody = ctx.find("_tc.request");
        return requireRequestBody(requestBody, "current scenario (TC-ID='" + tcKey + "')");
    }

    private String requireRequestBody(String requestBody, String sourceLabel) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] No request body found from " + sourceLabel + ".");
        }
        return requestBody;
    }

    private void setRequestBody(String requestBody, String sourceLabel) {
        ctx.helper().request().body(requestBody);
        ctx.write("[request:body] source=" + sourceLabel + "\n" + requestBody);
        log.info("[ApiRequestSteps] set request body from {}: {}", sourceLabel, requestBody);
    }

    private void setJsonRequestBodyAtPath(
            String requestBody,
            String jsonPath,
            String value,
            String sourceLabel,
            String varName) {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] JSON path is null/blank — cannot inject stored variable '"
                    + varName + "' into request body from " + sourceLabel);
        }
        if (value == null || value.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] stored variable '" + varName
                    + "' is null/blank — cannot inject into request body from " + sourceLabel);
        }

        try {
            JsonNode root = MAPPER.readTree(requestBody);
            if (!(root instanceof ObjectNode body)) {
                throw new IllegalArgumentException("request body root must be a JSON object");
            }

            String[] pathParts = jsonPath.split("\\.", -1);
            ObjectNode parent = body;
            for (int i = 0; i < pathParts.length - 1; i++) {
                JsonNode child = parent.get(pathParts[i]);
                if (!(child instanceof ObjectNode)) {
                    throw new IllegalArgumentException(
                            "JSON path parent does not resolve to an object: '" + jsonPath + "'");
                }
                parent = (ObjectNode) child;
            }

            String leaf = pathParts[pathParts.length - 1];
            if (leaf.isBlank()) {
                throw new IllegalArgumentException("JSON path has a blank field: '" + jsonPath + "'");
            }
            parent.put(leaf, value);
            setRequestBody(
                    MAPPER.writeValueAsString(root),
                    sourceLabel + " with JSON path '" + jsonPath
                    + "' from stored variable '" + varName + "'"
            );
        } catch (Exception e) {
            throw new AssertionError(
                    "[ApiRequestSteps] Failed to inject stored variable '" + varName
                    + "' at JSON path '" + jsonPath + "' into request body from "
                    + sourceLabel + ": " + e.getMessage(), e);
        }
    }

    private String currentScenarioLabel() {
        return "current scenario (TC-ID='" + ctx.get("_tc.key") + "')";
    }

    private void setJsonRequestBodyWithTradeId(
            String requestBody,
            String tradeId,
            String sourceLabel,
            String varName) {
        if (tradeId == null || tradeId.isBlank()) {
            throw new AssertionError(
                    "[ApiRequestSteps] stored variable '" + varName
                    + "' is null/blank — cannot inject tradeId into request body from " + sourceLabel);
        }

        try {
            ObjectNode body = (ObjectNode) MAPPER.readTree(requestBody);
            body.putArray("tradeIds").add(tradeId);
            if (hasShares(body)) {
                String notional = ctx.find(notionalKey(tradeId));
                if (notional == null) {
                    throw new AssertionError("[ApiRequestSteps] the request gives shares of the notional of trade "
                            + tradeId + ", which was not read: build the request with 'build json request from"
                            + " current scenario with trade id from stored variable ...'");
                }
                for (JsonNode entry : body.path("data")) {
                    String value = entry.path("value").asText();
                    if (isShares(value)) {
                        ((ObjectNode) entry).put("value", amountsOfShares(value, new BigDecimal(notional)));
                    }
                }
            }
            String finalRequestBody = MAPPER.writeValueAsString(body);
            // what was sent, amounts included: the assertions on the event compare with it
            ctx.set("_tc.sentRequest", finalRequestBody);

            ctx.helper().request().body(finalRequestBody);
            ctx.write("[request:body] source=" + sourceLabel + "\n" + finalRequestBody);
            log.info("[ApiRequestSteps] set event request body from {} with tradeId='{}' (varName='{}'): {}",
                    sourceLabel, tradeId, varName, finalRequestBody);
        } catch (Exception e) {
            throw new AssertionError(
                    "[ApiRequestSteps] Failed to inject tradeId into request body from "
                    + sourceLabel + ": " + e.getMessage(), e);
        }
    }

    private static String notionalKey(String tradeId) {
        return "_notional." + tradeId;
    }

    /** Whether a {@code data} value of the request is a share or a list of shares of the notional. */
    private static boolean hasShares(JsonNode request) {
        for (JsonNode entry : request.path("data")) {
            if (isShares(entry.path("value").asText())) return true;
        }
        return false;
    }

    /** {@code 50%}, or a list of them, {@code [60%, 30%, 10%]}. */
    static boolean isShares(String value) {
        List<String> items = listItems(value);
        if (items == null) return SHARE.matcher(value).matches();
        if (items.isEmpty()) return false;
        for (String item : items) {
            if (!SHARE.matcher(item).matches()) return false;
        }
        return true;
    }

    /**
     * The amounts of {@code value}'s shares of {@code notional}, as the request writes numbers ({@code 35000},
     * {@code 3468.75}): each rounded to the cent; when a list's shares sum to 100%, its last amount is the rest of the
     * notional rounded to the cent, so that the amounts sum to it.
     */
    static String amountsOfShares(String value, BigDecimal notional) {
        List<String> items = listItems(value);
        if (items == null) return plain(share(value, notional));
        List<BigDecimal> percents = new ArrayList<>();
        for (String item : items) percents.add(percent(item));
        boolean whole = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(HUNDRED) == 0;
        List<String> amounts = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            BigDecimal amount = whole && i == items.size() - 1
                    ? notional.setScale(2, RoundingMode.HALF_UP).subtract(allocated)
                    : share(items.get(i), notional);
            allocated = allocated.add(amount);
            amounts.add(plain(amount));
        }
        return "[" + String.join(", ", amounts) + "]";
    }

    /** The items of a list written {@code [a, b]}, else null. */
    private static List<String> listItems(String value) {
        String text = value.trim();
        if (!text.startsWith("[") || !text.endsWith("]")) return null;
        String inner = text.substring(1, text.length() - 1).trim();
        List<String> items = new ArrayList<>();
        if (inner.isEmpty()) return items;
        for (String item : inner.split(",")) items.add(item.trim());
        return items;
    }

    private static BigDecimal percent(String share) {
        Matcher m = SHARE.matcher(share);
        if (!m.matches()) throw new AssertionError("[ApiRequestSteps] not a share of the notional: " + share);
        return new BigDecimal(m.group(1));
    }

    private static BigDecimal share(String share, BigDecimal notional) {
        return notional.multiply(percent(share)).divide(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
