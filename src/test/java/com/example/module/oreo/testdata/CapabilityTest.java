package com.example.module.oreo.testdata;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CapabilityTest {

    @Test
    public void everyCapabilityIsInExactlyOneGroup() {
        for (Capability capability : Capability.values()) {
            long groups = Capability.GROUPS.values().stream().filter(g -> g.contains(capability)).count();
            assertEquals(capability.tagName() + " is in " + groups + " group(s) of Capability.GROUPS", 1, groups);
        }
    }

    @Test
    public void theProductFeaturesAreNotLifecycleEvents() {
        assertEquals(EnumSet.of(Capability.FIXINGS, Capability.CORTEX_FORMULA, Capability.PRODUCT_UPLOAD),
                Capability.PRODUCT_FEATURES);
        assertTrue(Capability.SUPPORT_CAPABILITY.stream().noneMatch(Capability.PRODUCT_FEATURES::contains));
    }

    @Test
    public void onlyAndExceptTakePartOfAGroup() {
        assertEquals(Set.of(Capability.FIXINGS), Capability.only(Capability.FIXINGS));
        assertEquals(EnumSet.of(Capability.CORTEX_FORMULA, Capability.PRODUCT_UPLOAD),
                Capability.except(Capability.PRODUCT_FEATURES, Capability.FIXINGS));
        try {
            Capability.except(Capability.PRODUCT_FEATURES, Capability.ALLOCATION);
            fail("allocation is not a product feature");
        } catch (IllegalArgumentException expected) {
            // a capability of another group would take nothing away
        }
    }

    @Test
    public void aCompositionMatchesTheEndOfItsTradeType() {
        assertTrue(ProductDatFiles.FX_CO.isTradeType("Example.Finance.Tradeable.PayoffScript.Ctrl.CorrOption"));
        assertTrue(ProductDatFiles.FX_TRF.isTradeType("Example.Finance.Tradeable.TRF"));
        assertFalse(ProductDatFiles.FX_TRF.isTradeType("Example.Finance.Tradeable.PayoffScript.Ctrl.TRFGeneric"));
        assertFalse(ProductDatFiles.FX_CO.isTradeType("FX_CO"));
        assertEquals("FX_PSCRIPT", ProductDatFiles.FX_CO.productId());
    }

    @Test
    public void aTradeOfAnotherCompositionIsReported() {
        String corrOption = "Example.Finance.Tradeable.PayoffScript.Ctrl.CorrOption";
        assertEquals(List.of(), ProductDatFiles.FX_CO.mismatches("FX_CO", corrOption));
        assertEquals(List.of("compositionId is 'FX_FSB', not 'FX_CO'"),
                ProductDatFiles.FX_CO.mismatches("FX_FSB", corrOption));
        assertEquals(List.of("tradeType '" + corrOption + "' does not end with 'PayoffScript.Ctrl.FloatingStrikeBarrier'"),
                ProductDatFiles.FX_FSB.mismatches("FX_FSB", corrOption));
        // recorded before composition IDs: nothing to compare
        assertEquals(List.of(), ProductDatFiles.FX_CO.mismatches("", "FX_CO"));
    }

    @Test
    public void anUnknownCompositionNamesTheRegistry() {
        assertEquals(ProductDatFiles.FX_FSB, ProductDatFiles.of(" fx fsb"));
        String message = null;
        try {
            ProductDatFiles.of("FX_PSCRIPT");
        } catch (AssertionError expected) {
            message = expected.getMessage();
        }
        assertTrue("FX_PSCRIPT is a product ID, not a composition", message != null && message.contains("ProductDatFiles"));
    }
}
