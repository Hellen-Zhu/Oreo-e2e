package com.example.module.oreo.testdata;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a product can be tested for: one capability per scenario that not every product supports. A variation in
 * data only (a product that is always Sell) is not a capability but a {@code byProduct} value.
 *
 * <p>Each Scenario Outline with {@code <product_type>} carries one tag {@code @capability#<name>}; its
 * {@code Examples} are the products of {@link ProductDatFiles#with(Capability)}, written by
 * {@code TestDataTool sync-examples}. The names are those of the Playwright project's registry
 * ({@code framework/products.ts}); a constant is added with the first Outline that needs it.
 */
public enum Capability {

    /** A normal trade booked. */
    CREATE("create"),
    STEP_IN_FULL("stepInFull"),
    STEP_IN_PARTIAL("stepInPartial"),
    EARLY_TERMINATION("earlyTermination"),
    NOVATION_REMAINING("novationRemaining"),
    CANCELLATION("cancellation"),
    ALLOCATION("allocation"),
    AMENDMENT("amendment"),
    PARTIAL_TERMINATION("partialTermination"),
    PARTIAL_NOVATION_REMAINING("partialNovationRemaining"),
    STEP_OUT_FULL("stepOutFull"),
    STEP_OUT_PARTIAL("stepOutPartial"),
    PORTFOLIO_REASSIGNMENT("portfolioReassignment"),
    /** Fixings on the trade detail page; not in the Playwright registry yet, which has no fixings spec. */
    FIXINGS("fixings");

    /**
     * The trade lifecycle: booking (create, step in full / partial) and the events on a live trade. A product that
     * supports all of them names the group instead of each one, one that lacks some names
     * {@code except(LIFECYCLE_EVENTS, ...)}. The Playwright registry's {@code LIFECYCLE_EVENTS} has the events only
     * (its products list create and step in beside it). A new constant joins the group only if every product that
     * names the group supports it.
     */
    public static final Set<Capability> LIFECYCLE_EVENTS = Collections.unmodifiableSet(EnumSet.of(
            CREATE, STEP_IN_FULL, STEP_IN_PARTIAL,
            CANCELLATION, ALLOCATION, AMENDMENT, EARLY_TERMINATION, PARTIAL_TERMINATION, NOVATION_REMAINING,
            PARTIAL_NOVATION_REMAINING, STEP_OUT_FULL, STEP_OUT_PARTIAL, PORTFOLIO_REASSIGNMENT));

    /** The tag of an Outline: {@code @capability#stepInFull}. */
    public static final String TAG_PREFIX = "@capability#";

    private final String tagName;

    Capability(String tagName) {
        this.tagName = tagName;
    }

    /** The name in the tag and in the Playwright project's registry: {@code stepInFull}. */
    public String tagName() {
        return tagName;
    }

    /** {@code group} without {@code left}; fails on a capability that is not in the group (a typo would drop nothing). */
    public static Set<Capability> except(Set<Capability> group, Capability... left) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        out.addAll(group);
        for (Capability c : left) {
            if (!out.remove(c)) {
                throw new IllegalArgumentException(c.tagName + " is not in the group " + group);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** The capability named {@code name} (case-sensitive); fails listing the known names. */
    public static Capability fromTagName(String name) {
        for (Capability c : values()) {
            if (c.tagName.equals(name)) return c;
        }
        throw new IllegalArgumentException("'" + name + "' is not a capability; known: "
                + Arrays.stream(values()).map(Capability::tagName).collect(Collectors.joining(", ")));
    }
}
