package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoverageTest {

    private static ProductExamples.Entry outline(String file, String rowId, Capability capability,
                                                 String... products) {
        return new ProductExamples.Entry(file, rowId, capability, List.of(products), List.of(products), List.of(),
                List.of());
    }

    private static final List<ProductExamples.Entry> ENTRIES = List.of(
            outline("api/trading/trade_cancellation.feature", "TC-CANCEL-<product_type>-API-001",
                    Capability.CANCELLATION, "P1", "P2"),
            outline("ui/trading/trade_cancellation.feature", "TC-CANCEL-<product_type>-UI-001",
                    Capability.CANCELLATION, "P1"),
            outline("ui/trading/trade_allocation.feature", "TC-ALLOC-<product_type>-UI-001",
                    Capability.ALLOCATION, "P1", "P2"));

    private static final Set<String> WIP = Set.of("ui/trading/trade_allocation.feature|TC-ALLOC-<product_type>-UI-001");

    @Test
    public void countsTheOutlinesThatRunByLayer() {
        Coverage.Cell cell = Coverage.cell(ENTRIES, Set.of(), Capability.CANCELLATION, "P1", true);
        assertEquals("1/1", cell.text());
    }

    @Test
    public void aMissingRowIsMarkedForSyncExamples() {
        Coverage.Cell cell = Coverage.cell(ENTRIES, Set.of(), Capability.CANCELLATION, "P2", true);
        assertEquals("1/0 *", cell.text());
    }

    @Test
    public void aWipOutlineDoesNotCountAsRunning() {
        Coverage.Cell cell = Coverage.cell(ENTRIES, WIP, Capability.ALLOCATION, "P1", true);
        assertEquals(Coverage.NO_CASE + " +1wip", cell.text());
        assertTrue(cell.gap());
    }

    @Test
    public void aCellSyncExamplesWouldFillIsNotAGap() {
        List<ProductExamples.Entry> entries = List.of(outline("ui/a.feature", "TC-A-<product_type>-UI-001",
                Capability.ALLOCATION, "P1"));
        Coverage.Cell cell = Coverage.cell(entries, Set.of(), Capability.ALLOCATION, "P2", true);
        assertEquals(Coverage.NO_CASE + " *", cell.text());
        assertEquals(false, cell.gap());
    }

    @Test
    public void anUnsupportedCapabilityIsNotAGap() {
        Coverage.Cell cell = Coverage.cell(ENTRIES, Set.of(), Capability.FIXINGS, "P1", false);
        assertEquals(Coverage.NOT_SUPPORTED, cell.text());
        assertEquals(false, cell.gap());
    }

    @Test
    public void listsTheGapsUnderTheMatrix() {
        BiPredicate<String, Capability> supports = (product, capability) ->
                !(product.equals("P2") && capability == Capability.FIXINGS);
        String report = Coverage.render(ENTRIES, WIP, List.of("P1", "P2"), supports);
        assertTrue(report, report.contains("P1: allocation"));
        assertTrue(report, report.contains("P2: fixings") == false);
        assertTrue(report, report.contains("P1: fixings"));
        assertTrue(report, report.contains("P2: cancellation"));
    }

    @Test
    public void eachGroupIsASectionOfItsOwnCapabilities() {
        String report = Coverage.render(ENTRIES, Set.of(), List.of("P1"), (product, capability) -> true);
        int lifecycle = report.indexOf("Trade lifecycle");
        int features = report.indexOf("Product features");
        assertTrue(report, lifecycle >= 0 && features > lifecycle);
        assertTrue(report, report.indexOf("\nallocation ") > lifecycle && report.indexOf("\nallocation ") < features);
        assertTrue(report, report.indexOf("\nfixings ") > features);
    }

    /** P1 and P2 send ID-1 and ID-2 by default; the API case of cancellation sends ID-X for P2. P1 and P2 are trade type T. */
    private static final Coverage.Facts FACTS = new Coverage.Facts(
            (product, capability) -> capability != Capability.FIXINGS,
            (id, composition) -> id.equals("TC-CANCEL-P2-API-001") ? "ID-X" : "ID-" + composition.substring(1),
            composition -> "ID-" + composition.substring(1),
            composition -> "T");

    @Test
    public void aProductIdSomeCasesSendIsARowOfItsComposition() {
        List<Coverage.Row> rows = Coverage.rows(Coverage.View.PRODUCT_ID, List.of("P1", "P2"), ENTRIES, FACTS);
        assertEquals(List.of("ID-1 / P1", "ID-2 / P2", "ID-X / P2"), rows.stream().map(Coverage.Row::label).toList());
        // the default row answers for P2: its API cancellation case sends ID-X, its UI one is to sync
        Coverage.Cell ownCancel = Coverage.cell(rows.get(1), Capability.CANCELLATION, ENTRIES, Set.of(), FACTS).counts();
        assertEquals(Coverage.NO_CASE + " *", ownCancel.text());
        Coverage.Cell otherCancel = Coverage.cell(rows.get(2), Capability.CANCELLATION, ENTRIES, Set.of(), FACTS).counts();
        assertEquals("1/0", otherCancel.text());
        // a product ID only some cases send has no gap of its own
        Coverage.Cell otherAlloc = Coverage.cell(rows.get(2), Capability.ALLOCATION, ENTRIES, Set.of(), FACTS).counts();
        assertEquals(Coverage.NOT_SUPPORTED, otherAlloc.text());
    }

    @Test
    public void aTradeTypeGathersItsCompositions() {
        List<Coverage.Row> rows = Coverage.rows(Coverage.View.TRADE_TYPE, List.of("P1", "P2"), ENTRIES, FACTS);
        assertEquals(1, rows.size());
        assertEquals(List.of("P1", "P2"), rows.get(0).compositions());
        Coverage.RowCell cancel = Coverage.cell(rows.get(0), Capability.CANCELLATION, ENTRIES, Set.of(), FACTS);
        assertEquals("2/1 *", cancel.counts().text());
        assertEquals(Coverage.NOT_SUPPORTED, Coverage.cell(rows.get(0), Capability.FIXINGS, ENTRIES, Set.of(), FACTS)
                .counts().text());
        String report = Coverage.render(Coverage.View.TRADE_TYPE, rows, ENTRIES, Set.of(), FACTS);
        assertTrue(report, report.contains("\n  T = P1 + P2\n"));
    }

    @Test
    public void aTradeTypeAndProductIdGathersTheCompositionsThatSendIt() {
        // P1 and P2 are trade type T and send ID-1 by default; the API case of cancellation sends ID-X for P2
        Coverage.Facts facts = new Coverage.Facts((product, capability) -> capability != Capability.FIXINGS,
                (id, composition) -> id.equals("TC-CANCEL-P2-API-001") ? "ID-X" : "ID-1",
                composition -> "ID-1", composition -> "T");
        Coverage.View view = Coverage.View.of("tradeTypeProductId");
        List<Coverage.Row> rows = Coverage.rows(view, List.of("P1", "P2"), ENTRIES, facts);
        assertEquals(List.of("T / ID-1", "T / ID-X"), rows.stream().map(Coverage.Row::label).toList());
        assertEquals(List.of("P1", "P2"), rows.get(0).compositions());
        assertEquals(List.of("P2"), rows.get(1).compositions());
        // P1's API and UI cases send ID-1; P2's API case sends ID-X and its UI one is to sync
        assertEquals("1/1 *", Coverage.cell(rows.get(0), Capability.CANCELLATION, ENTRIES, Set.of(), facts)
                .counts().text());
        // a product ID only some cases send shows them and has no gap of its own
        assertEquals("1/0", Coverage.cell(rows.get(1), Capability.CANCELLATION, ENTRIES, Set.of(), facts)
                .counts().text());
        assertEquals(Coverage.NOT_SUPPORTED, Coverage.cell(rows.get(1), Capability.ALLOCATION, ENTRIES, Set.of(),
                facts).counts().text());
        String report = Coverage.render(view, rows, ENTRIES, Set.of(), facts);
        assertTrue(report, report.contains("Trade type / product ID = its compositions:\n  T / ID-1 = P1 + P2\n"
                + "  T / ID-X = P2\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownViewFails() {
        Coverage.View.of("product");
    }

    @Test
    public void wipIsInheritedFromTheFeatureAndTheRule() throws IOException {
        Path file = Files.createTempFile("coverage", ".feature");
        try {
            Files.writeString(file, String.join("\n",
                    "Feature: x",
                    "  Rule: r",
                    "    Scenario Outline: [TC-A-<product_type>-UI-001] a",
                    "      Examples:",
                    "        | product_type |",
                    "        | P1           |",
                    "  @wip",
                    "  Rule: s",
                    "    Scenario Outline: [TC-A-<product_type>-UI-002] b",
                    "      Examples:",
                    "        | product_type |",
                    "        | P1           |",
                    "  Rule: t",
                    "    @wip",
                    "    Scenario Outline: [TC-A-<product_type>-UI-003] c",
                    "      Examples:",
                    "        | product_type |",
                    "        | P1           |",
                    ""), StandardCharsets.UTF_8);
            Set<String> wip = Coverage.wipOutlines("ui/a.feature", FeatureFile.read(file));
            assertEquals(Set.of("ui/a.feature|TC-A-<product_type>-UI-002", "ui/a.feature|TC-A-<product_type>-UI-003"),
                    wip);
        } finally {
            Files.delete(file);
        }
    }
}
