package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.testdata.TestDataRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The offline checks of the test data against the feature files and the code (section 10 of the test data design).
 * Every check runs; each prints {@code [ok]} or {@code [FAIL]} with its problems, and its warnings.
 */
final class Checks {

    /** Setup files the code names but that do not exist yet: not covered for now (user, 2026-10-16). */
    private static final Set<String> KNOWN_MISSING = Set.of("trade-cancellation", "trade-amendment");

    private static final String INDENT = "         ";

    private final Path project;
    private final Path data;
    private final List<FeatureCases.FeatureCase> featureCases;
    private final TestDataRepository repo = new TestDataRepository("data/");

    /** What one check found: problems fail it, warnings and notes are printed only. */
    static final class Result {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
    }

    private interface Check {
        void run(Result result) throws IOException;
    }

    private Checks(Path project, List<FeatureCases.FeatureCase> featureCases) {
        this.project = project;
        this.data = project.resolve("src/test/resources/data");
        this.featureCases = featureCases;
    }

    static int run() throws IOException {
        Path project = ProjectPaths.projectRoot();
        TestDataTool.requireFreshData(project);
        List<FeatureCases.FeatureCase> cases = FeatureCases.load(project.resolve("src/test/resources/features"));
        if (cases.isEmpty()) {
            throw new IllegalStateException("no [TC-...] scenarios found in src/test/resources/features");
        }
        Checks c = new Checks(project, cases);
        ExpectedFileChecks e = new ExpectedFileChecks(project, cases);
        int problems = 0;
        problems += c.check("data files are well formed", null, c::dataFilesAreWellFormed);
        problems += c.check("every feature case resolves", null, c::everyFeatureCaseResolves);
        problems += c.check("every row and event file has a feature", null, c::everyRowAndEventFileHasAFeature);
        problems += c.check("every API case has its expected file", null, e::everyApiCaseHasItsExpectedFile);
        problems += c.check("the rules agree with the expected files", "placeholders from the converted old data,"
                + " weaker than a recording: give each a rule, or record again", e::rulesAgreeWithExpectedFiles);
        problems += c.check("the expected files are compacted", "not compacted: TestDataTool compact all",
                e::expectedFilesAreCompacted);
        problems += c.check("every setup name used exists", null, c::everyUsedSetupNameExists);
        problems += c.check("the product Outlines' Examples match the registry", null,
                c::productExamplesMatchTheRegistry);
        problems += c.check("every product with a capability has its dat file", null,
                c::everyProductWithACapabilityHasItsDatFile);
        problems += c.check("the recorded trades agree with the composition registry", "recorded before composition"
                + " IDs: record again", c::recordedTradesAgreeWithTheRegistry);
        problems += c.check("no API snippet only renames a shared outcome", null,
                c::noApiSnippetOnlyRenamesASharedOutcome);
        System.out.println(problems == 0 ? "No problem." : problems + " problem(s).");
        return problems;
    }

    /** Runs one check and prints what it found; an exception is one problem of that check. */
    private int check(String name, String warningHint, Check check) {
        Result result = new Result();
        try {
            check.run(result);
        } catch (IOException | RuntimeException e) {
            result.errors.add(e.getMessage() != null ? e.getMessage() : e.toString());
        }
        StringBuilder head = new StringBuilder(result.errors.isEmpty() ? "[ok]   " : "[FAIL] ").append(name);
        if (!result.errors.isEmpty()) head.append(": ").append(result.errors.size()).append(" problem(s)");
        if (!result.warnings.isEmpty()) {
            head.append(result.errors.isEmpty() ? ": " : ", ").append(result.warnings.size()).append(" warning(s)");
            if (warningHint != null) head.append(" (").append(warningHint).append(')');
        }
        System.out.println(head);
        result.notes.forEach(n -> System.out.println(INDENT + n));
        result.errors.forEach(e -> System.out.println(INDENT + e));
        result.warnings.forEach(w -> System.out.println(INDENT + "[warn] " + w));
        return result.errors.size();
    }

    // ── 10.1 ─────────────────────────────────────────────────────────────────

    private void dataFilesAreWellFormed(Result result) throws IOException {
        for (String channelDir : List.of("api", "ui")) {
            for (Path file : jsonFiles(data.resolve(channelDir))) {
                String name = baseName(file);
                try {
                    if (name.equals("common")) {
                        repo.validate(channelDir, null);
                    } else {
                        repo.validate(channelDir, name);
                        checkDatFiles(file, result.errors);
                    }
                } catch (RuntimeException e) {
                    result.errors.add(e.getMessage());
                }
            }
        }
        for (Path file : jsonFiles(data.resolve("setup"))) {
            try {
                repo.setup(baseName(file));
            } catch (RuntimeException e) {
                result.errors.add(e.getMessage());
            }
        }
    }

    /** Every {@code byProduct} key (event file and rows) has its dat file on the classpath. */
    private static void checkDatFiles(Path file, List<String> errors) throws IOException {
        JsonNode event = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        Set<String> products = new TreeSet<>();
        event.path("byProduct").fieldNames().forEachRemaining(products::add);
        event.path("cases").forEach(row -> row.path("byProduct").fieldNames().forEachRemaining(products::add));
        for (String product : products) {
            String datPath = ProductDatFiles.valueOf(product).path();
            if (Thread.currentThread().getContextClassLoader().getResource(datPath) == null) {
                errors.add(file.getFileName() + ": byProduct " + product + " has no dat file " + datPath);
            }
        }
    }

    // ── 10.2 ─────────────────────────────────────────────────────────────────

    private void everyFeatureCaseResolves(Result result) {
        for (FeatureCases.FeatureCase c : featureCases) {
            try {
                CaseId id = CaseId.parse(c.caseId());
                if (!id.eventFile().equals(c.featureName())) {
                    result.errors.add(c.caseId() + ": event file " + id.eventFile() + ".json but the feature file is "
                            + c.featureName() + ".feature");
                }
                if (!id.channelDir().equals(c.channelDir())) {
                    result.errors.add(c.caseId() + ": channel " + id.channel() + " but the feature is under features/"
                            + c.channelDir());
                }
                TestData.forCase(c.caseId());
            } catch (RuntimeException e) {
                result.errors.add(c.caseId() + ": " + e.getMessage());
            }
        }
    }

    // ── 10.3 ─────────────────────────────────────────────────────────────────

    private void everyRowAndEventFileHasAFeature(Result result) throws IOException {
        for (String channelDir : List.of("api", "ui")) {
            for (Path file : jsonFiles(data.resolve(channelDir))) {
                String event = baseName(file);
                if (event.equals("common")) continue;
                List<FeatureCases.FeatureCase> ofFile = featureCases.stream()
                        .filter(c -> c.channelDir().equals(channelDir) && c.featureName().equals(event))
                        .toList();
                if (ofFile.isEmpty()) {
                    result.errors.add("data/" + channelDir + "/" + event + ".json has no feature file " + event
                            + ".feature under features/" + channelDir);
                    continue;
                }
                JsonNode json = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
                for (JsonNode row : json.path("cases")) {
                    String rowId = row.path("id").asText();
                    boolean used = ofFile.stream().anyMatch(c -> CaseId.parse(c.caseId()).matchesRow(rowId));
                    if (!used) {
                        result.errors.add("data/" + channelDir + "/" + event + ".json: row " + rowId
                                + " matches no scenario of " + event + ".feature");
                    }
                }
            }
        }
    }

    // ── 10.6 ─────────────────────────────────────────────────────────────────

    private static final Pattern SETUP_CALL = Pattern.compile("TestData\\.setup\\(\"([^\"]+)\"\\)");
    /**
     * The provisioning glue passes the setup name as a string argument to its provision... helpers and to
     * TradeApiClient's provision... and triggerEvent methods.
     */
    private static final Pattern PROVISIONING_CALL = Pattern.compile("\\b(?:provision\\w*|triggerEvent)\\(([^;{]*)\\);");
    private static final Pattern NAME_LITERAL = Pattern.compile("\"([a-z0-9][a-z0-9-]*)\"");
    private static final Pattern STEP_TEXT = Pattern.compile("from test data '([^']+)'");

    private void everyUsedSetupNameExists(Result result) throws IOException {
        Set<String> used = new TreeSet<>();
        for (Path file : files(project.resolve("src/main/java"), ".java")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (file.toString().contains("testdata")) continue;
            collect(SETUP_CALL, text, used);
            Matcher call = PROVISIONING_CALL.matcher(text);
            while (call.find()) {
                collect(NAME_LITERAL, call.group(1), used);
            }
        }
        for (Path dir : List.of(project.resolve("src/main/resources/snippet"),
                project.resolve("src/test/resources/features"))) {
            for (Path file : files(dir, dir.endsWith("snippet") ? ".snippet" : ".feature")) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    // A snippet's '@Given "^... from test data '([^']*)'$"' line defines the step (its regex is
                    // no name); a feature's '@' line holds tags. Only the other lines use a setup name.
                    if (line.trim().startsWith("@")) continue;
                    collect(STEP_TEXT, line, used);
                }
            }
        }
        used.removeIf(name -> name.startsWith("$"));
        if (used.isEmpty()) {
            result.errors.add("no setup names found in the code");
            return;
        }
        for (String name : used) {
            if (KNOWN_MISSING.contains(name)) continue;
            if (!Files.isRegularFile(data.resolve("setup").resolve(name + ".json"))) {
                result.errors.add("setup data '" + name + "' is used but data/setup/" + name
                        + ".json does not exist");
            }
        }
        result.notes.add("setup names used: " + used + "; known missing: " + new TreeSet<>(KNOWN_MISSING));
    }

    private static void collect(Pattern pattern, String text, Set<String> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) out.add(m.group(1));
    }

    // ── 10.8 ─────────────────────────────────────────────────────────────────

    /** Each product Outline's Examples are the products of its capability tag (ProductExamples). */
    private void productExamplesMatchTheRegistry(Result result) {
        ProductExamples.Plan plan = ProductExamples.plan(project.resolve("src/test/resources/features"),
                ProductExamples::registryProducts);
        result.errors.addAll(plan.errors());
        for (ProductExamples.Entry entry : plan.entries()) {
            if (entry.changed()) {
                result.errors.add(entry.file() + " " + entry.rowId() + ": " + entry.note()
                        + "; run TestDataTool sync-examples");
            }
        }
        result.warnings.addAll(plan.warnings());
    }

    /** A product with a capability is in the Examples, so its cases upload its dat file. */
    private void everyProductWithACapabilityHasItsDatFile(Result result) {
        for (ProductDatFiles p : ProductDatFiles.values()) {
            if (p.capabilities().isEmpty()) continue;
            if (Thread.currentThread().getContextClassLoader().getResource(p.path()) == null) {
                result.errors.add(p + " has capabilities but no dat file " + p.path());
            }
        }
    }

    /**
     * The shared basic and instrument of each composition ({@code api/expected/_shared/{basic,instrument}/<C>.json})
     * are a trade of that composition: {@code compositionId} is {@code C}, and the trade type of {@code basic} and of
     * the instrument ({@code _value._type.ref[0]}) ends with the registered one. A shared file named after no
     * composition fails; a basic without {@code compositionId} (recorded before composition IDs) is a warning.
     */
    private void recordedTradesAgreeWithTheRegistry(Result result) throws IOException {
        Path shared = data.resolve("api/expected/_shared");
        for (String part : List.of("basic", "instrument")) {
            for (Path file : jsonFiles(shared.resolve(part))) {
                String name = baseName(file);
                if (Arrays.stream(ProductDatFiles.values()).noneMatch(c -> c.name().equals(name))) {
                    result.errors.add("_shared/" + part + "/" + file.getFileName() + ": " + name
                            + " is not a composition of ProductDatFiles");
                }
            }
        }
        for (ProductDatFiles composition : ProductDatFiles.values()) {
            Path basicFile = shared.resolve("basic/" + composition.name() + ".json");
            if (!Files.isRegularFile(basicFile)) continue;
            JsonNode basic = Json.parse(Files.readString(basicFile, StandardCharsets.UTF_8));
            String compositionId = basic.path("compositionId").asText("");
            if (compositionId.isBlank()) {
                result.warnings.add("_shared/basic/" + composition.name() + ".json has no compositionId");
                continue;
            }
            composition.mismatches(compositionId, basic.path("tradeType").asText(""))
                    .forEach(p -> result.errors.add("_shared/basic/" + composition.name() + ".json: " + p));
            Path instrumentFile = shared.resolve("instrument/" + composition.name() + ".json");
            if (!Files.isRegularFile(instrumentFile)) continue;
            JsonNode type = Json.parse(Files.readString(instrumentFile, StandardCharsets.UTF_8))
                    .at("/_value/_type/ref/0");
            if (!type.isMissingNode() && !composition.isTradeType(type.asText(""))) {
                result.errors.add("_shared/instrument/" + composition.name() + ".json: _value._type.ref[0] '"
                        + type.asText("") + "' does not end with '" + composition.tradeType() + "'");
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    /** The outcome wording is the shared vocabulary of {@code api/common_api.snippet}, not a domain synonym. */
    private void noApiSnippetOnlyRenamesASharedOutcome(Result result) throws IOException {
        List<UnusedSteps.Definition> snippets = new ArrayList<>();
        for (Path file : files(project.resolve("src/main/resources/snippet/api"), ".snippet")) {
            String name = project.relativize(file).toString().replace('\\', '/');
            snippets.addAll(UnusedSteps.snippets(name, Files.readAllLines(file, StandardCharsets.UTF_8)));
        }
        if (UnusedSteps.sharedOutcomes(snippets).isEmpty()) {
            result.errors.add("no shared outcome found in snippet/api/common_api.snippet");
            return;
        }
        for (UnusedSteps.Definition d : UnusedSteps.renamingWrappers(snippets)) {
            result.errors.add(d.file() + ":" + d.line() + ": '" + d.pattern().pattern() + "' only renames a shared"
                    + " outcome of common_api.snippet: call that outcome in the feature and delete this step");
        }
    }

    private static List<Path> files(Path dir, String extension) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(extension)).sorted().toList();
        }
    }

    private static String baseName(Path file) {
        return file.getFileName().toString().replaceFirst("\\.json$", "");
    }
}
