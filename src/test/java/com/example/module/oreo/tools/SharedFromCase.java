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
 * Takes the shared response parts ({@code _shared/<name>.json}) from the recorded response of one case: each part
 * the case declares ({@code sharedFragments}) becomes the shared part of its name, as the case's file has it (after a
 * recording with {@code OREO_EXPECTED=update}, the full new response). Every expected file is then compacted again
 * against the new parts, so each case still expects what it expected before: a case whose part differs keeps the
 * keys that differ beside its reference. Shared parts the case does not declare, or does not hold as an object (a
 * whole-part placeholder), are kept. Nothing is written when any event has a problem.
 */
final class SharedFromCase {

    private SharedFromCase() {}

    /** {@code source}: a case ID, or the path of its expected file ({@code .../TC-....json}). */
    static int run(String source) throws IOException {
        TestDataTool.requireFreshData(ProjectPaths.projectRoot());
        String raw = source;
        if (raw.endsWith(".json")) {
            String name = Path.of(raw).getFileName().toString();
            raw = name.substring(0, name.length() - ".json".length());
        }
        CaseId id = CaseId.parse(raw);
        ExpectedResponses files = ExpectedResponses.standard();
        if (files.resolve(id).isEmpty()) {
            System.out.println("shared " + source + ": no expected file " + files.fileFor(id) + " (record it first)");
            return 1;
        }
        JsonNode response = Json.parse(files.resolve(id).orElseThrow().json());
        Map<String, String> fragments = TestData.forCase(id.raw()).sharedFragments();
        if (fragments.isEmpty()) {
            System.out.println("shared " + source + ": " + id.raw() + " declares no shared parts (sharedFragments)");
            return 1;
        }

        Map<String, ObjectNode> parts = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, String> fragment : fragments.entrySet()) {
            JsonNode part = response;
            for (String key : fragment.getKey().split("\\.")) part = part == null ? null : part.get(key);
            if (part instanceof ObjectNode object) {
                parts.put(fragment.getValue(), object);
            } else {
                skipped.add(fragment.getValue() + " kept: " + fragment.getKey() + " is "
                        + (part == null ? "not in the response" : "not an object (a whole-part placeholder)"));
            }
        }
        skipped.forEach(s -> System.out.println("  " + s));
        if (parts.isEmpty()) {
            System.out.println("Nothing written: " + id.raw() + " has none of its shared parts as an object");
            return 1;
        }

        System.out.println("Shared parts from " + id.raw() + ": " + String.join(", ", parts.keySet()));
        List<String> events = ExpectedCompaction.order(files.events(), TestData.sharedSources());
        return Compact.planAndWrite(files, List.of(), events, ExpectedCompaction.Shared.withParts(files, parts));
    }
}
