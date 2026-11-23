package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SeedData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code seed-report [<run ID>|<run ID>,<run ID>...|<run ID>..|all]}: the summary of the SIT seed for UAT
 * (docs/sit-seed.md). Reads the manifest of the latest run in {@code target/seed} (or of the given runs merged, the
 * runs from one on, or every run, {@link #runs}), keeps the latest line of each booking
 * (environment, case, composition, product ID, taxonomy, variant, event) and writes
 * {@code target/seed/seed-report-<run ID or all>.csv}, one row per booking with its Itradeable Type (from the catalog's
 * {@code itradeableTypes} when the line has none), portfolio, risk portfolio and entity; prints the counts, by kind and by Itradeable
 * Type, and the bookings that failed. Checks the catalog first. Never fails on a failed booking: the report lists it.
 */
final class SeedReport {

    static final List<String> COLUMNS = List.of("run", "tradeId", "env", "kind", "case", "itradeableType",
            "composition", "productId", "taxonomy", "portfolioId", "riskPortfolio", "scbEntity", "variant", "event",
            "setup", "dat", "childTradeIds", "status", "at", "error");

    private SeedReport() {}

    /** All runs, the latest line of each booking: {@code seed-report all}. */
    static final String ALL = "all";

    /**
     * The report of run {@code which}: {@code null} for the latest run, a run ID ({@code 20261120-153000}), or
     * {@link #ALL}. Writes {@code target/seed/seed-report-<run ID or all>.csv}.
     */
    static int run(String which) throws IOException {
        SeedData catalog = SeedData.catalog();
        System.out.println("Catalog " + SeedData.CATALOG + ": " + catalog.itradeableTypes().size()
                + " Itradeable Types, " + catalog.variants().size() + " variants, "
                + catalog.events().size() + " events");
        Path dir = SeedData.manifestDir();
        List<Path> manifests = SeedData.manifests(dir);
        if (manifests.isEmpty()) {
            System.out.println("No manifests in " + ProjectPaths.projectRoot().relativize(dir)
                    + ": run SeedTest first (docs/sit-seed.md)");
            return 0;
        }
        List<String> all = manifests.stream()
                .map(p -> p.getFileName().toString().replaceAll("^manifest-|\\.jsonl$", "")).toList();
        List<String> runs = runs(which, all);
        String name = ALL.equals(which) ? ALL : which != null && which.endsWith("..") ? which.replace("..", "-on")
                : String.join("+", runs);
        List<JsonNode> entries = new ArrayList<>();
        for (String run : runs) {
            entries.addAll(SeedData.readManifest(dir.resolve("manifest-" + run + ".jsonl")));
        }
        System.out.println("Run(s) " + String.join(", ", runs) + " (" + manifests.size() + " run(s) in "
                + ProjectPaths.projectRoot().relativize(dir) + ")");
        List<JsonNode> latest = withItradeableTypes(latest(asEvents(entries)), catalog.itradeableTypes());
        Path csv = dir.resolve("seed-report-" + name + ".csv");
        Files.writeString(csv, csv(latest), StandardCharsets.UTF_8);

        Map<String, int[]> counts = new TreeMap<>();
        Map<String, int[]> byType = new TreeMap<>();
        List<JsonNode> failed = new ArrayList<>();
        for (JsonNode entry : latest) {
            boolean booked = "booked".equals(entry.path("status").asText());
            counts.computeIfAbsent(entry.path("kind").asText(), k -> new int[2])[booked ? 0 : 1]++;
            byType.computeIfAbsent(entry.path("itradeableType").asText("?"), k -> new int[2])[booked ? 0 : 1]++;
            if (!booked) failed.add(entry);
        }
        counts.forEach((kind, n) -> System.out.println(kind + ": " + n[0] + " booked, " + n[1] + " failed"));
        System.out.println("By Itradeable Type:");
        byType.forEach((type, n) -> System.out.println("  " + type + ": " + n[0] + " booked, " + n[1] + " failed"));
        for (JsonNode entry : failed) {
            System.out.println("  failed " + entry.path("itradeableType").asText("?") + " " + key(entry) + ": "
                    + entry.path("error").asText());
        }
        System.out.println("Wrote " + ProjectPaths.projectRoot().relativize(csv));
        return 0;
    }

    /**
     * The runs {@code which} names, oldest first, of {@code all} (every run ID, oldest first): {@code null} the latest;
     * {@link #ALL} every one; {@code <run>..} that run and every later one; {@code <run>,<run>,...} those runs. Merged,
     * the latest line of each booking wins: a rerun of the failed cases over its first run.
     *
     * @throws IllegalArgumentException when a run ID is not one of {@code all}
     */
    static List<String> runs(String which, List<String> all) {
        if (which == null) return List.of(all.get(all.size() - 1));
        if (ALL.equals(which)) return all;
        if (which.endsWith("..")) {
            String from = which.substring(0, which.length() - 2);
            int i = all.indexOf(from);
            if (i < 0) throw new IllegalArgumentException("[Seed] no run " + from + ": " + all);
            return all.subList(i, all.size());
        }
        List<String> named = new ArrayList<>();
        for (String run : which.split(",")) {
            String id = run.trim();
            if (!all.contains(id)) throw new IllegalArgumentException("[Seed] no run " + id + ": " + all);
            if (!named.contains(id)) named.add(id);
        }
        return all.stream().filter(named::contains).toList();
    }

    /**
     * {@code entries} with each step-in booking (a line with a {@code variant}) of kind {@code event}: the report lists
     * step-ins among the lifecycle events, also those of runs that wrote them as {@code trade}.
     */
    static List<JsonNode> asEvents(List<JsonNode> entries) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode entry : entries) {
            boolean stepIn = entry.isObject() && !entry.path("variant").asText("").isEmpty()
                    && !"event".equals(entry.path("kind").asText());
            out.add(stepIn ? ((ObjectNode) entry.deepCopy()).put("kind", "event") : entry);
        }
        return out;
    }

    /** The latest entry of each booking, in the order of first appearance. */
    static List<JsonNode> latest(List<JsonNode> entries) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            byKey.put(key(entry), entry);
        }
        return List.copyOf(byKey.values());
    }

    /**
     * {@code entries} with the Itradeable Type of each one's composition, from {@code types} (composition ->
     * Itradeable Type), where a line has none: lines written before the manifest carried it.
     */
    static List<JsonNode> withItradeableTypes(List<JsonNode> entries, Map<String, String> types) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode entry : entries) {
            String type = types.get(entry.path("composition").asText());
            if (entry.hasNonNull("itradeableType") || type == null || !entry.isObject()) {
                out.add(entry);
            } else {
                out.add(((ObjectNode) entry.deepCopy()).put("itradeableType", type));
            }
        }
        return out;
    }

    private static String key(JsonNode entry) {
        return entry.path("env").asText("") + "|" + entry.path("kind").asText() + "|"
                + entry.path("case").asText("") + "|" + entry.path("composition").asText() + "|" + entry.path("productId").asText() + "|"
                + entry.path("taxonomy").asText("") + "|" + entry.path("variant").asText("") + "|" + entry.path("event").asText("");
    }

    static String csv(List<JsonNode> entries) {
        StringBuilder out = new StringBuilder(String.join(",", COLUMNS)).append('\n');
        for (JsonNode entry : entries) {
            List<String> cells = new ArrayList<>();
            for (String column : COLUMNS) {
                JsonNode value = entry.path(column);
                List<String> parts = new ArrayList<>();
                if (value.isArray()) value.forEach(v -> parts.add(v.asText()));
                cells.add(cell(value.isArray() ? String.join(" ", parts) : value.asText("")));
            }
            out.append(String.join(",", cells)).append('\n');
        }
        return out.toString();
    }

    // ── seed-dats: where each seed case's dat file goes ─────────────────────────────────────────────────────────

    /** Where the seed features are, under the project. */
    static final String SEED_FEATURES = "src/test/resources/seed/features";
    static final String RESOURCES = "src/test/resources/";
    /** The first line of a dat file that is still a placeholder. */
    static final String PLACEHOLDER = "# Placeholder";

    /** The state of a case's dat file. */
    enum DatState {
        /** The file is real. */
        ok,
        /** The file is a one-line placeholder: to export. */
        placeholder,
        /** No file yet: to export. */
        missing,
        /** The default dat of a registered composition, the E2E cases': never overwritten by an export. */
        e2e,
        /** An earlier case of the list books the same file: exported once, for that case. */
        shared
    }

    /** A seed case's dat file: the case's IDs, its path under the project and its state. */
    record CaseDat(String caseId, String kind, String composition, String productId, String taxonomy, String path,
                   DatState state, String sameAs) {}

    /** {@code kind}: {@code trade} for a new trade, {@code event} for a lifecycle event or a step-in, as the report. */
    static final List<String> DAT_COLUMNS = List.of("case", "kind", "composition", "productId", "taxonomy", "datFile",
            "fileName", "state", "sameAs");

    /**
     * {@code seed-dats}: every seed case of the features (commented-out rows too), the dat file it books and whether
     * that file is still to be exported; writes {@code target/seed/seed-dats.csv}, which the Cortex export script
     * reads ({@code docs/sit-seed.md}).
     */
    static int dats() throws IOException {
        Path root = ProjectPaths.projectRoot();
        List<CaseDat> dats = caseDats(root, SeedData.catalog());
        StringBuilder out = new StringBuilder(String.join(",", DAT_COLUMNS)).append('\n');
        Map<DatState, Integer> counts = new TreeMap<>();
        for (CaseDat d : dats) {
            String file = d.path().substring(d.path().lastIndexOf('/') + 1);
            out.append(String.join(",", List.of(d.caseId(), d.kind(), d.composition(), d.productId(),
                    d.taxonomy() == null ? "" : d.taxonomy(), d.path(), file, d.state().name(),
                    d.sameAs() == null ? "" : d.sameAs()).stream().map(SeedReport::cell).toList())).append('\n');
            counts.merge(d.state(), 1, Integer::sum);
        }
        Path csv = SeedData.manifestDir().resolve("seed-dats.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, out.toString(), StandardCharsets.UTF_8);
        System.out.println(dats.size() + " seed cases: " + counts);
        for (CaseDat d : dats) {
            if (d.state() == DatState.placeholder || d.state() == DatState.missing) {
                System.out.println("  to export " + d.caseId() + " -> " + d.path());
            }
        }
        System.out.println("Wrote " + root.relativize(csv));
        return 0;
    }

    /** The dat file of every seed case of the features under {@code root}, in case order. */
    static List<CaseDat> caseDats(Path root, SeedData catalog) throws IOException {
        java.util.regex.Pattern row = java.util.regex.Pattern.compile("^\\s*(?:#\\s*)?\\|(.*)\\|\\s*$");
        Map<String, List<String>> cases = new TreeMap<>();
        try (var files = Files.list(root.resolve(SEED_FEATURES))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".feature")).sorted().toList()) {
                List<String> header = null;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    var m = row.matcher(line);
                    if (!m.matches()) {
                        if (line.trim().startsWith("Scenario")) header = null;
                        continue;
                    }
                    List<String> cells = java.util.Arrays.stream(m.group(1).split("\\|")).map(String::trim).toList();
                    if (header == null) {
                        header = cells;
                    } else if (header.contains("case")) {
                        List<String> h = header;
                        cases.put(cells.get(h.indexOf("case")), List.of(cells.get(h.indexOf("composition")),
                                cells.get(h.indexOf("product_id")),
                                h.contains("taxonomy") ? cells.get(h.indexOf("taxonomy")) : "",
                                h.contains("event") ? cells.get(h.indexOf("event")) : "",
                                h.contains("variant") ? cells.get(h.indexOf("variant")) : ""));
                    }
                }
            }
        }
        List<CaseDat> out = new ArrayList<>();
        Map<String, String> firstCase = new LinkedHashMap<>();
        for (var e : cases.entrySet()) {
            List<String> c = e.getValue();
            String taxonomy = c.get(2).isEmpty() ? null : c.get(2);
            SeedData.Trade trade = !c.get(3).isEmpty() ? catalog.forEvent(e.getKey(), c.get(3), c.get(0), c.get(1))
                    : !c.get(4).isEmpty() ? catalog.forVariant(e.getKey(), c.get(4), c.get(0), c.get(1))
                    : catalog.forCase(e.getKey(), c.get(0), c.get(1), taxonomy);
            String path = RESOURCES + trade.dat();
            String sameAs = firstCase.putIfAbsent(path, e.getKey());
            DatState state;
            if (trade.registered() && trade.dat().equals(
                    com.example.module.oreo.testdata.ProductDatFiles.valueOf(c.get(0)).path())) {
                state = DatState.e2e;
            } else if (sameAs != null) {
                state = DatState.shared;
            } else {
                Path file = root.resolve(path);
                state = !Files.isRegularFile(file) ? DatState.missing
                        : Files.readString(file, StandardCharsets.UTF_8).startsWith(PLACEHOLDER) ? DatState.placeholder
                        : DatState.ok;
            }
            String kind = c.get(3).isEmpty() && c.get(4).isEmpty() ? "trade" : "event";
            out.add(new CaseDat(e.getKey(), kind, c.get(0), c.get(1), taxonomy, path, state, sameAs));
        }
        // new trades first, then the events and step-ins together, each by case ID
        out.sort(java.util.Comparator.comparing((CaseDat d) -> !"trade".equals(d.kind())).thenComparing(CaseDat::caseId));
        return out;
    }

    private static String cell(String value) {
        if (value.isEmpty() || value.chars().noneMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r')) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
