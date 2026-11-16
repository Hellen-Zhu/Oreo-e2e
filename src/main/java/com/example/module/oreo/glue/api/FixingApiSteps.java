package com.example.module.oreo.glue.api;

import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.utils.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The fixings of a trade: every date of its fixing schedule up to today has a fixing value, checked on the trade
 * retrieved after its online fixings. The snippets of {@code snippet/api/trade_fixings.snippet} call it.
 */
public class FixingApiSteps {

    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    /**
     * On the trade just retrieved ({@code GET /trades/{id}}): every date of the trade's fixing schedule up to today
     * (today included) has a numeric value in {@code data.fixings}, for every fixing source, and no later date has
     * one. The schedule is the gold source: its dates are read at the response path the case data stores as
     * {@code pathVar} ({@code request.fixingDates}: the default in {@code defaults}, a product whose fixing dates are
     * elsewhere overrides it in its {@code byProduct}). A product with several fixing sources gives the response paths of their
     * names as {@code request.fixingSources} of its {@code byProduct} (FX_CO: {@code fixingSource1},
     * {@code fixingSource2}): when the schedule has a date up to today, {@code data.fixings} has exactly one entry
     * per source ({@code FX:<source>}). A product with one source needs none: without it the fixings are {}.
     *
     * <pre>And every date at the stored path 'fixingDates' up to today has a fixing value</pre>
     */
    @Then("^every date at the stored path '([^']*)' up to today has a fixing value$")
    public void everyDateUpToTodayHasAFixingValue(String pathVar) {
        String path = ctx.find(pathVar);
        if (path == null || path.isBlank()) {
            throw new AssertionError("[FixingApiSteps] no '" + pathVar + "' in the case data of " + ctx.find("_tc.key")
                    + ": the response path of the schedule's fixing dates (request." + pathVar + ", in defaults)");
        }
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
            throw new AssertionError("[FixingApiSteps] fixings of trade " + ctx.find("createdTradeId") + " (today "
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
}
