package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SeedData;
import com.fasterxml.jackson.databind.JsonNode;

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
 * {@code seed-report}: the summary of the SIT seed for UAT (docs/sit-seed.md). Reads every manifest in
 * {@code target/seed}, keeps the latest line of each booking (environment, trade, event) and writes
 * {@code target/seed/seed-report.csv}, one row per booking; prints the counts and the bookings that failed. Checks the
 * catalog first. Never fails on a failed booking: the report lists it.
 */
final class SeedReport {

    static final List<String> COLUMNS = List.of("env", "kind", "trade", "composition", "productId", "setup", "event",
            "tradeId", "childTradeIds", "status", "at", "error");

    private SeedReport() {}

    static int run() throws IOException {
        SeedData catalog = SeedData.catalog();
        System.out.println("Catalog " + SeedData.CATALOG + ": " + catalog.trades().size() + " trades, "
                + catalog.events().size() + " events");
        Path dir = SeedData.manifestDir();
        List<JsonNode> latest = latest(SeedData.readManifests(dir));
        if (latest.isEmpty()) {
            System.out.println("No bookings in " + ProjectPaths.projectRoot().relativize(dir)
                    + ": run SeedTest first (docs/sit-seed.md)");
            return 0;
        }
        Path csv = dir.resolve("seed-report.csv");
        Files.writeString(csv, csv(latest), StandardCharsets.UTF_8);

        Map<String, int[]> counts = new TreeMap<>();
        List<JsonNode> failed = new ArrayList<>();
        for (JsonNode entry : latest) {
            boolean booked = "booked".equals(entry.path("status").asText());
            counts.computeIfAbsent(entry.path("kind").asText(), k -> new int[2])[booked ? 0 : 1]++;
            if (!booked) failed.add(entry);
        }
        counts.forEach((kind, n) -> System.out.println(kind + ": " + n[0] + " booked, " + n[1] + " failed"));
        for (JsonNode entry : failed) {
            System.out.println("  failed " + key(entry) + ": " + entry.path("error").asText());
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

    private static String key(JsonNode entry) {
        return entry.path("env").asText("") + "|" + entry.path("kind").asText() + "|" + entry.path("trade").asText()
                + "|" + entry.path("event").asText("");
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
