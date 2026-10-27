package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.javacrumbs.jsonunit.JsonAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExpectedResponsesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path root;
    private ExpectedResponses expected;
    private final CaseId id = CaseId.parse("TC-TRADE-CREATION-FX_TRF-API-001");
    private final CaseId variant = CaseId.parse("TC-TRADE-CREATION-FX_TRF-API-002");
    private final CaseId third = CaseId.parse("TC-TRADE-CREATION-FX_TRF-API-003");

    @Before
    public void setUp() {
        root = tmp.getRoot().toPath();
        expected = new ExpectedResponses(root);
    }

    @Test
    public void fileForUsesEventFolder() {
        assertEquals(root.resolve("trade_creation").resolve("TC-TRADE-CREATION-FX_TRF-API-001.json"),
                expected.fileFor(id));
    }

    @Test
    public void writeThenRead() throws IOException {
        String json = "{\n  \"a\": 1\n}\n";
        expected.write(id, json);
        assertEquals(json, expected.read(id).orElseThrow());
        String onDisk = new String(Files.readAllBytes(expected.fileFor(id)), StandardCharsets.UTF_8);
        assertFalse(onDisk.contains("\r"));
    }

    @Test
    public void readMissingIsEmpty() {
        assertTrue(expected.read(id).isEmpty());
    }

    @Test
    public void readsCrlfFile() throws IOException {
        Path file = expected.fileFor(id);
        Files.createDirectories(file.getParent());
        Files.write(file, "{\r\n  \"a\": 1\r\n}\r\n".getBytes(StandardCharsets.UTF_8));
        JsonAssert.assertJsonEquals(expected.read(id).orElseThrow(), "{\"a\":1}");
    }

    @Test
    public void updateModeSwitch() {
        assertFalse(ExpectedResponses.updateMode(null, null));
        assertTrue(ExpectedResponses.updateMode("update", null));
        assertTrue(ExpectedResponses.updateMode(null, "UPDATE"));
        assertFalse(ExpectedResponses.updateMode("", null));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnknownMode() {
        ExpectedResponses.updateMode("updat", null);
    }

    // ── Base and patch ───────────────────────────────────────────────────────

    private static String patchOn(String base, String ops) {
        return "{\"_base\":\"" + base + "\",\"_patch\":" + ops + "}";
    }

    private void assertResolveFails(CaseId caseId, String... messageParts) {
        try {
            expected.resolve(caseId);
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            for (String part : messageParts) {
                assertTrue(e.getMessage(), e.getMessage().contains(part));
            }
        }
    }

    @Test
    public void resolvesAFullFile() {
        expected.write(id, "{\n  \"a\": 1\n}\n");
        ExpectedResponses.Resolved resolved = expected.resolve(id).orElseThrow();
        assertEquals(Json.parse("{\"a\":1}"), Json.parse(resolved.json()));
        assertNull(resolved.base());
        assertEquals(expected.fileFor(id).toString(), resolved.describe());
    }

    @Test
    public void resolvesAPatchOnItsBase() {
        expected.write(id, "{\"a\":1,\"b\":2}");
        expected.write(variant, patchOn(id.raw(), "[{\"op\":\"replace\",\"path\":\"/b\",\"value\":3}]"));
        ExpectedResponses.Resolved resolved = expected.resolve(variant).orElseThrow();
        assertEquals(Json.parse("{\"a\":1,\"b\":3}"), Json.parse(resolved.json()));
        assertEquals(id.raw(), resolved.base());
        assertTrue(resolved.describe(), resolved.describe().endsWith("(patch on " + id.raw() + ")"));
    }

    @Test
    public void patchOnAMissingBaseFails() {
        expected.write(variant, patchOn(id.raw(), "[]"));
        assertResolveFails(variant, expected.fileFor(id).toString(), "OREO_EXPECTED=update");
    }

    @Test
    public void patchOnAPatchFails() {
        expected.write(id, "{\"a\":1}");
        expected.write(variant, patchOn(id.raw(), "[]"));
        expected.write(third, patchOn(variant.raw(), "[]"));
        assertResolveFails(third, "is itself a patch", "record " + third.raw() + " again with OREO_EXPECTED=update");
    }

    @Test
    public void patchThatDoesNotApplySaysHowToRecordAgain() {
        expected.write(id, "{\"a\":1}");
        expected.write(variant, patchOn(id.raw(), "[{\"op\":\"add\",\"path\":\"/a\",\"value\":2}]"));
        assertResolveFails(variant, "op 0 (add /a)", "record " + variant.raw() + " again with OREO_EXPECTED=update");
    }

    @Test(expected = IllegalStateException.class)
    public void patchOnAnotherEventFails() {
        expected.write(variant, patchOn("TC-OTHER-EVENT-FX_TRF-API-001", "[]"));
        expected.resolve(variant);
    }

    @Test(expected = IllegalStateException.class)
    public void malformedPatchFails() {
        expected.write(variant, "{\"_base\":1,\"_patch\":[]}");
        expected.resolve(variant);
    }

    @Test
    public void isPatchNeedsExactlyTheTwoKeys() {
        assertTrue(ExpectedResponses.isPatch(Json.parse("{\"_base\":\"x\",\"_patch\":[]}")));
        assertFalse(ExpectedResponses.isPatch(Json.parse("{\"_base\":\"x\",\"_patch\":[],\"code\":200}")));
        assertFalse(ExpectedResponses.isPatch(Json.parse("{\"code\":200}")));
    }

    @Test
    public void listsEventsAndCases() {
        expected.write(variant, "{}");
        expected.write(id, "{}");
        assertEquals(List.of("trade_creation"), expected.events());
        assertEquals(List.of(id, variant), expected.cases("trade_creation"));
    }

    // ── Shared parts ─────────────────────────────────────────────────────────

    private static final String BASIC = "{\"status\":\"Live\",\"ccy\":\"USD\"}";

    @Test
    public void resolvesAReferenceWithItsOverrides() {
        expected.shared().write("basic/FX_TRF", BASIC);
        expected.write(id, "{\"trade\":{\"basic\":{\"_shared\":\"basic/FX_TRF\",\"status\":\"Draft\",\"udf\":[1]}}}");
        assertEquals(Json.parse("{\"trade\":{\"basic\":{\"status\":\"Draft\",\"ccy\":\"USD\",\"udf\":[1]}}}"),
                Json.parse(expected.resolve(id).orElseThrow().json()));
    }

    @Test
    public void resolvesAPatchOnABaseWithAReference() {
        expected.shared().write("basic/FX_TRF", BASIC);
        expected.write(id, "{\"basic\":{\"_shared\":\"basic/FX_TRF\"}}");
        expected.write(variant, patchOn(id.raw(), "[{\"op\":\"replace\",\"path\":\"/basic/ccy\",\"value\":\"EUR\"}]"));
        assertEquals(Json.parse("{\"basic\":{\"status\":\"Live\",\"ccy\":\"EUR\"}}"),
                Json.parse(expected.resolve(variant).orElseThrow().json()));
    }

    @Test
    public void referenceWithoutItsFileSaysHowToFix() {
        expected.write(id, "{\"basic\":{\"_shared\":\"basic/FX_TRF\"}}");
        assertResolveFails(id, "basic/FX_TRF", "TestDataTool compact all");
    }

    @Test
    public void sharedFolderIsNotAnEvent() {
        expected.shared().write("basic/FX_TRF", BASIC);
        expected.write(id, "{}");
        assertEquals(List.of("trade_creation"), expected.events());
        assertEquals(List.of("basic/FX_TRF"), expected.shared().names());
    }

    @Test
    public void referenceHoldsOnlyTheKeysThatDiffer() {
        ObjectNode shared = (ObjectNode) Json.parse(BASIC);
        assertEquals(Json.parse("{\"_shared\":\"b\",\"status\":\"Draft\",\"udf\":[]}"), SharedFragments.reference("b",
                shared, (ObjectNode) Json.parse("{\"status\":\"Draft\",\"ccy\":\"USD\",\"udf\":[]}")));
        assertNull("a reference cannot remove a key",
                SharedFragments.reference("b", shared, (ObjectNode) Json.parse("{\"status\":\"Live\"}")));
    }

    @Test
    public void compactionWritesTheSharedPartAndTheReferences() {
        expected.write(id, "{\"trade\":{\"id\":1,\"basic\":" + BASIC + "}}");
        expected.write(variant, "{\"trade\":{\"id\":2,\"basic\":{\"status\":\"Draft\",\"ccy\":\"USD\"}}}");
        expected.write(third, "{\"error\":\"no trade\"}");
        ExpectedCompaction.Shared shared = ExpectedCompaction.Shared.fromDisk(expected);
        ExpectedCompaction.Plan plan = ExpectedCompaction.plan(expected, "trade_creation", c -> null,
                c -> Map.of("trade.basic", "basic/FX_TRF"), shared);
        assertEquals(List.of(), plan.errors());
        ExpectedCompaction.write(expected, shared);
        ExpectedCompaction.write(expected, plan);

        assertEquals(Json.parse(BASIC), expected.shared().read("basic/FX_TRF").orElseThrow());
        assertEquals(Json.parse("{\"trade\":{\"id\":1,\"basic\":{\"_shared\":\"basic/FX_TRF\"}}}"),
                Json.parse(expected.read(id).orElseThrow()));
        assertEquals(Json.parse("{\"trade\":{\"id\":2,\"basic\":{\"_shared\":\"basic/FX_TRF\",\"status\":\"Draft\"}}}"),
                Json.parse(expected.read(variant).orElseThrow()));
        assertEquals(Json.parse("{\"error\":\"no trade\"}"), Json.parse(expected.read(third).orElseThrow()));
        assertEquals(Json.parse("{\"trade\":{\"id\":2,\"basic\":{\"status\":\"Draft\",\"ccy\":\"USD\"}}}"),
                Json.parse(expected.resolve(variant).orElseThrow().json()));
    }

    @Test
    public void rebuildTakesTheSharedPartFromTheSourceEventFirst() {
        CaseId amendment = CaseId.parse("TC-TRADE-AMENDMENT-FX_TRF-API-001");
        expected.write(amendment, "{\"data\":{\"basic\":{\"status\":\"Live\",\"ccy\":\"USD\",\"version\":2}}}");
        expected.write(id, "{\"data\":{\"trade\":{\"basic\":{\"status\":\"Live\",\"ccy\":\"USD\",\"version\":1}}}}");
        List<String> order = ExpectedCompaction.order(expected.events(), List.of("trade_creation", "no_recordings"));
        assertEquals(List.of("trade_creation", "trade_amendment"), order);

        ExpectedCompaction.Shared shared = ExpectedCompaction.Shared.rebuild(expected);
        for (String event : order) {
            ExpectedCompaction.write(expected, ExpectedCompaction.plan(expected, event, c -> null,
                    c -> Map.of(event.equals("trade_creation") ? "data.trade.basic" : "data.basic", "basic/FX_TRF"),
                    shared));
        }
        ExpectedCompaction.write(expected, shared);
        assertEquals(Json.parse("{\"status\":\"Live\",\"ccy\":\"USD\",\"version\":1}"),
                expected.shared().read("basic/FX_TRF").orElseThrow());
        assertEquals(Json.parse("{\"data\":{\"basic\":{\"_shared\":\"basic/FX_TRF\",\"version\":2}}}"),
                Json.parse(expected.read(amendment).orElseThrow()));
    }

    @Test
    public void rebuildDeletesASharedPartNoCaseUses() {
        expected.shared().write("basic/OLD", BASIC);
        expected.write(id, "{}");
        ExpectedCompaction.Shared shared = ExpectedCompaction.Shared.rebuild(expected);
        ExpectedCompaction.plan(expected, "trade_creation", c -> null, c -> Map.of(), shared);
        ExpectedCompaction.write(expected, shared);
        assertEquals(List.of(), expected.shared().names());
    }
}
