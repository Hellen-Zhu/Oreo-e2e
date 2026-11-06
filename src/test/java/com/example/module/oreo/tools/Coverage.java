package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * The coverage matrix ({@code coverage}): one row per capability, by group of {@link Capability#GROUPS} (trade
 * lifecycle, product features), one column per product of the registry, each cell the layers whose cases test it for
 * that product: {@code API+UI}, {@code API}, {@code UI}, or {@code NONE}. It reads the registry
 * ({@link ProductDatFiles}) and the product Outlines ({@code @capability#} tags, {@link ProductExamples}); it writes
 * nothing and never fails. A Scenario with a fixed product is not counted: only the product Outlines say which
 * capability they test. Whether a case has its data and its recording is the check's business, not this report's.
 */
final class Coverage {

    static final String NOT_SUPPORTED = "-";
    static final String NO_CASE = "NONE";
    private static final String WIP_TAG = "@wip";

    /**
     * One cell: the Outlines of the capability that run for the product, by layer; {@code wip} more that carry
     * {@code @wip}; {@code unsynced} when an Outline of the capability has no row for the product yet.
     */
    record Cell(boolean supported, int api, int ui, int wip, boolean unsynced) {

        String text() {
            if (!supported) return NOT_SUPPORTED;
            String text = api > 0 && ui > 0 ? "API+UI" : api > 0 ? "API" : ui > 0 ? "UI" : NO_CASE;
            if (wip > 0) text += " +wip";
            if (unsynced) text += " sync";
            return text;
        }

        /** Supported and nothing runs for it, which {@code sync-examples} would not change: a case to write. */
        boolean gap() {
            return supported && api == 0 && ui == 0 && !unsynced;
        }
    }

    private Coverage() {}

    static int run() {
        Path project = ProjectPaths.projectRoot();
        Path features = project.resolve("src/test/resources/features");
        ProductExamples.Plan plan = ProductExamples.plan(features, ProductExamples::registryProducts);
        List<String> products = Arrays.stream(ProductDatFiles.values()).map(Enum::name).toList();
        BiPredicate<String, Capability> supports =
                (product, capability) -> ProductDatFiles.valueOf(product).capabilities().contains(capability);
        System.out.print(render(plan.entries(), wipOutlines(features), products, supports));
        if (!plan.errors().isEmpty()) {
            System.out.println();
            System.out.println(plan.errors().size() + " Outline(s) not counted, the check reports them:");
            plan.errors().forEach(e -> System.out.println("  " + e));
        }
        return 0;
    }

    /** The cell of {@code capability} and {@code product}. */
    static Cell cell(List<ProductExamples.Entry> entries, Set<String> wip, Capability capability, String product,
                     boolean supported) {
        int api = 0;
        int ui = 0;
        int wipCount = 0;
        boolean unsynced = false;
        for (ProductExamples.Entry entry : entries) {
            if (entry.capability() != capability) continue;
            if (!entry.before().contains(product)) {
                unsynced |= supported;
                continue;
            }
            if (wip.contains(key(entry.file(), entry.rowId()))) wipCount++;
            else if (entry.file().startsWith("api/")) api++;
            else ui++;
        }
        return new Cell(supported, api, ui, wipCount, unsynced);
    }

    /**
     * The matrix, its legend and the gaps: the capabilities a product supports with no case at all. Each group of
     * {@link Capability#GROUPS} is a section headed by its title, its capabilities in declaration order.
     */
    static String render(List<ProductExamples.Entry> entries, Set<String> wip, List<String> products,
                         BiPredicate<String, Capability> supports) {
        List<List<String>> rows = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> unsynced = new ArrayList<>();
        for (Map.Entry<String, Set<Capability>> group : Capability.GROUPS.entrySet()) {
            if (!rows.isEmpty()) rows.add(null);
            List<String> header = new ArrayList<>();
            header.add(group.getKey());
            header.addAll(rows.isEmpty() ? products : products.stream().map(p -> "").toList());
            rows.add(header);
            for (Capability capability : Capability.values()) {
                if (!group.getValue().contains(capability)) continue;
                List<String> row = new ArrayList<>();
                row.add(capability.tagName());
                for (String product : products) {
                    Cell cell = cell(entries, wip, capability, product, supports.test(product, capability));
                    row.add(cell.text());
                    if (cell.gap()) gaps.add(product + ": " + capability.tagName());
                    if (cell.unsynced()) unsynced.add(product + ": " + capability.tagName());
                }
                rows.add(row);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("Coverage: which layers test each capability for each product\n\n");
        out.append(table(rows)).append('\n');
        out.append("  API+UI  tested by API cases and by UI cases\n");
        out.append("  API     tested by API cases only\n");
        out.append("  UI      tested by UI cases only\n");
        out.append("  ").append(NO_CASE).append("    supported, but no case tests it: write one\n");
        out.append("  ").append(NOT_SUPPORTED).append("       the product does not support it (ProductDatFiles)\n");
        out.append("  +wip    more cases of it carry @wip and do not run\n");
        out.append("  sync    a case of it has no row for the product yet: run sync-examples\n");
        out.append('\n');
        out.append(gaps.isEmpty() ? "No gap.\n" : gaps.size() + " gap(s), supported with no case to run, write one:\n");
        gaps.forEach(g -> out.append("  ").append(g).append('\n'));
        if (!unsynced.isEmpty()) {
            out.append(unsynced.size() + " cell(s) not synced, run sync-examples:\n");
            unsynced.forEach(u -> out.append("  ").append(u).append('\n'));
        }
        return out.toString();
    }

    /** The rows as text columns, each padded to its widest cell; a null row is a blank line. */
    private static String table(List<List<String>> rows) {
        int columns = rows.getFirst().size();
        int[] width = new int[columns];
        for (List<String> row : rows) {
            if (row == null) continue;
            for (int i = 0; i < columns; i++) width[i] = Math.max(width[i], row.get(i).length());
        }
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            if (row == null) {
                out.append('\n');
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < columns; i++) {
                String cell = row.get(i);
                line.append(cell).append(" ".repeat(width[i] - cell.length() + 2));
            }
            out.append(line.toString().stripTrailing()).append('\n');
        }
        return out.toString();
    }

    /** The product Outlines that carry {@code @wip}, themselves or through their Feature or Rule, by {@link #key}. */
    static Set<String> wipOutlines(Path featuresRoot) {
        Set<String> wip = new HashSet<>();
        for (String channelDir : List.of("api", "ui")) {
            Path dir = featuresRoot.resolve(channelDir);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path path : walk.filter(p -> p.getFileName().toString().endsWith(".feature")).sorted().toList()) {
                    String file = featuresRoot.relativize(path).toString().replace('\\', '/');
                    wip.addAll(wipOutlines(file, FeatureFile.read(path)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("[Coverage] cannot list " + dir, e);
            }
        }
        return wip;
    }

    /** The Outlines of {@code feature} that carry {@code @wip}, tags inherited as Cucumber does. */
    static Set<String> wipOutlines(String file, FeatureFile feature) {
        Set<String> wip = new HashSet<>();
        boolean featureWip = false;
        boolean ruleWip = false;
        for (FeatureFile.Block block : feature.blocks()) {
            boolean tagged = block.tags().contains(WIP_TAG);
            if (block.kind() == FeatureFile.Kind.FEATURE) featureWip = tagged;
            if (block.kind() == FeatureFile.Kind.RULE) ruleWip = tagged;
            if (block.kind() == FeatureFile.Kind.OUTLINE && block.rowId() != null
                    && (featureWip || ruleWip || tagged)) {
                wip.add(key(file, block.rowId()));
            }
        }
        return wip;
    }

    private static String key(String file, String rowId) {
        return file + "|" + rowId;
    }
}
