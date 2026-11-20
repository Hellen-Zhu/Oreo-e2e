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
 * {@code seed-report [<run ID>|all]}: the summary of the SIT seed for UAT (docs/sit-seed.md). Reads the manifest of
 * the latest run in {@code target/seed} (or of the given run, or every run), keeps the latest line of each booking
 * (environment, case, composition, product ID, taxonomy, variant, event) and writes
 * {@code target/seed/seed-report-<run ID or all>.csv}, one row per booking with its Itradeable Type (from the catalog's
 * {@code itradeableTypes} when the line has none), portfolio and entity; prints the counts, by kind and by Itradeable
 * Type, and the bookings that failed. Checks the catalog first. Never fails on a failed booking: the report lists it.
 */
final class SeedReport {

    static final List<String> COLUMNS = List.of("run", "env", "kind", "case", "itradeableType", "composition",
            "productId", "taxonomy", "portfolioId", "scbEntity", "variant", "event", "setup", "tradeId", "childTradeIds", "status", "at", "error");

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

    private static String cell(String value) {
        if (value.isEmpty() || value.chars().noneMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r')) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
