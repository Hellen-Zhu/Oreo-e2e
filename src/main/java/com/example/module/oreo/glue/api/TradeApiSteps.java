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
    // Fixings — every schedule date up to today has its fixing value
    // ─────────────────────────────────────────────────────────────────────

    /**
     * On the trade just retrieved ({@code GET /trades/{id}}): every date of the trade's fixing schedule up to today
     * (today included) has a numeric value in {@code data.fixings}, for every fixing source, and no later date has
     * one. The schedule is the gold source: its dates are read at the response path {@code defaultPath}, or at the
     * path the case data gives as {@code overrideVar} (a product whose fixing dates are elsewhere, in its
     * {@code byProduct}), if it is set. A product with several fixing sources gives the response paths of their
     * names as {@code request.fixingSources} of its {@code byProduct} (FX_CO: {@code fixingSource1},
     * {@code fixingSource2}): when the schedule has a date up to today, {@code data.fixings} has exactly one entry
     * per source ({@code FX:<source>}). A product with one source needs none: without it the fixings are {}.
     *
     * <pre>And every date at 'data.tradeDetailsSchedule["Fixing Date"]' up to today has a fixing value, unless 'fixingDates' is set</pre>
     */
    @Then("^every date at '([^']*)' up to today has a fixing value, unless '([^']*)' is set$")
    public void everyDateUpToTodayHasAFixingValue(String defaultPath, String overrideVar) {
        String override = ctx.find(overrideVar);
        String path = override == null || override.isBlank() ? defaultPath : override;
        ctx.helper().response().statusCode(200);
        Object schedule = ctx.helper().response().extract().path(path);
        JsonNode fixings = Json.parse(ctx.helper().response().extract().asString()).path("data").path("fixings");
        Map<String, Object> sources = null;
        JsonNode sourcePaths = ctx.find("_tc.request.fixingSources");
        if (sourcePaths != null) {
            sources = new LinkedHashMap<>();
            for (JsonNode sourcePath : sourcePaths) {
                sources.put(sourcePath.asText(), ctx.helper().response().extract().path(sourcePath.asText()));
            }
        }
        LocalDate today = DateUtil.today();
        List<String> problems = fixingProblems(schedule, path, fixings, sources, today);
        if (!problems.isEmpty()) {
            throw new AssertionError("[TradeApiSteps] fixings of trade " + ctx.find("createdTradeId") + " (today "
                    + today + ", schedule at " + path + "):\n  " + String.join("\n  ", problems));
        }
        ctx.write("[fixings] every date up to " + today + " at " + path + " has a value for "
                + fieldNames(fixings));
    }

    /**
     * The problems of the step above: {@code schedule} is what the response holds at {@code path}; {@code sources}
     * the fixing source names by their response path, {@code null} when the case gives none.
     */
    static List<String> fixingProblems(Object schedule, String path, JsonNode fixings, Map<String, Object> sources,
                                       LocalDate today) {
        List<String> problems = new ArrayList<>();
        if (!(schedule instanceof List<?> list) || list.isEmpty()) {
            problems.add("no schedule dates at " + path + " (" + schedule + "): the path is wrong for this product");
            return problems;
        }
        TreeSet<LocalDate> expected = new TreeSet<>();
        for (Object date : list) {
            LocalDate parsed = parseDate(date);
            if (parsed == null) {
                problems.add(path + ": '" + date + "' is not a date (yyyy-MM-dd)");
            } else if (!parsed.isAfter(today)) {
                expected.add(parsed);
            }
        }
        if (!fixings.isObject()) {
            problems.add("data.fixings is not an object: " + fixings);
            return problems;
        }
        if (expected.isEmpty() && !fixings.isEmpty()) {
            problems.add("no schedule date up to today, but data.fixings has " + fieldNames(fixings));
        }
        if (!expected.isEmpty() && fixings.isEmpty()) {
            problems.add("data.fixings is {}, but " + expected.size() + " schedule dates are up to today: " + expected);
        }
        if (sources != null && !expected.isEmpty()) {
            List<String> keys = fieldNames(fixings);
            for (Map.Entry<String, Object> source : sources.entrySet()) {
                if (!(source.getValue() instanceof String name) || name.isBlank()) {
                    problems.add("no fixing source at " + source.getKey() + " (" + source.getValue() + ")");
                } else if (!keys.removeIf(key -> key.equals(name) || key.endsWith(":" + name))) {
                    problems.add("no fixings for the source " + name + " (" + source.getKey() + ")");
                }
            }
            if (!keys.isEmpty()) {
                problems.add("fixings for sources the trade does not name: " + keys);
            }
        }
        for (Map.Entry<String, JsonNode> source : fixings.properties()) {
            TreeSet<LocalDate> valued = new TreeSet<>();
            for (JsonNode fixing : source.getValue()) {
                JsonNode fixingDate = fixing.path("fixingDate");
                LocalDate date = parseDate(fixingDate.isTextual() ? fixingDate.asText() : null);
                if (date == null) {
                    problems.add(source.getKey() + ": a fixing without a fixingDate: " + fixing);
                } else if (fixing.path("value").isNumber()) {
                    valued.add(date);
                }
            }
            TreeSet<LocalDate> missing = new TreeSet<>(expected);
            missing.removeAll(valued);
            TreeSet<LocalDate> unexpected = new TreeSet<>(valued);
            unexpected.removeAll(expected);
            if (!missing.isEmpty()) {
                problems.add(source.getKey() + ": no value for " + missing.size() + " schedule dates " + missing);
            }
            if (!unexpected.isEmpty()) {
                problems.add(source.getKey() + ": a value for dates that are not schedule dates up to today "
                        + unexpected);
            }
        }
        return problems;
    }

    private static LocalDate parseDate(Object value) {
        if (!(value instanceof String text)) return null;
        try {
            return LocalDate.parse(text);
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
