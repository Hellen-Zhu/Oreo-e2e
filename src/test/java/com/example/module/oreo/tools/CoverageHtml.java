package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The coverage matrix as a page ({@code coverage --html}): one tab per group of {@link Capability#GROUPS}, one row
 * per product, one column per capability, each cell the layers that test it. The page is the template
 * {@code src/test/resources/tools/coverage-report.html} with the data of {@link #model} in it as JSON: one file,
 * nothing loaded from elsewhere, so it opens offline. The same JSON is written beside it for other tools to read.
 */
final class CoverageHtml {

    static final String TEMPLATE = "src/test/resources/tools/coverage-report.html";
    static final String DATA_MARK = "@@COVERAGE_DATA@@";

    private static final Set<String> SUFFIX_VARIANTS = Set.of("full", "partial");
    private static final Set<String> PREFIX_VARIANTS = Set.of("early", "partial");

    /** A column: the capability and its header, a family ("Step in") and a variant ("Full"), blank when alone. */
    record Column(Capability capability, String family, String variant) {}

    private CoverageHtml() {}

    /**
     * The columns of {@code group}: the capabilities of one family side by side, families in the order of their first
     * capability in {@link Capability}. In a family of more than one, a capability without a variant word is "Full"
     * ({@code novationRemaining} beside {@code partialNovationRemaining}).
     */
    static List<Column> columns(Set<Capability> group) {
        Map<String, List<Capability>> families = new LinkedHashMap<>();
        Map<Capability, String> variants = new LinkedHashMap<>();
        for (Capability capability : Capability.values()) {
            if (!group.contains(capability)) continue;
            String[] label = label(capability.tagName());
            families.computeIfAbsent(label[0], f -> new ArrayList<>()).add(capability);
            variants.put(capability, label[1]);
        }
        List<Column> columns = new ArrayList<>();
        families.forEach((family, members) -> {
            for (Capability capability : members) {
                String variant = variants.get(capability);
                if (variant.isEmpty() && members.size() > 1) variant = "Full";
                columns.add(new Column(capability, family, variant));
            }
        });
        return columns;
    }

    /**
     * A tag name as {family, variant}: {@code stepInFull} is {"Step in", "Full"}, {@code earlyTermination}
     * {"Termination", "Early"}, {@code cortexFormula} {"Cortex formula", ""}.
     */
    static String[] label(String tagName) {
        List<String> words = new ArrayList<>(Arrays.asList(tagName.split("(?=[A-Z])")));
        words.replaceAll(w -> w.toLowerCase(Locale.ROOT));
        String variant = "";
        if (words.size() > 1 && SUFFIX_VARIANTS.contains(words.get(words.size() - 1))) {
            variant = words.remove(words.size() - 1);
        } else if (words.size() > 1 && PREFIX_VARIANTS.contains(words.get(0))) {
            variant = words.remove(0);
        }
        return new String[] {capitalize(String.join(" ", words)), capitalize(variant)};
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * The data of the page: per group its columns and, per product, one cell per column with its counts, whether it is
     * a gap, the cases that run for it and those {@code sync-examples} would add.
     */
    static Map<String, Object> model(List<ProductExamples.Entry> entries, Set<String> wip, List<String> products,
                                     BiPredicate<String, Capability> supports, String generated) {
        List<Object> groups = new ArrayList<>();
        for (Map.Entry<String, Set<Capability>> group : Capability.GROUPS.entrySet()) {
            List<Column> columns = columns(group.getValue());
            List<Object> header = new ArrayList<>();
            for (Column column : columns) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("tag", column.capability().tagName());
                h.put("family", column.family());
                h.put("variant", column.variant());
                header.add(h);
            }
            List<Object> rows = new ArrayList<>();
            for (String product : products) {
                List<Object> cells = new ArrayList<>();
                for (Column column : columns) {
                    cells.add(cell(entries, wip, column.capability(), product,
                            supports.test(product, column.capability())));
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("product", product);
                row.put("cells", cells);
                rows.add(row);
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("title", group.getKey());
            g.put("columns", header);
            g.put("rows", rows);
            groups.add(g);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("generated", generated);
        model.put("products", products);
        model.put("groups", groups);
        return model;
    }

    private static Map<String, Object> cell(List<ProductExamples.Entry> entries, Set<String> wip,
                                            Capability capability, String product, boolean supported) {
        Coverage.Cell counts = Coverage.cell(entries, wip, capability, product, supported);
        List<Object> cases = new ArrayList<>();
        List<String> toSync = new ArrayList<>();
        for (ProductExamples.Entry entry : entries) {
            if (entry.capability() != capability) continue;
            String id = entry.rowId().replace("<product_type>", product);
            if (!entry.before().contains(product)) {
                if (supported) toSync.add(id);
                continue;
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", id);
            c.put("layer", entry.file().startsWith("api/") ? "API" : "UI");
            c.put("file", entry.file());
            c.put("wip", wip.contains(entry.file() + "|" + entry.rowId()));
            cases.add(c);
        }
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("supported", supported);
        cell.put("api", counts.api());
        cell.put("ui", counts.ui());
        cell.put("wip", counts.wip());
        cell.put("gap", counts.gap());
        cell.put("cases", cases);
        cell.put("toSync", toSync);
        return cell;
    }

    /** The model as JSON. */
    static String json(Map<String, Object> model) {
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("[Coverage] cannot write the coverage data: " + e.getMessage(), e);
        }
    }

    /**
     * The page: {@code template} with the JSON in place of {@link #DATA_MARK}. Every {@code <} of the JSON is written
     * {@code <}, so no value can end the script element that holds it.
     */
    static String page(String template, String json) {
        if (!template.contains(DATA_MARK)) {
            throw new IllegalStateException("[Coverage] " + TEMPLATE + " has no " + DATA_MARK);
        }
        return template.replace(DATA_MARK, json.replace("<", "\\u003c"));
    }
}
