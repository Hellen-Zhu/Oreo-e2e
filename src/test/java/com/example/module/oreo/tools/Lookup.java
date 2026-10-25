package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.CaseData;
import com.example.module.oreo.testdata.CaseId;
import com.example.module.oreo.testdata.ExpectedResponses;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.TestData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Prints the data of one case without running it: {@code case TC-TRADE-CREATION-FX_PSCRIPT_TRF-API-002}. */
final class Lookup {

    private Lookup() {}

    static int run(String caseId) throws IOException {
        Path project = ProjectPaths.projectRoot();
        TestDataTool.requireFreshData(project);
        CaseId id = CaseId.parse(caseId);
        boolean noExpected;
        try {
            noExpected = FeatureCases.load(project.resolve("src/test/resources/features")).stream()
                    .anyMatch(c -> c.caseId().equals(id.raw()) && c.noExpected());
        } catch (RuntimeException e) {
            noExpected = false; // a broken feature file is check's to report; the case's data still prints
        }
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
        if (id.channelDir().equals("api") && noExpected) {
            out.append("Expected file: none (the case is tagged ").append(ExpectedResponses.NONE_TAG)
                    .append(": its response is not compared with a recording)\n");
        } else if (id.channelDir().equals("api")) {
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
