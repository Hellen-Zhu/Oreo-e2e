package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResponseSuggestionsTest {

    private static List<ResponseSuggestions.Finding> find(String actual, String previous, String request) {
        return ResponseSuggestions.dynamicValues(Json.parse(actual), previous == null ? null : Json.parse(previous),
                request);
    }

    @Test
    public void findsGeneratedValuesAndSkipsPlaceholders() {
        List<ResponseSuggestions.Finding> found = find("""
                { "id": "TRD0001234567", "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                  "dealDate": "2026-10-25", "at": "2026-10-25T09:15:02.123Z", "uuid": "123e4567-e89b-12d3-a456-426614174000",
                  "ts": 1792918502123, "masked": "${json-unit.any-string}", "currencyPair": "USD/CNH",
                  "counterpartyFmId": "900000001", "notional": 70000.0, "ok": true }""", null, null);
        assertEquals(List.of("id", "traceId", "dealDate", "at", "uuid", "ts"),
                found.stream().map(ResponseSuggestions.Finding::path).toList());
        assertTrue(found.stream().allMatch(f -> f.rule().equals("type")));
    }

    @Test
    public void textHoldingAnIdBecomesARegex() {
        List<ResponseSuggestions.Finding> found =
                find("{ \"msg\": \"Submitted for checker approval. TaskId: CHK-123456\" }", null, null);
        assertEquals("regex:Submitted for checker approval\\. TaskId: .*", found.get(0).rule());
    }

    @Test
    public void valueChangedSinceTheCommittedRecordingIsDynamic() {
        List<ResponseSuggestions.Finding> found =
                find("{ \"rate\": 7.1234, \"status\": \"Live\" }", "{ \"rate\": 7.1301, \"status\": \"Live\" }", null);
        assertEquals(1, found.size());
        assertEquals("rate", found.get(0).path());
        assertTrue(found.get(0).reason(), found.get(0).reason().startsWith("changed since the committed recording"));
    }

    @Test
    public void dateTheRequestSendsIsFlaggedWithANote() {
        List<ResponseSuggestions.Finding> found =
                find("{ \"maturity\": \"2027-01-15\" }", null, "{\"maturity\":\"2027-01-15\"}");
        assertTrue(found.get(0).reason(), found.get(0).reason().contains("the request sends this value"));
    }

    @Test
    public void quoteEscapesRegexCharacters() {
        assertEquals("a\\.b\\(c\\)\\[d\\]\\*", ResponseSuggestions.quote("a.b(c)[d]*"));
    }

    @Test
    public void rulesArePlacedInTheNarrowestLayerThatCoversThem() {
        Map<String, JsonNode> responses = Map.of(
                "TC-EV-FX_TRF-API-001", Json.parse("{\"id\":\"TRD0000001\",\"v\":{\"d\":\"2026-01-01\"},\"l\":[{\"t\":\"2026-01-01\"},{\"t\":\"2026-01-02\"}]}"),
                "TC-EV-FX_TRF-API-002", Json.parse("{\"id\":\"TRD0000002\",\"v\":{\"d\":\"2026-01-01\"}}"),
                "TC-EV-FX_PSCRIPT-API-001", Json.parse("{\"id\":\"TRD0000003\"}"),
                "TC-EV-FX_PSCRIPT-API-002", Json.parse("{\"id\":\"TRD0000004\"}"),
                "TC-EV-API-003", Json.parse("{\"error\":\"bad\"}"));
        Map<String, List<ResponseSuggestions.Finding>> findings = new java.util.TreeMap<>();
        responses.forEach((c, r) -> findings.put(c, ResponseSuggestions.dynamicValues(r, null, null)));
        List<ResponseSuggestions.Rule> rules = ResponseSuggestions.placeRules(findings,
                (c, path) -> ResponseSuggestions.has(responses.get(c), path));
        assertEquals(List.of("defaults id", "byProduct.FX_TRF v.d", "row TC-EV-FX_TRF-API-001 l[*].t"),
                rules.stream().map(r -> r.layer() + " " + r.path()).toList());
    }

    @Test
    public void hasFollowsWildcards() {
        JsonNode json = Json.parse("{\"a\":[{\"b\":1},{\"c\":2}]}");
        assertTrue(ResponseSuggestions.has(json, "a[*].c"));
        assertTrue(ResponseSuggestions.has(json, "a[0].b"));
        assertFalse(ResponseSuggestions.has(json, "a[0].c"));
        assertFalse(ResponseSuggestions.has(json, "x"));
    }

    @Test
    public void baseIsTheCaseTheOthersDifferLeastFrom() {
        String big = "\"basic\":{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7,\"h\":8,\"i\":9,\"j\":10,\"k\":11,\"l\":12,\"m\":13,\"n\":14,\"o\":15,\"p\":16}";
        Map<String, JsonNode> responses = Map.of(
                "TC-EV-FX_TRF-API-001", Json.parse("{" + big + ",\"s\":\"New\"}"),
                "TC-EV-FX_TRF-API-002", Json.parse("{" + big + ",\"s\":\"StepIn\"}"),
                "TC-EV-FX_TRF-API-003", Json.parse("{" + big + ",\"s\":\"StepOut\"}"),
                "TC-EV-FX_PSCRIPT-API-001", Json.parse("{" + big + ",\"s\":\"New\"}"),
                "TC-EV-FX_PSCRIPT-API-002", Json.parse("{" + big + ",\"s\":\"StepIn\"}"),
                "TC-EV-API-004", Json.parse("{\"error\":\"bad\"}"));
        List<ResponseSuggestions.Base> bases = ResponseSuggestions.bases(responses);
        assertEquals(List.of("TC-EV-FX_PSCRIPT-API-001", "TC-EV-FX_TRF-API-001"),
                bases.stream().map(ResponseSuggestions.Base::caseId).toList());
        assertTrue(bases.get(0).worthIt());
        assertEquals("TC-EV-<product_type>-API-001", ResponseSuggestions.commonBase(bases));
    }

    @Test
    public void noBaseWhenTheResponsesShareNothing() {
        Map<String, JsonNode> responses = Map.of(
                "TC-EV-FX_TRF-API-001", Json.parse("{\"a\":1}"),
                "TC-EV-FX_TRF-API-002", Json.parse("{\"b\":2}"));
        List<ResponseSuggestions.Base> bases = ResponseSuggestions.bases(responses);
        assertFalse(bases.get(0).worthIt());
        assertNull(ResponseSuggestions.commonBase(bases));
    }
}
