package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SeedReportTest {

    @Test
    public void theReportShowsEachBookingsItradeableType() {
        List<JsonNode> entries = List.of(
                Json.parse("""
                        {"kind": "trade", "composition": "FX_FSB", "productId": "FX_PSCRIPT", "status": "booked"}"""),
                Json.parse("""
                        {"kind": "trade", "itradeableType": "T.Recorded", "composition": "FX_TRF",
                         "productId": "FX_TRF", "status": "booked"}"""),
                Json.parse("""
                        {"kind": "trade", "composition": "FX_NEW", "productId": "FX_NEW", "status": "failed"}"""));
        List<JsonNode> filled = SeedReport.withItradeableTypes(entries,
                Map.of("FX_FSB", "Cortex.Finance.Tradeable.PayoffScript.Ctrl.FloatingStrikeB",
                        "FX_TRF", "Cortex.Finance.Tradeable.TRF"));
        assertEquals("Cortex.Finance.Tradeable.PayoffScript.Ctrl.FloatingStrikeB",
                filled.get(0).path("itradeableType").asText());
        assertEquals("T.Recorded", filled.get(1).path("itradeableType").asText());
        assertTrue(filled.get(2).path("itradeableType").isMissingNode());
        String csv = SeedReport.csv(filled);
        assertTrue(csv, csv.startsWith("run,tradeId,env,kind,case,itradeableType,composition,productId,taxonomy,portfolioId,"));
        assertTrue(csv, csv.contains(",,,trade,,Cortex.Finance.Tradeable.PayoffScript.Ctrl.FloatingStrikeB,FX_FSB,FX_PSCRIPT,"));
    }

    @Test
    public void thePageHoldsTheDataWhereTheMarkIs() throws java.io.IOException {
        String template = java.nio.file.Files.readString(
                com.example.module.oreo.testdata.ProjectPaths.projectRoot().resolve(SeedReport.TEMPLATE));
        String json = SeedReport.model("20261120-153000", 2, List.of(Json.parse("""
                {"kind": "trade", "case": "SEED-TRADE-001", "tradeId": "T1", "status": "failed",
                 "error": "</script><b>"}""")));
        String page = SeedReport.page(template, json);
        assertTrue(!page.contains(SeedReport.DATA_MARK));
        assertTrue(page.contains("\\u003c/script>\\u003cb>"));
        assertTrue(page.contains("\"run\":\"20261120-153000\""));
        try {
            SeedReport.page("<html></html>", json);
            org.junit.Assert.fail("a template without the mark");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(SeedReport.DATA_MARK));
        }
    }
}
