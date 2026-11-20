package com.example.module.oreo.glue.api;

import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.testdata.Json;
import com.example.module.oreo.testdata.SeedData;
import com.example.module.oreo.utils.DateUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.utils.TradeApiClient.CreatedTrade;
import com.example.module.oreo.utils.TradeApiClient.EventResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;

import java.util.List;

/**
 * The steps of the SIT seed ({@code SeedTest}, {@code docs/sit-seed.md}): book the trades and events of
 * {@link SeedData#catalog()} and write each booking, or its failure, to the run's manifest. They check only that a booking
 * happened (a trade ID, an approved task), never the response: the seed makes data, the E2E cases test it.
 */
public class SeedSteps {

    private static final int MAX_ERROR = 500;

    private RestServiceResolver.Context ctx;

    @Before
    public void beforeScenario(Scenario scenario) {
        this.ctx = RestServiceResolver.from(scenario);
    }

    /**
     * Seed case {@code caseId}: a live trade of {@code composition} under {@code productId}, approved by the checker;
     * {@code taxonomy} (the ISDA taxonomy after its asset class) labels the booking.
     */
    @Given("^seed case '([^']*)' books a live trade of composition '([^']*)' and product ID '([^']*)' with taxonomy '([^']*)' via api$")
    public void bookCase(String caseId, String composition, String productId, String taxonomy) throws Exception {
        book(SeedData.catalog().forCase(caseId, composition, productId, taxonomy));
    }

    /**
     * A live trade of {@code composition} under {@code productId}, as {@link #bookCase}, with the body of the
     * catalog's variant {@code variant} (StepIn Full, ...).
     */
    @Given("^a '([^']*)' seed trade of composition '([^']*)' and product ID '([^']*)' is booked live via api$")
    public void bookVariant(String variant, String composition, String productId) throws Exception {
        book(SeedData.catalog().trade(composition, productId, variant));
    }

    private void book(SeedData.Trade trade) throws Exception {
        ObjectNode entry = entry("trade", trade);
        try {
            CreatedTrade live = live(trade, entry);
            entry.put("tradeId", live.tradeId()).put("taskId", live.taskId());
            done(entry);
        } catch (Exception | AssertionError e) {
            fail(entry, e);
            throw e;
        }
    }

    /**
     * The catalog's event {@code event} on a new live trade of {@code composition} under {@code productId}: the event
     * sent by the maker with its E2E case's request (triggered, or the amendment through the trade's update) and, when
     * it made a checker task, approved by the checker.
     */
    @Given("^seed event '([^']*)' is booked on a new live trade of composition '([^']*)' and product ID '([^']*)' via api$")
    public void bookEvent(String event, String composition, String productId) throws Exception {
        SeedData.Trade trade = SeedData.catalog().trade(composition, productId, null);
        SeedData.Event e = SeedData.catalog().event(event);
        ObjectNode entry = entry("event", trade).put("event", e.name()).put("eventCase", e.caseId());
        try {
            String request = SeedData.eventRequest(e, trade);
            CreatedTrade live = live(trade, entry);
            entry.put("tradeId", live.tradeId());
            String taskId;
            if (SeedData.UPDATE.equals(e.via())) {
                taskId = TradeApiClient.provisionPendingAmendmentWithBody(ctx.helper(), live.tradeId(), request,
                        e.caseId()).taskId();
            } else {
                EventResult result = TradeApiClient.triggerEventWithBody(ctx.helper(), List.of(live.tradeId()),
                        TestUser.MAKER.email(), request, e.caseId()).get(0);
                taskId = result.checkerTaskId();
                result.childTradeIds().forEach(entry.putArray("childTradeIds")::add);
            }
            entry.put("eventTaskId", taskId);
            if (!taskId.isBlank()) {
                TradeApiClient.approveTask(ctx.helper(), taskId, TestUser.CHECKER.email());
            }
            done(entry);
        } catch (Exception | AssertionError ex) {
            fail(entry, ex);
            throw ex;
        }
    }

    /**
     * A live trade of {@code trade}: its body ({@link SeedData#request}) and dat file. The body's portfolio, risk portfolio
     * and entity go to {@code entry}.
     */
    private CreatedTrade live(SeedData.Trade trade, ObjectNode entry) throws Exception {
        String request = SeedData.request(trade);
        JsonNode basic = Json.parse(request).path("basic");
        for (String field : List.of("portfolioId", "riskPortfolio", "scbEntity")) {
            if (basic.hasNonNull(field)) entry.put(field, basic.get(field).asText());
        }
        CreatedTrade live = TradeApiClient.provisionLiveTradeWithBody(ctx.helper(), request, trade.productId(),
                trade.dat(), trade.caseId() != null ? trade.caseId() : trade.setup());
        ctx.set("createdTradeId", live.tradeId());
        return live;
    }

    private static ObjectNode entry(String kind, SeedData.Trade trade) {
        ObjectNode entry = Json.MAPPER.createObjectNode();
        entry.put("at", DateUtil.format(DateUtil.now()));
        String env = System.getenv("OREO_SEED_ENV");
        if (env != null && !env.isBlank()) entry.put("env", env.trim());
        entry.put("run", SeedData.runId());
        if (trade.caseId() != null) entry.put("case", trade.caseId());
        entry.put("kind", kind).put("itradeableType", trade.itradeableType()).put("composition", trade.composition())
                .put("productId", trade.productId())
                .put("setup", trade.setup());
        if (trade.taxonomy() != null) entry.put("taxonomy", trade.taxonomy());
        if (trade.variant() != null) entry.put("variant", trade.variant());
        return entry;
    }

    private void done(ObjectNode entry) {
        entry.put("status", "booked");
        SeedData.record(entry);
        ctx.write("[seed] " + Json.compact(entry));
    }

    private void fail(ObjectNode entry, Throwable e) {
        String message = String.valueOf(e.getMessage());
        entry.put("status", "failed")
                .put("error", message.length() > MAX_ERROR ? message.substring(0, MAX_ERROR) + "..." : message);
        SeedData.record(entry);
        ctx.write("[seed] " + Json.compact(entry));
    }
}
