package com.example.module.oreo.tools;

import com.example.module.oreo.testdata.ExpectedResponses;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The case IDs of the feature files: {@code <featuresRoot>/<api|ui>/**}{@code /*.feature}. A scenario's ID is the
 * first {@code [TC-...]} in its name. A Scenario Outline's ID has one placeholder: {@code <product_type>}, expanded
 * over the {@code product_type} column of every {@code Examples} table, or a column for the case number at its end
 * ({@code TC-REFDATA-SYNC-MANAGEMENT-API-<case>}), expanded over every Examples row as Cucumber names the scenarios.
 */
final class FeatureCases {

    static final String PRODUCT_PLACEHOLDER = "<product_type>";

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");
    private static final Pattern NUMBER_PLACEHOLDER = Pattern.compile("-(API|UI)-<([^>]+)>$");

    /**
     * @param caseId      the expanded case ID, e.g. {@code TC-TRADE-CREATION-FX_TRF-API-001}
     * @param rowId       the ID of the case's row in the event file: for a product Outline the ID as written, one row
     *                    for every product ({@code TC-TRADE-CREATION-<product_type>-API-001}), else the case ID
     * @param channelDir  {@code api} or {@code ui}: the folder under the features root
     * @param featureName the file name without {@code .feature}
     * @param title       the scenario name after the ID; in an Outline numbered by a column, with the row's values
     * @param noExpected  the case or its Feature or Rule carries {@link ExpectedResponses#NONE_TAG}
     */
    record FeatureCase(String caseId, String rowId, String channelDir, String featureName, String title,
                       boolean noExpected) {}

    private FeatureCases() {}

    static List<FeatureCase> load(Path featuresRoot) {
        List<FeatureCase> cases = new ArrayList<>();
        for (String channelDir : List.of("api", "ui")) {
            Path dir = featuresRoot.resolve(channelDir);
            if (!Files.isDirectory(dir)) continue;
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(p -> p.getFileName().toString().endsWith(".feature")).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException("[FeatureCases] cannot list " + dir, e);
            }
            for (Path file : files) {
                cases.addAll(cases(FeatureFile.read(file), channelDir));
            }
        }
        return cases;
    }

    static List<FeatureCase> cases(FeatureFile feature, String channelDir) {
        String featureName = feature.file().getFileName().toString().replaceFirst("\\.feature$", "");
        List<FeatureCase> cases = new ArrayList<>();
        // Tags inherited as Cucumber does: the Feature's, then the current Rule's
        boolean featureNone = false;
        boolean ruleNone = false;
        for (FeatureFile.Block block : feature.blocks()) {
            boolean none = block.tags().contains(ExpectedResponses.NONE_TAG);
            if (block.kind() == FeatureFile.Kind.FEATURE) featureNone = none;
            if (block.kind() == FeatureFile.Kind.RULE) ruleNone = none;
            boolean outline = block.kind() == FeatureFile.Kind.OUTLINE;
            if (block.rowId() == null || !(outline || block.kind() == FeatureFile.Kind.SCENARIO)) continue;
            boolean noExpected = featureNone || ruleNone || none;
            String rowId = block.rowId();
            String title = title(feature, block);
            String where = "[FeatureCases] " + feature.file() + ": " + rowId;
            if (block.examples().stream().anyMatch(t -> t.tags().contains(ExpectedResponses.NONE_TAG))) {
                throw new IllegalStateException(where + ": " + ExpectedResponses.NONE_TAG + " on an Examples table;"
                        + " put it on the Scenario Outline line (all its cases, or none, have expected files)");
            }
            if (noExpected && !channelDir.equals("api")) {
                throw new IllegalStateException(where + ": " + ExpectedResponses.NONE_TAG + " is for API cases;"
                        + " a UI case has no expected file anyway");
            }
            long placeholders = PLACEHOLDER.matcher(rowId).results().count();
            if (placeholders == 0) {
                cases.add(new FeatureCase(rowId, rowId, channelDir, featureName, title, noExpected));
            } else if (!outline) {
                throw new IllegalStateException(where + " has a placeholder, which only a Scenario Outline fills");
            } else if (rowId.contains(PRODUCT_PLACEHOLDER)) {
                if (placeholders > 1) {
                    throw new IllegalStateException(where + " has a placeholder other than " + PRODUCT_PLACEHOLDER
                            + "; an ID has one placeholder");
                }
                List<String> products = block.examples().stream()
                        .flatMap(t -> t.column("product_type").stream()).toList();
                if (products.isEmpty()) {
                    throw new IllegalStateException(where + " has no Examples column product_type to expand "
                            + PRODUCT_PLACEHOLDER);
                }
                Set<String> ids = new LinkedHashSet<>();
                products.forEach(p -> ids.add(rowId.replace(PRODUCT_PLACEHOLDER, p)));
                ids.forEach(id -> cases.add(new FeatureCase(id, rowId, channelDir, featureName, title, noExpected)));
            } else {
                cases.addAll(numbered(where, block, title, channelDir, featureName, noExpected));
            }
        }
        return cases;
    }

    /** An Outline whose ID ends with a column for the case number: one case per Examples row, each its own row. */
    private static List<FeatureCase> numbered(String where, FeatureFile.Block block, String title, String channelDir,
                                              String featureName, boolean noExpected) {
        String rowId = block.rowId();
        Matcher number = NUMBER_PLACEHOLDER.matcher(rowId);
        if (PLACEHOLDER.matcher(rowId).results().count() > 1 || !number.find()) {
            throw new IllegalStateException(where + " has a placeholder other than " + PRODUCT_PLACEHOLDER
                    + " that is not the case number at the end: write TC-<EVENT>-<API|UI>-<case> with a column case"
                    + " of 001, 002 ..., the other columns in the scenario name and in the case data");
        }
        String column = number.group(2);
        List<FeatureCase> cases = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (FeatureFile.Table table : block.examples()) {
            if (!table.header().contains(column)) {
                throw new IllegalStateException(where + ": an Examples table of it has no column " + column);
            }
            for (List<String> row : table.rows()) {
                String id = fill(rowId, table.header(), row);
                if (!ids.add(id)) {
                    throw new IllegalStateException(where + ": two Examples rows give the case " + id);
                }
                cases.add(new FeatureCase(id, id, channelDir, featureName, fill(title, table.header(), row),
                        noExpected));
            }
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException(where + " has no Examples row to expand <" + column + ">");
        }
        return cases;
    }

    /** {@code text} with every {@code <column>} of {@code header} replaced by the row's value, as Cucumber does. */
    private static String fill(String text, List<String> header, List<String> row) {
        String out = text;
        for (int i = 0; i < header.size() && i < row.size(); i++) {
            out = out.replace("<" + header.get(i) + ">", row.get(i));
        }
        return out;
    }

    /** The scenario line's text after {@code [<row ID>]}, trimmed. */
    private static String title(FeatureFile feature, FeatureFile.Block block) {
        String line = feature.lines().get(block.line());
        int at = line.indexOf("[" + block.rowId() + "]");
        return at < 0 ? "" : line.substring(at + block.rowId().length() + 2).trim();
    }
}
