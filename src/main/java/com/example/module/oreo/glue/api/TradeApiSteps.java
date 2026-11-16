package com.example.module.oreo.glue.api;

import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ResponseMasker;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.utils.DateUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class TradeApiSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeApiSteps.class);
    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    @Given("^set multi-part form parameter '([^']*)' for product '([^']*)' from test data '([^']*)'$")
    public void setMultiPartFormParameterFromTestData(String partName, String product, String key) {
        String requestWithProductId = setBasicProductId(TestData.setup(key).request(),
                ProductDatFiles.of(product).productId(ctx.find("_tc.productId")), key);
        ctx.helper().request().multiPart(partName, requestWithProductId, "application/json");
        log.info("[TradeApiSteps] set multi-part form parameter '{}' from test data '{}' as '{}'", partName, key,requestWithProductId);
    }

    /**
     *
     * <pre>And set multi-part form parameter 'trade' from current scenario</pre>
     */
    @Given("^set multi-part form parameter '([^']*)' for product '([^']*)' from current scenario$")
    public void setMultiPartFormParameterFromCurrentScenario(String partName, String product) {
        String requestBody = ctx.find("_tc.request");
        if (requestBody == null) {
            throw new AssertionError("[TradeApiSteps] Case '" + ctx.get("_tc.key")
                    + "' has no request in its test data (data/api/<event>.json).");
        }
        String requestWithProductId = setBasicProductId(requestBody,
                ProductDatFiles.of(product).productId(ctx.find("_tc.productId")),
                ctx.get("_tc.key"));
        ctx.helper().request().multiPart(partName, requestWithProductId, "application/json");
        log.info("[TradeApiSteps] set multi-part form parameter '{}' from current scenario ('{}') as '{}'",
                partName, ctx.get("_tc.key"),requestWithProductId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multipart — dat file by product
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Attaches the dat file of the given composition as the {@code datFile} multipart part: the case's dat file
     * ({@code datFile} of its test data), else the composition's default one; writes the composition and its product ID
     * to the report. The name maps to a {@link ProductDatFiles} constant (case-insensitive, spaces
     * replaced with underscores).
     *
     * <p>Adding a new composition requires only a new {@link ProductDatFiles} constant — no new step needed.
     *
     * <pre>And set multi-part dat file for product 'FX_TRF'</pre>
     */
    @Given("^set multi-part dat file for product '([^']*)'$")
    public void setMultiPartDatFileForProduct(String product) {
        ProductDatFiles composition = ProductDatFiles.of(product);
        ctx.write("[" + composition.describe(ctx.find("_tc.productId")) + "]");
        attachDatFile(composition.path(ctx.find("_tc.datFile")));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixings — every past fixing date of the queried trade has its value
    // ─────────────────────────────────────────────────────────────────────

    /**
     * On the trade just retrieved ({@code GET /trades/{id}}): the dates of the trade's fixing schedule before today
     * are exactly the dates that have a numeric value, for every observable of {@code data.fixings}. A missing or
     * {@code null} value fails, and so does a value for a date that is not a past schedule date; a trade with no past
     * fixing date has {@code data.fixings} {@code {}}. Today is left out on both sides (its fixing may not be
     * published yet when the case runs).
     *
     * <p>Where the schedule's fixing dates are is product knowledge, so it is case data:
     * {@code checkData.fixingDates}, a list of response paths ({@code data.tradeDetailsSchedule.Fixing Date}),
     * per product in {@code byProduct}. A path that gives no date fails: the check never passes on an empty schedule.
     *
     * <pre>Then every schedule date before today has a fixing value</pre>
     */
    @Then("^every schedule date before today has a fixing value$")
    public void everyScheduleDateBeforeTodayHasAFixingValue() {
        String tcKey = ctx.get("_tc.key");
        JsonNode paths = TestData.forCase(tcKey).checkData("fixingDates");
        if (paths == null || !paths.isArray() || paths.isEmpty()) {
            throw new AssertionError("[TradeApiSteps] " + tcKey + " has no checkData.fixingDates: give the response"
                    + " paths of the trade's fixing dates in its data file (defaults, or byProduct of its product)");
        }
        List<String> datePaths = new ArrayList<>();
        paths.forEach(p -> datePaths.add(p.asText()));
        JsonNode body = Json.parse(ctx.helper().response().extract().asString());
        LocalDate today = DateUtil.today();
        List<String> problems = pastFixingProblems(body, datePaths, today, tcKey);
        if (!problems.isEmpty()) {
            throw new AssertionError("[TradeApiSteps] fixings of " + tcKey + " (today " + today + "):\n  "
                    + String.join("\n  ", problems));
        }
        ctx.write("[fixings] every schedule date before " + today + " at " + datePaths + " has a value for "
                + fieldNames(body.path("data").path("fixings")));
    }

    /** The problems of {@link #everyScheduleDateBeforeTodayHasAFixingValue}; empty when there is none. */
    static List<String> pastFixingProblems(JsonNode body, List<String> datePaths, LocalDate today, String label) {
        List<String> problems = new ArrayList<>();
        TreeSet<LocalDate> scheduled = new TreeSet<>();
        for (String path : datePaths) {
            for (JsonNode found : ResponseMasker.select(body, path, label)) {
                for (JsonNode date : found.isArray() ? found : List.of(found)) {
                    LocalDate parsed = parseDate(date);
                    if (parsed == null) {
                        problems.add(path + ": '" + date.asText() + "' is not a date (yyyy-MM-dd)");
                    } else {
                        scheduled.add(parsed);
                    }
                }
            }
        }
        if (scheduled.isEmpty()) {
            problems.add("no fixing date at " + datePaths + ": checkData.fixingDates does not match this response");
            return problems;
        }
        SortedSet<LocalDate> expected = scheduled.headSet(today, false);
        JsonNode fixings = body.path("data").path("fixings");
        if (!fixings.isObject()) {
            problems.add("data.fixings is not an object: " + fixings);
            return problems;
        }
        if (expected.isEmpty() && !fixings.isEmpty()) {
            problems.add("no schedule date before today (first " + scheduled.first() + "), but data.fixings has "
                    + fieldNames(fixings));
        }
        if (!expected.isEmpty() && fixings.isEmpty()) {
            problems.add("data.fixings is {}, but " + expected.size() + " schedule dates are before today: "
                    + expected);
        }
        for (Map.Entry<String, JsonNode> observable : fixings.properties()) {
            TreeSet<LocalDate> valued = new TreeSet<>();
            for (JsonNode fixing : observable.getValue()) {
                LocalDate date = parseDate(fixing.path("fixingDate"));
                if (date == null) {
                    problems.add(observable.getKey() + ": a fixing without a fixingDate: " + fixing);
                } else if (fixing.path("value").isNumber()) {
                    valued.add(date);
                }
            }
            valued.remove(today);
            TreeSet<LocalDate> missing = new TreeSet<>(expected);
            missing.removeAll(valued);
            TreeSet<LocalDate> unexpected = new TreeSet<>(valued);
            unexpected.removeAll(expected);
            if (!missing.isEmpty()) {
                problems.add(observable.getKey() + ": no value for the past schedule dates " + missing);
            }
            if (!unexpected.isEmpty()) {
                problems.add(observable.getKey() + ": a value for dates that are not past schedule dates "
                        + unexpected);
            }
        }
        return problems;
    }

    private static LocalDate parseDate(JsonNode node) {
        if (!node.isTextual()) return null;
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────────────────

    private void attachDatFile(String classpathPath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = getClass().getClassLoader();
        URL resource = cl.getResource(classpathPath);
        if (resource == null) {
            throw new AssertionError(
                    "[TradeApiSteps] dat file '" + classpathPath + "' not found on classpath.");
        }
        try {
            ctx.helper().request().multiPart("datFile", Paths.get(resource.toURI()).toFile());
        } catch (Exception e) {
            throw new AssertionError(
                    "[TradeApiSteps] Failed to attach dat file '" + classpathPath + "': " + e.getMessage(), e);
        }
        log.info("[TradeApiSteps] attached dat file '{}'", classpathPath);
        ctx.write("[dat file] " + classpathPath);
    }

    private String setBasicProductId(String requestJson, String productId, String key) {
        try {
            return TradeApiClient.withProductId(requestJson, productId);
        } catch (AssertionError e) {
            throw new AssertionError("[TradeApiSteps] request for key '" + key + "': " + e.getMessage(), e);
        }
    }
}
