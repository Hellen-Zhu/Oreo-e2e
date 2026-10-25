package com.example.module.oreo.glue.api;

import cucumber.api.PendingException;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RiskMetricApiSteps {

    private static final Logger log = LoggerFactory.getLogger(RiskMetricApiSteps.class);

    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    @Given("^the risk engine is unavailable$")
    public void theRiskEngineIsUnavailable() {
        throw new PendingException(
                "[RiskMetricApiSteps] Risk engine cannot be made unavailable via API in E2E context — " +
                "requires manual risk engine shutdown or environment configuration");
    }
}
