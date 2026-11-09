package com.example.module.oreo.testdata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.example.module.oreo.testdata.Capability.*;

/**
 * The composition registry: each trade type the suite covers, registered once. A constant is named as the trade's
 * composition ID ({@code basic.compositionId}, one per trade type) and gives the end of its trade type (the part after
 * {@code Tradeable.}, matched as the end of {@code basic.tradeType}), the one product ID the cases send for it
 * ({@code basic.productId}; a trade type may have several, one is enough), and the capabilities it supports. Feature
 * files name only the composition (the {@code <product_type>} of the {@code Examples} rows); the trade type and the
 * product ID are read from here: by the requests, by the check after every trade the suite creates
 * ({@code TradeApiClient}), by the report line of each case and by {@code TestDataTool check}.
 *
 * <p>Its dat files are in one folder per composition, {@code src/test/resources/data/dat/<C>/}: {@code <C>.dat} is the
 * default one, the others are variants (same trade type, another structure) that a case chooses with {@code datFile}
 * in its test data.
 *
 * <h3>Adding a composition</h3>
 * <ol>
 *   <li>Add its default dat file as {@code src/test/resources/data/dat/<C>/<C>.dat}</li>
 *   <li>Add a new enum constant here, named as the composition ID: its trade type (after {@code Tradeable.}), the
 *       product ID to send, then one entry per group of {@link Capability#GROUPS} it has: the trade lifecycle
 *       {@link Capability#SUPPORT_CAPABILITY} and the product features {@link Capability#PRODUCT_FEATURES}, each
 *       whole, {@code except(<group>, ...)} or {@code only(...)}; a group it has none of is left out (the order of the
 *       constants is the order of the {@code Examples} rows; never put one first)</li>
 *   <li>Run {@code TestDataTool sync-examples} (skill {@code maintaining-e2e-test-data}): it writes the composition
 *       into the {@code Examples} of every Outline tagged with one of its capabilities</li>
 *   <li>Review {@code git diff}</li>
 * </ol>
 */
public enum ProductDatFiles {

    /** FX Total Return Forward. */
    FX_TRF("TRF", "FX_TRF", SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX Correlation Option. */
    FX_CO("PayoffScript.Ctrl.CorrOption", "FX_PSCRIPT", SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX Floating Strike Barrier. */
    FX_FSB("PayoffScript.Ctrl.FloatingStrikeBarrier", "FX_PSCRIPT_FSKO", SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX TRF on the payoff script. */
    FX_PSCRIPT_TRF("PayoffScript.Ctrl.TRFGeneric", "FX_PSCRIPT_TRF", SUPPORT_CAPABILITY, PRODUCT_FEATURES);

    /** Placeholder for FX Option: add data/dat/FX_OPT/FX_OPT.dat when its template is available. Supports nothing. */
//    FX_OPT("<trade type>", "FX_OPT", except(SUPPORT_CAPABILITY, ALLOCATION, PORTFOLIO_REASSIGNMENT), only(PRODUCT_UPLOAD));

    /** The classpath folder of the dat files, one folder per composition. */
    public static final String DAT_DIR = "data/dat/";
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*\\.dat");

    private final String tradeType;
    private final String productId;
    private final Set<Capability> capabilities;

    /**
     * {@code tradeType}: the end of the trade type, after {@code Tradeable.}; {@code productId}: the product ID the
     * cases send. The capabilities of {@code groups}: each a group of {@link Capability#GROUPS} ({@code SUPPORT_CAPABILITY},
     * {@code PRODUCT_FEATURES}), or part of one ({@code except(...)}, {@code only(...)}).
     */
    @SafeVarargs
    ProductDatFiles(String tradeType, String productId, Set<Capability>... groups) {
        this.tradeType = tradeType;
        this.productId = productId;
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        for (Set<Capability> group : groups) set.addAll(group);
        this.capabilities = Collections.unmodifiableSet(set);
    }

    /**
     * The composition named {@code composition} (case-insensitive, spaces as underscores).
     *
     * @throws AssertionError naming the registry when it has no such composition
     */
    public static ProductDatFiles of(String composition) {
        String name = composition == null ? "" : composition.trim().toUpperCase().replace(' ', '_');
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Unknown composition '" + composition + "': register '" + name
                    + "' in ProductDatFiles first (skill onboarding-e2e-product).", e);
        }
    }

    /** The end of the trade type, after {@code Tradeable.}: {@code PayoffScript.Ctrl.CorrOption}. */
    public String tradeType() {
        return tradeType;
    }

    /** The product ID the cases send for this composition ({@code basic.productId}). */
    public String productId() {
        return productId;
    }

    /** Whether {@code fullTradeType} is this composition's trade type: equal to, or ending with {@code .<tradeType>}. */
    public boolean isTradeType(String fullTradeType) {
        return fullTradeType != null && (fullTradeType.equals(tradeType) || fullTradeType.endsWith("." + tradeType));
    }

    /** The report line of a case: {@code composition FX_CO: trade type PayoffScript.Ctrl.CorrOption, product ID FX_PSCRIPT}. */
    public String describe() {
        return "composition " + name() + ": trade type " + tradeType + ", product ID " + productId;
    }

    /**
     * The problems of a trade the backend returned for this composition: its {@code basic.compositionId} is not this
     * one, or its {@code basic.tradeType} not this one's trade type; empty when both agree. A trade without a
     * composition ID (a backend or recording older than composition IDs) has none: there is nothing to compare.
     */
    public List<String> mismatches(String compositionId, String fullTradeType) {
        if (compositionId == null || compositionId.isBlank()) return List.of();
        List<String> problems = new ArrayList<>();
        if (!compositionId.equals(name())) {
            problems.add("compositionId is '" + compositionId + "', not '" + name() + "'");
        }
        if (!isTradeType(fullTradeType)) {
            problems.add("tradeType '" + fullTradeType + "' does not end with '" + tradeType + "'");
        }
        return problems;
    }

    /** The name of the composition's default dat file, {@code <C>.dat}. */
    public String defaultDatFile() {
        return name() + ".dat";
    }

    /**
     * Classpath path of the composition's default dat file, {@code data/dat/<C>/<C>.dat}, suitable for
     * {@code getClassLoader().getResource(path)}.
     */
    public String path() {
        return path(null);
    }

    /**
     * Classpath path of a dat file of this composition, {@code data/dat/<C>/<datFile>}: a variant chosen by the case's
     * test data ({@code datFile}), or the default file when {@code datFile} is {@code null} or blank.
     */
    public String path(String datFile) {
        String file = datFile == null || datFile.isBlank() ? defaultDatFile() : datFile;
        checkFileName(file);
        return DAT_DIR + name() + "/" + file;
    }

    /** Fails with an {@link IllegalArgumentException} unless {@code datFile} is a file name ending in {@code .dat}. */
    public static void checkFileName(String datFile) {
        if (datFile == null || !FILE_NAME.matcher(datFile).matches()) {
            throw new IllegalArgumentException("'" + datFile + "' is not a dat file name: a file of the composition's"
                    + " folder " + DAT_DIR + "<composition>/, such as FX_TRF.dat, without a folder");
        }
    }

    /** The capabilities this composition supports (unmodifiable). */
    public Set<Capability> capabilities() {
        return capabilities;
    }

    /** The compositions that support {@code capability}, in registry order: the rows of its Outlines' {@code Examples}. */
    public static List<ProductDatFiles> with(Capability capability) {
        return Arrays.stream(values()).filter(p -> p.capabilities.contains(capability)).toList();
    }
}
