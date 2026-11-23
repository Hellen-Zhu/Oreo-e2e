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
import java.util.concurrent.Callable;

/**
 * The steps of the SIT seed ({@code SeedTest}, {@code docs/sit-seed.md}): book the trades and events of
 * {@link SeedData#catalog()} and write each booking, or its failure, to the run's manifest. They check only that a booking
 * happened (a trade ID, an approved task), never the response: the seed makes data, the E2E cases test it. A failed
 * booking names the step it failed at ({@code failedStep}: create trade, approve trade, ...) and keeps the trade ID
 * once the trade exists.
 */
public class SeedSteps {

    private static final int MAX_ERROR = 500;
    /** The step a booking is at; {@code failedStep} in the manifest when it fails there. */
    private static final String STEP = "step";

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
        book(resolve(() -> SeedData.catalog().forCase(caseId, composition, productId, taxonomy),
                unresolved("trade", caseId, composition, productId).put("taxonomy", taxonomy)), "trade");
    }

    /**
     * Seed case {@code caseId}: a live trade of {@code composition} under {@code productId}, as {@link #bookCase},
     * with the body of the catalog's variant {@code variant} (stepin-full, ...) and the case's dat file, portfolio
     * and entity.
     */
    @Given("^seed case '([^']*)' books a live '([^']*)' trade of composition '([^']*)' and product ID '([^']*)' via api$")
    public void bookVariant(String caseId, String variant, String composition, String productId) throws Exception {
        book(resolve(() -> SeedData.catalog().forVariant(caseId, variant, composition, productId),
                unresolved("event", caseId, composition, productId).put("variant", variant)), "event");
    }

    /** Books {@code trade} live; {@code kind} files it in the manifest ({@code trade}, or {@code event} for a step-in). */
    private void book(SeedData.Trade trade, String kind) throws Exception {
        ObjectNode entry = entry(kind, trade);
        try {
            live(trade, entry);
            done(entry);
        } catch (Exception | AssertionError e) {
            fail(entry, e);
            throw e;
        }
    }

    /**
     * Seed case {@code caseId}: the catalog's event {@code event} on a new live trade of {@code composition} under
     * {@code productId}, booked with the case's fields ({@code cases}), the event sent by the maker with its E2E
     * case's request and the seed case's {@code eventRequest} over it (triggered, or the amendment through the
     * trade's update) and, when it made a checker task, approved by the checker.
     */
    @Given("^seed case '([^']*)' books event '([^']*)' on a new live trade of composition '([^']*)' and product ID '([^']*)' via api$")
    public void bookEvent(String caseId, String event, String composition, String productId) throws Exception {
        ObjectNode unresolved = unresolved("event", caseId, composition, productId).put("event", event);
        SeedData.Trade trade = resolve(() -> SeedData.catalog().forEvent(caseId, event, composition, productId),
                unresolved);
        SeedData.Event e = resolve(() -> SeedData.catalog().event(event), unresolved);
        ObjectNode entry = entry("event", trade).put("event", e.name()).put("eventCase", e.caseId());
        try {
            entry.put(STEP, "build the event request");
            String request = SeedData.eventRequest(e, trade);
            CreatedTrade live = live(trade, entry);
            entry.put(STEP, "read the trade's notional");
            request = withAmounts(request, live.tradeId(), trade);
            entry.set("eventRequest", sentEvent(request, live.tradeId(), e));
            String taskId;
            entry.put(STEP, SeedData.UPDATE.equals(e.via()) ? "submit the amendment" : "trigger the event");
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
            entry.put(STEP, "approve the event");
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
     * {@code request} with its shares of the notional ({@code [50%, 50%]}) replaced by the amounts of the notional of
     * trade {@code tradeId}, as the E2E event step sends them; {@code request} itself when it gives none.
     */
    private String withAmounts(String request, String tradeId, SeedData.Trade trade) throws Exception {
        JsonNode body = Json.parse(request);
        if (!ApiRequestSteps.hasShares(body)) return request;
        ObjectNode withAmounts = (ObjectNode) body;
        ApiRequestSteps.putAmounts(withAmounts, ApiRequestSteps.notionalOf(ctx.helper(), tradeId, trade.caseId()));
        return Json.MAPPER.writeValueAsString(withAmounts);
    }

    /**
     * The event body as sent, for the manifest: {@code request} with its {@code tradeIds} replaced by the trade's, as
     * {@link TradeApiClient#triggerEventWithBody} does; an amendment's body as it is.
     */
    private static JsonNode sentEvent(String request, String tradeId, SeedData.Event e) {
        JsonNode body = Json.parse(request);
        if (!SeedData.UPDATE.equals(e.via()) && body instanceof ObjectNode object) {
            object.putArray("tradeIds").add(tradeId);
        }
        return body;
    }

    /**
     * A live trade of {@code trade}: its body ({@link SeedData#request}, its effective and MiFID execution time the
     * booking's, {@link SeedData#withTimes}) and dat file, submitted by the maker and approved by the checker. The
     * body's portfolio, risk portfolio and entity go to {@code entry}, and so do the body as sent, the dat path and,
     * as soon as the trade exists, its trade and task IDs: a trade whose approval fails is still found.
     */
    private CreatedTrade live(SeedData.Trade trade, ObjectNode entry) throws Exception {
        entry.put(STEP, "build the trade request");
        String request = SeedData.withTimes(SeedData.request(trade), DateUtil.now());
        JsonNode basic = Json.parse(request).path("basic");
        for (String field : List.of("portfolioId", "riskPortfolio", "scbEntity")) {
            if (basic.hasNonNull(field)) entry.put(field, basic.get(field).asText());
        }
        // what is sent, for the report: the trade part of the request (its product ID set) and the dat file
        entry.set("request", Json.parse(TradeApiClient.withProductId(request, trade.productId())));
        entry.put("dat", trade.dat());
        entry.put(STEP, "create trade");
        CreatedTrade created = TradeApiClient.submitTradeWithBody(ctx.helper(), TestUser.MAKER.email(), request,
                trade.productId(), trade.dat(), trade.caseId() != null ? trade.caseId() : trade.setup());
        entry.put("tradeId", created.tradeId()).put("taskId", created.taskId());
        ctx.set("createdTradeId", created.tradeId());
        entry.put(STEP, "approve trade");
        TradeApiClient.approveTask(ctx.helper(), created.taskId(), TestUser.CHECKER.email());
        return created;
    }

    /**
     * What {@code catalog} gives, the booking resolved from the seed catalog; when it fails (a case naming no dat
     * file, a TODO value), the failure is a manifest line of its own, {@code unresolved}, so the report lists it.
     */
    private <T> T resolve(Callable<T> catalog, ObjectNode unresolved) throws Exception {
        try {
            return catalog.call();
        } catch (Exception | AssertionError e) {
            unresolved.put(STEP, "read the seed catalog");
            fail(unresolved, e);
            throw e;
        }
    }

    /**
     * The manifest line of a booking with the row's own values only ({@code caseId} {@code null} for none): what a
     * booking the catalog could not resolve records, and the start of {@link #entry}.
     */
    private static ObjectNode unresolved(String kind, String caseId, String composition, String productId) {
        ObjectNode entry = Json.MAPPER.createObjectNode();
        entry.put("at", DateUtil.format(DateUtil.now()));
        String env = System.getenv("OREO_SEED_ENV");
        if (env != null && !env.isBlank()) entry.put("env", env.trim());
        entry.put("run", SeedData.runId());
        if (caseId != null) entry.put("case", caseId);
        return entry.put("kind", kind).put("composition", composition).put("productId", productId);
    }

    private static ObjectNode entry(String kind, SeedData.Trade trade) {
        ObjectNode entry = unresolved(kind, trade.caseId(), trade.composition(), trade.productId());
        entry.put("itradeableType", trade.itradeableType()).put("setup", trade.setup());
        if (trade.taxonomy() != null) entry.put("taxonomy", trade.taxonomy());
        if (trade.variant() != null) entry.put("variant", trade.variant());
        return entry;
    }

    private void done(ObjectNode entry) {
        entry.remove(STEP);
        entry.put("status", "booked");
        SeedData.record(entry);
        ctx.write("[seed] " + summary(entry));
    }

    private void fail(ObjectNode entry, Throwable e) {
        String message = String.valueOf(e.getMessage());
        JsonNode step = entry.remove(STEP);
        if (step != null) entry.put("failedStep", step.asText());
        entry.put("status", "failed")
                .put("error", message.length() > MAX_ERROR ? message.substring(0, MAX_ERROR) + "..." : message);
        SeedData.record(entry);
        ctx.write("[seed] " + summary(entry));
    }

    /** {@code entry} without the bodies it sent, for the scenario log: the manifest keeps them. */
    private static String summary(ObjectNode entry) {
        ObjectNode copy = entry.deepCopy();
        copy.remove(List.of("request", "eventRequest"));
        return Json.compact(copy);
    }
}
