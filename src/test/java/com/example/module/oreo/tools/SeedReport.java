package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SeedData;
import com.example.module.oreo.utils.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * {@code seed-report [<run ID>|all]}: the summary of the SIT seed for UAT (docs/sit-seed.md). Reads the manifest of
 * the latest run in {@code target/seed} (or of the given run, or every run), keeps the latest line of each booking
 * (environment, case, composition, product ID, taxonomy, variant, event) and writes
 * {@code target/seed/seed-report-<run ID or all>.csv}, one row per booking with its Itradeable Type (from the catalog's
 * {@code itradeableTypes} when the line has none), portfolio, risk portfolio and entity; prints the counts, by kind and by Itradeable
 * Type, and the bookings that failed. Checks the catalog first. Never fails on a failed booking: the report lists it.
 */
final class SeedReport {

    static final List<String> COLUMNS = List.of("run", "tradeId", "env", "kind", "case", "itradeableType",
            "composition", "productId", "taxonomy", "portfolioId", "riskPortfolio", "scbEntity", "variant", "event",
            "setup", "childTradeIds", "status", "at", "error");

    /** The page's template: the report's data goes in place of {@link #DATA_MARK}. */
    static final String TEMPLATE = "src/test/resources/tools/seed-report.html";
    static final String DATA_MARK = "@@SEED_DATA@@";

    private SeedReport() {}

    /** All runs, the latest line of each booking: {@code seed-report all}. */
    static final String ALL = "all";

    /**
     * The report of run {@code which}: {@code null} for the latest run, a run ID ({@code 20261120-153000}), or
     * {@link #ALL}. Writes {@code target/seed/seed-report-<run ID or all>.csv} and the same as a page,
     * {@code seed-report-<run ID or all>.html}.
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
        String name;
        List<JsonNode> entries = new ArrayList<>();
        if (ALL.equals(which)) {
            name = ALL;
            entries.addAll(SeedData.readManifests(dir));
        } else {
            Path file = which == null ? manifests.get(manifests.size() - 1)
                    : dir.resolve("manifest-" + which + ".jsonl");
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("[Seed] no manifest of run " + which + " in "
                        + ProjectPaths.projectRoot().relativize(dir) + ": "
                        + manifests.stream().map(p -> p.getFileName().toString()).toList());
            }
            name = file.getFileName().toString().replaceAll("^manifest-|\\.jsonl$", "");
            entries.addAll(SeedData.readManifest(file));
        }
        System.out.println("Run " + name + " (" + manifests.size() + " run(s) in "
                + ProjectPaths.projectRoot().relativize(dir) + ")");
        List<JsonNode> latest = withItradeableTypes(latest(entries), catalog.itradeableTypes());
        Path csv = dir.resolve("seed-report-" + name + ".csv");
        Files.writeString(csv, csv(latest), StandardCharsets.UTF_8);
        Path templateFile = ProjectPaths.projectRoot().resolve(TEMPLATE);
        if (!Files.isRegularFile(templateFile)) {
            throw new IllegalStateException("[Seed] no page template " + TEMPLATE);
        }
        Path html = dir.resolve("seed-report-" + name + ".html");
        Files.writeString(html, page(Files.readString(templateFile, StandardCharsets.UTF_8),
                model(name, manifests.size(), latest)), StandardCharsets.UTF_8);

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
        System.out.println("Wrote " + ProjectPaths.projectRoot().relativize(csv) + " and "
                + ProjectPaths.projectRoot().relativize(html));
        return 0;
    }

    /** The data of the page: the run, how many runs there are, when, and the bookings. */
    static String model(String run, int runs, List<JsonNode> entries) {
        ObjectNode model = Json.MAPPER.createObjectNode();
        model.put("run", run).put("runs", runs).put("generated", DateUtil.format(DateUtil.now()));
        ArrayNode rows = model.putArray("bookings");
        entries.forEach(rows::add);
        return Json.compact(model);
    }

    /**
     * The page: {@code template} with {@code json} in place of {@link #DATA_MARK}. Every {@code <} of the JSON is
     * escaped, so no value can end the script element that holds it.
     */
    static String page(String template, String json) {
        if (!template.contains(DATA_MARK)) {
            throw new IllegalStateException("[Seed] " + TEMPLATE + " has no " + DATA_MARK);
        }
        return template.replace(DATA_MARK, json.replace("<", "\\u003c"));
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
    record CaseDat(String caseId, String composition, String productId, String taxonomy, String path, DatState state,
                   String sameAs) {}

    static final List<String> DAT_COLUMNS = List.of("case", "composition", "productId", "taxonomy", "datFile",
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
            out.append(String.join(",", List.of(d.caseId(), d.composition(), d.productId(),
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
                                h.contains("event") ? cells.get(h.indexOf("event")) : ""));
                    }
                }
            }
        }
        List<CaseDat> out = new ArrayList<>();
        Map<String, String> firstCase = new LinkedHashMap<>();
        for (var e : cases.entrySet()) {
            List<String> c = e.getValue();
            String taxonomy = c.get(2).isEmpty() ? null : c.get(2);
            SeedData.Trade trade = c.get(3).isEmpty() ? catalog.forCase(e.getKey(), c.get(0), c.get(1), taxonomy)
                    : catalog.forEvent(e.getKey(), c.get(3), c.get(0), c.get(1));
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
            out.add(new CaseDat(e.getKey(), c.get(0), c.get(1), taxonomy, path, state, sameAs));
        }
        return out;
    }

    private static String cell(String value) {
        if (value.isEmpty() || value.chars().noneMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r')) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
