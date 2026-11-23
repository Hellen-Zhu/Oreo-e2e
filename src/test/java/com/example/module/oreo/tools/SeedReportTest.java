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

    @Test
    public void eachSeedCaseHasTheDatFileItBooks() throws java.io.IOException {
        List<SeedReport.CaseDat> dats = SeedReport.caseDats(
                com.example.module.oreo.testdata.ProjectPaths.projectRoot(),
                com.example.module.oreo.testdata.SeedData.catalog());
        Map<String, SeedReport.CaseDat> byCase = new java.util.HashMap<>();
        dats.forEach(d -> byCase.put(d.caseId(), d));
        assertEquals("one row per seed case, commented-out rows too", dats.size(), byCase.size());
        assertEquals("src/test/resources/seed/dat/FXBO/FX_BO_MULTIDIGI_Precious.dat",
                byCase.get("SEED-TRADE-008").path());
        assertEquals("src/test/resources/seed/dat/FXBO/FX_BO_MULTIDIGI.dat", byCase.get("SEED-TRADE-009").path());
        assertEquals("src/test/resources/seed/dat/FX_TRF/FX_TRF_DLYSTTL.dat", byCase.get("SEED-TRADE-037").path());
        for (SeedReport.CaseDat d : dats) {
            assertTrue(d.caseId() + " books a seed file, never data/dat: " + d.path(),
                    d.path().startsWith(SeedReport.RESOURCES + com.example.module.oreo.testdata.SeedData.SEED_DAT_DIR
                            + d.composition() + "/"));
            if (d.state() == SeedReport.DatState.shared) {
                assertTrue(d.caseId(), d.sameAs() != null && !d.sameAs().equals(d.caseId()));
            }
        }
    }

    @Test
    public void aStepInIsReportedAmongTheEventsWhateverItsRunWrote() {
        List<JsonNode> in = List.of(
                Json.parse("{\"kind\": \"trade\", \"case\": \"SEED-TRADE-001\"}"),
                Json.parse("{\"kind\": \"trade\", \"variant\": \"stepin-full\"}"),
                Json.parse("{\"kind\": \"event\", \"case\": \"SEED-STEPIN-002\", \"variant\": \"stepin-partial\"}"),
                Json.parse("{\"kind\": \"event\", \"event\": \"termination\"}"));
        List<String> kinds = SeedReport.asEvents(in).stream().map(e -> e.path("kind").asText()).toList();
        assertEquals(List.of("trade", "event", "event", "event"), kinds);
        assertEquals("the manifest's line is left as it was", "trade", in.get(1).path("kind").asText());
    }

    @Test
    public void aReportMergesTheRunsItNamesTheLatestLineWinning() {
        List<String> all = List.of("20261120-100000", "20261120-110000", "20261120-120000");
        assertEquals(List.of("20261120-120000"), SeedReport.runs(null, all));
        assertEquals(all, SeedReport.runs("all", all));
        assertEquals(List.of("20261120-110000", "20261120-120000"), SeedReport.runs("20261120-110000..", all));
        assertEquals("oldest first, whatever the order given, each once",
                List.of("20261120-100000", "20261120-120000"),
                SeedReport.runs("20261120-120000,20261120-100000,20261120-120000", all));
        for (String bad : List.of("20261120-130000", "20261120-100000,nope", "nope..")) {
            try {
                SeedReport.runs(bad, all);
                org.junit.Assert.fail(bad + " is refused");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("no run"));
            }
        }
        List<JsonNode> merged = SeedReport.latest(List.of(
                Json.parse("{\"run\": \"r1\", \"kind\": \"trade\", \"case\": \"SEED-TRADE-001\", \"status\": \"failed\"}"),
                Json.parse("{\"run\": \"r1\", \"kind\": \"trade\", \"case\": \"SEED-TRADE-002\", \"status\": \"booked\"}"),
                Json.parse("{\"run\": \"r2\", \"kind\": \"trade\", \"case\": \"SEED-TRADE-001\", \"status\": \"booked\"}")));
        assertEquals(2, merged.size());
        assertEquals("the rerun's line wins", "r2", merged.get(0).path("run").asText());
        assertEquals("booked", merged.get(0).path("status").asText());
    }

    @Test
    public void seedDatsListsTheNewTradesThenTheEventsAndStepInsTogether() throws java.io.IOException {
        List<SeedReport.CaseDat> dats = SeedReport.caseDats(
                com.example.module.oreo.testdata.ProjectPaths.projectRoot(),
                com.example.module.oreo.testdata.SeedData.catalog());
        List<String> kinds = dats.stream().map(SeedReport.CaseDat::kind).toList();
        int firstEvent = kinds.indexOf("event");
        assertTrue("trades first: " + kinds, firstEvent > 0 && kinds.subList(firstEvent, kinds.size()).stream()
                .allMatch("event"::equals));
        for (SeedReport.CaseDat d : dats) {
            assertEquals(d.caseId(), d.caseId().startsWith("SEED-TRADE-") ? "trade" : "event", d.kind());
        }
    }
}
