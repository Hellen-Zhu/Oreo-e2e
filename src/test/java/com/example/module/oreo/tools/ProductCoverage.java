package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prints, for one product ({@code product FX_TRF}), each capability with the product Outlines that test it and their
 * case IDs for that product, as a table QA answers row by row (supported / not supported). Writes nothing. A product
 * that is not in {@link ProductDatFiles} yet is shown with every capability, the starting point of onboarding.
 */
final class ProductCoverage {

    private static final Pattern PRODUCT_ID = Pattern.compile("[A-Z][A-Z0-9_]*");

    private ProductCoverage() {}

    static int run(String product) {
        if (!PRODUCT_ID.matcher(product).matches()) {
            System.out.println("product " + product + ": not a product ID (upper case, digits and _, e.g. FX_TRF)");
            return 1;
        }
        Optional<ProductDatFiles> registered = Arrays.stream(ProductDatFiles.values())
                .filter(p -> p.name().equals(product)).findFirst();
        Set<Capability> capabilities = registered.map(ProductDatFiles::capabilities).orElse(Set.of());
        ProductExamples.Plan plan = ProductExamples.plan(
                ProjectPaths.projectRoot().resolve("src/test/resources/features"), ProductExamples::registryProducts);

        StringBuilder out = new StringBuilder();
        out.append("Coverage of ").append(product).append(": ").append(registered.isPresent()
                ? capabilities.size() + " of " + Capability.values().length + " capabilities in ProductDatFiles"
                : "not in ProductDatFiles; every capability is shown, the default of onboarding").append('\n');
        plan.errors().forEach(e -> out.append("  [problem] ").append(e).append('\n'));

        out.append("\nQA answers every row: a capability covers all its Outlines, API and UI.\n")
                .append("| Capability | Registry | Outlines | QA: supported? |\n|---|---|---|---|\n");
        for (Capability capability : Capability.values()) {
            List<ProductExamples.Entry> entries = entries(plan, capability);
            long api = entries.stream().filter(e -> e.file().startsWith("api/")).count();
            out.append("| ").append(capability.tagName()).append(" | ")
                    .append(capabilities.contains(capability) ? "yes" : "no").append(" | ")
                    .append(entries.isEmpty() ? "none" : api + " API, " + (entries.size() - api) + " UI")
                    .append(" | |\n");
        }

        out.append("\nThe Outlines, with the case IDs of ").append(product).append(":\n")
                .append("| Capability | Case ID | Title | Feature | Examples |\n|---|---|---|---|---|\n");
        int sync = 0;
        for (Capability capability : Capability.values()) {
            for (ProductExamples.Entry entry : entries(plan, capability)) {
                boolean inExamples = entry.before().contains(product);
                if (inExamples != capabilities.contains(capability)) sync++;
                out.append("| ").append(capability.tagName()).append(" | ")
                        .append(entry.rowId().replace(FeatureCases.PRODUCT_PLACEHOLDER, product)).append(" | ")
                        .append(entry.title().replace(FeatureCases.PRODUCT_PLACEHOLDER, product).replace("|", "\\|"))
                        .append(" | ").append(entry.file()).append(" | ").append(inExamples ? "row" : "no row")
                        .append(" |\n");
            }
        }
        if (sync > 0) {
            out.append('\n').append(sync).append(" Outline(s) whose Examples differ from the registry for ")
                    .append(product).append(": run sync-examples.");
        }
        out.append("\nThen take the unsupported capabilities off the constant in ProductDatFiles and run sync-examples.");
        System.out.println(out);
        return plan.errors().size();
    }

    private static List<ProductExamples.Entry> entries(ProductExamples.Plan plan, Capability capability) {
        return plan.entries().stream().filter(e -> e.capability() == capability).toList();
    }
}
