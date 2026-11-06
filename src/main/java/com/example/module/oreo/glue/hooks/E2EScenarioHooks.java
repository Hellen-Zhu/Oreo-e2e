package com.example.module.oreo.glue.hooks;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.testdata.TestData;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.config.TestUser;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class E2EScenarioHooks {
    private static final Logger log = LoggerFactory.getLogger(E2EScenarioHooks.class);

    @Before(value = "@playwright", order = 2)
    public void resetUserPreferenceDefaults(Scenario scenario) {
        log.info("[E2EScenarioHooks] reset user preferences to defaults");

        RequestResponseHelper helper = RestApiUtil.helperFromScenario(scenario);
        String payload = TestData.setup("user-preference-defaults").request();

        RestApiUtil.put(helper, "/uc/api/user-preferences", TestUser.MAKER.email(), payload);
        RestApiUtil.put(helper, "/uc/api/user-preferences", TestUser.CHECKER.email(), payload);
    }

    /**
     * In MOCK risk engine mode, the risk metrics the trade detail page loads come from setup risk-metrics-success-mock
     * for the whole scenario, however the page is reached (blotter, direct navigation, reload after an action). After
     * the order 1 hook, which may set the scenario's own risk engine mode.
     */
    @Before(value = "@playwright", order = 3)
    public void mockTradeDetailRiskMetrics(Scenario scenario) {
        PlaywrightServiceResolver.from(scenario)
                .mockRiskCallForScenario(TradeApiClient.RISK_METRICS_PATH_END, "risk-metrics-success-mock");
    }
}
