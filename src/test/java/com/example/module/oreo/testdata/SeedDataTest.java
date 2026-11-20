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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
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
    public void theCatalogLoadsAndItsCompositionsAreRegistered() {
        SeedData catalog = SeedData.load(SeedData.CATALOG);
        SeedData.Trade trf = catalog.trade("fx-trf");
        assertEquals("FX_TRF", trf.composition());
        assertEquals(ProductDatFiles.FX_TRF.productId(), trf.productId());
        assertEquals(ProductDatFiles.FX_TRF.path(), trf.dat());
        assertEquals(SeedData.DEFAULT_SETUP, trf.setup());
        for (SeedData.Trade t : catalog.trades().values()) {
            TestData.setup(t.setup()); // every setup body exists
        }
    }

    @Test
    public void everyRowOfTheSeedFeaturesIsInTheCatalog() throws IOException, URISyntaxException {
        SeedData catalog = SeedData.load(SeedData.CATALOG);
        Path features = Paths.get(getClass().getClassLoader().getResource("seed/features").toURI());
        Pattern trade = Pattern.compile("seed trade '<trade>'|seed event '<event>'");
        Pattern row = Pattern.compile("^\\s*\\|(.*)\\|\\s*$");
        int rows = 0;
        try (var files = Files.list(features)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".feature")).toList()) {
                List<String> header = null;
                for (String line : Files.readAllLines(file)) {
                    Matcher m = row.matcher(line);
                    if (!m.matches()) continue;
                    List<String> cells = List.of(m.group(1).split("\\|")).stream().map(String::trim).toList();
                    if (header == null) {
                        header = cells;
                        continue;
                    }
                    for (int i = 0; i < cells.size(); i++) {
                        if (header.get(i).equals("trade")) catalog.trade(cells.get(i));
                        if (header.get(i).equals("event")) catalog.event(cells.get(i));
                    }
                    rows++;
                }
                assertTrue(file + " names its rows", trade.matcher(Files.readString(file)).find());
            }
        }
        assertTrue("the seed features have rows", rows > 0);
    }

    @Test
    public void aTradeIsACompositionAndAProductId() {
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"trades": {
                   "fx-x": {"composition": "FX_X", "productId": "FX_X1"},
                   "fx-x-2": {"composition": "FX_X", "productId": "FX_X2", "dat": "OTHER.dat"},
                   "fx-trf-other-id": {"composition": "FX_TRF", "productId": "FX_TRF_DLYSTTL"}},
                 "events": {"termination": {"case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001"}}}"""));
        SeedData.Trade x = catalog.trade("fx-x");
        assertEquals("FX_X", x.composition());
        assertEquals("FX_X1", x.productId());
        assertEquals("seed/dat/FX_X/FX_X1.dat", x.dat());
        assertTrue(!x.registered());
        assertEquals("seed/dat/FX_X/OTHER.dat", catalog.trade("fx-x-2").dat());
        SeedData.Trade other = catalog.trade("fx-trf-other-id");
        assertTrue(other.registered());
        assertEquals("FX_TRF_DLYSTTL", other.productId());
        assertEquals(ProductDatFiles.FX_TRF.path(), other.dat());
        try {
            SeedData.eventRequest(catalog.event("termination"), x);
            fail("an event needs a registered composition");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("registered composition"));
        }
    }

    @Test
    public void anEventSendsItsCasesRequest() {
        SeedData catalog = SeedData.fromJson("t.json", Json.parse("""
                {"trades": {"fx-trf": {"composition": "FX_TRF"}},
                 "events": {"termination": {"case": "TC-TRADE-EARLY-TERMINATION-<product_type>-API-001"}}}"""));
        String request = SeedData.eventRequest(catalog.event("termination"), catalog.trade("fx-trf"));
        assertEquals(TestData.forCase("TC-TRADE-EARLY-TERMINATION-FX_TRF-API-001").request(), request);
    }

    @Test
    public void anEventIsTriggeredUnlessItGoesThroughTheUpdate() {
        SeedData catalog = SeedData.load(SeedData.CATALOG);
        assertEquals(SeedData.TRIGGER_EVENT, catalog.event("withdrawal").via());
        assertEquals(SeedData.UPDATE, catalog.event("amendment").via());
    }

    @Test
    public void badEntriesNameWhereTheyAre() {
        bad("""
                {"trades": {}}""", "no trades");
        bad("""
                {"trades": {"Fx": {"composition": "FX_TRF"}}}""", "trades.Fx");
        bad("""
                {"trades": {"fx": {"productId": "FX_X"}}}""", "\"composition\"");
        bad("""
                {"trades": {"fx": {"composition": "FX_X"}}}""", "\"productId\" is needed");
        bad("""
                {"trades": {"fx": {"composition": "FX_X", "productId": "P", "dat": "a/b.dat"}}}""", "trades.fx");
        bad("""
                {"trades": {"a": {"composition": "FX_TRF"}, "b": {"composition": "FX_TRF", "productId": "FX_TRF"}}}""",
                "one booking per composition and product ID");
        bad("""
                {"trades": {"fx": {"composition": "FX_TRF"}}, "events": {"e": {"case": "TC-TRADE-X-FX_TRF-API-001"}}}""",
                "events.e");
        bad("""
                {"trades": {"fx": {"composition": "FX_TRF"}},
                 "events": {"e": {"case": "TC-TRADE-AMENDMENT-<product_type>-API-001", "via": "put"}}}""", "\"via\"");
    }

    @Test
    public void bookingsAreAppendedAndReadBack() throws IOException {
        System.setProperty(ProjectPaths.PROPERTY, tmp.getRoot().toString());
        SeedData.record(Json.MAPPER.createObjectNode().put("kind", "trade").put("trade", "fx-trf"));
        SeedData.record(Json.MAPPER.createObjectNode().put("kind", "event").put("trade", "fx-trf"));
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
