package com.example.module.oreo;

import com.example.genie.junit.Genie;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;
import com.example.genie.fork.model.ForkMode;
import com.example.genie.fork.model.ForkWorkingDirectoryPolicy;
import com.example.genie.junit.GenieOptions;

@RunWith(Genie.class)
@CucumberOptions(
        junit    = {"--step-notifications"},
        features = {"classpath:features"},
        glue     = {
            "classpath:com.example.module.oreo.glue",
            "classpath:snippet"
        },
//        tags     = {"@TC-TRADE-ALLOCATION-UI-001 or @TC-TRADE-ALLOCATION-UI-002"}    // OR
//        tags     = {"@trade_lifecycle", "@playwright", "@smoke"}                     // AND
//        tags     = {"@playwright","not @trade_lifecycle"}                            // NOT
//        tags     = {"@TC-TRADE-AUDIT-API-008"}                                       // single case only
          tags     = {"not @wip","@ADO#trade_creation"}              // run all api+ui cases
//        name = {"^\\[TC-TRADE-CREATION-FX_CO-API-001\\].*$"}        // TC-TRADE-CREATION-FX_TRF-API-001
//        name = {"^\\[TC-TRADE-CREATION-FX_TRF-API-\\d+\\].*"}      // TC-TRADE-CREATION-FX_TRF-API-.*
//        name = {"^\\[TC-[^\\]]*FX_TRF-API[^\\]]*\\].*$"}          // TC.*FX_TRF-API.*
)

// — Parallel execution —————————————————————————————————————————————
////
@GenieOptions(
        properties = "src/test/resources/qa.properties",
//        forkMode             = ForkMode.SCENARIO_DEFINITION,
        forkMode             = ForkMode.FEATURE,
        forkCount            = 3,
        forkWorkingDirPolicy = ForkWorkingDirectoryPolicy.DISCRETE
)

public class OreoTest {
}
