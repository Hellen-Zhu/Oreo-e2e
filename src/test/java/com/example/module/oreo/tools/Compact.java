package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ExpectedCompaction;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SharedFragments;
import com.example.module.oreo.testdata.TestData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        return planAndWrite(files, seeds, events, shared);
    }

    /**
     * Plans {@code events} with {@code shared} (the {@code seeds} for their shared parts only) and, when nothing has a
     * problem, writes the shared parts and the files that change and prints them; returns the number of problems.
     */
    static int planAndWrite(ExpectedResponses files, List<String> seeds, List<String> events,
                            ExpectedCompaction.Shared shared) {
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
        List<ExpectedCompaction.SharedEntry> sharedEntries = shared.entries();
        ExpectedCompaction.write(files, shared);
        if (!sharedEntries.isEmpty()) out.append(SharedFragments.FOLDER).append(":\n");
        for (ExpectedCompaction.SharedEntry entry : sharedEntries) {
            out.append("  ").append(entry.name()).append(".json: ")
                    .append(entry.after() == null ? "deleted (no case uses it)"
                            : entry.before().isEmpty() ? "new" : "rewritten").append('\n');
            changed++;
        }
        // only the files that change are listed: the others are counted
        for (ExpectedCompaction.Plan plan : plans) {
            ExpectedCompaction.write(files, plan);
            List<ExpectedCompaction.Entry> rewritten = plan.entries().stream().filter(ExpectedCompaction.Entry::changed)
                    .toList();
            unchanged += plan.entries().size() - rewritten.size();
            if (rewritten.isEmpty()) continue;
            out.append(plan.eventFile()).append(":\n");
            for (ExpectedCompaction.Entry entry : rewritten) {
                out.append("  ").append(entry.id().raw()).append(".json: ").append(entry.note()).append('\n');
            }
            changed += rewritten.size();
        }
        out.append(changed).append(" file(s) rewritten, ").append(unchanged).append(" unchanged")
                .append(changed == 0 ? "." : ": review git diff, then commit.");
        System.out.println(out);
        return 0;
    }
}
