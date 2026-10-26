package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.CaseData;
import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.ResponseSuggestions;
import com.example.module.oreo.testdata.TestData;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Suggests the {@code dynamicFields} rules and the {@code expectedBase} of one event from its recorded responses
 * ({@code suggest trade_creation}); writes nothing. Run it after a first recording or a re-recording, before
 * {@code compact}: a value that changed since the committed recording is the strongest evidence of a dynamic value,
 * so re-record (OREO_EXPECTED=update) without committing, then suggest. Every suggestion is for a person to judge.
 */
final class Suggest {

    private Suggest() {}

    static int run(String event) throws IOException, InterruptedException {
        Path project = ProjectPaths.projectRoot();
        TestDataTool.requireFreshData(project);
        ExpectedResponses files = ExpectedResponses.standard();
        if (!files.events().contains(event)) {
            System.out.println("suggest " + event + ": no such folder of expected files; known: " + files.events());
            return 1;
        }
        Optional<ExpectedResponses> committed = committed(project, files, event);

        Map<String, JsonNode> responses = new TreeMap<>();
        Map<String, List<ResponseSuggestions.Finding>> findings = new TreeMap<>();
        Map<String, String> declaredBase = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (CaseId id : files.cases(event)) {
            JsonNode actual;
            CaseData data;
            try {
                actual = Json.parse(files.resolve(id).orElseThrow().json());
                data = TestData.forCase(id.raw());
            } catch (RuntimeException e) {
                problems.add(id.raw() + ": " + e.getMessage());
                continue;
            }
            JsonNode previous = committed.flatMap(c -> previous(c, id)).orElse(null);
            responses.put(id.raw(), actual);
            findings.put(id.raw(), ResponseSuggestions.dynamicValues(actual, previous, data.request()));
            declaredBase.put(id.raw(), data.expectedBase());
        }

        StringBuilder out = new StringBuilder();
        out.append("Suggestions for ").append(event).append(" (").append(responses.size()).append(" recorded case(s); ")
                .append(committed.isPresent() ? "compared with the committed recordings" : "no committed recordings to compare with")
                .append("). Judge each one; nothing is written.\n");
        problems.forEach(p -> out.append("  [problem] ").append(p).append('\n'));

        out.append("\n1. dynamicFields: plain values that look generated\n");
        List<ResponseSuggestions.Rule> rules = ResponseSuggestions.placeRules(findings,
                (c, path) -> ResponseSuggestions.has(responses.get(c), path));
        if (rules.isEmpty()) out.append("  (none: every dynamic value already has a rule)\n");
        Map<String, ObjectNode> byLayer = new LinkedHashMap<>();
        for (ResponseSuggestions.Rule rule : rules) {
            out.append("  ").append(rule.layer()).append(": ").append(rule.path()).append(" = ").append(rule.rule())
                    .append("\n      ").append(rule.reason())
                    .append(rule.layer().startsWith("row") ? "" : " (" + rule.cases().size() + " case(s))").append('\n');
            byLayer.computeIfAbsent(rule.layer(), k -> Json.MAPPER.createObjectNode()).put(rule.path(), rule.rule());
        }
        if (!byLayer.isEmpty()) {
            out.append("  As data (the \"dynamicFields\" of each layer of data/api/").append(event).append(".json):\n");
            byLayer.forEach((layer, map) -> out.append("    ").append(layer).append(": ")
                    .append(Json.compact(map)).append('\n'));
            out.append("  A case whose response lacks a defaults path (a rejected case) needs \"dynamicFields\": null"
                    + " in its row. Then record again (OREO_EXPECTED=update) and run suggest once more.\n");
        }

        out.append("\n2. expectedBase: the base that leaves the fewest characters\n");
        List<ResponseSuggestions.Base> bases = ResponseSuggestions.bases(responses);
        if (bases.isEmpty()) out.append("  (none: no product has two recorded cases)\n");
        boolean keepDeclared = true;
        for (ResponseSuggestions.Base base : bases) {
            String declared = declaredBase.entrySet().stream()
                    .filter(e -> base.product().equals(CaseId.parse(e.getKey()).product()) && e.getValue() != null)
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            out.append("  ").append(base.product()).append(": all full ").append(base.allFull()).append(" characters");
            if (declared != null) {
                Integer with = base.with(declared);
                out.append("; declared ").append(declared).append(" ")
                        .append(with == null ? "(not a recorded case of this product)" : with + " characters");
            }
            out.append("; best ").append(base.worthIt() ? base.caseId() + " " + base.withBase() + " characters"
                    : "no base (no patch is shorter than its full response)").append('\n');
            if (base.worthIt()) {
                base.forms().forEach((c, form) -> out.append("      on the best: ").append(c).append(": ")
                        .append(form).append('\n'));
            }
            keepDeclared &= base.declaredIsGoodEnough(declared);
        }
        String common = ResponseSuggestions.commonBase(bases);
        if (keepDeclared && !bases.isEmpty()) {
            out.append("  Keep the declared base: it is within 10% of the best for every product, and the plain case"
                    + " is the easier base to read a patch against.\n");
        } else if (bases.stream().noneMatch(ResponseSuggestions.Base::worthIt)) {
            out.append("  No base: leave expectedBase out (or null) and say why in _comment.\n");
        } else if (common != null) {
            out.append("  As data: \"defaults\": { \"expectedBase\": \"").append(common).append("\" }\n");
        } else if (!bases.isEmpty()) {
            out.append("  The products differ: name each base in byProduct.<P>.expectedBase, or none.\n");
        }
        out.append("\nMask first, then the base: the base is measured on the recordings as they are."
                + " After changing the data: compact ").append(event).append(", review git diff.");
        System.out.println(out);
        return problems.size();
    }

    /** The committed recordings of the event (with the shared parts), in a temporary folder; empty without git. */
    private static Optional<ExpectedResponses> committed(Path project, ExpectedResponses files, String event)
            throws IOException, InterruptedException {
        String prefix = project.relativize(files.root()).toString().replace('\\', '/') + "/";
        Optional<String> listing = git(project, "ls-tree", "-r", "--name-only", "HEAD", "--", prefix + event,
                prefix + "_shared");
        if (listing.isEmpty() || listing.get().isBlank()) return Optional.empty();
        Path tmp = Files.createTempDirectory("oreo-suggest");
        tmp.toFile().deleteOnExit();
        for (String name : listing.get().split("\n")) {
            Optional<String> text = git(project, "show", "HEAD:" + name);
            if (text.isEmpty()) continue;
            Path file = tmp.resolve(name.substring(prefix.length()));
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.get(), StandardCharsets.UTF_8);
        }
        return Optional.of(new ExpectedResponses(tmp));
    }

    private static Optional<JsonNode> previous(ExpectedResponses committed, CaseId id) {
        try {
            return committed.resolve(id).map(r -> Json.parse(r.json()));
        } catch (RuntimeException e) {
            return Optional.empty(); // a committed file that no longer resolves gives no evidence
        }
    }

    private static Optional<String> git(Path project, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", project.toString()));
        command.addAll(List.of(args));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            return Optional.empty(); // no git: suggestions from the patterns only
        }
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return Optional.empty();
        return Optional.of(text);
    }
}
