package com.example.module.oreo.testdata;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
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
    public void aCompositionSendsItsRegisteredProductId() {
        assertEquals("FX_PSCRIPT", ProductDatFiles.FX_CO.productId());
        assertEquals("composition FX_CO, product ID FX_PSCRIPT", ProductDatFiles.FX_CO.describe());
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
