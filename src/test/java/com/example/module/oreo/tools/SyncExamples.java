package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;

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
