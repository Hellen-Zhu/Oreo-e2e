package com.example.module.oreo.testdata;

import com.example.testdata.Product;
import com.example.testdata.ProductRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <p>It is the {@link Product} registry of the test data library ({@code com.example.oreo:oreo-test-data}): the
 * library's defaults give the dat file paths, the report line and the trade check ({@code path}, {@code describe},
 * {@code mismatches}), and {@link Registry} hands the compositions to {@code TestDataTool}.
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
public enum ProductDatFiles implements Product {

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
    @Override
    public String tradeType() {
        return tradeType;
    }

    /** The product ID the cases send for this composition ({@code basic.productId}). */
    @Override
    public String productId() {
        return productId;
    }

    /** The tag names of {@link #capabilities()}, in {@link Capability} order: {@code stepInFull}. */
    @Override
    public Set<String> capabilityTags() {
        return capabilities.stream().map(Capability::tagName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The capabilities this composition supports (unmodifiable). */
    public Set<Capability> capabilities() {
        return capabilities;
    }

    /** The compositions that support {@code capability}, in registry order: the rows of its Outlines' {@code Examples}. */
    public static List<ProductDatFiles> with(Capability capability) {
        return Arrays.stream(values()).filter(p -> p.capabilities.contains(capability)).toList();
    }

    /**
     * This registry, as the test data library ({@code com.example.oreo:oreo-test-data}) sees it: found through
     * {@code META-INF/services/com.example.testdata.ProductRegistry}, it gives {@code TestDataTool} (check,
     * sync-examples, coverage) and the case lookup the compositions and their capability tags.
     */
    public static final class Registry implements ProductRegistry {

        @Override
        public List<ProductDatFiles> products() {
            return List.of(values());
        }

        @Override
        public Map<String, List<String>> capabilityGroups() {
            Map<String, List<String>> groups = new LinkedHashMap<>();
            Capability.GROUPS.forEach((title, group) ->
                    groups.put(title, group.stream().sorted().map(Capability::tagName).toList()));
            return Collections.unmodifiableMap(groups);
        }
    }
}
