package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoverageHtmlTest {

    private static ProductExamples.Entry outline(String file, String rowId, Capability capability,
                                                 String... products) {
        return new ProductExamples.Entry(file, rowId, capability, List.of(products), List.of(products), List.of(),
                List.of());
    }

    @Test
    public void aTagNameIsAFamilyAndAVariant() {
        assertArrayEquals(new String[] {"Step in", "Full"}, CoverageHtml.label("stepInFull"));
        assertArrayEquals(new String[] {"Termination", "Early"}, CoverageHtml.label("earlyTermination"));
        assertArrayEquals(new String[] {"Novation remaining", "Partial"},
                CoverageHtml.label("partialNovationRemaining"));
        assertArrayEquals(new String[] {"Cortex formula", ""}, CoverageHtml.label("cortexFormula"));
        assertArrayEquals(new String[] {"Create", ""}, CoverageHtml.label("create"));
    }

    @Test
    public void theCapabilitiesOfAFamilyAreSideBySide() {
        List<CoverageHtml.Column> columns = CoverageHtml.columns(Capability.SUPPORT_CAPABILITY);
        assertEquals(Capability.SUPPORT_CAPABILITY.size(), columns.size());
        int early = indexOf(columns, Capability.EARLY_TERMINATION);
        assertEquals(early + 1, indexOf(columns, Capability.PARTIAL_TERMINATION));
        int novation = indexOf(columns, Capability.NOVATION_REMAINING);
        assertEquals(novation + 1, indexOf(columns, Capability.PARTIAL_NOVATION_REMAINING));
        assertEquals("Full", columns.get(novation).variant());
        assertEquals("", columns.get(indexOf(columns, Capability.CANCELLATION)).variant());
    }

    private static int indexOf(List<CoverageHtml.Column> columns, Capability capability) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).capability() == capability) return i;
        }
        throw new AssertionError(capability + " is not a column");
    }

    @Test
    public void aCellListsItsCasesAndTheRowsToSync() throws IOException {
        List<ProductExamples.Entry> entries = List.of(
                outline("api/trading/trade_cancellation.feature", "TC-CANCEL-<product_type>-API-001",
                        Capability.CANCELLATION, "P1"),
                outline("ui/trading/trade_cancellation.feature", "TC-CANCEL-<product_type>-UI-001",
                        Capability.CANCELLATION, "P1", "P2"));
        Set<String> wip = Set.of("ui/trading/trade_cancellation.feature|TC-CANCEL-<product_type>-UI-001");
        Coverage.Facts facts = new Coverage.Facts((p, c) -> true, (id, c) -> "ID-" + c, c -> "ID-" + c, c -> "T");
        Map<String, Object> model = CoverageHtml.model(entries, wip, List.of("P1", "P2"), facts, "now");
        JsonNode views = new ObjectMapper().readTree(CoverageHtml.json(model)).get("views");
        assertEquals(List.of("composition", "productId", "tradeType"),
                List.of(views.get(0).get("key").asText(), views.get(1).get("key").asText(),
                        views.get(2).get("key").asText()));
        JsonNode lifecycle = views.get(0).get("groups").get(0);
        assertEquals("Trade lifecycle", lifecycle.get("title").asText());
        int column = indexOf(CoverageHtml.columns(Capability.SUPPORT_CAPABILITY), Capability.CANCELLATION);

        JsonNode p1 = lifecycle.get("rows").get(0).get("cells").get(column);
        assertEquals(1, p1.get("api").asInt());
        assertEquals(0, p1.get("ui").asInt());
        assertEquals(1, p1.get("wip").asInt());
        assertEquals("TC-CANCEL-P1-API-001", p1.get("cases").get(0).get("id").asText());
        assertEquals("API", p1.get("cases").get(0).get("layer").asText());
        assertTrue(p1.get("cases").get(1).get("wip").asBoolean());
        assertEquals("P1", p1.get("cases").get(0).get("composition").asText());
        assertEquals("ID-P1", p1.get("cases").get(0).get("productId").asText());
        assertEquals("P1", lifecycle.get("rows").get(0).get("label").asText());
        JsonNode byType = views.get(2).get("groups").get(0).get("rows");
        assertEquals(1, byType.size());
        assertEquals("T", byType.get(0).get("label").asText());

        JsonNode p2 = lifecycle.get("rows").get(1).get("cells").get(column);
        assertEquals("TC-CANCEL-P2-API-001", p2.get("toSync").get(0).asText());
        assertFalse(p2.get("gap").asBoolean());
    }

    @Test
    public void theDataCannotEndItsScriptElement() {
        String page = CoverageHtml.page("<script>" + CoverageHtml.DATA_MARK + "</script>", "{\"id\":\"</script>\"}");
        assertEquals("<script>{\"id\":\"\\u003c/script>\"}</script>", page);
    }

    @Test(expected = IllegalStateException.class)
    public void aTemplateWithoutTheMarkFails() {
        CoverageHtml.page("<html></html>", "{}");
    }
}
