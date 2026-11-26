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
    public void aBookingLeftSubmittedIsReportedFailedWithItsTrade() {
        List<JsonNode> in = List.of(
                Json.parse("{\"kind\": \"trade\", \"case\": \"SEED-TRADE-001\", \"tradeId\": \"T-1\", \"status\": \"submitted\"}"),
                Json.parse("{\"kind\": \"trade\", \"case\": \"SEED-TRADE-002\", \"tradeId\": \"T-2\", \"status\": \"submitted\"}"),
                Json.parse("{\"kind\": \"trade\", \"case\": \"SEED-TRADE-002\", \"tradeId\": \"T-2\", \"status\": \"booked\"}"));
        List<JsonNode> latest = SeedReport.unfinishedAsFailed(SeedReport.latest(in));
        assertEquals(2, latest.size());
        assertEquals("failed", latest.get(0).path("status").asText());
        assertEquals("T-1", latest.get(0).path("tradeId").asText());
        assertEquals(SeedReport.UNFINISHED, latest.get(0).path("error").asText());
        assertEquals("a later line of the booking wins over its submitted line", "booked",
                latest.get(1).path("status").asText());
        assertEquals("the manifest's line is left as it was", "submitted", in.get(0).path("status").asText());
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

    private static final String TYPE = "Cortex.Finance.Tradeable.TRF";

    private static List<SeedReport.LegacyRule> rules(String json) {
        return SeedReport.legacyRules(Json.parse("{\"request\": " + json + "}"));
    }

    @Test
    public void aBackToBackCaseTakesTheValuesOfItsPackagesOwnTrade() {
        JsonNode fetched = Json.parse("""
                {"case": "SEED-TRADE-001", "tradeId": "T-2", "packageId": "T-1",
                 "trade": [{"tradeId": "T-2", "packageId": "T-1", "tradeType": "%1$s", "portfolioId": "LEG_PORTFOLIO"}],
                 "package": [
                   {"tradeId": "T-1", "packageId": "T-1", "tradeType": "%1$s", "portfolioId": "CUSTOMER_PORTFOLIO",
                    "premiumAmount": "2250000.0", "csaExcluded": "N",
                    "tags": [{"name": "HARD_MARGIN", "value": "-5600.0"}, {"name": "OTHER", "value": "x"}]},
                   {"tradeId": "T-2", "packageId": "T-1", "tradeType": "%1$s", "portfolioId": "LEG_PORTFOLIO"}]}"""
                .formatted(TYPE));
        List<SeedReport.LegacyRule> rules = rules("""
                {"basic.portfolioId": "portfolioId", "basic.riskPortfolio": "portfolioId",
                 "basic.premiumAmount": {"from": "premiumAmount", "as": "number"},
                 "basic.excludeFromCsa": {"from": "csaExcluded", "as": "boolean"},
                 "basic.hardMargin": {"from": "tag:HARD_MARGIN", "as": "number"}}""");
        SeedReport.LegacyCase c = SeedReport.legacyCase(fetched, rules, TYPE, Json.parse("""
                {"basic": {"portfolioId": "CUSTOMER_PORTFOLIO", "riskPortfolio": "EXAMPLE_PORTFOLIO_A"}}"""));
        assertEquals(null, c.refused());
        assertEquals(Json.parse("""
                {"basic": {"portfolioId": "CUSTOMER_PORTFOLIO", "riskPortfolio": "CUSTOMER_PORTFOLIO",
                 "premiumAmount": 2250000, "excludeFromCsa": false, "hardMargin": -5600}}"""), c.request());
        List<String> statuses = c.rows().stream().map(r -> r.get(8)).toList();
        assertEquals(List.of("same", "changed", "set", "set", "set"), statuses);
        List<String> changed = c.rows().get(1);
        assertEquals(List.of("SEED-TRADE-001", "T-2", "T-1", "basic.riskPortfolio", "portfolioId",
                "CUSTOMER_PORTFOLIO", "CUSTOMER_PORTFOLIO", "EXAMPLE_PORTFOLIO_A", "changed", ""), changed);
        assertEquals("tag:HARD_MARGIN as number", c.rows().get(4).get(4));
    }

    @Test
    public void aTradeWithoutAPackageGivesItsOwnValues() {
        JsonNode fetched = Json.parse("""
                {"case": "SEED-TRADE-002", "tradeId": "T-3",
                 "trade": {"tradeId": "T-3", "tradeType": "%s", "portfolioId": "OWN_PORTFOLIO", "isEsgLinked": false}}"""
                .formatted(TYPE));
        SeedReport.LegacyCase c = SeedReport.legacyCase(fetched, rules("""
                {"basic.portfolioId": "portfolioId", "basic.esgLinked": {"from": "isEsgLinked", "as": "boolean"}}"""),
                TYPE, com.fasterxml.jackson.databind.node.MissingNode.getInstance());
        assertEquals(Json.parse("{\"basic\": {\"portfolioId\": \"OWN_PORTFOLIO\", \"esgLinked\": false}}"), c.request());
        assertEquals(List.of("set", "set"), c.rows().stream().map(r -> r.get(8)).toList());
    }

    @Test
    public void aValueTheTradeLacksOrThatCannotBeConvertedKeepsTheCatalogs() {
        JsonNode fetched = Json.parse("""
                {"case": "SEED-TRADE-003", "tradeId": "T-4",
                 "trade": [{"tradeId": "T-4", "tradeType": "%s", "premiumAmount": "n/a", "premiumCurrency": "",
                            "tags": [{"name": "TWICE", "value": "1"}, {"name": "TWICE", "value": "2"}]}]}"""
                .formatted(TYPE));
        SeedReport.LegacyCase c = SeedReport.legacyCase(fetched, rules("""
                {"basic.portfolioId": "portfolioId", "basic.premiumCurrency": "premiumCurrency",
                 "basic.premiumAmount": {"from": "premiumAmount", "as": "number"}, "basic.structureId": "tag:TWICE"}"""),
                TYPE, Json.parse("{\"basic\": {\"premiumAmount\": 10000}}"));
        assertEquals("nothing set", Json.MAPPER.createObjectNode(), c.request());
        assertEquals(List.of("missing", "missing", "invalid", "invalid"), c.rows().stream().map(r -> r.get(8)).toList());
        assertEquals("not a number: n/a", c.rows().get(2).get(9));
        assertEquals("10000", c.rows().get(2).get(7));
        assertTrue(c.rows().get(3).get(9), c.rows().get(3).get(9).contains("2 tags TWICE"));
    }

    @Test
    public void aCaseIsRefusedWhenItsTradeIsNotOfItsItradeableTypeOrNotInTheResponse() {
        List<SeedReport.LegacyRule> rules = rules("{\"basic.portfolioId\": \"portfolioId\"}");
        SeedReport.LegacyCase other = SeedReport.legacyCase(Json.parse("""
                {"case": "SEED-TRADE-004", "tradeId": "T-5",
                 "trade": [{"tradeId": "T-5", "tradeType": "Cortex.FX.Other", "portfolioId": "P"}]}"""),
                rules, TYPE, com.fasterxml.jackson.databind.node.MissingNode.getInstance());
        assertTrue(other.refused(), other.refused().contains("Cortex.FX.Other") && other.refused().contains(TYPE));
        assertEquals(1, other.rows().size());
        assertEquals("refused", other.rows().getFirst().get(8));
        assertTrue(other.request().isEmpty());
        SeedReport.LegacyCase absent = SeedReport.legacyCase(Json.parse("""
                {"case": "SEED-TRADE-005", "tradeId": "T-6", "packageId": "T-7",
                 "trade": [{"tradeId": "T-6", "packageId": "T-7", "tradeType": "%s"}], "package": []}"""
                .formatted(TYPE)), rules, TYPE, com.fasterxml.jackson.databind.node.MissingNode.getInstance());
        assertEquals("0 trades T-7 in the response of 0 trade(s)", absent.refused());
    }

    @Test
    public void aValueIsConvertedAsItsRuleSays() {
        assertEquals(Json.parse("2250000"), SeedReport.convert(Json.parse("\"2250000.0\""), "number"));
        assertEquals(Json.parse("-8400"), SeedReport.convert(Json.parse("-8400.0"), "number"));
        assertEquals(Json.parse("0.5"), SeedReport.convert(Json.parse("\"0.50\""), "number"));
        assertEquals(Json.parse("false"), SeedReport.convert(Json.parse("\"N\""), "boolean"));
        assertEquals(Json.parse("true"), SeedReport.convert(Json.parse("\"Yes\""), "boolean"));
        assertEquals(Json.parse("false"), SeedReport.convert(Json.parse("false"), "boolean"));
        assertEquals(Json.parse("\"2026-08-05\""), SeedReport.convert(Json.parse("\"05-08-2026\""), "date:dd-MM-yyyy"));
        assertEquals(Json.parse("\"x\""), SeedReport.convert(Json.parse("\"x\""), null));
        for (String[] bad : new String[][]{{"\"maybe\"", "boolean"}, {"\"2026-08-05\"", "date:dd-MM-yyyy"}}) {
            try {
                SeedReport.convert(Json.parse(bad[0]), bad[1]);
                org.junit.Assert.fail(bad[0] + " as " + bad[1]);
            } catch (IllegalArgumentException | java.time.DateTimeException expected) {
                // refused
            }
        }
    }

    @Test
    public void theRulesAreCheckedAsTheyAreRead() throws java.io.IOException {
        List<SeedReport.LegacyRule> committed = SeedReport.legacyRules(Json.parse(java.nio.file.Files.readString(
                com.example.module.oreo.testdata.ProjectPaths.projectRoot().resolve(SeedReport.LEGACY_RULES))));
        assertTrue("the committed rules", !committed.isEmpty());
        assertEquals("a rule's comment is not a rule", 1, rules("""
                {"_comment": "c", "basic.shellId": "shellTicketId"}""").size());
        for (String bad : List.of("{\"basic.x\": {\"from\": \"x\", \"as\": \"upper\"}}", "{\"basic..x\": \"x\"}",
                "{\"basic.x\": {\"as\": \"number\"}}", "{\"basic.x\": {\"from\": \"x\", \"as\": \"date:qq-ii\"}}",
                "{\"basic.x\": 1}")) {
            try {
                rules(bad);
                org.junit.Assert.fail(bad + " is refused");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage(), e.getMessage().contains(SeedReport.LEGACY_RULES));
            }
        }
    }

    @Test
    public void theCasesAreWrittenACasePerLineAsTheCatalogHasThem() {
        com.fasterxml.jackson.databind.node.ObjectNode cases = (com.fasterxml.jackson.databind.node.ObjectNode)
                Json.parse("""
                        {"_comment": "c", "SEED-TRADE-001": {"dat": "a.dat", "request": {"basic": {"premiumAmount": 2250000}}},
                         "SEED-TRADE-002": {"request": {"basic": {"portfolioId": "P"}}}}""");
        String block = SeedReport.casesBlock(cases);
        assertEquals(List.of("{", "  \"cases\": {", "    \"_comment\": \"c\",",
                "    \"SEED-TRADE-001\": {\"dat\":\"a.dat\",\"request\":{\"basic\":{\"premiumAmount\":2250000}}},",
                "    \"SEED-TRADE-002\": {\"request\":{\"basic\":{\"portfolioId\":\"P\"}}}", "  }", "}"),
                List.of(block.split("\n")));
        assertEquals(cases, Json.parse(block).path("cases"));
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
