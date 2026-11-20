package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ExpectedCompaction;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SharedFragments;
import com.example.module.oreo.testdata.TestData;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compacts the recorded API responses (section 7.5 of the test data design): a variant's file becomes a patch on the
 * base case its data declares, a case without a base keeps its full response; in a full response, the parts the data
 * declares shared ({@code sharedFragments}) become references to {@code _shared/<name>.json}. Run after a recording,
 * before the review, for one event ({@code compact trade_creation}) or {@code all}; {@code all} also takes every shared
 * part again from the first case that has it and deletes the shared files no case uses. The events of
 * {@code sharedSources} ({@code api/common.json}) come first: their cases give the shared parts. For one event, a
 * shared file that does not exist yet is taken from the source events too (their own files are not rewritten).
 * Nothing is written when any event has a problem.
 */
final class Compact {

    private Compact() {}

    static int run(String scope) throws IOException {
        TestDataTool.requireFreshData(ProjectPaths.projectRoot());
        ExpectedResponses files = ExpectedResponses.standard();
        List<String> known = files.events();
        List<String> sources = TestData.sharedSources();
        List<String> events = scope.equals("all") ? ExpectedCompaction.order(known, sources) : List.of(scope);
        if (!known.containsAll(events)) {
            System.out.println("compact " + scope + ": no such folder of expected files; known: " + known + " or all");
            return 1;
        }
        List<String> seeds = scope.equals("all") ? List.of()
                : ExpectedCompaction.order(known, sources).stream().filter(sources::contains)
                        .filter(e -> !e.equals(scope)).toList();

        ExpectedCompaction.Shared shared = scope.equals("all")
                ? ExpectedCompaction.Shared.rebuild(files) : ExpectedCompaction.Shared.fromDisk(files);
        return planAndWrite(files, seeds, events, shared, null);
    }

    /**
     * Plans {@code events} with {@code shared} (the {@code seeds} for their shared parts only) and, when nothing has a
     * problem, writes the shared parts and the files that change and prints them; returns the number of problems.
     * {@code onlyParts} not {@code null} ({@code shared <case>}): writes only those shared parts and the files whose
     * expectation depends on them (a file that refers to one, a patch on such a file); the other files that would
     * change are counted and left for {@code compact}.
     */
    static int planAndWrite(ExpectedResponses files, List<String> seeds, List<String> events,
                            ExpectedCompaction.Shared shared, Set<String> onlyParts) {
        List<ExpectedCompaction.Plan> plans = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String seed : seeds) {
            // planned for its shared parts only: a part missing from _shared/ is taken from the source event
            ExpectedCompaction.plan(files, seed, id -> TestData.forCase(id.raw()).expectedBase(),
                    id -> TestData.forCase(id.raw()).sharedFragments(), shared)
                    .errors().forEach(e -> errors.add(seed + " (source of the shared parts): " + e));
        }
        for (String event : events) {
            ExpectedCompaction.Plan plan = ExpectedCompaction.plan(files, event,
                    id -> TestData.forCase(id.raw()).expectedBase(),
                    id -> TestData.forCase(id.raw()).sharedFragments(), shared);
            plans.add(plan);
            plan.errors().forEach(e -> errors.add(event + ": " + e));
        }
        if (!errors.isEmpty()) {
            System.out.println("Nothing written, " + errors.size() + " problem(s):\n  " + String.join("\n  ", errors));
            return errors.size();
        }

        StringBuilder out = new StringBuilder();
        int changed = 0;
        int unchanged = 0;
        int left = 0;
        // the files to write: with onlyParts, those whose expectation depends on the parts given
        Set<String> affected = onlyParts == null ? null : affected(plans, onlyParts);
        List<List<ExpectedCompaction.Entry>> rewritten = new ArrayList<>();
        Set<String> referenced = new TreeSet<>();
        for (ExpectedCompaction.Plan plan : plans) {
            List<ExpectedCompaction.Entry> entries = new ArrayList<>();
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                if (!entry.changed()) unchanged++;
                else if (affected != null && !affected.contains(entry.id().raw())) left++;
                else {
                    entries.add(entry);
                    referenced.addAll(SharedFragments.references(Json.parse(entry.after())));
                }
            }
            rewritten.add(entries);
        }
        // with onlyParts, the parts given and the new parts a written file refers to
        List<ExpectedCompaction.SharedEntry> sharedEntries = shared.entries().stream()
                .filter(e -> onlyParts == null || onlyParts.contains(e.name())
                        || e.before().isEmpty() && referenced.contains(e.name()))
                .toList();
        ExpectedCompaction.write(files, sharedEntries);
        if (!sharedEntries.isEmpty()) out.append(SharedFragments.FOLDER).append(":\n");
        for (ExpectedCompaction.SharedEntry entry : sharedEntries) {
            out.append("  ").append(entry.name()).append(".json: ")
                    .append(entry.after() == null ? "deleted (no case uses it)"
                            : entry.before().isEmpty() ? "new" : "rewritten").append('\n');
            changed++;
        }
        // only the files that change are listed: the others are counted
        for (int i = 0; i < plans.size(); i++) {
            ExpectedCompaction.Plan plan = plans.get(i);
            List<ExpectedCompaction.Entry> entries = rewritten.get(i);
            ExpectedCompaction.write(files, plan, entries);
            if (entries.isEmpty()) continue;
            out.append(plan.eventFile()).append(":\n");
            for (ExpectedCompaction.Entry entry : entries) {
                out.append("  ").append(entry.id().raw()).append(".json: ").append(entry.note()).append('\n');
                if (entry.warning() != null) out.append("    [warn] ").append(entry.warning()).append('\n');
            }
            changed += entries.size();
        }
        out.append(changed).append(" file(s) rewritten, ").append(unchanged).append(" unchanged")
                .append(changed == 0 ? "." : ": review git diff, then commit.");
        if (left > 0) {
            out.append('\n').append(left).append(" other file(s), which do not depend on ")
                    .append(String.join(", ", onlyParts)).append(", would change: left as they are (compact <event>")
                    .append(" rewrites them).");
        }
        System.out.println(out);
        return 0;
    }

    /**
     * The cases whose expectation depends on {@code parts}: a file that refers to one of them before or after the
     * compaction, and a patch on such a file.
     */
    private static Set<String> affected(List<ExpectedCompaction.Plan> plans, Set<String> parts) {
        Set<String> out = new TreeSet<>();
        for (ExpectedCompaction.Plan plan : plans) {
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                if (refersTo(entry.before(), parts) || refersTo(entry.after(), parts)) out.add(entry.id().raw());
            }
        }
        for (ExpectedCompaction.Plan plan : plans) {
            for (ExpectedCompaction.Entry entry : plan.entries()) {
                for (String text : List.of(entry.before(), entry.after())) {
                    String base = base(text);
                    if (base != null && out.contains(base)) out.add(entry.id().raw());
                }
            }
        }
        return out;
    }

    private static boolean refersTo(String text, Set<String> parts) {
        if (text == null || text.isBlank()) return false;
        return SharedFragments.references(Json.parse(text)).stream().anyMatch(parts::contains);
    }

    /** The {@code _base} of a patch file's text, else {@code null}. */
    private static String base(String text) {
        if (text == null || text.isBlank()) return null;
        JsonNode base = Json.parse(text).get("_base");
        return base != null && base.isTextual() ? base.asText() : null;
    }
}
