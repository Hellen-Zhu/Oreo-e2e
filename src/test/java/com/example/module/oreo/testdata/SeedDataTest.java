package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SeedDataTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void clearProjectDir() {
        System.clearProperty(ProjectPaths.PROPERTY);
    }

    @Test
    public void theCatalogLoadsAndItsSetupsExist() {
        SeedData catalog = SeedData.load(SeedData.CATALOG);
        for (String setup : catalog.variants().values()) {
            TestData.setup(setup);
        }
        assertEquals(SeedData.TRIGGER_EVENT, catalog.event("withdrawal").via());
        SeedData.Trade fsb = catalog.trade("FX_FSB", "FX_PSCRIPT_FSKO", null);
        assertEquals("FX_PSCRIPT_FSB", fsb.itradeableType());
        assertTrue(fsb.registered());
        assertEquals(SeedData.UPDATE, catalog.event("amendment").via());
    }

    /**
     * Every row of the seed features resolves (its case ID, IDs, taxonomy, variant and event), has its dat file and
     * setup, and no two rows book the same thing or share a case ID; an event's composition is registered.
     */
    @Test
    public void everyRowOfTheSeedFeaturesResolvesOnce() throws IOException, URISyntaxException {
        SeedData catalog = SeedData.load(SeedData.CATALOG);
        Path features = Paths.get(getClass().getClassLoader().getResource("seed/features").toURI());
        Pattern row = Pattern.compile("^\\s*\\|(.*)\\|\\s*$");
        Set<String> seen = new HashSet<>();
        Set<String> cases = new HashSet<>();
        int rows = 0;
        try (var files = Files.list(features)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".feature")).sorted().toList()) {
                List<String> header = null;
                for (String line : Files.readAllLines(file)) {
                    Matcher m = row.matcher(line);
                    if (!m.matches()) {
                        if (line.trim().startsWith("Scenario")) header = null;
                        continue;
                    }
                    List<String> cells = List.of(m.group(1).split("\\|")).stream().map(String::trim).toList();
                    if (header == null) {
                        header = cells;
                        continue;
                    }
                    String variant = cell(header, cells, "variant");
                    String event = cell(header, cells, "event");
                    String caseId = cell(header, cells, "case");
                    if (caseId != null) {
                        SeedData.checkCase(caseId);
                        assertTrue(file + ": case ID twice: " + caseId, cases.add(caseId));
                    }
                    SeedData.Trade trade = catalog.trade(cell(header, cells, "composition"),
                            cell(header, cells, "product_id"), cell(header, cells, "taxonomy"), variant);
                    assertTrue(file + ": no dat file " + trade.dat(),
                            getClass().getClassLoader().getResource(trade.dat()) != null);
                    TestData.setup(trade.setup());
                    if (event != null) {
                        catalog.event(event);
                        assertTrue(file + ": " + event + " on an unregistered " + trade.composition(),
                                trade.registered());
                    }
                    String key = trade.composition() + "|" + trade.productId() + "|" + trade.taxonomy() + "|"
                            + variant + "|" + event;
                    assertTrue(file + " books twice: " + key, seen.add(key));
                    rows++;
                }
            }
        }
        assertTrue("the seed features have rows", rows > 0);
    }

    private static String cell(List<String> header, List<String> cells, String column) {
        int i = header.indexOf(column);
        return i < 0 ? null : cells.get(i);
    }

    @Test
    public void aPairTakesItsDatFileByWhetherItsCompositionIsRegistered() {
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"itradeableTypes": {"FX_X": "FX_X", "FXBO": "FXBO", "FX_TRF": "FX_TRF"},
                 "trades": [{"composition": "FX_X", "productId": "FX_X2", "dat": "OTHER.dat", "setup": "other-body"}],
                 "variants": {"stepin-full": {"setup": "trade-create-stepin-full"}}}"""));
        SeedData.Trade x = catalog.trade("FX_X", "FX_X1", null);
        assertFalse(x.registered());
        assertEquals("seed/dat/FX_X/FX_X1.dat", x.dat());
        assertEquals(SeedData.DEFAULT_SETUP, x.setup());
        assertNull(x.variant());
        SeedData.Trade x2 = catalog.trade("FX_X", "FX_X2", null);
        assertEquals("seed/dat/FX_X/OTHER.dat", x2.dat());
        assertEquals("other-body", x2.setup());
        SeedData.Trade trf = catalog.trade("FX_TRF", "FX_TRF_DLYSTTL", "stepin-full");
        assertTrue(trf.registered());
        assertEquals(ProductDatFiles.FX_TRF.path(), trf.dat());
        assertEquals("trade-create-stepin-full", trf.setup());
        assertEquals("stepin-full", trf.variant());
    }

    @Test
    public void anEntryWithATaxonomyWinsOverOneWithout() {
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"itradeableTypes": {"FX_X": "FX_X", "FXBO": "FXBO", "FX_TRF": "FX_TRF"},
                 "trades": [{"composition": "FXBO", "productId": "FX_BO_X", "setup": "body-a"},
                            {"composition": "FXBO", "productId": "FX_BO_X", "taxonomy": "Metals:Precious:Exotic",
                             "setup": "body-b"}]}"""));
        SeedData.Trade metals = catalog.trade("FXBO", "FX_BO_X", "Metals:Precious:Exotic", null);
        assertEquals("body-b", metals.setup());
        assertEquals("Metals:Precious:Exotic", metals.taxonomy());
        assertEquals("seed/dat/FXBO/FX_BO_X.dat", metals.dat());
        assertEquals("body-a", catalog.trade("FXBO", "FX_BO_X", "ComplexExotic", null).setup());
        assertEquals("body-a", catalog.trade("FXBO", "FX_BO_X", null).setup());
        assertEquals(SeedData.DEFAULT_SETUP, catalog.trade("FXBO", "FX_BO_Y", "ComplexExotic", null).setup());
    }

    @Test
    public void anEventNeedsARegisteredComposition() {
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"itradeableTypes": {"FX_X": "FX_X", "FXBO": "FXBO", "FX_TRF": "FX_TRF"},
                 "events": {"termination": {"case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001"}}}"""));
        try {
            SeedData.eventRequest(catalog.event("termination"), catalog.trade("FX_X", "FX_X1", null));
            fail("an event needs a registered composition");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("registered composition"));
        }
        assertEquals(TestData.forCase("TC-TRADE-EARLY-TERMINATION-FX_TRF-API-001").request(),
                SeedData.eventRequest(catalog.event("termination"), catalog.trade("FX_TRF", "FX_TRF", null)));
    }

    @Test
    public void badEntriesNameWhereTheyAre() {
        bad("""
                {"trades": {}}""", "a list of");
        bad("""
                {"trades": [{"productId": "FX_X"}]}""", "trades[0]: \"composition\"");
        bad("""
                {"trades": [{"composition": "FX_X"}]}""", "\"productId\"");
        bad("""
                {"trades": [{"composition": "FX_X", "productId": "P", "dat": "a/b.dat"}]}""", "trades[0]");
        bad("""
                {"trades": [{"composition": "FX_X", "productId": "P"}, {"composition": "FX_X", "productId": "P"}]}""",
                "listed twice");
        bad("""
                {"variants": {"StepIn": {"setup": "x"}}}""", "variants.StepIn");
        bad("""
                {"variants": {"stepin": {}}}""", "the variant's body");
        bad("""
                {"events": {"e": {"case": "TC-TRADE-X-FX_TRF-API-001"}}}""", "events.e");
        bad("""
                {"events": {"e": {"case": "TC-TRADE-AMENDMENT-<product_type>-API-001", "via": "put"}}}""", "\"via\"");
        bad("""
                {"trades": [{"composition": "FX_X", "productId": "P", "taxonomy": "Complex Exotic"}]}""",
                "\"taxonomy\"");
        bad("""
                {"trades": [{"composition": "FX_X", "productId": "P", "taxonomy": "A"},
                            {"composition": "FX_X", "productId": "P", "taxonomy": "A"}]}""", "listed twice");
        bad("""
                {"itradeableTypes": []}""", "itradeableTypes:");
        bad("""
                {"itradeableTypes": {"FX PSCRIPT": "FX_FSB"}}""", "\"Itradeable Type\"");
        bad("""
                {"itradeableTypes": {"FX_PSCRIPT_FSB": 1}}""", "\"composition\"");
        bad("""
                {"itradeableTypes": {"FX_PSCRIPT_FSB": "FX_FSB", "FX_FSB": "FX_FSB"}}""", "one to one");
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"itradeableTypes": {"FX_TRF": "FX_TRF"}}"""));
        try {
            catalog.trade("FX_CO", "FX_PSCRIPT", null);
            fail("a composition needs its Itradeable Type");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has no Itradeable Type"));
        }
        try {
            catalog.trade("FX-TRF", "FX_TRF", null);
            fail("an ID is letters, digits and _");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("\"composition\" is an ID"));
        }
        try {
            catalog.trade("FX_TRF", "FX_TRF", "ComplexExotic (CorrOption)", null);
            fail("a taxonomy is words joined by :");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("\"taxonomy\""));
        }
        for (String id : new String[] {"SEED-TRADE-1", "TC-TRADE-001", "seed-trade-001"}) {
            try {
                SeedData.checkCase(id);
                fail("not a seed case ID: " + id);
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("seed case ID"));
            }
        }
        assertEquals("SEED-TRADE-001", SeedData.checkCase("SEED-TRADE-001"));
        try {
            catalog.trade("FX_TRF", "FX_TRF", "nope");
            fail("no such variant");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no variant 'nope'"));
        }
    }

    @Test
    public void bookingsAreAppendedAndReadBack() throws IOException {
        System.setProperty(ProjectPaths.PROPERTY, tmp.getRoot().toString());
        SeedData.record(Json.MAPPER.createObjectNode().put("kind", "trade").put("composition", "FX_TRF"));
        SeedData.record(Json.MAPPER.createObjectNode().put("kind", "event").put("composition", "FX_TRF"));
        List<JsonNode> entries = SeedData.readManifests(SeedData.manifestDir());
        assertEquals(2, entries.size());
        assertEquals("event", entries.get(1).path("kind").asText());
    }

    private static void bad(String json, String expected) {
        try {
            SeedData.fromJson("t.json", Json.parse(json));
            fail("expected an error with: " + expected);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expected));
        }
    }
}
