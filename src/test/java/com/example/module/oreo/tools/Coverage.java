package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.Capability;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.testdata.ProjectPaths;
import com.example.module.oreo.testdata.SharedFragments;
import com.example.module.oreo.testdata.TestDataRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The coverage matrix ({@code coverage}): one row per capability, by group of {@link Capability#GROUPS} (trade
 * lifecycle, product features), one column per product of the registry, each cell
 * the number of product Outlines that run for that product, {@code API/UI}. It reads the registry
 * ({@link ProductDatFiles}) and the product Outlines ({@code @capability#} tags, {@link ProductExamples}); it writes
 * nothing and never fails. A Scenario with a fixed product is not counted: only the product Outlines say which
 * capability they test. Whether a case has its data and its recording is the check's business, not this report's.
 * The rows can be the compositions (the default), the product IDs the cases send with their composition, or the trade
 * types ({@link View}, {@code --by}). {@code coverage --html} also writes the matrix as a page and its data as JSON
 * ({@link CoverageHtml}) under {@code target/coverage}, every view in it.
 */
final class Coverage {

    static final String NOT_SUPPORTED = "-";
    static final String NO_CASE = "NONE";
    static final String OUTPUT_DIR = "target/coverage";
    /** The trade type of a composition whose trade creation has no recording yet. */
    static final String NO_TRADE_TYPE = "(not recorded)";
    private static final String WIP_TAG = "@wip";

    /**
     * The ways to look at the matrix, by row: each composition of the registry; each product ID a case sends, with
     * its composition ({@code FX_PSCRIPT / FX_CO}: a product ID many compositions send has a row for each); each trade
     * type, the {@code tradeType} of the composition's recorded trade ({@code expected/_shared/basic/<C>.json}),
     * gathering its compositions.
     */
    enum View {
        COMPOSITION("composition", "Composition", "composition"),
        PRODUCT_ID("productId", "Product ID / composition", "product ID"),
        TRADE_TYPE("tradeType", "Trade type", "trade type");

        final String key;
        final String title;
        final String noun;

        View(String key, String title, String noun) {
            this.key = key;
            this.title = title;
            this.noun = noun;
        }

        /** The view named {@code key} ({@code composition}, {@code productId}, {@code tradeType}). */
        static View of(String key) {
            for (View view : values()) {
                if (view.key.equalsIgnoreCase(key)) return view;
            }
            throw new IllegalArgumentException("[Coverage] no view '" + key + "': --by composition, productId or"
                    + " tradeType");
        }
    }

    /**
     * A composition in a row. {@code productId}: only its cases that send this product ID ({@code null}: all of them).
     * {@code owns}: the row answers for what the composition supports, its gaps and its rows to sync; a row of a
     * product ID only some cases send does not, it just shows them.
     */
    record Member(String composition, String productId, boolean owns) {}

    /** A row of a view: its label and the compositions it gathers. */
    record Row(String label, List<Member> members) {

        List<String> compositions() {
            return members.stream().map(Member::composition).distinct().toList();
        }
    }

    /** A case of a cell: one Outline row run for one composition, with the product ID it sends. */
    record CaseRef(String id, String file, boolean api, boolean wip, String composition, String productId) {}

    /** The cell of a row: its cases, the Outline rows sync-examples would add, and whether it is supported. */
    record RowCell(boolean supported, List<CaseRef> cases, List<String> toSync) {

        Cell counts() {
            int api = 0;
            int ui = 0;
            int wip = 0;
            for (CaseRef c : cases) {
                if (c.wip()) wip++;
                else if (c.api()) api++;
                else ui++;
            }
            return new Cell(supported, api, ui, wip, !toSync.isEmpty());
        }
    }

    /**
     * What the views need beside the Outlines: what a composition supports, the product ID a case sends
     * ({@code (case ID, composition)}), a composition's default product ID and its trade type.
     */
    record Facts(BiPredicate<String, Capability> supports, BiFunction<String, String, String> productIdOf,
                 Function<String, String> defaultProductId, Function<String, String> tradeTypeOf) {

        /** Composition rows only: no product ID or trade type is asked for. */
        static Facts supporting(BiPredicate<String, Capability> supports) {
            return new Facts(supports, (id, composition) -> null, composition -> null, composition -> null);
        }
    }

    /**
     * One cell: the Outlines of the capability that run for the product, by layer; {@code wip} more that carry
     * {@code @wip}; {@code unsynced} when an Outline of the capability has no row for the product yet.
     */
    record Cell(boolean supported, int api, int ui, int wip, boolean unsynced) {

        String text() {
            if (!supported) return NOT_SUPPORTED;
            String text = api == 0 && ui == 0 ? NO_CASE : api + "/" + ui;
            if (wip > 0) text += " +" + wip + "wip";
            if (unsynced) text += " *";
            return text;
        }

        /** Supported and nothing runs for it, which {@code sync-examples} would not change: a case to write. */
        boolean gap() {
            return supported && api == 0 && ui == 0 && !unsynced;
        }
    }

    private Coverage() {}

    static int run(boolean html, View view) throws IOException {
        Path project = ProjectPaths.projectRoot();
        TestDataTool.requireFreshData(project);
        Path features = project.resolve("src/test/resources/features");
        ProductExamples.Plan plan = ProductExamples.plan(features, ProductExamples::registryProducts);
        List<String> products = Arrays.stream(ProductDatFiles.values()).map(Enum::name).toList();
        Facts facts = registryFacts(project);
        Set<String> wip = wipOutlines(features);
        System.out.print(render(view, rows(view, products, plan.entries(), facts), plan.entries(), wip, facts));
        if (!plan.errors().isEmpty()) {
            System.out.println();
            System.out.println(plan.errors().size() + " Outline(s) not counted, the check reports them:");
            plan.errors().forEach(e -> System.out.println("  " + e));
        }
        if (html) writePage(project, plan.entries(), wip, products, facts);
        return 0;
    }

    /**
     * The facts of the registry: what each composition supports and its default product ID ({@link ProductDatFiles}),
     * the product ID of each case (its test data, {@code productId}; a case whose data does not load counts with the
     * default, the check reports it) and the trade type of each composition (its recorded trade's {@code basic}).
     */
    static Facts registryFacts(Path project) {
        TestDataRepository repo = new TestDataRepository("data/");
        SharedFragments recorded = new SharedFragments(project.resolve("src/test/resources/data/api/expected"));
        Map<String, String> sent = new HashMap<>();
        BiFunction<String, String, String> productIdOf = (id, composition) -> sent.computeIfAbsent(id, key -> {
            String fromCase = null;
            try {
                fromCase = repo.forCase(key).productId();
            } catch (RuntimeException | AssertionError e) {
                // the check reports it
            }
            return ProductDatFiles.valueOf(composition).productId(fromCase);
        });
        Function<String, String> tradeTypeOf = composition -> {
            try {
                return recorded.read("basic/" + composition)
                        .map(basic -> basic.path("tradeType").asText(""))
                        .filter(type -> !type.isBlank())
                        .orElse(NO_TRADE_TYPE);
            } catch (RuntimeException e) {
                return NO_TRADE_TYPE;
            }
        };
        return new Facts((product, capability) -> ProductDatFiles.valueOf(product).capabilities().contains(capability),
                productIdOf, composition -> ProductDatFiles.valueOf(composition).productId(), tradeTypeOf);
    }

    /**
     * The rows of {@code view} over {@code compositions}, in registry order: a composition's rows of product IDs
     * after one another, its default product ID first; a trade type at the place of its first composition.
     */
    static List<Row> rows(View view, List<String> compositions, List<ProductExamples.Entry> entries, Facts facts) {
        List<Row> rows = new ArrayList<>();
        switch (view) {
            case COMPOSITION -> compositions.forEach(c -> rows.add(new Row(c, List.of(new Member(c, null, true)))));
            case PRODUCT_ID -> {
                for (String c : compositions) {
                    String defaultId = facts.defaultProductId().apply(c);
                    rows.add(new Row(defaultId + " / " + c, List.of(new Member(c, defaultId, true))));
                    Set<String> others = new TreeSet<>();
                    for (ProductExamples.Entry entry : entries) {
                        if (!entry.before().contains(c)) continue;
                        String sent = facts.productIdOf().apply(caseId(entry, c), c);
                        if (sent != null && !sent.equals(defaultId)) others.add(sent);
                    }
                    others.forEach(id -> rows.add(new Row(id + " / " + c, List.of(new Member(c, id, false)))));
                }
            }
            case TRADE_TYPE -> {
                Map<String, List<Member>> byType = new LinkedHashMap<>();
                for (String c : compositions) {
                    byType.computeIfAbsent(facts.tradeTypeOf().apply(c), t -> new ArrayList<>())
                            .add(new Member(c, null, true));
                }
                byType.forEach((type, members) -> rows.add(new Row(type, List.copyOf(members))));
            }
        }
        return rows;
    }

    /** The case ID of an Outline row for {@code composition}. */
    static String caseId(ProductExamples.Entry entry, String composition) {
        return entry.rowId().replace("<product_type>", composition);
    }

    /**
     * The cell of {@code row} and {@code capability}: the cases of its compositions (those that send the member's
     * product ID), supported when a composition the row owns supports it, or when it shows cases of a product ID
     * only some cases send.
     */
    static RowCell cell(Row row, Capability capability, List<ProductExamples.Entry> entries, Set<String> wip,
                        Facts facts) {
        List<CaseRef> cases = new ArrayList<>();
        List<String> toSync = new ArrayList<>();
        boolean supported = false;
        for (Member member : row.members()) {
            boolean supports = member.owns() && facts.supports().test(member.composition(), capability);
            supported |= supports;
            for (ProductExamples.Entry entry : entries) {
                if (entry.capability() != capability) continue;
                String id = caseId(entry, member.composition());
                if (!entry.before().contains(member.composition())) {
                    if (supports) toSync.add(id);
                    continue;
                }
                String productId = facts.productIdOf().apply(id, member.composition());
                if (member.productId() != null && !member.productId().equals(productId)) continue;
                cases.add(new CaseRef(id, entry.file(), entry.file().startsWith("api/"),
                        wip.contains(key(entry.file(), entry.rowId())), member.composition(), productId));
            }
        }
        if (!cases.isEmpty() && row.members().stream().noneMatch(Member::owns)) supported = true;
        return new RowCell(supported, cases, toSync);
    }

    /** {@code target/coverage/coverage.html} and {@code coverage.json}: the matrix of every group, with its cases. */
    private static void writePage(Path project, List<ProductExamples.Entry> entries, Set<String> wip,
                                  List<String> products, Facts facts) {
        String generated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
                .format(OffsetDateTime.now(ZoneOffset.UTC));
        String json = CoverageHtml.json(CoverageHtml.model(entries, wip, products, facts, generated));
        Path templateFile = project.resolve(CoverageHtml.TEMPLATE);
        if (!Files.isRegularFile(templateFile)) {
            throw new IllegalStateException("[Coverage] no page template " + CoverageHtml.TEMPLATE + " in " + project
                    + ": --html fills it with the matrix; it is part of the repository, copy it from there");
        }
        Path dir = project.resolve(OUTPUT_DIR);
        String template;
        try {
            template = Files.readString(templateFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("[Coverage] cannot read the page template " + templateFile, e);
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("coverage.json"), json, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("coverage.html"), CoverageHtml.page(template, json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("[Coverage] cannot write " + dir, e);
        }
        System.out.println();
        System.out.println("Wrote " + OUTPUT_DIR + "/coverage.html (open it in a browser) and " + OUTPUT_DIR
                + "/coverage.json");
    }

    /** The cell of {@code capability} and the composition {@code product}. */
    static Cell cell(List<ProductExamples.Entry> entries, Set<String> wip, Capability capability, String product,
                     boolean supported) {
        Row row = new Row(product, List.of(new Member(product, null, true)));
        return cell(row, capability, entries, wip, Facts.supporting((p, c) -> supported)).counts();
    }

    /** The matrix of the compositions {@code products}, as {@link #render(View, List, List, Set, Facts)}. */
    static String render(List<ProductExamples.Entry> entries, Set<String> wip, List<String> products,
                         BiPredicate<String, Capability> supports) {
        Facts facts = Facts.supporting(supports);
        return render(View.COMPOSITION, rows(View.COMPOSITION, products, entries, facts), entries, wip, facts);
    }

    /**
     * The matrix, its legend and the gaps: the capabilities a row supports with no case at all. Each group of
     * {@link Capability#GROUPS} is a section headed by its title, its capabilities in declaration order.
     */
    static String render(View view, List<Row> viewRows, List<ProductExamples.Entry> entries, Set<String> wip,
                         Facts facts) {
        List<String> labels = viewRows.stream().map(Row::label).toList();
        List<List<String>> rows = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> unsynced = new ArrayList<>();
        for (Map.Entry<String, Set<Capability>> group : Capability.GROUPS.entrySet()) {
            if (!rows.isEmpty()) rows.add(null);
            List<String> header = new ArrayList<>();
            header.add(group.getKey());
            header.addAll(rows.isEmpty() ? labels : labels.stream().map(p -> "").toList());
            rows.add(header);
            for (Capability capability : Capability.values()) {
                if (!group.getValue().contains(capability)) continue;
                List<String> row = new ArrayList<>();
                row.add(capability.tagName());
                for (Row viewRow : viewRows) {
                    Cell cell = cell(viewRow, capability, entries, wip, facts).counts();
                    row.add(cell.text());
                    if (cell.gap()) gaps.add(viewRow.label() + ": " + capability.tagName());
                    if (cell.unsynced()) unsynced.add(viewRow.label() + ": " + capability.tagName());
                }
                rows.add(row);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("Coverage: ").append(view.noun).append(" x capability");
        if (view == View.TRADE_TYPE && viewRows.stream().anyMatch(r -> !r.compositions().equals(List.of(r.label())))) {
            out.append(" (").append(view.title).append(": ")
                    .append(String.join(", ", viewRows.stream()
                            .filter(r -> !r.compositions().equals(List.of(r.label())))
                            .map(r -> r.label() + " = " + String.join(" + ", r.compositions())).toList()))
                    .append(')');
        }
        out.append('\n');
        out.append("Each cell is API/UI: the number of API cases / the number of UI cases that test the capability"
                + " for the ").append(view.noun).append(".\n");
        out.append("  e.g. 4/5 = 4 API cases and 5 UI cases, 1/0 = API cases only, 0/1 = UI cases only\n\n");
        out.append(table(rows)).append('\n');
        out.append("  a/u    a API cases and u UI cases run for it (not @wip)\n");
        out.append("  ").append(NO_CASE).append("   supported and no Outline runs for it\n");
        out.append("  ").append(NOT_SUPPORTED).append("      not supported (ProductDatFiles)\n");
        out.append("  +Nwip  N more Outlines carry @wip\n");
        out.append("  *      an Outline of it has no row for the composition: run sync-examples\n");
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
