package com.example.module.oreo.testdata;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.example.module.oreo.testdata.Capability.*;

/**
 * The product registry: each product and the capabilities it supports. Its dat files are in one folder per product,
 * {@code src/test/resources/data/dat/<P>/}: {@code <P>.dat} is the default one, the others are variants of the
 * product (same product ID, another structure) that a case chooses with {@code datFile} in its test data.
 *
 * <h3>Adding a new product type</h3>
 * <ol>
 *   <li>Add its default dat file as {@code src/test/resources/data/dat/<P>/<P>.dat}</li>
 *   <li>Add a new enum constant here, named as the product ID, with one entry per group of {@link Capability#GROUPS}
 *       it has: the trade lifecycle {@link Capability#SUPPORT_CAPABILITY} and the product features
 *       {@link Capability#PRODUCT_FEATURES}, each whole, {@code except(<group>, ...)} or {@code only(...)}; a group the
 *       product has none of is left out (the order of the constants is the order of the {@code Examples} rows; never
 *       put one first)</li>
 *   <li>Run {@code TestDataTool sync-examples} (skill {@code maintaining-e2e-test-data}): it writes the product
 *       into the {@code Examples} of every Outline tagged with one of its capabilities</li>
 *   <li>Review {@code git diff}</li>
 * </ol>
 */
public enum ProductDatFiles {

    /** FX Total Return Forward — uses the standard TRF .dat template. */
    FX_TRF(SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** CorrOption (FX Correlation Option). */
    FX_PSCRIPT(SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    /** FX Floating Strike Barrier. */
    FX_PSCRIPT_FSKO(SUPPORT_CAPABILITY, PRODUCT_FEATURES),

    FX_PSCRIPT_TRF(SUPPORT_CAPABILITY, PRODUCT_FEATURES);

    /** Placeholder for FX Option: add data/dat/FX_OPT/FX_OPT.dat when its template is available. Supports nothing. */
//    FX_OPT(except(SUPPORT_CAPABILITY, ALLOCATION, PORTFOLIO_REASSIGNMENT), only(PRODUCT_UPLOAD));

    /** The classpath folder of the dat files, one folder per product. */
    public static final String DAT_DIR = "data/dat/";
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*\\.dat");

    private final Set<Capability> capabilities;

    /**
     * The capabilities of {@code groups}: each a group of {@link Capability#GROUPS} ({@code SUPPORT_CAPABILITY},
     * {@code PRODUCT_FEATURES}), or part of one ({@code except(...)}, {@code only(...)}).
     */
    @SafeVarargs
    ProductDatFiles(Set<Capability>... groups) {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        for (Set<Capability> group : groups) set.addAll(group);
        this.capabilities = Collections.unmodifiableSet(set);
    }

    /** The name of the product's default dat file, {@code <P>.dat}. */
    public String defaultDatFile() {
        return name() + ".dat";
    }

    /**
     * Classpath path of the product's default dat file, {@code data/dat/<P>/<P>.dat}, suitable for
     * {@code getClassLoader().getResource(path)}.
     */
    public String path() {
        return path(null);
    }

    /**
     * Classpath path of a dat file of this product, {@code data/dat/<P>/<datFile>}: a variant chosen by the case's
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
            throw new IllegalArgumentException("'" + datFile + "' is not a dat file name: a file of the product's"
                    + " folder " + DAT_DIR + "<product>/, such as FX_TRF.dat, without a folder");
        }
    }

    /** The capabilities this product supports (unmodifiable). */
    public Set<Capability> capabilities() {
        return capabilities;
    }

    /** The products that support {@code capability}, in registry order: the rows of its Outlines' {@code Examples}. */
    public static List<ProductDatFiles> with(Capability capability) {
        return Arrays.stream(values()).filter(p -> p.capabilities.contains(capability)).toList();
    }
}
