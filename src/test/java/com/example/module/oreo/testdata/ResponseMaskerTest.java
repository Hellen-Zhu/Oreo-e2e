package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
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
    public void quotedKeyNamesAKeyWithBrackets() {
        String masked = ResponseMasker.mask(FIXINGS, rules("data.fixings['FX:EURSGD@[BFIX]-1500-JP TYO']", "type"), "x");
        assertJson("{\"data\":{\"fixings\":{\"FX:EURSGD@[BFIX]-1500-JP TYO\":\"" + ResponseMasker.ANY_ARRAY + "\","
                + "\"FX:EURUSD@[BFIX]-1500-JP TYO\":[\"2026-01-02\",\"2026-01-03\"]}}}", masked);
    }

    @Test
    public void quotedKeyThenIndex() {
        String masked = ResponseMasker.mask(FIXINGS, rules("data.fixings['FX:EURUSD@[BFIX]-1500-JP TYO'][1]", "type"), "x");
        JsonNode values = Json.parse(masked).at("/data/fixings/FX:EURUSD@[BFIX]-1500-JP TYO");
        assertEquals("2026-01-02", values.get(0).asText());
        assertEquals(ANY_STRING, values.get(1).asText());
    }

    @Test
    public void pathsParseAndRenderBack() {
        for (String path : List.of("data.trade.udf[*].value", "data.fixings.*", "[0].id", "a.b[2][*]",
                "data.fixings['FX:EURSGD@[BFIX]-1500-JP TYO'].x", "data.fixings['a.b']")) {
            assertEquals(path, ResponseMasker.render(ResponseMasker.parsePath(path, "x")));
        }
    }

    @Test
    public void malformedPathsFail() {
        for (String path : List.of("a..b", "a.", "a[x]", "a['']", "a['b", "a.[0]", "a[BFIX]", "")) {
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
}
