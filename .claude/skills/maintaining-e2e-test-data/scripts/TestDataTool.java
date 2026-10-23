import com.example.module.oreo.testdata.Capability;
import com.example.module.oreo.testdata.CaseData;
import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.ExpectedCompaction;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.ResponseMasker;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.testdata.TestDataRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The offline tools of the oreo-e2e test data, a Java single-file program. Run it from the oreo-e2e folder after
 * the build line of {@code SKILL.md} (the folder above this one), which compiles the project, copies the data to
 * {@code target/test-classes} and writes the classpath:
 * <pre>
 * check                 the offline checks of the data against the feature files and the code
 * sync-examples         writes the Examples of the product Outlines from ProductDatFiles
 * compact &lt;event&gt;|all   turns the recorded responses of the variants into patches on their base
 * case &lt;case ID&gt;        prints the merged data of one case
 * </pre>
 * Exit code 1 on a problem. The first class of the file runs; the file is compiled apart from the project, so what
 * it uses from the project must be public.
 */
public class TestDataTool {

    private static final String USAGE =
            "usage: TestDataTool check | sync-examples | compact <event>|all | case <case ID>";

    public static void main(String[] args) throws IOException {
        String command = args.length == 0 ? "" : args[0];
        int problems;
        try {
            problems = switch (command) {
                case "check" -> args.length == 1 ? Checks.run() : usage();
                case "sync-examples" -> args.length == 1 ? SyncExamples.run() : usage();
                case "compact" -> args.length == 2 ? Compact.run(args[1].trim()) : usage();
                case "case" -> args.length == 2 ? Lookup.run(args[1].trim()) : usage();
                default -> usage();
            };
        } catch (IllegalStateException | IllegalArgumentException e) {
            // the project's errors say what is wrong and where; anything else keeps its stack trace
            System.out.println(e.getMessage());
            problems = 1;
        }
        System.exit(problems == 0 ? 0 : 1);
    }

    private static int usage() {
        System.out.println(USAGE);
        return 1;
    }

    /**
     * The case data is read from the copy of {@code src/test/resources/data} that {@code mvn test-compile} makes in
     * {@code target/test-classes}: stops when the copy is not the same, so that no answer is about old data. The
     * expected files are left out, they are read from {@code src}.
     */
    static void requireFreshData(Path project) throws IOException {
        Path source = project.resolve("src/test/resources/data");
        Path copy = project.resolve("target/test-classes/data");
        List<String> inSource = dataFiles(source);
        Set<String> names = new TreeSet<>(inSource);
        names.addAll(dataFiles(copy));
        List<String> stale = new ArrayList<>();
        boolean removed = false;
        for (String name : names) {
            Path a = source.resolve(name);
            Path b = copy.resolve(name);
            if (!Files.isRegularFile(a)) removed = true;
            if (!Files.isRegularFile(a) || !Files.isRegularFile(b) || Files.mismatch(a, b) != -1) stale.add(name);
        }
        if (!stale.isEmpty()) {
            throw new IllegalStateException("target/test-classes/data is not the same as src/test/resources/data ("
                    + stale.size() + " file(s), the first " + stale.get(0) + "): run the build line of SKILL.md"
                    + " again" + (removed ? "; a file removed from src stays in target until mvn clean" : ""));
        }
    }

    /** The {@code .json} and {@code .dat} files under {@code dir}, not under {@code api/expected}, with {@code /}. */
    private static List<String> dataFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".json") || name.endsWith(".dat"))
                    .filter(name -> !name.startsWith("api/expected/"))
                    .sorted().toList();
        }
    }
}

/**
 * The offline checks of the test data against the feature files and the code (section 10 of the test data design).
 * Every check runs; each prints {@code [ok]} or {@code [FAIL]} with its problems, and its warnings.
 */
final class Checks {

    /** Setup files the code names but that do not exist yet: not covered for now (user, 2026-10-16). */
    private static final Set<String> KNOWN_MISSING = Set.of("trade-cancellation", "trade-amendment");

    private static final String PLACEHOLDER = "${json-unit.";
    private static final Set<String> TYPE_PLACEHOLDERS =
            Set.of(ResponseMasker.ANY_STRING, ResponseMasker.ANY_NUMBER, ResponseMasker.ANY_BOOLEAN);

    private static final String INDENT = "         ";

    private final Path project;
    private final Path data;
    private final List<FeatureCases.FeatureCase> featureCases;
    private final TestDataRepository repo = new TestDataRepository("data/");

    /** What one check found: problems fail it, warnings and notes are printed only. */
    private static final class Result {
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
        int problems = 0;
        problems += c.check("data files are well formed", null, c::dataFilesAreWellFormed);
        problems += c.check("every feature case resolves", null, c::everyFeatureCaseResolves);
        problems += c.check("every row and event file has a feature", null, c::everyRowAndEventFileHasAFeature);
        problems += c.check("every API case has its expected file", null, c::everyApiCaseHasItsExpectedFile);
        problems += c.check("the rules agree with the expected files", "placeholders from the converted old data,"
                + " weaker than a recording: give each a rule, or record again", c::rulesAgreeWithExpectedFiles);
        problems += c.check("the expected files are compacted", "not compacted: TestDataTool compact all",
                c::expectedFilesAreCompacted);
        problems += c.check("every setup name used exists", null, c::everyUsedSetupNameExists);
        problems += c.check("the product Outlines' Examples match the registry", null,
                c::productExamplesMatchTheRegistry);
        problems += c.check("every product with a capability has its dat file", null,
                c::everyProductWithACapabilityHasItsDatFile);
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

    // ── 10.4 ─────────────────────────────────────────────────────────────────

    private void everyApiCaseHasItsExpectedFile(Result result) throws IOException {
        ExpectedResponses expected = ExpectedResponses.standard();
        Set<Path> wanted = new TreeSet<>();
        for (FeatureCases.FeatureCase c : featureCases) {
            if (!c.channelDir().equals("api")) continue;
            Path file = expected.fileFor(CaseId.parse(c.caseId()));
            wanted.add(file.toAbsolutePath().normalize());
            if (!Files.isRegularFile(file)) {
                result.errors.add(c.caseId() + ": no expected file " + project.relativize(file)
                        + " (run the case to record it, review it, commit it)");
            }
        }
        Path root = data.resolve("api/expected");
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    if (!wanted.contains(file.toAbsolutePath().normalize())) {
                        result.errors.add(project.relativize(file) + " belongs to no API case of the feature files");
                    }
                }
            }
        }
    }

    // ── 10.5 ─────────────────────────────────────────────────────────────────

    private void rulesAgreeWithExpectedFiles(Result result) {
        ExpectedResponses expected = ExpectedResponses.standard();
        for (FeatureCases.FeatureCase c : featureCases) {
            if (!c.channelDir().equals("api")) continue;
            CaseId id = CaseId.parse(c.caseId());
            JsonNode file;
            try {
                ExpectedResponses.Resolved resolved = expected.resolve(id).orElse(null);
                if (resolved == null) continue; // reported by everyApiCaseHasItsExpectedFile
                file = Json.parse(resolved.json());
            } catch (IllegalStateException e) {
                result.errors.add(c.caseId() + ": " + e.getMessage());
                continue;
            }
            Map<String, String> rules = TestData.forCase(c.caseId()).dynamicFields();
            List<List<Object>> ruleSteps = new ArrayList<>();
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                String label = c.caseId() + ": rule " + rule.getKey() + " = " + rule.getValue();
                List<Object> steps;
                try {
                    steps = ResponseMasker.parsePath(rule.getKey(), c.caseId());
                } catch (IllegalStateException e) {
                    result.errors.add(label + ": " + e.getMessage());
                    continue;
                }
                ruleSteps.add(steps);
                List<Hit> hits = new ArrayList<>();
                select(file, steps, 0, "", hits);
                if (hits.isEmpty()) {
                    result.errors.add(label + ": the path is not in the expected file");
                }
                for (Hit hit : hits) {
                    checkHit(label, rule.getValue(), hit, result.errors, result.warnings);
                }
            }
            placeholdersWithoutRule(c.caseId(), file, new ArrayList<>(), ruleSteps, result.warnings);
        }
    }

    // ── 10.7 ─────────────────────────────────────────────────────────────────

    private void expectedFilesAreCompacted(Result result) {
        ExpectedResponses expected = ExpectedResponses.standard();
        for (String event : expected.events()) {
            ExpectedCompaction.Plan plan = ExpectedCompaction.plan(expected, event,
                    id -> TestData.forCase(id.raw()).expectedBase());
            result.errors.addAll(plan.errors());
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                if (entry.changed()) {
                    result.warnings.add(project.relativize(expected.fileFor(entry.id())) + ": " + entry.note());
                }
            }
        }
    }

    /** A value a rule path reaches in an expected file; {@code underPlaceholder}: an ancestor is a placeholder. */
    private record Hit(String path, JsonNode value, boolean underPlaceholder) {}

    private static void select(JsonNode node, List<Object> steps, int i, String at, List<Hit> out) {
        if (i == steps.size()) {
            out.add(new Hit(at, node, false));
            return;
        }
        if (isPlaceholder(node)) {
            out.add(new Hit(at, node, true));
            return;
        }
        Object step = steps.get(i);
        if (step instanceof String key) {
            if (node.isObject() && node.has(key)) {
                select(node.get(key), steps, i + 1, at.isEmpty() ? key : at + "." + key, out);
            }
        } else if (node.isArray()) {
            int from = ResponseMasker.isAny(step) ? 0 : (Integer) step;
            int to = ResponseMasker.isAny(step) ? node.size() : Math.min(from + 1, node.size());
            for (int n = from; n < to; n++) {
                select(node.get(n), steps, i + 1, at + "[" + n + "]", out);
            }
        }
    }

    private static void checkHit(String label, String rule, Hit hit, List<String> errors, List<String> warnings) {
        JsonNode value = hit.value();
        String at = " (at " + hit.path() + ")";
        if (hit.underPlaceholder()) {
            warnings.add(label + ": under the placeholder " + value.asText() + at + "; record again");
            return;
        }
        if (rule.equals("type")) {
            if (value.isNull() || TYPE_PLACEHOLDERS.contains(value.asText(""))) return;
            wrongValue(label + at, value, errors, warnings);
        } else if (rule.equals("ignore")) {
            if (ResponseMasker.IGNORE.equals(value.asText(""))) return;
            wrongValue(label + at, value, errors, warnings);
        } else if (rule.startsWith("regex:")) {
            if ((ResponseMasker.REGEX + rule.substring("regex:".length())).equals(value.asText(""))) return;
            wrongValue(label + at, value, errors, warnings);
        } else if (rule.startsWith("var:")) {
            if ((ResponseMasker.VAR_START + rule.substring("var:".length()) + "}").equals(value.asText(""))) return;
            wrongValue(label + at, value, errors, warnings);
        } else if (rule.equals("type-tree")) {
            if (isPlaceholder(value)) {
                warnings.add(label + at + ": the file has " + value.asText() + " instead of a typed tree");
                return;
            }
            List<String> plain = new ArrayList<>();
            plainLeaves(value, hit.path(), plain);
            if (!plain.isEmpty()) {
                errors.add(label + ": leaves without a placeholder " + plain + "; record again");
            }
        } else {
            errors.add(label + ": unknown rule (type, type-tree, regex:<pattern>, var:<name>, ignore)");
        }
    }

    /** No placeholder at all: an error (the rule was changed without recording); another placeholder: a warning. */
    private static void wrongValue(String label, JsonNode value, List<String> errors, List<String> warnings) {
        if (isPlaceholder(value)) {
            warnings.add(label + ": the file has " + value.asText() + ", not what this rule records");
        } else {
            errors.add(label + ": the file has the plain value " + Json.compact(value) + "; record again");
        }
    }

    private static void plainLeaves(JsonNode node, String at, List<String> out) {
        if (node.isObject()) {
            node.properties().forEach(e -> plainLeaves(e.getValue(), at + "." + e.getKey(), out));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) plainLeaves(node.get(i), at + "[" + i + "]", out);
        } else if (!node.isNull() && !TYPE_PLACEHOLDERS.contains(node.asText(""))) {
            out.add(at);
        }
    }

    /** Warns about every placeholder that no rule reaches (neither at, above nor below it). */
    private static void placeholdersWithoutRule(String caseId, JsonNode node, List<Object> at,
                                                List<List<Object>> rules, List<String> warnings) {
        if (isPlaceholder(node)) {
            boolean covered = rules.stream().anyMatch(r -> prefixMatches(r, at) || prefixMatches(at, r));
            if (!covered) {
                warnings.add(caseId + ": " + node.asText() + " at " + render(at) + " has no rule");
            }
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(e -> {
                at.add(e.getKey());
                placeholdersWithoutRule(caseId, e.getValue(), at, rules, warnings);
                at.remove(at.size() - 1);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                at.add(i);
                placeholdersWithoutRule(caseId, node.get(i), at, rules, warnings);
                at.remove(at.size() - 1);
            }
        }
    }

    /** {@code true} when {@code prefix} (rule steps or a concrete path) matches the start of {@code path}. */
    private static boolean prefixMatches(List<Object> prefix, List<Object> path) {
        if (prefix.size() > path.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            Object a = prefix.get(i);
            Object b = path.get(i);
            boolean index = (a instanceof Integer || ResponseMasker.isAny(a))
                    && (b instanceof Integer || ResponseMasker.isAny(b));
            boolean same = a.equals(b) || (index && (ResponseMasker.isAny(a) || ResponseMasker.isAny(b)));
            if (!same) return false;
        }
        return true;
    }

    private static String render(List<Object> path) {
        StringBuilder out = new StringBuilder();
        for (Object step : path) {
            if (step instanceof String key) {
                out.append(out.length() == 0 ? "" : ".").append(key);
            } else {
                out.append('[').append(step).append(']');
            }
        }
        return out.toString();
    }

    private static boolean isPlaceholder(JsonNode node) {
        return node.isTextual()
                && (node.textValue().startsWith(PLACEHOLDER) || node.textValue().startsWith(ResponseMasker.VAR_START));
    }

    // ── 10.6 ─────────────────────────────────────────────────────────────────

    private static final Pattern SETUP_CALL = Pattern.compile("TestData\\.setup\\(\"([^\"]+)\"\\)");
    /** The provisioning helpers of TradeProvisioningSteps take the setup name as a string argument. */
    private static final Pattern PROVISIONING_CALL = Pattern.compile(
            "(?:submitTradeAsPendingApproval|provisionLiveTradeAs|provisionLifecycleEvent|submitAmendment)"
                    + "\\(([^;{]*)\\);");
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
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

/**
 * Writes the {@code Examples} of every product Scenario Outline from the registry ({@link ProductDatFiles}): one row
 * per product of the Outline's {@code @capability#<name>} tag. Run after adding a product or a capability, then
 * review the diff. Nothing is written when any Outline has a problem.
 */
final class SyncExamples {

    private SyncExamples() {}

    static int run() {
        ProductExamples.Plan plan = ProductExamples.plan(
                ProjectPaths.projectRoot().resolve("src/test/resources/features"), ProductExamples::registryProducts);
        if (!plan.errors().isEmpty()) {
            System.out.println("Nothing written, " + plan.errors().size() + " problem(s):\n  "
                    + String.join("\n  ", plan.errors()));
            return plan.errors().size();
        }
        ProductExamples.write(plan);

        StringBuilder out = new StringBuilder();
        int changed = 0;
        for (ProductExamples.Entry entry : plan.entries()) {
            out.append(entry.file()).append(' ').append(entry.rowId()).append(": ").append(entry.note()).append('\n');
            if (entry.changed()) changed++;
        }
        plan.warnings().forEach(w -> out.append("[warn] ").append(w).append('\n'));
        out.append(changed).append(" Outline(s) rewritten: review git diff, then commit.");
        System.out.println(out);
        return 0;
    }
}

/**
 * Compacts the recorded API responses (section 7.5 of the test data design): a variant's file becomes a patch on the
 * base case its data declares, a case without a base keeps its full response. Run after a recording, before the
 * review, for one event ({@code compact trade_creation}) or {@code all}. Nothing is written when any event has a
 * problem.
 */
final class Compact {

    private Compact() {}

    static int run(String scope) throws IOException {
        TestDataTool.requireFreshData(ProjectPaths.projectRoot());
        ExpectedResponses files = ExpectedResponses.standard();
        List<String> known = files.events();
        List<String> events = scope.equals("all") ? known : List.of(scope);
        if (!known.containsAll(events)) {
            System.out.println("compact " + scope + ": no such folder of expected files; known: " + known + " or all");
            return 1;
        }

        List<ExpectedCompaction.Plan> plans = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String event : events) {
            ExpectedCompaction.Plan plan = ExpectedCompaction.plan(files, event,
                    id -> TestData.forCase(id.raw()).expectedBase());
            plans.add(plan);
            plan.errors().forEach(e -> errors.add(event + ": " + e));
        }
        if (!errors.isEmpty()) {
            System.out.println("Nothing written, " + errors.size() + " problem(s):\n  " + String.join("\n  ", errors));
            return errors.size();
        }

        StringBuilder out = new StringBuilder();
        int changed = 0;
        for (ExpectedCompaction.Plan plan : plans) {
            ExpectedCompaction.write(files, plan);
            out.append(plan.eventFile()).append(":\n");
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                out.append("  ").append(entry.id().raw()).append(".json: ").append(entry.note())
                        .append(entry.changed() ? "" : " (unchanged)").append('\n');
                if (entry.changed()) changed++;
            }
        }
        out.append(changed).append(" file(s) rewritten: review git diff, then commit.");
        System.out.println(out);
        return 0;
    }
}

/** Prints the data of one case without running it: {@code case TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002}. */
final class Lookup {

    private Lookup() {}

    static int run(String caseId) throws IOException {
        TestDataTool.requireFreshData(ProjectPaths.projectRoot());
        CaseId id = CaseId.parse(caseId);
        CaseData data = TestData.forCase(id.raw());

        StringBuilder out = new StringBuilder();
        out.append("Case: ").append(id.raw()).append('\n');
        out.append("Data file: data/").append(id.channelDir()).append('/').append(id.eventFile()).append(".json\n");
        out.append("Layers:\n");
        if (data.layers().isEmpty()) out.append("  (none)\n");
        for (int i = 0; i < data.layers().size(); i++) {
            out.append("  ").append(i + 1).append(". ").append(data.layers().get(i)).append('\n');
        }
        out.append("Dat file: ").append(id.product() != null
                ? ProductDatFiles.valueOf(id.product()).path(data.datFile())
                : data.datFile() != null ? data.datFile() + " (in the folder of the step's product)"
                : "(the default of the step's product)").append('\n');
        out.append("Request:\n").append(Json.pretty(Json.parse(data.request())));
        if (id.channelDir().equals("api")) {
            out.append("dynamicFields:\n");
            if (data.dynamicFields().isEmpty()) out.append("  (none)\n");
            for (Map.Entry<String, String> rule : data.dynamicFields().entrySet()) {
                out.append("  ").append(rule.getKey()).append(" = ").append(rule.getValue()).append('\n');
            }
            out.append("expectedBase: ").append(data.expectedBase() == null ? "(none)" : data.expectedBase())
                    .append('\n');
            ExpectedResponses expected = ExpectedResponses.standard();
            Optional<ExpectedResponses.Resolved> resolved = expected.resolve(id);
            if (resolved.isEmpty()) {
                out.append("Expected file: ").append(expected.fileFor(id))
                        .append(" (not recorded: recorded at the first run)\n");
            } else {
                out.append("Expected file: ").append(resolved.get().describe()).append('\n');
                out.append("Expected response (full form):\n").append(resolved.get().json());
            }
        }
        System.out.println(out);
        return 0;
    }
}

/**
 * The {@code Examples} of the product Outlines, written from the registry: each Scenario Outline whose case ID has
 * {@code <product_type>} carries one {@code @capability#<name>} tag, and its single {@code product_type} table lists
 * the products of that capability ({@link ProductDatFiles#with(Capability)}), in registry order. {@link #plan} reads
 * every feature file under {@code api/} and {@code ui/} and changes nothing; {@link #write} writes the plan only when
 * it has no error. Used by {@code sync-examples} and by the check.
 */
final class ProductExamples {

    private static final String PRODUCT_COLUMN = "product_type";
    private static final String ONLY = ": only an Outline with " + FeatureCases.PRODUCT_PLACEHOLDER + " carries one";

    /**
     * One product Outline.
     *
     * @param file     its feature file, relative to the features root with {@code /}
     * @param before   the products of its table now
     * @param after    the products of its capability
     * @param oldLines its table's lines now
     * @param newLines its table's lines as written from the registry
     */
    record Entry(String file, String rowId, Capability capability, List<String> before, List<String> after,
                 List<String> oldLines, List<String> newLines) {

        boolean changed() {
            return !oldLines.equals(newLines);
        }

        /**
         * {@code unchanged}; {@code add A, B} / {@code remove C} / {@code add A, remove C} (a duplicate row counts
         * as removed); else {@code reordered} or {@code reformatted}.
         */
        String note() {
            if (!changed()) return "unchanged";
            List<String> parts = new ArrayList<>();
            List<String> added = minus(after, before);
            List<String> removed = minus(before, after);
            if (!added.isEmpty()) parts.add("add " + String.join(", ", added));
            if (!removed.isEmpty()) parts.add("remove " + String.join(", ", removed));
            if (!parts.isEmpty()) return String.join(", ", parts);
            return before.equals(after) ? "reformatted" : "reordered";
        }

        /** The values of {@code a} left after taking out one occurrence per value of {@code b}. */
        private static List<String> minus(List<String> a, List<String> b) {
            List<String> rest = new ArrayList<>(b);
            List<String> out = new ArrayList<>();
            for (String value : a) {
                if (!rest.remove(value)) out.add(value);
            }
            return out;
        }
    }

    /**
     * @param rewrites the new text of each file with a changed entry, by its path
     * @param warnings capabilities that no Outline carries
     */
    record Plan(List<Entry> entries, Map<Path, String> rewrites, List<String> errors, List<String> warnings) {}

    private ProductExamples() {}

    /** The names of the products of {@code capability}, in registry order. */
    static List<String> registryProducts(Capability capability) {
        return ProductDatFiles.with(capability).stream().map(Enum::name).toList();
    }

    static Plan plan(Path featuresRoot, Function<Capability, List<String>> productsOf) {
        List<Entry> entries = new ArrayList<>();
        Map<Path, String> rewrites = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Path path : featureFiles(featuresRoot)) {
            FeatureFile feature = FeatureFile.read(path);
            String file = featuresRoot.relativize(path).toString().replace('\\', '/');
            List<FeatureFile.Table> changedTables = new ArrayList<>();
            List<List<String>> changedLines = new ArrayList<>();
            for (FeatureFile.Block block : feature.blocks()) {
                Entry entry = entry(file, feature, block, productsOf, errors);
                if (entry == null) continue;
                entries.add(entry);
                if (entry.changed()) {
                    changedTables.add(block.examples().get(0));
                    changedLines.add(entry.newLines());
                }
            }
            if (changedTables.isEmpty()) continue;
            List<String> lines = new ArrayList<>(feature.lines());
            for (int i = changedTables.size() - 1; i >= 0; i--) {
                FeatureFile.Table table = changedTables.get(i);
                List<String> range = lines.subList(table.headerLine(), table.lastLine() + 1);
                range.clear();
                range.addAll(changedLines.get(i));
            }
            rewrites.put(path, feature.render(lines));
        }
        List<String> warnings = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            if (entries.stream().noneMatch(e -> e.capability() == capability)) {
                warnings.add("capability " + capability.tagName() + ": no Outline tests it");
            }
        }
        return new Plan(List.copyOf(entries), Collections.unmodifiableMap(rewrites), List.copyOf(errors),
                List.copyOf(warnings));
    }

    /** Writes the plan's rewrites as UTF-8; nothing at all when it has an error. */
    static void write(Plan plan) {
        if (!plan.errors().isEmpty()) {
            throw new IllegalStateException("[ProductExamples] nothing written, " + plan.errors().size()
                    + " problem(s):\n  " + String.join("\n  ", plan.errors()));
        }
        for (Map.Entry<Path, String> rewrite : plan.rewrites().entrySet()) {
            try {
                Files.write(rewrite.getKey(), rewrite.getValue().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("[ProductExamples] cannot write " + rewrite.getKey(), e);
            }
        }
    }

    /** The entry of {@code block} when it is a product Outline without problem; its problems go to {@code errors}. */
    private static Entry entry(String file, FeatureFile feature, FeatureFile.Block block,
                               Function<Capability, List<String>> productsOf, List<String> errors) {
        boolean managed = block.kind() == FeatureFile.Kind.OUTLINE && block.rowId() != null
                && block.rowId().contains(FeatureCases.PRODUCT_PLACEHOLDER);
        List<String> tags = capabilityTags(block.tags());
        if (!managed) {
            tags.forEach(tag -> errors.add(file + ": " + tag + " on " + misplaced(block) + ONLY));
        }
        for (FeatureFile.Table table : block.examples()) {
            String of = block.rowId() != null ? block.rowId() : "line " + (block.line() + 1);
            capabilityTags(table.tags()).forEach(tag -> errors.add(file + ": " + tag + " on an Examples block of "
                    + of + ONLY));
        }
        if (!managed) return null;

        String where = file + ": " + block.rowId();
        if (tags.isEmpty()) {
            errors.add(where + " has no " + Capability.TAG_PREFIX + " tag; known: " + Arrays.stream(Capability.values())
                    .map(Capability::tagName).collect(Collectors.joining(", ")));
            return null;
        }
        if (tags.size() > 1) {
            errors.add(where + " has " + tags.size() + " " + Capability.TAG_PREFIX + " tags: "
                    + String.join(", ", tags));
            return null;
        }
        Capability capability;
        try {
            capability = Capability.fromTagName(tags.get(0).substring(Capability.TAG_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            errors.add(where + ": " + e.getMessage());
            return null;
        }
        if (block.examples().size() != 1 || !block.examples().get(0).header().equals(List.of(PRODUCT_COLUMN))) {
            errors.add(where + ": Examples must be one table with the single column " + PRODUCT_COLUMN);
            return null;
        }
        FeatureFile.Table table = block.examples().get(0);
        List<String> oldLines = List.copyOf(feature.lines().subList(table.headerLine(), table.lastLine() + 1));
        if (oldLines.stream().anyMatch(line -> !line.strip().startsWith("|"))) {
            errors.add(where + ": a comment or blank line inside the product table; the registry decides the rows"
                    + " (remove the line, or take the capability off the product in ProductDatFiles)");
            return null;
        }
        List<String> products = productsOf.apply(capability);
        if (products.isEmpty()) {
            errors.add(where + ": no product has capability " + capability.tagName() + " (ProductDatFiles)");
            return null;
        }
        return new Entry(file, block.rowId(), capability, table.column(PRODUCT_COLUMN), List.copyOf(products),
                oldLines, tableLines(table.indent(), products));
    }

    /** The header and one row per product, every cell padded to the widest value. */
    private static List<String> tableLines(String indent, List<String> products) {
        int width = PRODUCT_COLUMN.length();
        for (String product : products) width = Math.max(width, product.length());
        List<String> lines = new ArrayList<>();
        lines.add(row(indent, PRODUCT_COLUMN, width));
        for (String product : products) lines.add(row(indent, product, width));
        return lines;
    }

    private static String row(String indent, String value, int width) {
        return indent + "| " + value + " ".repeat(width - value.length()) + " |";
    }

    private static List<String> capabilityTags(List<String> tags) {
        return tags.stream().filter(t -> t.startsWith(Capability.TAG_PREFIX)).toList();
    }

    /** Where a capability tag that is not on a product Outline sits. */
    private static String misplaced(FeatureFile.Block block) {
        String at = "at line " + (block.line() + 1);
        return switch (block.kind()) {
            case FEATURE -> "the Feature";
            case BACKGROUND -> "the Background " + at;
            case RULE -> "the Rule " + at;
            case SCENARIO -> "the Scenario " + (block.rowId() != null ? block.rowId() : at);
            case OUTLINE -> "the Outline " + (block.rowId() != null ? block.rowId() : at)
                    + " without " + FeatureCases.PRODUCT_PLACEHOLDER;
        };
    }

    private static List<Path> featureFiles(Path featuresRoot) {
        List<Path> files = new ArrayList<>();
        for (String channelDir : List.of("api", "ui")) {
            Path dir = featuresRoot.resolve(channelDir);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                files.addAll(walk.filter(p -> p.getFileName().toString().endsWith(".feature")).sorted().toList());
            } catch (IOException e) {
                throw new UncheckedIOException("[ProductExamples] cannot list " + dir, e);
            }
        }
        return files;
    }
}

/**
 * The case IDs of the feature files: {@code <featuresRoot>/<api|ui>/**}{@code /*.feature}. A scenario's ID is the
 * first {@code [TC-...]} in its name. A Scenario Outline's ID has one placeholder: {@code <product_type>}, expanded
 * over the {@code product_type} column of every {@code Examples} table, or a column for the case number at its end
 * ({@code TC-REFDATA-SYNC-MANAGEMENT-API-<case>}), expanded over every Examples row as Cucumber names the scenarios.
 */
final class FeatureCases {

    static final String PRODUCT_PLACEHOLDER = "<product_type>";

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");
    private static final Pattern NUMBER_PLACEHOLDER = Pattern.compile("-(API|UI)-<([^>]+)>$");

    /**
     * @param caseId      the expanded case ID, e.g. {@code TC-TRADE-CREATION-FX_TRF-API-001}
     * @param rowId       the ID of the case's row in the event file: for a product Outline the ID as written, one row
     *                    for every product ({@code TC-TRADE-CREATION-<product_type>-API-001}), else the case ID
     * @param channelDir  {@code api} or {@code ui}: the folder under the features root
     * @param featureName the file name without {@code .feature}
     * @param title       the scenario name after the ID; in an Outline numbered by a column, with the row's values
     */
    record FeatureCase(String caseId, String rowId, String channelDir, String featureName, String title) {}

    private FeatureCases() {}

    static List<FeatureCase> load(Path featuresRoot) {
        List<FeatureCase> cases = new ArrayList<>();
        for (String channelDir : List.of("api", "ui")) {
            Path dir = featuresRoot.resolve(channelDir);
            if (!Files.isDirectory(dir)) continue;
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(p -> p.getFileName().toString().endsWith(".feature")).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException("[FeatureCases] cannot list " + dir, e);
            }
            for (Path file : files) {
                cases.addAll(cases(FeatureFile.read(file), channelDir));
            }
        }
        return cases;
    }

    static List<FeatureCase> cases(FeatureFile feature, String channelDir) {
        String featureName = feature.file().getFileName().toString().replaceFirst("\\.feature$", "");
        List<FeatureCase> cases = new ArrayList<>();
        for (FeatureFile.Block block : feature.blocks()) {
            boolean outline = block.kind() == FeatureFile.Kind.OUTLINE;
            if (block.rowId() == null || !(outline || block.kind() == FeatureFile.Kind.SCENARIO)) continue;
            String rowId = block.rowId();
            String title = title(feature, block);
            String where = "[FeatureCases] " + feature.file() + ": " + rowId;
            long placeholders = PLACEHOLDER.matcher(rowId).results().count();
            if (placeholders == 0) {
                cases.add(new FeatureCase(rowId, rowId, channelDir, featureName, title));
            } else if (!outline) {
                throw new IllegalStateException(where + " has a placeholder, which only a Scenario Outline fills");
            } else if (rowId.contains(PRODUCT_PLACEHOLDER)) {
                if (placeholders > 1) {
                    throw new IllegalStateException(where + " has a placeholder other than " + PRODUCT_PLACEHOLDER
                            + "; an ID has one placeholder");
                }
                List<String> products = block.examples().stream()
                        .flatMap(t -> t.column("product_type").stream()).toList();
                if (products.isEmpty()) {
                    throw new IllegalStateException(where + " has no Examples column product_type to expand "
                            + PRODUCT_PLACEHOLDER);
                }
                Set<String> ids = new LinkedHashSet<>();
                products.forEach(p -> ids.add(rowId.replace(PRODUCT_PLACEHOLDER, p)));
                ids.forEach(id -> cases.add(new FeatureCase(id, rowId, channelDir, featureName, title)));
            } else {
                cases.addAll(numbered(where, block, title, channelDir, featureName));
            }
        }
        return cases;
    }

    /** An Outline whose ID ends with a column for the case number: one case per Examples row, each its own row. */
    private static List<FeatureCase> numbered(String where, FeatureFile.Block block, String title, String channelDir,
                                              String featureName) {
        String rowId = block.rowId();
        Matcher number = NUMBER_PLACEHOLDER.matcher(rowId);
        if (PLACEHOLDER.matcher(rowId).results().count() > 1 || !number.find()) {
            throw new IllegalStateException(where + " has a placeholder other than " + PRODUCT_PLACEHOLDER
                    + " that is not the case number at the end: write TC-<EVENT>-<API|UI>-<case> with a column case"
                    + " of 001, 002 ..., the other columns in the scenario name and in the case data");
        }
        String column = number.group(2);
        List<FeatureCase> cases = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (FeatureFile.Table table : block.examples()) {
            if (!table.header().contains(column)) {
                throw new IllegalStateException(where + ": an Examples table of it has no column " + column);
            }
            for (List<String> row : table.rows()) {
                String id = fill(rowId, table.header(), row);
                if (!ids.add(id)) {
                    throw new IllegalStateException(where + ": two Examples rows give the case " + id);
                }
                cases.add(new FeatureCase(id, id, channelDir, featureName, fill(title, table.header(), row)));
            }
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException(where + " has no Examples row to expand <" + column + ">");
        }
        return cases;
    }

    /** {@code text} with every {@code <column>} of {@code header} replaced by the row's value, as Cucumber does. */
    private static String fill(String text, List<String> header, List<String> row) {
        String out = text;
        for (int i = 0; i < header.size() && i < row.size(); i++) {
            out = out.replace("<" + header.get(i) + ">", row.get(i));
        }
        return out;
    }

    /** The scenario line's text after {@code [<row ID>]}, trimmed. */
    private static String title(FeatureFile feature, FeatureFile.Block block) {
        String line = feature.lines().get(block.line());
        int at = line.indexOf("[" + block.rowId() + "]");
        return at < 0 ? "" : line.substring(at + block.rowId().length() + 2).trim();
    }
}

/**
 * A feature file read once: its lines, and its blocks (Feature, Background, Rule, Scenario, Scenario Outline) with
 * their tags and {@code Examples} tables. Line numbers are indexes into {@link #lines()}. {@link #render(List)}
 * writes lines back with the byte order mark, line separator and final newline of the file as read.
 */
final class FeatureFile {

    enum Kind { FEATURE, BACKGROUND, RULE, SCENARIO, OUTLINE }

    /**
     * An {@code Examples} table.
     *
     * @param tags       the tags above its {@code Examples:} line
     * @param headerLine the line of its header, -1 when no {@code |} line follows {@code Examples:}
     * @param lastLine   its last {@code |} line, -1 when it has none
     * @param indent     the leading whitespace of its header line
     */
    record Table(List<String> tags, int headerLine, int lastLine, String indent,
                 List<String> header, List<List<String>> rows) {

        /** That column's values, empty when the table has no such column. */
        List<String> column(String name) {
            int index = header.indexOf(name);
            if (index < 0) return List.of();
            List<String> values = new ArrayList<>();
            for (List<String> row : rows) {
                if (index < row.size()) values.add(row.get(index));
            }
            return List.copyOf(values);
        }
    }

    /**
     * @param line   the line of its keyword
     * @param rowId  the first {@code [TC-...]} of a Scenario or Outline name, otherwise null
     */
    record Block(Kind kind, List<String> tags, int line, String rowId, List<Table> examples) {}

    private static final String BOM = "﻿";

    private final Path file;
    private final boolean bom;
    private final String separator;
    private final boolean finalNewline;
    private final List<String> lines;
    private final List<Block> blocks;

    private FeatureFile(Path file, boolean bom, String separator, boolean finalNewline, List<String> lines) {
        this.file = file;
        this.bom = bom;
        this.separator = separator;
        this.finalNewline = finalNewline;
        this.lines = lines;
        this.blocks = parseBlocks(lines);
    }

    static FeatureFile read(Path file) {
        try {
            return parse(file, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("[FeatureFile] cannot read " + file, e);
        }
    }

    static FeatureFile parse(Path file, String text) {
        boolean bom = text.startsWith(BOM);
        String body = bom ? text.substring(BOM.length()) : text;
        String separator = body.contains("\r\n") ? "\r\n" : "\n";
        boolean finalNewline = body.endsWith("\n");
        List<String> lines = new ArrayList<>(Arrays.asList(body.split("\r?\n", -1)));
        if (finalNewline) lines.remove(lines.size() - 1);
        return new FeatureFile(file, bom, separator, finalNewline, List.copyOf(lines));
    }

    Path file() {
        return file;
    }

    List<String> lines() {
        return lines;
    }

    List<Block> blocks() {
        return blocks;
    }

    /** {@code lines} as file text, with the byte order mark, line separator and final newline as read. */
    String render(List<String> lines) {
        return (bom ? BOM : "") + String.join(separator, lines) + (finalNewline ? separator : "");
    }

    private static List<Block> parseBlocks(List<String> lines) {
        List<BlockBuilder> blocks = new ArrayList<>();
        List<String> pendingTags = new ArrayList<>();
        BlockBuilder current = null;
        TableBuilder table = null;
        String docString = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (docString != null) {
                if (line.startsWith(docString)) docString = null;
                continue;
            }
            if (table != null) {
                if (line.startsWith("|")) {
                    table.add(i, lines.get(i), cells(line));
                    continue;
                }
                // Gherkin allows blank and comment lines inside a table, also before its header
                if (line.isEmpty() || line.startsWith("#")) continue;
                table = null;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("\"\"\"") || line.startsWith("```")) {
                docString = line.substring(0, 3);
                pendingTags.clear();
            } else if (line.startsWith("@")) {
                for (String token : line.split("\\s+")) {
                    if (token.startsWith("@")) pendingTags.add(token);
                }
            } else if (startsWithAny(line, "Examples:", "Scenarios:")) {
                table = new TableBuilder(take(pendingTags));
                if (current != null) current.examples.add(table);
            } else if (startsWithAny(line, "Scenario Outline:", "Scenario Template:")) {
                current = new BlockBuilder(Kind.OUTLINE, take(pendingTags), i, CaseId.fromScenarioName(line));
                blocks.add(current);
            } else if (startsWithAny(line, "Scenario:", "Example:")) {
                current = new BlockBuilder(Kind.SCENARIO, take(pendingTags), i, CaseId.fromScenarioName(line));
                blocks.add(current);
            } else if (line.startsWith("Feature:")) {
                current = new BlockBuilder(Kind.FEATURE, take(pendingTags), i, null);
                blocks.add(current);
            } else if (line.startsWith("Background:")) {
                current = new BlockBuilder(Kind.BACKGROUND, take(pendingTags), i, null);
                blocks.add(current);
            } else if (line.startsWith("Rule:")) {
                current = new BlockBuilder(Kind.RULE, take(pendingTags), i, null);
                blocks.add(current);
            } else {
                pendingTags.clear();
            }
        }
        return blocks.stream().map(BlockBuilder::build).toList();
    }

    private static List<String> take(List<String> pendingTags) {
        List<String> tags = List.copyOf(pendingTags);
        pendingTags.clear();
        return tags;
    }

    private static List<String> cells(String tableLine) {
        String inner = tableLine.substring(1, tableLine.endsWith("|") && tableLine.length() > 1
                ? tableLine.length() - 1 : tableLine.length());
        List<String> cells = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) cells.add(cell.trim());
        return List.copyOf(cells);
    }

    private static boolean startsWithAny(String line, String... keywords) {
        for (String keyword : keywords) {
            if (line.startsWith(keyword)) return true;
        }
        return false;
    }

    private static final class BlockBuilder {
        final Kind kind;
        final List<String> tags;
        final int line;
        final String rowId;
        final List<TableBuilder> examples = new ArrayList<>();

        BlockBuilder(Kind kind, List<String> tags, int line, String rowId) {
            this.kind = kind;
            this.tags = tags;
            this.line = line;
            this.rowId = rowId;
        }

        Block build() {
            return new Block(kind, tags, line, rowId, examples.stream().map(TableBuilder::build).toList());
        }
    }

    private static final class TableBuilder {
        final List<String> tags;
        int headerLine = -1;
        int lastLine = -1;
        String indent = "";
        List<String> header = List.of();
        final List<List<String>> rows = new ArrayList<>();

        TableBuilder(List<String> tags) {
            this.tags = tags;
        }

        void add(int index, String rawLine, List<String> cells) {
            if (headerLine < 0) {
                headerLine = index;
                indent = rawLine.substring(0, rawLine.length() - rawLine.stripLeading().length());
                header = cells;
            } else {
                rows.add(cells);
            }
            lastLine = index;
        }

        Table build() {
            return new Table(tags, headerLine, lastLine, indent, header, List.copyOf(rows));
        }
    }
}
