package com.example.module.oreo.testdata;

import com.example.module.oreo.utils.DateUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
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

/**
 * The data files of these tests are written here, into a temporary folder, and read through a class loader that sees
 * that folder only: the project's own {@code data/} is never read, and no fixture lives under {@code src/test/resources}.
 */
public class TestDataRepositoryTest {

    @ClassRule
    public static final TemporaryFolder TMP = new TemporaryFolder();

    private static URLClassLoader fixtures;

    private final TestDataRepository repo = new TestDataRepository("data/");
    private ClassLoader previousLoader;

    @BeforeClass
    public static void writeFixtures() throws IOException {
        // Shared blocks
        write("api/common.json", """
                { "sharedSources": ["sample_event"],
                  "shared": {
                    "base": { "request": { "basic": { "a": "1", "premium": 10 } } },
                    "extra": { "request": { "basic": { "udf": ["x"] } }, "dynamicFields": { "msg": "type" } } } }""");
        // Every layer
        write("api/sample_event.json", """
                {
                  "defaults": { "use": ["base"], "dynamicFields": { "data.id": "type" },
                    "expectedBase": "TC-SAMPLE-EVENT-<product_type>-API-001" },
                  "byProduct": { "FX_PSCRIPT_TRF": { "request": { "basic": { "premium": null } } } },
                  "cases": [
                    { "id": "TC-SAMPLE-EVENT-<product_type>-API-002", "use": ["extra"],
                      "byProduct": { "FX_PSCRIPT_TRF": { "request": { "basic": { "ccy": "" } }, "expectedBase": null } } },
                    { "id": "TC-SAMPLE-EVENT-API-004", "_comment": "a negative case drops the rules",
                      "request": { "basic": { "a": "2" } }, "dynamicFields": null }
                  ]
                }""");
        // A block of the file's own (lists replace whole), and dates filled in when the case runs
        write("api/block_event.json", """
                {
                  "shared": { "target-amount": { "request": { "data": [
                    { "key": "newCptyName", "value": "A", "type": "Text" },
                    { "key": "novationDate", "value": "${date:T}", "type": "Date" },
                    { "key": "newTargetAmount", "value": 1000, "type": "Numeric" } ] } } },
                  "defaults": { "request": {
                    "data": [
                      { "key": "newCptyName", "value": "A", "type": "Text" },
                      { "key": "novationDate", "value": "${date:T}", "type": "Date" }
                    ],
                    "comments": "settles on ${date:T+2}, booked by ${userName}" } },
                  "byProduct": { "FX_TRF": { "use": ["target-amount"] } },
                  "cases": [
                    { "id": "TC-BLOCK-EVENT-API-003", "_comment": "a case without a product names the block itself",
                      "use": ["target-amount"] }
                  ]
                }""");
        // The dat file as case data
        write("api/dat_event.json", """
                {
                  "byProduct": { "FX_PSCRIPT": { "datFile": "variant.dat" } },
                  "cases": [
                    { "id": "TC-DAT-EVENT-<product_type>-API-002",
                      "byProduct": { "FX_TRF": { "datFile": "variant.dat" }, "FX_PSCRIPT": { "datFile": null } } },
                    { "id": "TC-DAT-EVENT-FX_TRF-API-003", "datFile": "missing.dat" },
                    { "id": "TC-DAT-EVENT-API-004", "_comment": "no product: the step's product folder", "datFile": "variant.dat" }
                  ]
                }""");
        write("dat/FX_TRF/variant.dat", "# a variant dat file of FX_TRF");
        write("dat/FX_PSCRIPT/variant.dat", "# a variant dat file of FX_PSCRIPT");
        // Broken event files, one mistake each
        write("api/two_rows.json", """
                { "cases": [ { "id": "TC-TWO-ROWS-<product_type>-API-001" }, { "id": "TC-TWO-ROWS-FX_TRF-API-001" } ] }""");
        write("api/bad_key.json", """
                { "byproduct": {} }""");
        write("api/bad_use.json", """
                { "cases": [ { "id": "TC-BAD-USE-API-009", "use": ["nope"] } ] }""");
        write("api/bad_base.json", """
                { "defaults": { "expectedBase": "TC-OTHER-EVENT-<product_type>-API-001" } }""");
        write("api/bad_date.json", """
                { "defaults": { "request": { "trade": { "tradeDate": "${date:tomorrow}" } } } }""");
        write("api/bad_dat.json", """
                { "defaults": { "datFile": "../FX_TRF.dat" } }""");
        write("api/clash_block.json", """
                { "shared": { "base": { "request": { "basic": { "a": "9" } } } } }""");
        // Shared response parts: a block names them and their files, the event gives their paths, a row adds or removes one
        write("api/fragment_event.json", """
                {
                  "shared": {
                    "parts": { "sharedFragments": { "basic": "basic/<product_type>", "instrument": "instrument/<product_type>" } },
                    "trace-part": { "sharedFragments": { "trace": "trace/common" } } },
                  "defaults": { "use": ["parts"],
                    "sharedFragments": { "basic": "data.trade.basic", "instrument": "data.trade.instrument" } },
                  "cases": [
                    { "id": "TC-FRAGMENT-EVENT-<product_type>-API-002", "use": ["trace-part"],
                      "sharedFragments": { "instrument": null, "trace": "data.trade.trace" } },
                    { "id": "TC-FRAGMENT-EVENT-<product_type>-API-003", "sharedFragments": null }
                  ]
                }""");
        // Rules written once against a shared part; this event holds the parts without "trade"
        write("api/part_rules_event.json", """
                {
                  "shared": { "response-parts": {
                    "sharedFragments": { "basic": "basic/<product_type>", "instrument": "instrument/<product_type>" },
                    "dynamicFields": { "{basic}.dealDate": "type", "{instrument}": "type-tree" } } },
                  "defaults": { "use": ["response-parts"], "dynamicFields": { "data.id": "type" },
                    "sharedFragments": { "basic": "data.basic", "instrument": "data.instrument" } },
                  "cases": [
                    { "id": "TC-PART-RULES-EVENT-<product_type>-API-002", "sharedFragments": null },
                    { "id": "TC-PART-RULES-EVENT-<product_type>-API-003", "sharedFragments": { "instrument": null } }
                  ]
                }""");
        write("api/no_path_event.json", """
                { "shared": { "parts": { "sharedFragments": { "basic": "basic/<product_type>" } } },
                  "defaults": { "use": ["parts"] } }""");
        write("api/path_key_event.json", """
                { "defaults": { "sharedFragments": { "data.trade.basic": "basic/<product_type>" } } }""");
        write("api/bad_part_rule.json", """
                { "defaults": { "dynamicFields": { "{basic.dealDate": "type" } } }""");
        write("api/bad_fragment.json", """
                { "defaults": { "sharedFragments": { "basic": "../basic" } } }""");
        // UI: flattening into ctx keys; dynamicFields, expectedBase and sharedFragments are API only
        write("ui/flatten_event.json", """
                { "defaults": { "request": { "basic": { "a": "1" }, "udf": [ { "k": "v" } ] } } }""");
        write("ui/ui_event.json", """
                { "defaults": { "dynamicFields": { "x": "type" } } }""");
        write("ui/ui_base.json", """
                { "defaults": { "expectedBase": "TC-UI-BASE-<product_type>-UI-001" } }""");
        write("ui/ui_fragment.json", """
                { "defaults": { "sharedFragments": { "basic": "basic/<product_type>" } } }""");
        // Setup data
        write("setup/base-setup.json", """
                { "request": { "basic": { "a": "1" } } }""");
        write("setup/child-setup.json", """
                { "extends": "base-setup", "request": { "basic": { "b": "2" } } }""");
        write("setup/list-setup.json", """
                { "request": { "udf": [ { "key": "a", "value": "1" } ], "valueDate": "${date:T-1}" } }""");
        write("setup/list-child-setup.json", """
                { "extends": "list-setup", "request": { "udf": [ { "key": "b", "value": "2" } ] } }""");
        write("setup/response-only-setup.json", """
                { "response": { "code": 200 } }""");
        write("setup/loop-a.json", """
                { "extends": "loop-b" }""");
        write("setup/loop-b.json", """
                { "extends": "loop-a" }""");

        // No parent: a resource is looked up in the temporary folder only, never in target/test-classes
        fixtures = new URLClassLoader(new URL[] {TMP.getRoot().toURI().toURL()}, null);
    }

    private static void write(String path, String content) throws IOException {
        Path file = TMP.getRoot().toPath().resolve("data").resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Before
    public void readFixtures() {
        previousLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(fixtures);
    }

    @After
    public void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(previousLoader);
    }

    private static void assertJson(String expected, String actual) {
        assertEquals(Json.parse(expected), Json.parse(actual));
    }

    /** Today plus {@code days}, as {@code ${date:T+days}} gives it. */
    private static String day(int days) {
        return DateUtil.today().plusDays(days).toString();
    }

    private static void assertFailsWith(Runnable load, String... parts) {
        try {
            load.run();
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            for (String part : parts) {
                assertTrue(e.getMessage(), e.getMessage().contains(part));
            }
        }
    }

    @Test
    public void caseWithoutRowRunsOnDefaults() {
        CaseData data = repo.forCase("TC-SAMPLE-EVENT-FX_TRF-API-001");
        assertJson("{\"basic\":{\"a\":\"1\",\"premium\":10}}", data.request());
        assertEquals(Map.of("data.id", "type"), data.dynamicFields());
        assertEquals(List.of("sample_event.json defaults (use base)"), data.layers());
    }

    @Test
    public void allLayersInOrder() {
        CaseData data = repo.forCase("TC-SAMPLE-EVENT-FX_PSCRIPT_TRF-API-002");
        assertJson("{\"basic\":{\"a\":\"1\",\"udf\":[\"x\"],\"ccy\":\"\"}}", data.request());
        assertEquals(List.of(
                "sample_event.json defaults (use base)",
                "sample_event.json byProduct FX_PSCRIPT_TRF",
                "row TC-SAMPLE-EVENT-<product_type>-API-002 (use extra)",
                "row TC-SAMPLE-EVENT-<product_type>-API-002 byProduct FX_PSCRIPT_TRF"), data.layers());
        assertEquals(Map.of("data.id", "type", "msg", "type"), data.dynamicFields());
    }

    @Test
    public void caseWithoutProductSkipsByProduct() {
        CaseData data = repo.forCase("TC-SAMPLE-EVENT-API-004");
        assertJson("{\"basic\":{\"a\":\"2\",\"premium\":10}}", data.request());
        assertTrue(data.dynamicFields().isEmpty());
    }

    @Test
    public void missingEventFileMeansNoData() {
        CaseData data = repo.forCase("TC-NO-SUCH-EVENT-FX_TRF-UI-001");
        assertFalse(data.hasRequest());
        assertEquals("{}", data.request());
        assertTrue(data.layers().isEmpty());
    }

    @Test
    public void flattensLikeTheOldRecord() {
        Map<String, String> fields = repo.forCase("TC-FLATTEN-EVENT-FX_TRF-UI-001").flattenRequest();
        assertEquals(Map.of("basic.a", "1", "udf[0].k", "v"), fields);
    }

    @Test(expected = IllegalStateException.class)
    public void unknownKeyIsAnError() {
        repo.forCase("TC-BAD-KEY-FX_TRF-API-001");
    }

    @Test(expected = IllegalStateException.class)
    public void twoMatchingRowsIsAnError() {
        repo.forCase("TC-TWO-ROWS-FX_TRF-API-001");
    }

    @Test(expected = IllegalStateException.class)
    public void unknownSharedBlockIsAnError() {
        repo.forCase("TC-BAD-USE-API-009");
    }

    @Test(expected = IllegalStateException.class)
    public void dynamicFieldsRejectedInUi() {
        repo.forCase("TC-UI-EVENT-FX_TRF-UI-001");
    }

    @Test
    public void expectedBaseFollowsTheProduct() {
        assertEquals("TC-SAMPLE-EVENT-FX_TRF-API-001", repo.forCase("TC-SAMPLE-EVENT-FX_TRF-API-002").expectedBase());
    }

    @Test
    public void baseCaseHasNoBase() {
        assertNull(repo.forCase("TC-SAMPLE-EVENT-FX_TRF-API-001").expectedBase());
    }

    @Test
    public void caseWithoutProductHasNoBase() {
        assertNull(repo.forCase("TC-SAMPLE-EVENT-API-004").expectedBase());
    }

    @Test
    public void nullRemovesTheBase() {
        assertNull(repo.forCase("TC-SAMPLE-EVENT-FX_PSCRIPT_TRF-API-002").expectedBase());
    }

    @Test(expected = IllegalStateException.class)
    public void baseInAnotherEventIsAnError() {
        repo.forCase("TC-BAD-BASE-FX_TRF-API-002");
    }

    @Test(expected = IllegalStateException.class)
    public void expectedBaseRejectedInUi() {
        repo.forCase("TC-UI-BASE-FX_TRF-UI-002");
    }

    @Test
    public void sharedFragmentsFollowTheProduct() {
        assertEquals(Map.of("data.trade.basic", "basic/FX_TRF", "data.trade.instrument", "instrument/FX_TRF"),
                repo.forCase("TC-FRAGMENT-EVENT-FX_TRF-API-001").sharedFragments());
    }

    @Test
    public void rowAddsAndRemovesSharedFragments() {
        assertEquals(Map.of("data.trade.basic", "basic/FX_PSCRIPT", "data.trade.trace", "trace/common"),
                repo.forCase("TC-FRAGMENT-EVENT-FX_PSCRIPT-API-002").sharedFragments());
        assertEquals(Map.of(), repo.forCase("TC-FRAGMENT-EVENT-FX_TRF-API-003").sharedFragments());
    }

    @Test
    public void caseWithoutProductSkipsProductFragments() {
        assertEquals(Map.of(), repo.forCase("TC-FRAGMENT-EVENT-API-009").sharedFragments());
    }

    @Test
    public void ruleOnASharedPartTakesThePathTheEventGivesThePart() {
        assertEquals(Map.of("data.basic.dealDate", "type", "data.instrument", "type-tree", "data.id", "type"),
                repo.forCase("TC-PART-RULES-EVENT-FX_TRF-API-001").dynamicFields());
    }

    @Test(expected = IllegalStateException.class)
    public void partWithoutItsPathIsAnError() {
        repo.forCase("TC-NO-PATH-EVENT-FX_TRF-API-001");
    }

    @Test(expected = IllegalStateException.class)
    public void pathAsKeyIsAnError() {
        repo.forCase("TC-PATH-KEY-EVENT-FX_TRF-API-001");
    }

    @Test
    public void partTakesThePathTheEventGives() {
        assertEquals(Map.of("data.basic", "basic/FX_TRF", "data.instrument", "instrument/FX_TRF"),
                repo.forCase("TC-PART-RULES-EVENT-FX_TRF-API-001").sharedFragments());
    }

    @Test
    public void caseWithoutThePartHasNoneOfItsRules() {
        assertEquals(Map.of("data.id", "type"), repo.forCase("TC-PART-RULES-EVENT-FX_TRF-API-002").dynamicFields());
        assertEquals(Map.of("data.basic.dealDate", "type", "data.id", "type"),
                repo.forCase("TC-PART-RULES-EVENT-FX_TRF-API-003").dynamicFields());
    }

    @Test(expected = IllegalStateException.class)
    public void malformedPartRuleIsAnError() {
        repo.forCase("TC-BAD-PART-RULE-FX_TRF-API-001");
    }

    @Test(expected = IllegalStateException.class)
    public void badFragmentNameIsAnError() {
        repo.forCase("TC-BAD-FRAGMENT-FX_TRF-API-001");
    }

    @Test(expected = IllegalStateException.class)
    public void sharedFragmentsRejectedInUi() {
        repo.forCase("TC-UI-FRAGMENT-FX_TRF-UI-001");
    }

    @Test
    public void productThatUsesTheFilesBlockGetsItsWholeList() {
        CaseData data = repo.forCase("TC-BLOCK-EVENT-FX_TRF-API-001");
        assertJson("{\"data\":[{\"key\":\"newCptyName\",\"value\":\"A\",\"type\":\"Text\"},"
                + "{\"key\":\"novationDate\",\"value\":\"" + day(0) + "\",\"type\":\"Date\"},"
                + "{\"key\":\"newTargetAmount\",\"value\":1000,\"type\":\"Numeric\"}],"
                + "\"comments\":\"settles on " + day(2) + ", booked by ${userName}\"}", data.request());
        assertEquals(List.of("block_event.json defaults", "block_event.json byProduct FX_TRF (use target-amount)"),
                data.layers());
    }

    @Test
    public void productWithoutTheBlockKeepsTheDefaultList() {
        CaseData data = repo.forCase("TC-BLOCK-EVENT-FX_PSCRIPT_TRF-API-001");
        assertEquals(2, Json.parse(data.request()).get("data").size());
        assertEquals(day(0), data.flattenRequest().get("data[1].value"));
    }

    @Test
    public void caseWithoutProductCanUseTheBlock() {
        CaseData data = repo.forCase("TC-BLOCK-EVENT-API-003");
        assertEquals("newTargetAmount", Json.parse(data.request()).at("/data/2/key").asText());
    }

    @Test
    public void unknownDateIsALoadErrorNamingTheField() {
        assertFailsWith(() -> repo.forCase("TC-BAD-DATE-FX_TRF-API-001"),
                "bad_date.json [defaults.request]", "trade.tradeDate", "${date:tomorrow}");
    }

    @Test
    public void sharedSourcesAreReadFromTheApiCommonFile() {
        assertEquals(List.of("sample_event"), repo.sharedSources("api"));
    }

    @Test
    public void sharedSourceThatIsNotAnEventNameIsAnError() throws IOException {
        Path file = TMP.getRoot().toPath().resolve("bad-sources/api/common.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ \"sharedSources\": [\"Trade-Creation\"] }", StandardCharsets.UTF_8);
        assertFailsWith(() -> new TestDataRepository("bad-sources/").sharedSources("api"),
                "[sharedSources]", "Trade-Creation", "not an event name");
    }

    @Test
    public void blockNamedLikeACommonBlockIsAnError() {
        assertFailsWith(() -> repo.forCase("TC-CLASH-BLOCK-FX_TRF-API-001"), "[shared.base]", "common.json");
    }

    @Test
    public void setupListReplacesTheListOfTheSetupItExtends() {
        assertJson("{\"udf\":[{\"key\":\"b\",\"value\":\"2\"}],"
                + "\"valueDate\":\"" + day(-1) + "\"}", repo.setup("list-child-setup").request());
    }

    @Test
    public void datFileIsTheProductsDefaultUnlessALayerNamesOne() {
        assertNull(repo.forCase("TC-DAT-EVENT-FX_TRF-API-001").datFile());
        assertEquals("variant.dat", repo.forCase("TC-DAT-EVENT-FX_PSCRIPT-API-001").datFile());
        assertEquals("variant.dat", repo.forCase("TC-DAT-EVENT-FX_TRF-API-002").datFile());
        assertNull(repo.forCase("TC-DAT-EVENT-FX_PSCRIPT-API-002").datFile());
        assertEquals("variant.dat", repo.forCase("TC-DAT-EVENT-API-004").datFile());
    }

    @Test
    public void missingVariantNamesTheLayerAndTheFile() {
        assertFailsWith(() -> repo.forCase("TC-DAT-EVENT-FX_TRF-API-003"),
                "[row TC-DAT-EVENT-FX_TRF-API-003 datFile]", "data/dat/FX_TRF/missing.dat");
    }

    @Test
    public void datFileWithAFolderIsALoadError() {
        assertFailsWith(() -> repo.forCase("TC-BAD-DAT-FX_TRF-API-001"), "[defaults.datFile]", "'../FX_TRF.dat'");
    }

    @Test
    public void datPathsAreInTheProductsFolder() {
        assertEquals("data/dat/FX_TRF/FX_TRF.dat", ProductDatFiles.FX_TRF.path());
        assertEquals("data/dat/FX_TRF/FX_TRF.dat", ProductDatFiles.FX_TRF.path(null));
        assertEquals("data/dat/FX_TRF/variant.dat", ProductDatFiles.FX_TRF.path("variant.dat"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void datPathRejectsAFolder() {
        ProductDatFiles.FX_TRF.path("sub/variant.dat");
    }

    @Test
    public void setupExtends() {
        SetupData setup = repo.setup("child-setup");
        assertJson("{\"basic\":{\"a\":\"1\",\"b\":\"2\"}}", setup.request());
        assertFalse(setup.hasResponse());
    }

    @Test(expected = IllegalStateException.class)
    public void setupCycleIsAnError() {
        repo.setup("loop-a");
    }

    @Test
    public void missingSetupRequestIsAnErrorNamingTheFile() {
        SetupData setup = repo.setup("response-only-setup");
        assertFalse(setup.hasRequest());
        try {
            setup.request();
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'response-only-setup' has no request"));
            assertTrue(e.getMessage(), e.getMessage().contains("data/setup/response-only-setup.json"));
        }
    }

    @Test
    public void missingSetupResponseIsAnError() {
        try {
            repo.setup("child-setup").response();
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'child-setup' has no response"));
            assertTrue(e.getMessage(), e.getMessage().contains("data/setup/child-setup.json"));
        }
    }

    @Test
    public void missingSetupNamesTheFolder() {
        try {
            repo.setup("nope");
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("data/setup/nope.json"));
        }
    }
}
