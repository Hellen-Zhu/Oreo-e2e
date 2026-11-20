package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The SIT seed: trades booked in bulk for UAT, not tests. A booking is named in the seed features by its seed case ID
 * ({@code SEED-TRADE-001}), its composition (the Excel's Itradeable Type, the E2E's composition), product ID and ISDA
 * taxonomy after the asset class ({@code ComplexExotic}, a label); its catalog, {@code seed/catalog.json} on the
 * classpath, maps each Itradeable Type to its composition, one to one, and holds what a pair needs beyond its IDs,
 * the variants and the lifecycle events. Its manifest,
 * {@code target/seed/manifest-<date>.jsonl} under the project, holds one line per booking, which
 * {@code TestDataTool seed-report} turns into the summary for UAT. Details: {@code docs/sit-seed.md}.
 *
 * <ul>
 *   <li>{@code itradeableTypes}, {@code {"<Itradeable Type>": "<composition>"}}: the Excel's full Itradeable Type
 *       of each composition the seed books ({@code Cortex.Finance.Tradeable.TRF} is {@code FX_TRF}), one to one. A booking's composition
 *       must be in it; its Itradeable Type goes to the manifest and the report.</li>
 *   <li>A pair's dat file: a composition registered in {@link ProductDatFiles} takes it from {@code data/dat/<C>/}
 *       ({@code <C>.dat}), any other from {@code seed/dat/<C>/} ({@code <productId>.dat}). {@code trades}, a list of
 *       {@code {"composition", "productId", "taxonomy"?, "dat"?, "setup"?}}, gives another file name or another body
 *       for a pair, or for a pair with one taxonomy; the body is {@code data/setup/<name>.json}, {@code trade-create}
 *       by default.</li>
 *   <li>A variant ({@code variants}, by name: {@code {"setup": ...}}) books a pair with another body: StepIn Full,
 *       StepIn Partial.</li>
 *   <li>An event is booked with the request of an E2E case ({@code "case"}, the Outline's ID with
 *       {@code <product_type>}) on a new trade of the pair: one body for the test and the seed. {@code "via"} is
 *       {@code trigger-event} (the default, the body's {@code tradeIds} replaced) or {@code update} (the amendment,
 *       posted to the trade's update path). The pair's composition must be registered: the case's product.</li>
 * </ul>
 */
public final class SeedData {

    public static final String CATALOG = "seed/catalog.json";
    public static final String DEFAULT_SETUP = "trade-create";
    /** Where the dat files of a composition the E2E cases do not register are, {@code seed/dat/<C>/}. */
    public static final String SEED_DAT_DIR = "seed/dat/";
    private static final String PRODUCT = "<product_type>";
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_]*");
    /** An Itradeable Type, the full name of the Excel's column: {@code Cortex.Finance.Tradeable.TRF}. */
    private static final Pattern ITRADEABLE_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    /** The ISDA taxonomy after its asset class: {@code ComplexExotic}, {@code SimpleExotic:Digital}. */
    private static final Pattern TAXONOMY = Pattern.compile("[A-Za-z]+(:[A-Za-z]+)*");
    /** A seed case ID, the {@code case} column: {@code SEED-TRADE-001}. */
    private static final Pattern CASE = Pattern.compile("SEED-[A-Z]+-[0-9]{3}");

    /**
     * What one booking sends: a composition, its Itradeable Type and a product ID, the ISDA taxonomy after its asset class (a label of
     * the booking, {@code null} for none), the variant ({@code null} for none), the classpath path of its dat file,
     * its setup body; {@code registered} when the composition is in {@link ProductDatFiles}.
     */
    public record Trade(String composition, String itradeableType, String productId, String taxonomy, String variant, String dat,
                        String setup, boolean registered) {}

    /** A lifecycle event of the catalog, booked with the request of the E2E case {@code caseId} through {@code via}. */
    public record Event(String name, String caseId, String via) {}

    /** An event sent to {@code POST /trades/trigger-event}, its {@code tradeIds} replaced: the default. */
    public static final String TRIGGER_EVENT = "trigger-event";
    /** An event sent to {@code POST /trades/<id>/update}, the trade amendment. */
    public static final String UPDATE = "update";

    private record Override(String dat, String setup) {}

    /** Itradeable Type -> composition, and back: one to one. */
    private final Map<String, String> itradeableTypes;
    private final Map<String, String> typeOfComposition;
    private final Map<String, Override> overrides;
    private final Map<String, String> variants;
    private final Map<String, Event> events;

    private SeedData(Map<String, String> itradeableTypes, Map<String, String> typeOfComposition,
                     Map<String, Override> overrides, Map<String, String> variants, Map<String, Event> events) {
        this.itradeableTypes = itradeableTypes;
        this.typeOfComposition = typeOfComposition;
        this.overrides = overrides;
        this.variants = variants;
        this.events = events;
    }

    private static volatile SeedData catalog;

    /** The catalog {@link #CATALOG}, read once. */
    public static SeedData catalog() {
        SeedData c = catalog;
        if (c == null) {
            synchronized (SeedData.class) {
                if (catalog == null) catalog = load(CATALOG);
                c = catalog;
            }
        }
        return c;
    }

    /**
     * The catalog at classpath {@code resource}.
     *
     * @throws IllegalStateException naming the entry, when it is missing or an entry is not as described above
     */
    public static SeedData load(String resource) {
        JsonNode root;
        try (InputStream in = SeedData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("[Seed] " + resource + " not found on the classpath");
            root = Json.MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("[Seed] " + resource + " is not JSON: " + e.getMessage(), e);
        }
        return fromJson(resource, root);
    }

    /** The catalog {@code root}, read from {@code resource} (named in the errors). */
    static SeedData fromJson(String resource, JsonNode root) {
        Map<String, String> itradeableTypes = new LinkedHashMap<>();
        Map<String, String> typeOfComposition = new LinkedHashMap<>();
        JsonNode types = root.path("itradeableTypes");
        if (!types.isMissingNode() && !types.isObject()) {
            throw new IllegalStateException("[Seed] " + resource + " itradeableTypes: {\"<Itradeable Type>\":"
                    + " \"<composition>\"}");
        }
        types.properties().forEach(e -> {
            String at = "[Seed] " + resource + " itradeableTypes." + e.getKey();
            if (!ITRADEABLE_TYPE.matcher(e.getKey()).matches()) {
                throw new IllegalStateException(at + ": an Itradeable Type is its full dotted name"
                        + " (Cortex.Finance.Tradeable.TRF)");
            }
            String composition = id(at, "composition", e.getValue().isTextual() ? e.getValue().asText() : null);
            String other = typeOfComposition.put(composition, e.getKey());
            if (other != null) {
                throw new IllegalStateException(at + ": " + composition + " is already Itradeable Type " + other
                        + " (one to one)");
            }
            itradeableTypes.put(e.getKey(), composition);
        });
        Map<String, Override> overrides = new LinkedHashMap<>();
        JsonNode trades = root.path("trades");
        if (!trades.isMissingNode() && !trades.isArray()) {
            throw new IllegalStateException("[Seed] " + resource + " trades: a list of {composition, productId,"
                    + " taxonomy, dat, setup}");
        }
        for (int i = 0; i < trades.size(); i++) {
            JsonNode node = trades.get(i);
            String at = "[Seed] " + resource + " trades[" + i + "]";
            String composition = id(at, "composition", text(node, "composition"));
            String productId = id(at, "productId", text(node, "productId"));
            String taxonomy = text(node, "taxonomy");
            if (taxonomy != null) taxonomy(at, taxonomy);
            String dat = text(node, "dat");
            if (dat != null) checkDat(at, dat);
            String key = key(composition, productId, taxonomy);
            if (overrides.put(key, new Override(dat, text(node, "setup"))) != null) {
                throw new IllegalStateException(at + ": " + key + " is listed twice");
            }
        }
        Map<String, String> variants = new LinkedHashMap<>();
        root.path("variants").properties().forEach(e -> {
            String at = "[Seed] " + resource + " variants." + e.getKey();
            checkName(at, e.getKey());
            String setup = text(e.getValue(), "setup");
            if (setup == null) throw new IllegalStateException(at + ": \"setup\" is the variant's body");
            variants.put(e.getKey(), setup);
        });
        Map<String, Event> events = new LinkedHashMap<>();
        root.path("events").properties().forEach(e -> events.put(e.getKey(), event(resource, e.getKey(), e.getValue())));
        return new SeedData(Collections.unmodifiableMap(itradeableTypes),
                Collections.unmodifiableMap(typeOfComposition), Collections.unmodifiableMap(overrides), Collections.unmodifiableMap(variants),
                Collections.unmodifiableMap(events));
    }

    private static Event event(String resource, String name, JsonNode node) {
        String at = "[Seed] " + resource + " events." + name;
        checkName(at, name);
        String caseId = text(node, "case");
        if (caseId == null || !caseId.contains(PRODUCT)) {
            throw new IllegalStateException(at + ": \"case\" is the E2E Outline's case ID with " + PRODUCT);
        }
        CaseId.checkRowId(caseId);
        String via = text(node, "via");
        if (via == null) via = TRIGGER_EVENT;
        if (!via.equals(TRIGGER_EVENT) && !via.equals(UPDATE)) {
            throw new IllegalStateException(at + ": \"via\" is " + TRIGGER_EVENT + " (the default) or " + UPDATE);
        }
        return new Event(name, caseId, via);
    }

    /** {@link #trade(String, String, String, String)} with no taxonomy. */
    public Trade trade(String composition, String productId, String variant) {
        return trade(composition, productId, null, variant);
    }

    /**
     * What a booking of {@code composition} under {@code productId} with {@code taxonomy} ({@code null} for none)
     * sends, as variant {@code variant} ({@code null} for none). A {@code trades} entry with this taxonomy wins over
     * one without.
     *
     * @throws IllegalStateException when an ID or the taxonomy is not one, the composition has no Itradeable Type,
     *         or the catalog has no such variant
     */
    public Trade trade(String composition, String productId, String taxonomy, String variant) {
        String at = "[Seed] " + key(composition, productId, taxonomy);
        id(at, "composition", composition);
        id(at, "productId", productId);
        if (taxonomy != null) taxonomy(at, taxonomy);
        String itradeableType = typeOfComposition.get(composition);
        if (itradeableType == null) {
            throw new IllegalStateException(at + ": composition " + composition + " has no Itradeable Type in "
                    + CATALOG + " itradeableTypes");
        }
        Override override = taxonomy == null ? null : overrides.get(key(composition, productId, taxonomy));
        if (override == null) override = overrides.get(key(composition, productId, null));
        String setup = override == null || override.setup() == null ? DEFAULT_SETUP : override.setup();
        if (variant != null) {
            setup = variants.get(variant);
            if (setup == null) throw new IllegalStateException(at + ": no variant '" + variant + "' in " + CATALOG);
        }
        String dat = override == null ? null : override.dat();
        ProductDatFiles registered = registered(composition);
        String path = registered != null ? registered.path(dat)
                : SEED_DAT_DIR + composition + "/" + (dat == null ? productId + ".dat" : dat);
        return new Trade(composition, itradeableType, productId, taxonomy, variant, path, setup, registered != null);
    }

    /** The registered composition {@code name}, or {@code null}. */
    private static ProductDatFiles registered(String name) {
        for (ProductDatFiles p : ProductDatFiles.values()) {
            if (p.name().equals(name)) return p;
        }
        return null;
    }

    private static String key(String composition, String productId, String taxonomy) {
        return composition + " + " + productId + (taxonomy == null ? "" : " (" + taxonomy + ")");
    }

    private static String id(String at, String what, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalStateException(at + ": \"" + what + "\" is an ID: letters, digits and _ (FX_TRF), not '"
                    + value + "'");
        }
        return value;
    }

    private static void taxonomy(String at, String value) {
        if (!TAXONOMY.matcher(value).matches()) {
            throw new IllegalStateException(at + ": \"taxonomy\" is the ISDA taxonomy after its asset class, words"
                    + " joined by : (ComplexExotic, SimpleExotic:Digital), not '" + value + "'");
        }
    }

    /**
     * Checks a seed case ID, the {@code case} column ({@code SEED-TRADE-001}).
     *
     * @throws IllegalStateException when it is not one
     */
    public static String checkCase(String caseId) {
        if (caseId == null || !CASE.matcher(caseId).matches()) {
            throw new IllegalStateException("[Seed] a seed case ID is SEED-<KIND>-<3 digits> (SEED-TRADE-001), not '"
                    + caseId + "'");
        }
        return caseId;
    }

    private static void checkDat(String at, String dat) {
        try {
            ProductDatFiles.checkFileName(dat);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(at + ": " + e.getMessage(), e);
        }
    }

    private static void checkName(String at, String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalStateException(at + ": a name is lower case, digits and -");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    /** Itradeable Type -> composition, one to one. */
    public Map<String, String> itradeableTypes() {
        return itradeableTypes;
    }

    /** The variants, name -> setup. */
    public Map<String, String> variants() {
        return variants;
    }

    public Map<String, Event> events() {
        return events;
    }

    /** @throws IllegalStateException when the catalog has no event {@code name} */
    public Event event(String name) {
        Event e = events.get(name);
        if (e == null) throw new IllegalStateException("[Seed] no event '" + name + "' in " + CATALOG);
        return e;
    }

    /**
     * The request of {@code event} on {@code trade}: its E2E case's merged request for the trade's composition.
     *
     * @throws IllegalStateException when the composition is not registered or the case has no request
     */
    public static String eventRequest(Event event, Trade trade) {
        if (!trade.registered()) {
            throw new IllegalStateException("[Seed] event '" + event.name() + "' on " + key(trade.composition(),
                    trade.productId(), trade.taxonomy()) + ": an event needs a registered composition (its case data is per"
                    + " composition)");
        }
        String caseId = event.caseId().replace(PRODUCT, trade.composition());
        CaseData data = TestData.forCase(caseId);
        if (!data.hasRequest()) {
            throw new IllegalStateException("[Seed] event '" + event.name() + "': case " + caseId + " has no request");
        }
        return data.request();
    }

    // ── Manifest ─────────────────────────────────────────────────────────────

    /** The folder of the manifests, {@code target/seed} under the project. */
    public static Path manifestDir() {
        return ProjectPaths.projectRoot().resolve("target/seed");
    }

    /**
     * Appends {@code entry} as one line to today's manifest, under a file lock: the seed features run in parallel
     * JVMs.
     */
    public static void record(ObjectNode entry) {
        Path file = manifestDir().resolve("manifest-" + LocalDate.now() + ".jsonl");
        byte[] line = (Json.compact(entry) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
                 FileLock ignored = channel.lock()) {
                channel.write(ByteBuffer.wrap(line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("[Seed] cannot write " + file, e);
        }
    }

    /** Every entry of the manifests in {@code dir}, file by file in name order, line by line. */
    public static List<JsonNode> readManifests(Path dir) throws IOException {
        List<JsonNode> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        List<Path> files;
        try (var list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().matches("manifest-.*\\.jsonl")).sorted().toList();
        }
        for (Path file : files) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) out.add(Json.parse(line));
            }
        }
        return out;
    }
}
