package com.example.module.oreo.glue.api;

import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProductDatFiles;
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
import java.util.LinkedHashMap;
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
    // Fixings — every past fixing date of the retrieved trade has its value
    // ─────────────────────────────────────────────────────────────────────

    /**
     * On the trade just retrieved ({@code GET /trades/{id}}, after the online fixings): every date of the trade's
     * fixing schedule before today has a numeric value in {@code data.fixings}, for every observable, and no other
     * date has one. Today is left out (its fixing may not be published yet when the case runs).
     *
     * <p>The fixing schedule is found in the response, so no product needs a setting: it is the date list of the
     * response (a {@code tradeDetailsSchedule} column, the instrument's {@code resetDates} ...) that holds every
     * {@code fixingDate}. A trade without fixings ({@code {}}) has no date before today in any of its date lists.
     *
     * <pre>Then every schedule date before today has a fixing value</pre>
     */
    @Then("^every schedule date before today has a fixing value$")
    public void everyScheduleDateBeforeTodayHasAFixingValue() {
        ctx.helper().response().statusCode(200);
        JsonNode body = Json.parse(ctx.helper().response().extract().asString());
        LocalDate today = DateUtil.today();
        List<String> report = new ArrayList<>();
        List<String> problems = pastFixingProblems(body, today, report);
        report.forEach(line -> ctx.write("[fixings] " + line));
        if (!problems.isEmpty()) {
            throw new AssertionError("[TradeApiSteps] fixings of trade " + ctx.find("createdTradeId") + " (today "
                    + today + "):\n  " + String.join("\n  ", problems));
        }
    }

    /** The problems of the step above; empty when there is none. {@code report} gets what was compared. */
    static List<String> pastFixingProblems(JsonNode body, LocalDate today, List<String> report) {
        List<String> problems = new ArrayList<>();
        JsonNode fixings = body.path("data").path("fixings");
        if (!fixings.isObject()) {
            problems.add("data.fixings is not an object: " + fixings);
            return problems;
        }
        Map<String, TreeSet<LocalDate>> dateLists = new LinkedHashMap<>();
        collectDateLists(body.path("data"), "data", dateLists);

        if (fixings.isEmpty()) {
            dateLists.forEach((path, dates) -> {
                if (!dates.headSet(today, false).isEmpty()) {
                    problems.add("data.fixings is {}, but " + path + " has dates before today "
                            + dates.headSet(today, false));
                }
            });
            if (problems.isEmpty()) report.add("no fixings, and no date list has a date before " + today);
            return problems;
        }

        Map<String, TreeSet<LocalDate>> valued = new LinkedHashMap<>();
        TreeSet<LocalDate> listed = new TreeSet<>();
        for (Map.Entry<String, JsonNode> observable : fixings.properties()) {
            TreeSet<LocalDate> dates = new TreeSet<>();
            for (JsonNode fixing : observable.getValue()) {
                LocalDate date = parseDate(fixing.path("fixingDate"));
                if (date == null) {
                    problems.add(observable.getKey() + ": a fixing without a fixingDate: " + fixing);
                    continue;
                }
                listed.add(date);
                if (fixing.path("value").isNumber()) dates.add(date);
            }
            dates.remove(today);
            valued.put(observable.getKey(), dates);
        }

        // The fixing schedule: of the date lists holding every listed date, the one with the fewest dates before today
        String schedulePath = null;
        for (Map.Entry<String, TreeSet<LocalDate>> list : dateLists.entrySet()) {
            if (list.getValue().containsAll(listed) && (schedulePath == null
                    || list.getValue().headSet(today).size() < dateLists.get(schedulePath).headSet(today).size())) {
                schedulePath = list.getKey();
            }
        }
        if (schedulePath == null) {
            problems.add("no date list of the trade holds every fixingDate " + listed + ": a fixing on a date"
                    + " that is not in the schedule");
            return problems;
        }
        SortedSet<LocalDate> expected = dateLists.get(schedulePath).headSet(today, false);
        report.add(expected.size() + " schedule dates before " + today + " at " + schedulePath + ", observables "
                + valued.keySet());
        for (Map.Entry<String, TreeSet<LocalDate>> observable : valued.entrySet()) {
            TreeSet<LocalDate> missing = new TreeSet<>(expected);
            missing.removeAll(observable.getValue());
            TreeSet<LocalDate> unexpected = new TreeSet<>(observable.getValue());
            unexpected.removeAll(expected);
            if (!missing.isEmpty()) {
                problems.add(observable.getKey() + ": no value for the past schedule dates " + missing + " ("
                        + schedulePath + ")");
            }
            if (!unexpected.isEmpty()) {
                problems.add(observable.getKey() + ": a value for dates not before today " + unexpected);
            }
        }
        return problems;
    }

    /** Every non-empty array of the response, outside {@code data.fixings}, whose elements are all dates. */
    private static void collectDateLists(JsonNode node, String path, Map<String, TreeSet<LocalDate>> out) {
        if (path.equals("data.fixings")) return;
        if (node.isObject()) {
            node.properties().forEach(field -> collectDateLists(field.getValue(), path + "." + field.getKey(), out));
        } else if (node.isArray() && !node.isEmpty()) {
            TreeSet<LocalDate> dates = new TreeSet<>();
            for (JsonNode element : node) {
                LocalDate date = parseDate(element);
                if (date == null) {
                    for (int i = 0; i < node.size(); i++) {
                        collectDateLists(node.get(i), path + "[" + i + "]", out);
                    }
                    return;
                }
                dates.add(date);
            }
            out.put(path, dates);
        }
    }

    private static LocalDate parseDate(JsonNode node) {
        if (!node.isTextual()) return null;
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
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
