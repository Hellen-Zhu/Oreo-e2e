package com.example.module.oreo.tools;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FeatureCasesTest {

    private static FeatureCases.FeatureCase featureCase(String id) {
        return new FeatureCases.FeatureCase(id, id, "api", "trade_approval_rejection", "", false);
    }

    @Test
    public void aMalformedIdIsLeftOutAndTheOthersKept() {
        List<FeatureCases.FeatureCase> cases = List.of(
                featureCase("TC-TRADE-APPROVAL-REJECTION-API-001"),
                featureCase("TC-TRADE-APPROVAL-REJECTIONAPI-002"),
                featureCase("TC-TRADE-APPROVAL-REJECTION-API-003"));
        assertEquals(List.of("TC-TRADE-APPROVAL-REJECTION-API-001", "TC-TRADE-APPROVAL-REJECTION-API-003"),
                FeatureCases.withValidIds(cases).stream().map(FeatureCases.FeatureCase::caseId).toList());
    }

    @Test
    public void theNoteCountsTheCasesLeftOut() {
        assertNull(FeatureCases.skippedNote(0));
        assertEquals("2 case(s) with a malformed ID left out: see \"every feature case resolves\"",
                FeatureCases.skippedNote(2));
    }
}
