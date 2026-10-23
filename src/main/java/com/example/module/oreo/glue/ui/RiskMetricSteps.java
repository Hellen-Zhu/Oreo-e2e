package com.example.module.oreo.glue.ui;

import com.example.module.oreo.testdata.TestData;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/** Mocks of the risk metrics endpoints: the bulk one and one per metric type below it. */
public class RiskMetricSteps {

    private static final Logger log = LoggerFactory.getLogger(RiskMetricSteps.class);

    private static final long LOADING_STATE_DELAY_MS = 1500L;

    private static final List<String> METRIC_TYPES =
            List.of("PNL", "VAR95", "VAR99", "ES", "DELTA", "GAMMA", "VEGA", "THETA");

    /** The bulk endpoint only: {@code *} stops at {@code /}, so the per-metric paths below it do not match. */
    private static final String BULK_ROUTE_PATTERN = "**/api/v1/trades/*/risk-metrics";

    /** In the per-metric success body, replaced by the metric type. */
    private static final String METRIC_TYPE_PLACEHOLDER = "${metricType}";

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }

    @Given("^the bulk risk metrics route is mocked to return 500 and per-metric routes return delayed success$")
    public void mockBulkFailureWithDelayedPerMetricSuccess() {
        log.debug("[RiskMetricSteps] mockBulkFailureWithDelayedPerMetricSuccess");
        mockRiskMetrics(Set.of(), LOADING_STATE_DELAY_MS);
    }

    @Given("^the bulk risk metrics and VAR95 VAR99 per-metric routes are mocked to fail with other per-metric routes returning success$")
    public void mockBulkAndVar95Var99Failure() {
        log.debug("[RiskMetricSteps] mockBulkAndVar95Var99Failure");
        mockRiskMetrics(Set.of("VAR95", "VAR99"), 0);
    }

    /**
     * The bulk route answers 500; each per-metric route answers 500 when its type is in {@code failing}, else 200
     * with its success body after {@code delayMs} (0: at once). The bodies are read here, so missing test data fails
     * this step, not a route handler.
     */
    private void mockRiskMetrics(Set<String> failing, long delayMs) {
        String errorBody = TestData.setup("risk-metric-bulk-error").request();
        String successBody = TestData.setup("risk-metric-single-success").request();
        Page page = ctx.service().getPage();

        page.route(BULK_ROUTE_PATTERN, route -> fulfill(route, 500, errorBody));
        for (String metricType : METRIC_TYPES) {
            String pattern = BULK_ROUTE_PATTERN + "/" + metricType;
            if (failing.contains(metricType)) {
                page.route(pattern, route -> fulfill(route, 500, errorBody));
            } else {
                String body = successBody.replace(METRIC_TYPE_PLACEHOLDER, metricType);
                page.route(pattern, route -> fulfillAfter(route, 200, body, delayMs));
            }
        }
    }

    /** {@link #fulfill} after {@code delayMs}, on a virtual thread so that the other routes are not held up. */
    private static void fulfillAfter(Route route, int status, String body, long delayMs) {
        if (delayMs <= 0) {
            fulfill(route, status, body);
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            fulfill(route, status, body);
        });
    }

    private static void fulfill(Route route, int status, String body) {
        route.fulfill(new Route.FulfillOptions()
                .setStatus(status)
                .setBody(body)
                .setContentType("application/json"));
    }
}
