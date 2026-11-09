package com.example.module.oreo.glue.api;

import com.example.module.oreo.config.TestUser;
import com.example.testdata.CaseId;
import com.example.testdata.ExpectedResponses;
import com.example.testdata.Json;
import com.example.testdata.ResponseMasker;
import com.example.testdata.TestData;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import io.restassured.path.json.JsonPath;
import net.javacrumbs.jsonunit.JsonAssert;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Framework-level API response assertions.
 */
public class ApiResponseAssertionSteps {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseAssertionSteps.class);

    private RestServiceResolver.Context ctx;
    private Scenario scenario;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
        this.scenario = scenario;
    }

    @Then("^response body contains a key '([^']*)' as '([^']*)'$")
    public void responseBodyContainsKeyAs(String jsonPath, String expectedLiteral) {
        assertResponseFieldEquals(jsonPath, expectedLiteral);
    }

    @Then("^response body contains a key '([^']*)' with stored variable '([^']*)'$")
    public void responseBodyContainsKeyWithStoredVariable(String jsonPath, String varName) {
        String expected = ctx.get(varName);
        assertResponseFieldEquals(jsonPath, expected);
    }

    @Then("^response body contains a non-empty value at key '([^']*)'$")
    public void responseBodyContainsNonEmptyValueAtKey(String jsonPath) {
        Object value = ctx.helper().response().extract().path(jsonPath);
        if (value == null) {
            throw new AssertionError(
                    "Expected JSONPath '" + jsonPath + "' to resolve to a non-null value, but got null.");
        }
        if (value instanceof String stringValue && stringValue.trim().isEmpty()) {
            throw new AssertionError(
                    "Expected JSONPath '" + jsonPath + "' to resolve to a non-empty string, but got blank.");
        }
        if (value instanceof List<?> listValue && listValue.isEmpty()) {
            throw new AssertionError(
                    "Expected JSONPath '" + jsonPath + "' to resolve to a non-empty list, but it was empty.");
        }
        if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
            throw new AssertionError(
                    "Expected JSONPath '" + jsonPath + "' to resolve to a non-empty object, but it was empty.");
        }
        log.info("[ApiResponseAssertionSteps] asserted '{}' resolves to a non-empty value", jsonPath);
    }

    @Then("^response body contains an element in '([^']*)' with key '([^']*)' with stored variable '([^']*)'$")
    public void responseBodyContainsElementInArrayWithKeyFromStoredVariable(String arrayPath, String key, String varName) {
        String expected = ctx.get(varName);
        assertResponseArrayContainsElementWithKey(arrayPath, key, expected);
    }

    /** Strict comparison with the {@code response} of setup file {@code data/setup/<key>.json}; never records. */
    @Then("^response matches expected from test data '([^']*)'$")
    public void responseMatchesExpectedFromTestData(String key) {
        assertResponseMatches(TestData.setup(key).response(), "setup data '" + key + "'");
    }

    /**
     * Strict comparison with the recorded response of the case, {@code data/api/expected/<event>/<caseId>.json}
     * (a patch file is applied to its base first). A missing file is recorded (the actual response with the case's
     * {@code dynamicFields} masked, in full) and the scenario fails once, so the recording is reviewed; with
     * {@code OREO_EXPECTED=update} (or {@code -Doreo.expected=update}) the file is rewritten without being read and
     * the step passes. A {@code ${var:<name>}} value of the file is the scenario variable of that name. Fails in a
     * case tagged {@link ExpectedResponses#NONE_TAG}, which has no expected file by design.
     */
    @Then("^response matches current scenario$")
    public void responseMatchesCurrentScenario() {
        String tcKey = ctx.find("_tc.key");
        if (tcKey == null) {
            throw new AssertionError("[ApiResponseAssertionSteps] No [TC-...] case ID in the scenario name");
        }
        if (scenario.getSourceTagNames().contains(ExpectedResponses.NONE_TAG)) {
            throw new AssertionError("[ApiResponseAssertionSteps] " + tcKey + " is tagged "
                    + ExpectedResponses.NONE_TAG + " but compares its response with a recording: take the tag off,"
                    + " or use a step that does not end in 'response matches current scenario'");
        }
        CaseId id = CaseId.parse(tcKey);
        ExpectedResponses expectedResponses = ExpectedResponses.standard();
        Path file = expectedResponses.fileFor(id);
        String actual = ctx.helper().response().extract().asString();
        ctx.write("[response:actual] " + tcKey + "\n" + actual);
        log.info("[ApiResponseAssertionSteps] actual response of {}: {}", tcKey, actual);
        boolean update = ExpectedResponses.updateMode();
        Optional<ExpectedResponses.Resolved> recorded = update ? Optional.empty() : expectedResponses.resolve(id);

        if (update || recorded.isEmpty()) {
            String masked = ResponseMasker.mask(actual, TestData.forCase(tcKey).dynamicFields(), this::variable,
                    tcKey);
            expectedResponses.write(id, masked);
            if (recorded.isEmpty() && !update) {
                ctx.write("[expected:recorded] " + file + "\n" + masked);
                throw new AssertionError("Expected response recorded to " + file
                        + ": review it against the requirement, commit it, run again.");
            }
            ctx.write("[expected:updated] " + file + "\n" + masked);
            log.info("[ApiResponseAssertionSteps] expected response of {} updated: {}", tcKey, file);
            return;
        }

        ExpectedResponses.Resolved expected = recorded.get();
        log.info("[ApiResponseAssertionSteps] comparing the response of {} with {}", tcKey, expected.describe());
        try {
            JsonAssert.assertJsonEquals(ResponseMasker.fillVariables(expected.json(), actual, this::variable, tcKey),
                    actual);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + "\nExpected file: " + expected.describe()
                    + "\nIf the change is intended, run again with OREO_EXPECTED=update, compact with"
                    + " TestDataTool compact " + id.eventFile()
                    + " (.claude/skills/maintaining-e2e-test-data), and review the diff.", e);
        }
    }

    // ── Lifecycle event outcome ──────────────────────────────────────────

    /**
     * The response of {@code POST /trades/trigger-event}: 200, {@code status} SUCCESS and at least one result. Stores
     * the trade the event ran on as {@code eventTradeId} and the trades it created as {@code childTradeId1..n}, with
     * their number as {@code childTradeCount}, for the retrieval that is the case's evidence.
     */
    @Then("^the lifecycle event response lists its trades$")
    public void lifecycleEventResponseListsItsTrades() {
        JsonNode response = Json.parse(ctx.helper().response().statusCode(200).extract().asString());
        if (!"SUCCESS".equals(response.path("status").asText())) {
            throw new AssertionError("[ApiResponseAssertionSteps] the event response is not SUCCESS: " + response);
        }
        TradeApiClient.EventResult result = TradeApiClient.eventResults(response, "the event response").get(0);
        ctx.set("eventTradeId", result.tradeId());
        List<String> children = result.childTradeIds();
        ctx.set("childTradeCount", String.valueOf(children.size()));
        for (int i = 0; i < children.size(); i++) {
            ctx.set("childTradeId" + (i + 1), children.get(i));
        }
        ctx.write("[event] eventTradeId = " + result.tradeId() + ", childTradeIds = " + children);
    }

    /**
     * Retrieves every trade the event created ({@code childTradeId1..n}) as the maker and compares the values at
     * {@code responsePath} with the JSON array of the case request's {@code data} entry {@code dataKey}, in any order
     * (the response does not say which created trade is which entry); numbers compare by value.
     */
    @Then("^the new trades of the event have '([^']*)' as in the request's '([^']*)'$")
    public void newTradesOfTheEventHaveValuesFromRequest(String responsePath, String dataKey) {
        List<String> expected = sortedValues(requestDataArray(dataKey));
        int count = Integer.parseInt(ctx.get("childTradeCount"));
        List<JsonNode> actualNodes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String tradeId = ctx.get("childTradeId" + i);
            JsonNode trade = RestApiUtil.get(ctx.helper(), TradeApiClient.tradePath(tradeId), TestUser.MAKER.email());
            JsonNode value = trade.at("/" + responsePath.replace('.', '/'));
            if (value.isMissingNode()) {
                throw new AssertionError("[ApiResponseAssertionSteps] new trade " + tradeId + " has no "
                        + responsePath + ": " + trade);
            }
            actualNodes.add(value);
        }
        List<String> actual = sortedValues(actualNodes);
        if (!expected.equals(actual)) {
            throw new AssertionError("[ApiResponseAssertionSteps] the new trades' " + responsePath + " are " + actual
                    + ", the request's " + dataKey + " are " + expected);
        }
        ctx.write("[event] new trades' " + responsePath + " = " + actual + " as in the request's " + dataKey);
    }

    /** The values of the case request's {@code data} entry {@code key}: its {@code value} is a JSON array as text. */
    private List<JsonNode> requestDataArray(String key) {
        JsonNode request = Json.parse(TestData.forCase(ctx.find("_tc.key")).request());
        for (JsonNode entry : request.path("data")) {
            if (key.equals(entry.path("key").asText())) {
                JsonNode values = Json.parse(entry.path("value").asText());
                List<JsonNode> out = new ArrayList<>();
                values.forEach(out::add);
                return out;
            }
        }
        throw new AssertionError("[ApiResponseAssertionSteps] the case request has no data entry '" + key + "': "
                + request);
    }

    /** Each value as text, a number in its plain form without trailing zeros (35000 = 35000.0), sorted. */
    private static List<String> sortedValues(List<JsonNode> values) {
        List<String> out = new ArrayList<>();
        for (JsonNode value : values) {
            String text = value.asText();
            try {
                out.add(new BigDecimal(text).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                out.add(text);
            }
        }
        out.sort(null);
        return out;
    }

    private void assertResponseMatches(String expected, String sourceLabel) {
        String actual = ctx.helper().response().extract().asString();
        log.info("[ApiResponseAssertionSteps] comparing response snapshot for {}", sourceLabel);
        JsonAssert.assertJsonEquals(ResponseMasker.fillVariables(expected, actual, this::variable, sourceLabel),
                actual);
    }

    /** The scenario variable {@code name} as text, {@code null} when the scenario has not stored it. */
    private String variable(String name) {
        Object value = ctx.find(name);
        return value == null ? null : String.valueOf(value);
    }

    private void assertResponseFieldEquals(String jsonPath, String expected) {
        ctx.helper().response().body(jsonPath, Matchers.hasToString(expected));
        log.info("[ApiResponseAssertionSteps] asserted '{}' = '{}'", jsonPath, expected);
    }

    private void assertResponseArrayContainsElementWithKey(String arrayPath, String key, String expected) {
        String actualBody = ctx.helper().response().extract().asString();
        Object extracted = JsonPath.from(actualBody).get(arrayPath);

        if (!(extracted instanceof List<?> elements)) {
            throw new AssertionError(
                    "Expected JSONPath '" + arrayPath + "' to resolve to a list, but got: "
                            + (extracted == null ? "null" : extracted.getClass().getSimpleName())
            );
        }

        boolean matchFound = elements.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(element -> element.get(key))
                .anyMatch(value -> value != null && expected.equals(String.valueOf(value)));

        if (!matchFound) {
            throw new AssertionError(
                    "Expected array '" + arrayPath + "' to contain an element with key '" + key
                            + "' = '" + expected + "', but none was found. Response body: " + actualBody
            );
        }

        log.info("[ApiResponseAssertionSteps] asserted array '{}' contains key '{}' = '{}'", arrayPath, key, expected);
    }
}
