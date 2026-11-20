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
 * The SIT seed: trades booked in bulk for UAT, not tests. Its catalog, {@code seed/catalog.json} on the classpath,
 * names the trades ({@code trades}) and the lifecycle events ({@code events}) the seed features book by name; its
 * manifest, {@code target/seed/manifest-<date>.jsonl} under the project, holds one line per booking, which
 * {@code TestDataTool seed-report} turns into the summary for UAT. Details: {@code docs/sit-seed.md}.
 *
 * <ul>
 *   <li>A trade is a registered composition ({@code "composition": "FX_TRF"}: its product ID and dat file from
 *       {@link ProductDatFiles}), or a product ID with the classpath path of its dat file
 *       ({@code "productId"}, {@code "dat"}); {@code "setup"} is the body, {@code data/setup/<name>.json}
 *       ({@code trade-create} when absent).</li>
 *   <li>An event is booked with the request of an E2E case ({@code "case"}, the Outline's ID with
 *       {@code <product_type>}), its {@code tradeIds} replaced by the trade booked for it: one body for the test and
 *       the seed. Its trade must be a composition, the case's product.</li>
 * </ul>
 */
public final class SeedData {

    public static final String CATALOG = "seed/catalog.json";
    public static final String DEFAULT_SETUP = "trade-create";
    private static final String PRODUCT = "<product_type>";
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    /** A trade of the catalog; {@code composition} is {@code null} for a product ID that is not registered. */
    public record Trade(String name, String composition, String productId, String dat, String setup) {}

    /** A lifecycle event of the catalog, booked with the request of the E2E case {@code caseId} (with its product). */
    public record Event(String name, String caseId) {}

    private final Map<String, Trade> trades;
    private final Map<String, Event> events;

    private SeedData(Map<String, Trade> trades, Map<String, Event> events) {
        this.trades = trades;
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
        Map<String, Trade> trades = new LinkedHashMap<>();
        root.path("trades").properties().forEach(e -> trades.put(e.getKey(), trade(resource, e.getKey(), e.getValue())));
        Map<String, Event> events = new LinkedHashMap<>();
        root.path("events").properties().forEach(e -> events.put(e.getKey(), event(resource, e.getKey(), e.getValue())));
        if (trades.isEmpty()) throw new IllegalStateException("[Seed] " + resource + ": no trades");
        return new SeedData(Collections.unmodifiableMap(trades), Collections.unmodifiableMap(events));
    }

    private static Trade trade(String resource, String name, JsonNode node) {
        String at = "[Seed] " + resource + " trades." + name;
        checkName(at, name);
        String composition = text(node, "composition");
        String productId = text(node, "productId");
        String dat = text(node, "dat");
        String setup = text(node, "setup");
        if (composition != null) {
            if (productId != null || dat != null) {
                throw new IllegalStateException(at + ": a composition takes its product ID and dat file from"
                        + " ProductDatFiles; give either composition or productId and dat");
            }
            ProductDatFiles p = ProductDatFiles.of(composition);
            productId = p.productId();
            dat = p.path();
            composition = p.name();
        } else if (productId == null || dat == null) {
            throw new IllegalStateException(at + ": give composition, or productId and dat");
        }
        return new Trade(name, composition, productId, dat, setup == null ? DEFAULT_SETUP : setup);
    }

    private static Event event(String resource, String name, JsonNode node) {
        String at = "[Seed] " + resource + " events." + name;
        checkName(at, name);
        String caseId = text(node, "case");
        if (caseId == null || !caseId.contains(PRODUCT)) {
            throw new IllegalStateException(at + ": \"case\" is the E2E Outline's case ID with " + PRODUCT);
        }
        CaseId.checkRowId(caseId);
        return new Event(name, caseId);
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

    public Map<String, Trade> trades() {
        return trades;
    }

    public Map<String, Event> events() {
        return events;
    }

    /** @throws IllegalStateException when the catalog has no trade {@code name} */
    public Trade trade(String name) {
        Trade t = trades.get(name);
        if (t == null) throw new IllegalStateException("[Seed] no trade '" + name + "' in " + CATALOG);
        return t;
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
     * @throws IllegalStateException when the trade is not a composition or the case has no request
     */
    public static String eventRequest(Event event, Trade trade) {
        if (trade.composition() == null) {
            throw new IllegalStateException("[Seed] event '" + event.name() + "' on trade '" + trade.name()
                    + "': an event needs a trade of a registered composition (its case data is per composition)");
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
