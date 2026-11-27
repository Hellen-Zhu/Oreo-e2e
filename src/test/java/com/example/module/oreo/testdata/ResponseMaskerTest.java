package com.example.module.oreo.testdata;

import net.javacrumbs.jsonunit.JsonAssert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ResponseMaskerTest {

    private static final String ANY_STRING = "${json-unit.any-string}";
    private static final String ANY_NUMBER = "${json-unit.any-number}";
    private static final String ANY_BOOLEAN = "${json-unit.any-boolean}";
    private static final String MSG_RULE = "regex:Submitted for checker approval\\. TaskId: CHK-.*";

    private static void assertJson(String expected, String actual) {
        assertEquals(Json.parse(expected), Json.parse(actual));
    }

    private static Map<String, String> rules(String... pathsAndRules) {
        Map<String, String> rules = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndRules.length; i += 2) {
            rules.put(pathsAndRules[i], pathsAndRules[i + 1]);
        }
        return rules;
    }

    private static Function<String, String> vars(String... namesAndValues) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            vars.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return vars::get;
    }

    @Test
    public void typeReplacesLeavesByType() {
        String masked = ResponseMasker.mask("{\"id\":\"T1\",\"n\":5,\"b\":true,\"z\":null}",
                rules("id", "type", "n", "type", "b", "type", "z", "type"), "x");
        assertJson("{\"id\":\"" + ANY_STRING + "\",\"n\":\"" + ANY_NUMBER + "\",\"b\":\"" + ANY_BOOLEAN
                + "\",\"z\":null}", masked);
    }

    @Test
    public void typeTreeKeepsStructure() {
        String masked = ResponseMasker.mask("{\"s\":[{\"d\":\"2026-01-01\",\"v\":1.5}]}",
                rules("s", "type-tree"), "x");
        assertJson("{\"s\":[{\"d\":\"" + ANY_STRING + "\",\"v\":\"" + ANY_NUMBER + "\"}]}", masked);
    }

    @Test
    public void regexChecksTheValue() {
        String masked = ResponseMasker.mask("{\"msg\":\"Submitted for checker approval. TaskId: CHK-1\"}",
                rules("msg", MSG_RULE), "x");
        assertEquals("${json-unit.regex}Submitted for checker approval\\. TaskId: CHK-.*",
                Json.parse(masked).get("msg").asText());
    }

    @Test(expected = IllegalStateException.class)
    public void regexMismatchFails() {
        ResponseMasker.mask("{\"msg\":\"Access denied\"}", rules("msg", MSG_RULE), "x");
    }

    @Test
    public void ignoreWritesIgnore() {
        String masked = ResponseMasker.mask("{\"t\":{\"a\":1}}", rules("t", "ignore"), "x");
        assertJson("{\"t\":\"${json-unit.ignore}\"}", masked);
    }

    @Test
    public void starPathCoversEveryElement() {
        String masked = ResponseMasker.mask("{\"a\":[{\"t\":\"x\"},{\"t\":\"y\"}]}", rules("a[*].t", "type"), "x");
        assertJson("{\"a\":[{\"t\":\"" + ANY_STRING + "\"},{\"t\":\"" + ANY_STRING + "\"}]}", masked);
    }

    private static final String FIXINGS = "{\"data\":{\"fixings\":{\"FX:EURSGD@[BFIX]-1500-JP TYO\":[\"2026-01-02\"],"
            + "\"FX:EURUSD@[BFIX]-1500-JP TYO\":[\"2026-01-02\",\"2026-01-03\"]}}}";

    @Test
    public void starKeyCoversEveryValueOfAnObject() {
        String masked = ResponseMasker.mask(FIXINGS, rules("data.fixings.*", "type"), "x");
        assertJson("{\"data\":{\"fixings\":{\"FX:EURSGD@[BFIX]-1500-JP TYO\":\"" + ResponseMasker.ANY_ARRAY + "\","
                + "\"FX:EURUSD@[BFIX]-1500-JP TYO\":\"" + ResponseMasker.ANY_ARRAY + "\"}}}", masked);
    }

    @Test
    public void pathsParseAndRenderBack() {
        for (String path : List.of("data.trade.udf[*].value", "data.fixings.*", "[0].id", "a.b[2][*]",
                "data.fixings.*[0]")) {
            assertEquals(path, ResponseMasker.render(ResponseMasker.parsePath(path, "x")));
        }
        assertEquals("data.fixings.*", ResponseMasker.render(List.of("data", "fixings", "FX:EURSGD@[BFIX]-1500-JP TYO")));
    }

    @Test
    public void malformedPathsFail() {
        for (String path : List.of("a..b", "a.", "a[x]", "a['b']", "a.[0]", "a[BFIX]", "")) {
            try {
                ResponseMasker.parsePath(path, "x");
                fail("expected a failure for " + path);
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("rule path") || e.getMessage().contains("empty"));
            }
        }
    }

    @Test
    public void typeOnArrayWritesAnyArray() {
        String masked = ResponseMasker.mask("{\"s\":[{\"d\":\"2026-01-01\"}],\"e\":[]}", rules("s", "type", "e", "type"),
                "x");
        assertJson("{\"s\":\"${json-unit.any-array}\",\"e\":\"${json-unit.any-array}\"}", masked);
    }

    @Test
    public void anyArrayMatchesAnArrayOfAnotherLength() {
        String masked = ResponseMasker.mask("{\"s\":[1,2]}", rules("s", "type"), "x");
        JsonAssert.assertJsonEquals(masked, "{\"s\":[1,2,3]}");
    }

    @Test(expected = AssertionError.class)
    public void anyArrayRejectsAnotherType() {
        String masked = ResponseMasker.mask("{\"s\":[1,2]}", rules("s", "type"), "x");
        JsonAssert.assertJsonEquals(masked, "{\"s\":\"1,2\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void typeOnObjectFails() {
        ResponseMasker.mask("{\"t\":{\"a\":1}}", rules("t", "type"), "x");
    }

    @Test(expected = IllegalStateException.class)
    public void pathMatchingNothingFails() {
        ResponseMasker.mask("{\"t\":1}", rules("data.t", "type"), "x");
    }

    @Test(expected = IllegalStateException.class)
    public void unknownRuleFails() {
        ResponseMasker.mask("{\"t\":1}", rules("t", "maybe"), "x");
    }

    @Test
    public void keepsNumbersExactly() {
        String masked = ResponseMasker.mask(
                "{\"code\":200,\"n\":70000.0,\"p\":20150.8410403754144,\"q\":-18726.25346898106}", Map.of(), "x");
        assertTrue(masked, masked.contains("200,"));
        assertTrue(masked, masked.contains("70000.0"));
        assertTrue(masked, masked.contains("20150.8410403754144"));
        assertTrue(masked, masked.contains("-18726.25346898106"));
    }

    @Test
    public void maskedOutputMatchesTheActual() {
        String actual = "{\"code\":200,\"msg\":\"Submitted for checker approval. TaskId: CHK-7\","
                + "\"data\":{\"id\":\"T9\",\"trace\":{\"at\":\"2026-01-01\",\"n\":[1,2]},\"tmp\":{\"q\":true},"
                + "\"amount\":70000.0}}";
        String masked = ResponseMasker.mask(actual,
                rules("msg", MSG_RULE, "data.id", "type", "data.trace", "type-tree", "data.tmp", "ignore"), "x");
        JsonAssert.assertJsonEquals(masked, actual);
    }

    @Test
    public void varRecordsThePlaceholderWhenTheValueIsTheVariable() {
        String masked = ResponseMasker.mask("{\"data\":{\"tradeId\":\"T1\",\"n\":7}}",
                rules("data.tradeId", "var:createdTradeId", "data.n", "var:count"),
                vars("createdTradeId", "T1", "count", "7"), "x");
        assertJson("{\"data\":{\"tradeId\":\"${var:createdTradeId}\",\"n\":\"${var:count}\"}}", masked);
    }

    @Test(expected = IllegalStateException.class)
    public void varWithAnotherValueFails() {
        ResponseMasker.mask("{\"tradeId\":\"T2\"}", rules("tradeId", "var:createdTradeId"),
                vars("createdTradeId", "T1"), "x");
    }

    @Test(expected = IllegalStateException.class)
    public void varWithoutTheVariableFails() {
        ResponseMasker.mask("{\"tradeId\":\"T1\"}", rules("tradeId", "var:createdTradeId"), vars(), "x");
    }

    @Test(expected = IllegalStateException.class)
    public void varOnObjectFails() {
        ResponseMasker.mask("{\"t\":{\"a\":1}}", rules("t", "var:v"), vars("v", "1"), "x");
    }

    @Test
    public void fillVariablesMatchesTheActualWhereTheValueIsTheVariable() {
        String expected = "{\"data\":{\"tradeId\":\"${var:createdTradeId}\",\"n\":\"${var:count}\"},\"code\":200}";
        String actual = "{\"data\":{\"tradeId\":\"T1\",\"n\":7},\"code\":200}";
        String filled = ResponseMasker.fillVariables(expected, actual, vars("createdTradeId", "T1", "count", "7"), "x");
        JsonAssert.assertJsonEquals(filled, actual);
    }

    @Test
    public void fillVariablesWritesTheVariableWhereTheValueDiffers() {
        String filled = ResponseMasker.fillVariables("{\"tradeId\":\"${var:createdTradeId}\"}",
                "{\"tradeId\":\"T2\"}", vars("createdTradeId", "T1"), "x");
        assertEquals("T1", Json.parse(filled).get("tradeId").asText());
    }

    @Test(expected = IllegalStateException.class)
    public void fillVariablesWithoutTheVariableFails() {
        ResponseMasker.fillVariables("{\"tradeId\":\"${var:createdTradeId}\"}", "{\"tradeId\":\"T1\"}", vars(),
                "x");
    }

    @Test
    public void fillVariablesLeavesAFileWithoutVariablesAsItIs() {
        String expected = "{\"n\":70000.0}";
        assertSame(expected, ResponseMasker.fillVariables(expected, "{\"n\":70000.0}", vars(), "x"));
    }

    @Test
    public void recordedVariablesMatchTheActual() {
        String actual = "{\"data\":{\"tradeId\":\"T9\",\"results\":[{\"tradeId\":\"T9\"},{\"tradeId\":\"T9\"}]}}";
        Function<String, String> vars = vars("createdTradeId", "T9");
        Map<String, String> rules = rules("data.tradeId", "var:createdTradeId",
                "data.results[*].tradeId", "var:createdTradeId");
        String masked = ResponseMasker.mask(actual, rules, vars, "x");
        JsonAssert.assertJsonEquals(ResponseMasker.fillVariables(masked, actual, vars, "x"), actual);
    }

    private static final String EVENT_REQUEST = "{\"tradeIds\":[\"T1\"],\"data\":["
            + "{\"key\":\"settleDate\",\"value\":\"2026-11-20\",\"type\":\"Date\"},"
            + "{\"key\":\"amount\",\"value\":\"35000\",\"type\":\"Number\"}]}";

    private static Function<String, String> request(String requestJson) {
        return name -> name.startsWith(ResponseMasker.REQUEST_PREFIX)
                ? ResponseMasker.requestValue(requestJson, name.substring(ResponseMasker.REQUEST_PREFIX.length()), "x")
                : null;
    }

    @Test
    public void requestValueTakesADataEntryByItsKey() {
        assertEquals("2026-11-20", ResponseMasker.requestValue(EVENT_REQUEST, "data.settleDate", "x"));
        assertEquals("T1", ResponseMasker.requestValue(EVENT_REQUEST, "tradeIds[0]", "x"));
        assertEquals("2026-11-20", ResponseMasker.requestValue("{\"basic\":{\"d\":\"2026-11-20\"}}", "basic.d", "x"));
        assertEquals(null, ResponseMasker.requestValue(EVENT_REQUEST, "data.novationDate", "x"));
    }

    @Test
    public void requestRecordsThePathWhenTheValueIsTheRequests() {
        String actual = "{\"data\":{\"settleDate\":\"2026-11-20\",\"amount\":35000.00}}";
        Map<String, String> rules = rules("data.settleDate", "request:data.settleDate",
                "data.amount", "request:data.amount");
        String masked = ResponseMasker.mask(actual, rules, request(EVENT_REQUEST), "x");
        assertJson("{\"data\":{\"settleDate\":\"${request:data.settleDate}\",\"amount\":\"${request:data.amount}\"}}",
                masked);
        JsonAssert.assertJsonEquals(ResponseMasker.fillVariables(masked, actual, request(EVENT_REQUEST), "x"), actual);
    }

    @Test
    public void requestComparesWithTheRequestOfEachRun() {
        String expected = "{\"settleDate\":\"${request:data.settleDate}\"}";
        String nextRun = EVENT_REQUEST.replace("2026-11-20", "2026-11-21");
        String filled = ResponseMasker.fillVariables(expected, "{\"settleDate\":\"2026-11-20\"}", request(nextRun), "x");
        assertJson("{\"settleDate\":\"2026-11-21\"}", filled);
    }

    @Test(expected = IllegalStateException.class)
    public void requestNotRecordedWhenTheValueDiffers() {
        ResponseMasker.mask("{\"settleDate\":\"2026-11-23\"}", rules("settleDate", "request:data.settleDate"),
                request(EVENT_REQUEST), "x");
    }

    @Test(expected = IllegalStateException.class)
    public void requestPathWithoutAValueFails() {
        ResponseMasker.mask("{\"settleDate\":\"2026-11-20\"}", rules("settleDate", "request:data.novationDate"),
                request(EVENT_REQUEST), "x");
    }

    @Test
    public void requestPathNamesOneValue() {
        for (String path : List.of("data[*].value", "data.*", "{basic}.dealDate")) {
            try {
                ResponseMasker.checkRequestPath(path, "x");
                fail(path);
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage(), e.getMessage().contains(path));
            }
        }
    }

    private static Function<String, String> trade(String tradeJson) {
        return name -> name.startsWith(ResponseMasker.TRADE_PREFIX)
                ? ResponseMasker.requestValue(tradeJson, name.substring(ResponseMasker.TRADE_PREFIX.length()), "x")
                : null;
    }

    @Test
    public void tradeRecordsThePathOfTheTradesCreationBody() {
        String tradeBody = "{\"basic\":{\"effectiveTimestamp\":\"2026-11-14\",\"productId\":\"P1\"}}";
        String actual = "{\"data\":{\"trade\":{\"basic\":{\"effectiveTimestamp\":\"2026-11-14\"}}}}";
        String masked = ResponseMasker.mask(actual,
                rules("data.trade.basic.effectiveTimestamp", "trade:basic.effectiveTimestamp"), trade(tradeBody), "x");
        assertJson("{\"data\":{\"trade\":{\"basic\":{\"effectiveTimestamp\":\"${trade:basic.effectiveTimestamp}\"}}}}",
                masked);
        String nextRun = tradeBody.replace("2026-11-14", "2026-11-15");
        assertJson(actual.replace("2026-11-14", "2026-11-15"),
                ResponseMasker.fillVariables(masked, actual, trade(nextRun), "x"));
    }

    @Test
    public void tradeAndRequestAreSeparateSources() {
        try {
            ResponseMasker.mask("{\"d\":\"2026-11-14\"}", rules("d", "trade:basic.effectiveTimestamp"),
                    request("{\"basic\":{\"effectiveTimestamp\":\"2026-11-14\"}}"), "x");
            fail("a trade: rule read the case's request");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("created no trade"));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void tradeWithoutACreatedTradeFailsTheComparison() {
        ResponseMasker.fillVariables("{\"d\":\"${trade:basic.effectiveTimestamp}\"}", "{\"d\":\"2026-11-14\"}",
                name -> null, "x");
    }
}
