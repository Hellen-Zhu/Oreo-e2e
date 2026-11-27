package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.JsonLayers;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SeedData;
import com.example.module.oreo.utils.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@code seed-report [<run ID>|<run ID>,<run ID>...|<run ID>..|all]}: the summary of the SIT seed for UAT
 * (docs/sit-seed.md). Reads the manifest of the latest run in {@code target/seed} (or of the given runs merged, the
 * runs from one on, or every run, {@link #runs}), keeps the latest line of each booking
 * (environment, case, composition, product ID, taxonomy, variant, event) and writes
 * {@code target/seed/seed-report-<run ID or all>.csv}, one row per booking with its Itradeable Type (from the catalog's
 * {@code itradeableTypes} when the line has none), portfolio, risk portfolio and entity; prints the counts, by kind and by Itradeable
 * Type, and the bookings that failed, a booking whose latest line is {@code submitted} among them. Checks the catalog
 * first. Never fails on a failed booking: the report lists it.
 */
final class SeedReport {

    static final List<String> COLUMNS = List.of("run", "tradeId", "env", "kind", "case", "itradeableType",
            "composition", "productId", "taxonomy", "portfolioId", "riskPortfolio", "scbEntity", "variant", "event",
            "setup", "dat", "childTradeIds", "status", "at", "error");

    /** The error of a booking whose latest line is {@code submitted}: {@link #unfinishedAsFailed}. */
    static final String UNFINISHED = "submitted, then no result: the run stopped before this booking ended;"
            + " look the trade up, it may still be pending";

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
        List<JsonNode> latest = withItradeableTypes(unfinishedAsFailed(latest(asEvents(entries))),
                catalog.itradeableTypes());
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

    /**
     * {@code entries} with each booking whose latest line is {@code submitted} reported {@code failed}: its trade was
     * submitted and the run wrote no result after it (killed, timed out), so the trade may still be pending. Its trade
     * ID stays.
     */
    static List<JsonNode> unfinishedAsFailed(List<JsonNode> entries) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode entry : entries) {
            boolean unfinished = entry.isObject() && "submitted".equals(entry.path("status").asText());
            out.add(unfinished ? ((ObjectNode) entry.deepCopy()).put("status", "failed").put("error", UNFINISHED)
                    : entry);
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
            if (sameAs != null) {
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

    // ── seed-legacy: each case's values from its Sample Trade in the old system ─────────────────────────────────────

    /** The rules: a path of a case's request, where its value is in the old system's trade and how it is converted. */
    static final String LEGACY_RULES = RESOURCES + "seed/legacy.json";
    /** Where {@code scripts/legacy_trade_fetch.py} writes by default. */
    static final Path LEGACY_DIR = Path.of(System.getProperty("user.home"), "Downloads", "seed-legacy");
    static final List<String> LEGACY_COLUMNS = List.of("case", "tradeId", "packageId", "path", "from", "legacyValue",
            "value", "catalogValue", "status", "note");
    private static final java.util.regex.Pattern REQUEST_PATH =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*");

    /**
     * A rule of {@link #LEGACY_RULES}: the request path it sets, where the value is in the trade ({@code from}: a
     * field, dots between names and a number for an element of a list, or {@code tag:<name>}, the value of the
     * trade's tag of that name) and how it is converted ({@code as}: {@code null} as it is, {@code number},
     * {@code boolean}, {@code date:<pattern>}).
     */
    record LegacyRule(String path, String from, String as) {}

    /**
     * What one case's trade gives: the fields over its request, a row of {@link #LEGACY_COLUMNS} per rule, and why
     * the case is left out ({@code null} when it is not: then the rows say what each rule set).
     */
    record LegacyCase(String caseId, ObjectNode request, List<List<String>> rows, String refused) {}

    /**
     * {@code seed-legacy [<dir>]}: the values each seed case takes from its Sample Trade, the old system's trade that
     * {@code scripts/legacy_trade_fetch.py} wrote to {@code dir} ({@link #LEGACY_DIR} by default), by the rules of
     * {@link #LEGACY_RULES}. Writes {@code target/seed/seed-legacy-cases.json}, the catalog's {@code cases} with
     * those values set, and {@code target/seed/seed-legacy.csv}, a row per case and rule. A file is a trade's
     * ({@code <trade ID>.json}); one with no {@code case} is left out. Never fails on a case: the CSV says why one is
     * left out.
     */
    static int legacy(String dir) throws IOException {
        Path root = ProjectPaths.projectRoot();
        Path in = dir == null ? LEGACY_DIR : Path.of(dir);
        if (!Files.isDirectory(in)) {
            throw new IllegalStateException("[Seed] no directory " + in
                    + ": fetch the Sample Trades with scripts/legacy_trade_fetch.py first (docs/sit-seed.md)");
        }
        List<LegacyRule> rules = legacyRules(Json.parse(Files.readString(root.resolve(LEGACY_RULES),
                StandardCharsets.UTF_8)));
        SeedData catalog = SeedData.catalog();
        Map<String, String> types = new TreeMap<>();
        for (CaseDat d : caseDats(root, catalog)) types.put(d.caseId(), catalog.itradeableTypes().get(d.composition()));
        JsonNode catalogCases = Json.parse(Files.readString(root.resolve(RESOURCES + SeedData.CATALOG),
                StandardCharsets.UTF_8)).path("cases");
        ObjectNode cases = catalogCases.isObject() ? ((ObjectNode) catalogCases).deepCopy()
                : Json.MAPPER.createObjectNode();
        List<Path> files;
        try (var list = Files.list(in)) {
            files = list.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        List<List<String>> rows = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        List<String> refused = new ArrayList<>();
        Set<String> fetched = new TreeSet<>();
        List<String> noCase = new ArrayList<>();
        for (Path file : files) {
            JsonNode one = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            String caseId = one.path("case").asText("");
            if (caseId.isEmpty()) {                             // a trade fetched with no case: no case takes it
                noCase.add(file.getFileName().toString().replaceAll("\\.json$", ""));
                continue;
            }
            if (!fetched.add(caseId)) {
                refused.add(caseId + ": the case of another file too, " + file.getFileName() + " left out");
                continue;
            }
            LegacyCase c = types.containsKey(caseId)
                    ? legacyCase(one, rules, types.get(caseId), cases.path(caseId).path("request"))
                    : refusedCase(caseId, one, "not a seed case of " + SEED_FEATURES);
            rows.addAll(c.rows());
            c.rows().forEach(r -> counts.merge(r.get(8), 1, Integer::sum));
            if (c.refused() != null) {
                refused.add(caseId + ": " + c.refused());
                continue;
            }
            JsonNode entry = cases.path(caseId);
            ObjectNode e = entry.isObject() ? (ObjectNode) entry : cases.putObject(caseId);
            JsonNode request = e.path("request");
            e.set("request", JsonLayers.merge(request.isObject() ? (ObjectNode) request : Json.MAPPER.createObjectNode(),
                    c.request()));
        }
        Path out = SeedData.manifestDir();
        Files.createDirectories(out);
        Path csv = out.resolve("seed-legacy.csv");
        StringBuilder table = new StringBuilder(String.join(",", LEGACY_COLUMNS)).append('\n');
        rows.forEach(r -> table.append(String.join(",", r.stream().map(SeedReport::cell).toList())).append('\n'));
        Files.writeString(csv, table.toString(), StandardCharsets.UTF_8);
        Path json = out.resolve("seed-legacy-cases.json");
        Files.writeString(json, casesBlock(cases), StandardCharsets.UTF_8);

        System.out.println(files.size() + " fetched trade(s) in " + in + ", " + rules.size() + " rule(s) of "
                + LEGACY_RULES + ": " + counts);
        if (!noCase.isEmpty()) {
            System.out.println("  " + noCase.size() + " with no case, left out: " + String.join(" ", noCase));
        }
        refused.forEach(r -> System.out.println("  refused " + r));
        List<String> notFetched = types.keySet().stream().filter(c -> !fetched.contains(c)).toList();
        if (!notFetched.isEmpty()) {
            System.out.println("  " + notFetched.size() + " seed case(s) not fetched: " + String.join(" ", notFetched));
        }
        System.out.println("Wrote " + root.relativize(csv) + " and " + root.relativize(json)
                + ": on the company copy, replace the cases of " + SeedData.CATALOG + " with the latter");
        return 0;
    }

    /** The rules of {@link #LEGACY_RULES} ({@code root}), in their order. */
    static List<LegacyRule> legacyRules(JsonNode root) {
        JsonNode request = root.path("request");
        if (!request.isObject()) {
            throw new IllegalStateException("[Seed] " + LEGACY_RULES + ": {\"request\": {\"<request path>\":"
                    + " \"<field of the trade>\" | {\"from\": \"<field or tag:<name>>\", \"as\": \"<conversion>\"}}}");
        }
        List<LegacyRule> rules = new ArrayList<>();
        request.properties().forEach(e -> {
            if (e.getKey().equals(JsonLayers.COMMENT)) return;
            String at = "[Seed] " + LEGACY_RULES + " request." + e.getKey();
            if (!REQUEST_PATH.matcher(e.getKey()).matches()) {
                throw new IllegalStateException(at + ": a request path is its field names joined by dots"
                        + " (basic.portfolioId)");
            }
            JsonNode rule = e.getValue();
            String from = rule.isTextual() ? rule.asText() : rule.path("from").asText("");
            String as = rule.isObject() && rule.hasNonNull("as") ? rule.get("as").asText() : null;
            if (from.isBlank() || !(rule.isTextual() || rule.isObject())) {
                throw new IllegalStateException(at + ": \"<field of the trade>\" or {\"from\": ..., \"as\": ...}");
            }
            if (as != null && !as.equals("number") && !as.equals("boolean") && !as.startsWith("date:")) {
                throw new IllegalStateException(at + ": as is number, boolean or date:<pattern>, not " + as);
            }
            if (as != null && as.startsWith("date:")) {
                try {
                    DateTimeFormatter.ofPattern(as.substring(5), Locale.ROOT);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException(at + ": " + as + " is not a date pattern: " + ex.getMessage(), ex);
                }
            }
            rules.add(new LegacyRule(e.getKey(), from, as));
        });
        return rules;
    }

    /**
     * The values of one fetched case ({@code fetched}, a file of {@code legacy_trade_fetch.py}) by {@code rules},
     * when its trade is of the case's Itradeable Type {@code itradeableType}; {@code catalogRequest} is the case's
     * request in the catalog (missing for none), each rule's row compares with it.
     */
    static LegacyCase legacyCase(JsonNode fetched, List<LegacyRule> rules, String itradeableType,
                                 JsonNode catalogRequest) {
        String caseId = fetched.path("case").asText();
        JsonNode trade;
        try {
            trade = sourceTrade(fetched);
        } catch (IllegalArgumentException e) {
            return refusedCase(caseId, fetched, e.getMessage());
        }
        String type = trade.path("tradeType").asText("");
        if (!type.equals(itradeableType)) {
            return refusedCase(caseId, fetched, "the trade is a " + (type.isEmpty() ? "trade of no tradeType" : type)
                    + ", the case books a " + itradeableType
                    + ": the Excel's Sample Trade, or the catalog's Itradeable Type, is not right");
        }
        ObjectNode request = Json.MAPPER.createObjectNode();
        List<List<String>> rows = new ArrayList<>();
        for (LegacyRule rule : rules) {
            JsonNode before = at(catalogRequest, rule.path());
            JsonNode raw = MissingNode.getInstance();
            JsonNode value = null;
            String status;
            String note = "";
            try {
                raw = legacyValue(trade, rule.from());
                if (raw.isMissingNode() || raw.isNull() || (raw.isTextual() && raw.asText().isBlank())) {
                    status = "missing";
                } else {
                    value = convert(raw, rule.as());
                    status = before.isMissingNode() ? "set" : same(before, value) ? "same" : "changed";
                    set(request, rule.path(), value);
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                status = "invalid";
                note = e.getMessage();
            }
            rows.add(List.of(caseId, fetched.path("tradeId").asText(""), fetched.path("packageId").asText(""),
                    rule.path(), rule.from() + (rule.as() == null ? "" : " as " + rule.as()), text(raw), text(value),
                    text(before), status, note));
        }
        return new LegacyCase(caseId, request, rows, null);
    }

    private static LegacyCase refusedCase(String caseId, JsonNode fetched, String why) {
        List<String> row = List.of(caseId, fetched.path("tradeId").asText(""), fetched.path("packageId").asText(""),
                "", "", "", "", "", "refused", why);
        return new LegacyCase(caseId, Json.MAPPER.createObjectNode(), List.of(row), why);
    }

    /**
     * The trade a fetched case takes its values from: the package's own trade when the fetch has one (a
     * back-to-back trade, its {@code packageId} not its trade ID), else the Sample Trade itself.
     *
     * @throws IllegalArgumentException when the response has not exactly one trade of that ID
     */
    static JsonNode sourceTrade(JsonNode fetched) {
        String packageId = fetched.path("packageId").asText("");
        return packageId.isEmpty() ? tradeOf(fetched.path("trade"), fetched.path("tradeId").asText(""))
                : tradeOf(fetched.path("package"), packageId);
    }

    private static JsonNode tradeOf(JsonNode response, String tradeId) {
        List<JsonNode> trades = new ArrayList<>();
        if (response.isArray()) response.forEach(trades::add);
        else if (response.isObject()) trades.add(response);
        List<JsonNode> found = trades.stream().filter(t -> tradeId.equals(t.path("tradeId").asText())).toList();
        if (found.size() != 1) {
            throw new IllegalArgumentException(found.size() + " trades " + tradeId + " in the response of "
                    + trades.size() + " trade(s)");
        }
        return found.getFirst();
    }

    /**
     * The value of {@code from} in {@code trade}, missing for none: {@code tag:<name>} the value of the tag of that
     * name, else a field (dots between names, a number for an element of a list).
     *
     * @throws IllegalArgumentException when the trade has two tags of that name with different values
     */
    static JsonNode legacyValue(JsonNode trade, String from) {
        if (from.startsWith("tag:")) {
            String name = from.substring(4);
            List<JsonNode> values = new ArrayList<>();
            trade.path("tags").forEach(tag -> {
                if (name.equals(tag.path("name").asText())) values.add(tag.path("value"));
            });
            if (values.stream().distinct().count() > 1) {
                throw new IllegalArgumentException(values.size() + " tags " + name + " with different values");
            }
            return values.isEmpty() ? MissingNode.getInstance() : values.getFirst();
        }
        JsonNode node = trade;
        for (String part : from.split("\\.")) {
            node = node.isArray() && part.matches("[0-9]+") ? node.path(Integer.parseInt(part)) : node.path(part);
        }
        return node;
    }

    /**
     * {@code value} converted {@code as}: {@code null} as it is; {@code number} a number, its trailing zeros dropped
     * ({@code "2250000.0"} is {@code 2250000}); {@code boolean} from true/false, Y/N or yes/no; {@code date:<pattern>}
     * the date read with the pattern, as {@code yyyy-MM-dd}.
     *
     * @throws IllegalArgumentException or {@link DateTimeException} when it cannot be converted
     */
    static JsonNode convert(JsonNode value, String as) {
        if (as == null) return value;
        String text = value.asText().trim();
        if (as.equals("number")) {
            BigDecimal n;
            try {
                n = (value.isNumber() ? value.decimalValue() : new BigDecimal(text)).stripTrailingZeros();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not a number: " + text);
            }
            // read back as JSON: the node a data file would have for that number (2250000 an int)
            return Json.parse(n.toPlainString());
        }
        if (as.equals("boolean")) {
            if (value.isBoolean()) return value;
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "true", "y", "yes" -> BooleanNode.TRUE;
                case "false", "n", "no" -> BooleanNode.FALSE;
                default -> throw new IllegalArgumentException("not a boolean: " + text);
            };
        }
        DateTimeFormatter pattern =
                DateTimeFormatter.ofPattern(as.substring("date:".length()), Locale.ROOT);
        return TextNode.valueOf(LocalDate.parse(text, pattern).toString());
    }

    /** Equal values: numbers by their value ({@code 10000} and {@code 10000.0}), anything else as JSON. */
    private static boolean same(JsonNode a, JsonNode b) {
        return a.isNumber() && b.isNumber() ? a.decimalValue().compareTo(b.decimalValue()) == 0 : a.equals(b);
    }

    /** The node at {@code path} (dots between names) under {@code node}, missing for none. */
    private static JsonNode at(JsonNode node, String path) {
        for (String part : path.split("\\.")) node = node.path(part);
        return node;
    }

    /** Sets {@code path} (dots between names) under {@code target} to {@code value}, making the objects on the way. */
    private static void set(ObjectNode target, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode node = target;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode next = node.path(parts[i]);
            node = next.isObject() ? (ObjectNode) next : node.putObject(parts[i]);
        }
        node.set(parts[parts.length - 1], value);
    }

    /** A value for the CSV: text as it is, anything else as JSON, nothing for none. */
    private static String text(JsonNode value) {
        if (value == null || value.isMissingNode()) return "";
        return value.isTextual() ? value.textValue() : Json.compact(value);
    }

    /** {@code {"cases": ...}} with a case per line, as the catalog has them. */
    static String casesBlock(ObjectNode cases) {
        List<String> entries = new ArrayList<>();
        cases.properties().forEach(e -> entries.add("    " + Json.compact(
                TextNode.valueOf(e.getKey())) + ": " + Json.compact(e.getValue())));
        return "{\n  \"cases\": {\n" + String.join(",\n", entries) + "\n  }\n}\n";
    }

    private static String cell(String value) {
        if (value.isEmpty() || value.chars().noneMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r')) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
