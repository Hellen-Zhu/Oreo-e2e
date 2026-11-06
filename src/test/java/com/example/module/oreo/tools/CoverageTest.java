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
