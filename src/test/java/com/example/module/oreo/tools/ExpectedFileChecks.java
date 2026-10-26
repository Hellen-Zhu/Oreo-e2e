package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.ExpectedCompaction;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ResponseMasker;
import com.example.module.oreo.testdata.SharedFragments;
import com.example.module.oreo.testdata.TestData;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The checks of {@code check} on the recorded API responses (sections 10.4, 10.5 and 10.7): every API case has its
 * expected file and every file its case, the {@code dynamicFields} rules agree with the files, the files are
 * compacted (shared parts included). {@link Checks} runs them and prints what they found.
 */
final class ExpectedFileChecks {

    private static final String PLACEHOLDER = "${json-unit.";
    private static final Set<String> TYPE_PLACEHOLDERS =
            Set.of(ResponseMasker.ANY_STRING, ResponseMasker.ANY_NUMBER, ResponseMasker.ANY_BOOLEAN,
                    ResponseMasker.ANY_ARRAY);

    private final Path project;
    private final Path data;
    private final List<FeatureCases.FeatureCase> featureCases;

    ExpectedFileChecks(Path project, List<FeatureCases.FeatureCase> featureCases) {
        this.project = project;
        this.data = project.resolve("src/test/resources/data");
        this.featureCases = featureCases;
    }

    // ── 10.4 ─────────────────────────────────────────────────────────────────

    void everyApiCaseHasItsExpectedFile(Checks.Result result) throws IOException {
        ExpectedResponses expected = ExpectedResponses.standard();
        Set<Path> wanted = new TreeSet<>();
        int none = 0;
        for (FeatureCases.FeatureCase c : featureCases) {
            if (!c.channelDir().equals("api")) continue;
            Path file = expected.fileFor(CaseId.parse(c.caseId()));
            if (c.noExpected()) {
                // Not added to wanted: a file left from before the tag is reported below as belonging to no case
                none++;
                continue;
            }
            wanted.add(file.toAbsolutePath().normalize());
            if (!Files.isRegularFile(file)) {
                result.errors.add(c.caseId() + ": no expected file " + project.relativize(file)
                        + " (run the case to record it, review it, commit it)");
            }
        }
        Path root = data.resolve("api/expected");
        Path sharedFolder = root.resolve(SharedFragments.FOLDER);
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".json") && !p.startsWith(sharedFolder))
                        .sorted().toList()) {
                    if (!wanted.contains(file.toAbsolutePath().normalize())) {
                        result.errors.add(project.relativize(file) + " belongs to no API case of the feature files"
                                + " that compares its response (a case tagged " + ExpectedResponses.NONE_TAG
                                + " has none: delete the file, or take the tag off)");
                    }
                }
            }
        }
        if (none > 0) {
            result.notes.add(none + " API case(s) tagged " + ExpectedResponses.NONE_TAG + ": no expected file");
        }
    }

    // ── 10.5 ─────────────────────────────────────────────────────────────────

    void rulesAgreeWithExpectedFiles(Checks.Result result) {
        ExpectedResponses expected = ExpectedResponses.standard();
        for (FeatureCases.FeatureCase c : featureCases) {
            if (!c.channelDir().equals("api") || c.noExpected()) continue;
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

    /** Plans {@code compact all}: a file or shared part it would write, or a shared file it would delete, is reported. */
    void expectedFilesAreCompacted(Checks.Result result) {
        ExpectedResponses expected = ExpectedResponses.standard();
        ExpectedCompaction.Shared shared = ExpectedCompaction.Shared.rebuild(expected);
        for (String event : expected.events()) {
            ExpectedCompaction.Plan plan = ExpectedCompaction.plan(expected, event,
                    id -> TestData.forCase(id.raw()).expectedBase(),
                    id -> TestData.forCase(id.raw()).sharedFragments(), shared);
            result.errors.addAll(plan.errors());
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                if (entry.changed()) {
                    result.warnings.add(project.relativize(expected.fileFor(entry.id())) + ": " + entry.note());
                }
            }
        }
        for (ExpectedCompaction.SharedEntry entry : shared.entries()) {
            String file = project.relativize(expected.shared().fileFor(entry.name())).toString();
            result.warnings.add(file + ": " + (entry.after() == null ? "no case uses it"
                    : entry.before().isEmpty() ? "not written yet" : "differs from the first case that has it")
                    + " (TestDataTool compact all)");
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
                select(node.get(key), steps, i + 1, at + keyStep(at, key), out);
            }
        } else if (ResponseMasker.isAnyKey(step)) {
            if (node.isObject()) {
                for (String key : node.properties().stream().map(Map.Entry::getKey).toList()) {
                    select(node.get(key), steps, i + 1, at + keyStep(at, key), out);
                }
            }
        } else if (node.isArray()) {
            int from = ResponseMasker.isAny(step) ? 0 : (Integer) step;
            int to = ResponseMasker.isAny(step) ? node.size() : Math.min(from + 1, node.size());
            for (int n = from; n < to; n++) {
                select(node.get(n), steps, i + 1, at + "[" + n + "]", out);
            }
        }
    }

    /** {@code key} appended to the rendered path {@code at}. */
    private static String keyStep(String at, String key) {
        return at.isEmpty() ? key : "." + key;
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
            boolean key = (a instanceof String || ResponseMasker.isAnyKey(a))
                    && (b instanceof String || ResponseMasker.isAnyKey(b));
            boolean same = a.equals(b) || (index && (ResponseMasker.isAny(a) || ResponseMasker.isAny(b)))
                    || (key && (ResponseMasker.isAnyKey(a) || ResponseMasker.isAnyKey(b)));
            if (!same) return false;
        }
        return true;
    }

    private static String render(List<Object> path) {
        return ResponseMasker.render(path);
    }

    private static boolean isPlaceholder(JsonNode node) {
        return node.isTextual()
                && (node.textValue().startsWith(PLACEHOLDER) || node.textValue().startsWith(ResponseMasker.VAR_START));
    }
}
