package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.ExpectedCompaction;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes the shared response parts ({@code _shared/<name>.json}) from the recorded response of one case, or of every
 * product's case of a case ID with {@code <product_type>}: each part the case declares ({@code sharedFragments})
 * becomes the shared part of its name, as the case's file has it (after a recording with
 * {@code OREO_EXPECTED=update}, the full new response). Every expected file is then compacted again against the new
 * parts, so each case still expects what it expected before: a case whose part differs keeps the keys that differ
 * beside its reference. Shared parts the case does not declare, or does not hold as an object (a whole-part
 * placeholder), are kept. Nothing is written when any event has a problem.
 */
final class SharedFromCase {

    private SharedFromCase() {}

    /**
     * {@code source}: a case ID, the path of its expected file ({@code .../TC-....json}), or a case ID with
     * {@code <product_type>} (every product's case that has an expected file).
     */
    static int run(String source) throws IOException {
        TestDataTool.requireFreshData(ProjectPaths.projectRoot());
        String raw = source;
        if (raw.endsWith(".json")) {
            String name = Path.of(raw).getFileName().toString();
            raw = name.substring(0, name.length() - ".json".length());
        }
        ExpectedResponses files = ExpectedResponses.standard();
        List<CaseId> ids = new ArrayList<>();
        if (raw.contains(FeatureCases.PRODUCT_PLACEHOLDER)) {
            CaseId.checkRowId(raw);
            for (String event : files.events()) {
                for (CaseId id : files.cases(event)) if (id.matchesRow(raw)) ids.add(id);
            }
            if (ids.isEmpty()) {
                System.out.println("shared " + source + ": no product's case has an expected file (record them first)");
                return 1;
            }
        } else {
            CaseId id = CaseId.parse(raw);
            if (files.resolve(id).isEmpty()) {
                System.out.println("shared " + source + ": no expected file " + files.fileFor(id)
                        + " (record it first)");
                return 1;
            }
            ids.add(id);
        }

        Map<String, ObjectNode> parts = new LinkedHashMap<>();
        Map<String, String> from = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (CaseId id : ids) {
            JsonNode response = Json.parse(files.resolve(id).orElseThrow().json());
            Map<String, String> fragments = TestData.forCase(id.raw()).sharedFragments();
            if (fragments.isEmpty()) {
                errors.add(id.raw() + " declares no shared parts (sharedFragments)");
                continue;
            }
            for (Map.Entry<String, String> fragment : fragments.entrySet()) {
                JsonNode part = response;
                for (String key : fragment.getKey().split("\\.")) part = part == null ? null : part.get(key);
                String name = fragment.getValue();
                if (!(part instanceof ObjectNode object)) {
                    System.out.println("  " + name + " kept: " + fragment.getKey() + " of " + id.raw() + " is "
                            + (part == null ? "not in the response" : "not an object (a whole-part placeholder)"));
                } else if (from.containsKey(name)) {
                    errors.add(name + " is given by both " + from.get(name) + " and " + id.raw());
                } else {
                    parts.put(name, object);
                    from.put(name, id.raw());
                }
            }
        }
        if (!errors.isEmpty()) {
            System.out.println("Nothing written, " + errors.size() + " problem(s):\n  " + String.join("\n  ", errors));
            return errors.size();
        }
        if (parts.isEmpty()) {
            System.out.println("Nothing written: no shared part is an object in " + source);
            return 1;
        }

        from.forEach((name, id) -> System.out.println("  " + name + " <- " + id));
        List<String> events = ExpectedCompaction.order(files.events(), TestData.sharedSources());
        return Compact.planAndWrite(files, List.of(), events, ExpectedCompaction.Shared.withParts(files, parts));
    }
}
