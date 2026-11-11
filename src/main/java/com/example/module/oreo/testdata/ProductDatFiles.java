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
 * The composition registry: each composition the suite covers, registered once. A constant is named as the trade's
 * composition ID ({@code basic.compositionId}) and gives the product IDs the cases may send for it
 * ({@code basic.productId}; a product ID may serve several compositions and a composition have several product IDs:
 * only those a case sends are registered, the first is the default) and the capabilities it supports. Feature files
 * name only the composition (the {@code <product_type>} of the {@code Examples} rows); the product ID is read from
 * here by the requests and by the report line of each case. A case sends another registered product ID with
 * {@code productId} in its test data ({@link #productId(String)}).
 *
 * <p>Its dat files are in one folder per composition, {@code src/test/resources/data/dat/<C>/}: {@code <C>.dat} is the
 * default one, the others are variants (same composition, another structure) that a case chooses with {@code datFile}
 * in its test data.
 *
 * <h3>Adding a composition</h3>
 * <ol>
 *   <li>Add its default dat file as {@code src/test/resources/data/dat/<C>/<C>.dat}</li>
 *   <li>Add a new enum constant here, named as the composition ID: {@code productIds(<default>, <others>...)},
 *       then one entry per group of {@link Capability#GROUPS} it has: the trade lifecycle
 *       {@link Capability#SUPPORT_CAPABILITY} and the product features {@link Capability#PRODUCT_FEATURES}, each
 *       whole, {@code except(<group>, ...)} or {@code only(...)}; a group it has none of is left out (the order of
 *       the constants is the order of the {@code Examples} rows; never put one first)</li>
 *   <li>Run {@code TestDataTool sync-examples} (skill {@code maintaining-e2e-test-data}): it writes the composition
 *       into the {@code Examples} of every Outline tagged with one of its capabilities</li>
 *   <li>Review {@code git diff}</li>
 * </ol>
 */
public enum ProductDatFiles {

    /** FX Total Return Forward. */
    FX_TRF(productIds("FX_TRF"), SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX Correlation Option. */
    FX_CO(productIds("FX_PSCRIPT"), SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX Floating Strike Barrier. */
    FX_FSB(productIds("FX_PSCRIPT_FSKO"), SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX TRF on the payoff script. */
    FX_PSCRIPT_TRF(productIds("FX_PSCRIPT_TRF"), SUPPORT_CAPABILITY, PRODUCT_FEATURES);

    /** Placeholder for FX Option: add data/dat/FX_OPT/FX_OPT.dat when its template is available. Supports nothing. */
//    FX_OPT(productIds("FX_OPT"), except(SUPPORT_CAPABILITY, ALLOCATION, PORTFOLIO_REASSIGNMENT),
//            only(PRODUCT_UPLOAD));

    /** The classpath folder of the dat files, one folder per composition. */
    public static final String DAT_DIR = "data/dat/";
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*\\.dat");

    private final List<String> productIds;
    private final Set<Capability> capabilities;

    /**
     * {@code productIds}: the product IDs the cases may send, the default first. The capabilities of
     * {@code groups}: each a group of {@link Capability#GROUPS} ({@code SUPPORT_CAPABILITY},
     * {@code PRODUCT_FEATURES}), or part of one ({@code except(...)}, {@code only(...)}).
     */
    @SafeVarargs
    ProductDatFiles(List<String> productIds, Set<Capability>... groups) {
        this.productIds = productIds;
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

    /** The product IDs of a constant: {@code defaultId} first, then the others a case may send. */
    private static List<String> productIds(String defaultId, String... others) {
        List<String> ids = new ArrayList<>();
        ids.add(defaultId);
        ids.addAll(Arrays.asList(others));
        return List.copyOf(ids);
    }

    /** The product IDs registered for this composition, the default first (unmodifiable). */
    public List<String> productIds() {
        return productIds;
    }

    /** The product ID the cases send for this composition by default ({@code basic.productId}). */
    public String productId() {
        return productIds.get(0);
    }

    /**
     * The product ID a case sends: {@code fromCase} ({@code productId} of its test data, the context key
     * {@code _tc.productId}) when given, else the default {@link #productId()}.
     *
     * @throws AssertionError when {@code fromCase} is not registered for this composition
     */
    public String productId(String fromCase) {
        if (fromCase == null || fromCase.isBlank()) return productId();
        if (!productIds.contains(fromCase)) {
            throw new AssertionError("Product ID '" + fromCase + "' is not registered for composition " + name()
                    + " " + productIds + ": add it to its productIds(...) in ProductDatFiles first.");
        }
        return fromCase;
    }

    /** The report line of a case: {@code composition FX_CO, product ID FX_PSCRIPT}, {@link #productId(String)}. */
    public String describe(String fromCase) {
        return "composition " + name() + ", product ID " + productId(fromCase);
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
