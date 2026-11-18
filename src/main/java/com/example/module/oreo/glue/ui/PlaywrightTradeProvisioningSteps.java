package com.example.module.oreo.glue.ui;

import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.config.TestUser;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import com.example.module.oreo.testdata.ProductDatFiles;
import com.example.module.oreo.utils.RestApiUtil;
import com.example.module.oreo.utils.TradeApiClient;
import com.example.module.oreo.utils.TradeApiClient.CreatedTrade;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import com.fasterxml.jackson.databind.JsonNode;
import cucumber.api.java.en.Given;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The trade preconditions of UI scenarios, provisioned through the API ({@link TradeApiClient}'s
 * {@code provision...} methods, which the API steps share) and stored for the UI steps. They neither log in nor
 * navigate.
 */
public class PlaywrightTradeProvisioningSteps {

    private RequestResponseHelper helper;
    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.helper = RestApiUtil.helperFromScenario(scenario);
    }

    /** Saved by the maker, not submitted: a draft with no checker task. */
    @Given("^a saved Draft '([^']*)' trade exists in the blotter$")
    public void provisionDraftTrade(String product) throws Exception {
        ProductDatFiles p = composition(product);
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionDraftTrade(helper, "trade-create", productId(p), datPath(p)));
    }

    /** Submitted by the maker: a trade pending the checker's approval. */
    @Given("^a Draft Pending Approval '([^']*)' trade exists in the blotter$")
    public void provisionPendingTrade(String product) throws Exception {
        provisionPending(product, "trade-create");
    }

    @Given("^a Draft Pending Approval '([^']*)' StepIn Full trade exists in the blotter$")
    public void provisionPendingStepInFullTrade(String product) throws Exception {
        provisionPending(product, "trade-create-stepin-full");
    }

    @Given("^a Draft Pending Approval '([^']*)' StepIn Partial trade exists in the blotter$")
    public void provisionPendingStepInPartialTrade(String product) throws Exception {
        provisionPending(product, "trade-create-stepin-partial");
    }

    /** Submitted by the maker, approved by the checker. */
    @Given("^a Live '([^']*)' trade exists in the blotter$")
    public void provisionLiveTrade(String product) throws Exception {
        provisionLive(product, "createdTradeId", "createdTaskId");
    }

    /** A live trade whose cancellation the maker submitted: pending the checker's approval. */
    @Given("^a Live '([^']*)' trade with a pending Cancellation exists in the blotter$")
    public void provisionPendingCancellation(String product) throws Exception {
        provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
    }

    /** Two of them, stored as createdTradeId1 / createdTaskId1 and createdTradeId2 / createdTaskId2. */
    @Given("^multiple Live '([^']*)' trades with a pending Cancellation exist in the blotter$")
    public void provisionMultiplePendingCancellation(String product) throws Exception {
        provisionPendingCancellation(product, "createdTradeId1", "createdTaskId1");
        provisionPendingCancellation(product, "createdTradeId2", "createdTaskId2");
    }

    /** A live trade whose amendment the maker submitted (setup trade-amendment): pending the checker's approval. */
    @Given("^a Live '([^']*)' trade with a pending Amendment exists in the blotter$")
    public void provisionPendingAmendment(String product) throws Exception {
        CreatedTrade live = provisionLive(product, "createdTradeId", "createdTaskId");
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionPendingAmendment(helper, live.tradeId(), "trade-amendment"));
    }

    /** A live trade whose cancellation the checker approved. */
    @Given("^a Dead '([^']*)' trade exists in the blotter$")
    public void provisionDeadTrade(String product) throws Exception {
        CreatedTrade trade = provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.approveTask(helper, trade.taskId(), TestUser.CHECKER.email());
    }

    /** A live trade whose cancellation the checker rejected. */
    @Given("^a Draft '([^']*)' trade exists in the blotter$")
    public void provisionRejectedDraftTrade(String product) throws Exception {
        CreatedTrade trade = provisionPendingCancellation(product, "createdTradeId", "createdTaskId");
        TradeApiClient.rejectTask(helper, trade.taskId(), TestUser.CHECKER.email());
    }

    private void provisionPending(String product, String setupName) throws Exception {
        ProductDatFiles p = composition(product);
        store("createdTradeId", "createdTaskId",
                TradeApiClient.provisionPendingTrade(helper, setupName, productId(p), datPath(p)));
    }

    private CreatedTrade provisionLive(String product, String tradeKey, String taskKey) throws Exception {
        ProductDatFiles p = composition(product);
        return store(tradeKey, taskKey,
                TradeApiClient.provisionLiveTrade(helper, "trade-create", productId(p), datPath(p)));
    }

    private CreatedTrade provisionPendingCancellation(String product, String tradeKey, String taskKey)
            throws Exception {
        CreatedTrade live = provisionLive(product, tradeKey, taskKey);
        return store(tradeKey, taskKey,
                TradeApiClient.provisionPendingEvent(helper, live.tradeId(), "trade-cancellation"));
    }

    private CreatedTrade store(String tradeKey, String taskKey, CreatedTrade trade) {
        ctx.set(tradeKey, trade.tradeId());
        ctx.set(taskKey, trade.taskId());
        ctx.write(String.format("[provisioned] %s = %s, %s = %s", tradeKey, trade.tradeId(), taskKey, trade.taskId()));
        return trade;
    }

    /**
     * The notionals a partial novation remaining leaves, from the trade's own notional ({@code basic.notionalAmount},
     * read through the API) and the remaining notional the case gives: stores {@code originalNotional},
     * {@code newNotional} (original - remaining: the novated trade's) and {@code equationLineValue} (the event form's
     * line, {@code Notional: <original> - <remaining> = <new>}), each as the portal shows amounts ({@code 69,000.00}:
     * en-US, the preference maker and checker sign in with).
     *
     * <pre>When the novated notional of trade 'createdTradeId' is computed from the remaining notional 'remainingNotional'</pre>
     */
    @Given("^the novated notional of trade '([^']*)' is computed from the remaining notional '([^']*)'$")
    public void computeNovatedNotional(String tradeVar, String remainingVar) {
        String tradeId = ctx.find(tradeVar);
        BigDecimal original = tradeNotional(tradeId);
        Object remainingValue = ctx.find(remainingVar);
        if (remainingValue == null) {
            throw new AssertionError("[PlaywrightTradeProvisioningSteps] no '" + remainingVar + "' in the case data of "
                    + ctx.find("_tc.key"));
        }
        BigDecimal remaining = new BigDecimal(String.valueOf(remainingValue).replace(",", "").trim());
        BigDecimal novated = original.subtract(remaining);
        ctx.set("originalNotional", amount(original));
        ctx.set("newNotional", amount(novated));
        ctx.set("equationLineValue", "Notional: " + amount(original) + " - " + amount(remaining) + " = "
                + amount(novated));
        ctx.write("[notional] trade " + tradeId + ": " + amount(original) + " - " + amount(remaining) + " = "
                + amount(novated));
    }

    /**
     * An amount of the case data that may be a share of the trade's notional: {@code '<shareVar>'} is {@code "50%"}
     * (computed from the trade's {@code basic.notionalAmount}, read through the API, rounded to the cent) or an amount
     * as it is ({@code "5000"}). Stores it as {@code '<amountVar>'}, as typed into a form ({@code 3468.75}), and as
     * {@code '<amountVar>.display'}, as the portal shows it ({@code 3,468.75}).
     *
     * <p>A case whose data has no {@code '<shareVar>'} (or a blank one) stores nothing.
     *
     * <pre>When the share 'newNotional' of the notional of trade 'createdTradeId' is stored as 'newNotionalAmount'</pre>
     */
    @Given("^the share '([^']*)' of the notional of trade '([^']*)' is stored as '([^']*)'$")
    public void storeShareOfNotional(String shareVar, String tradeVar, String amountVar) {
        Object given = ctx.find(shareVar);
        if (given == null || String.valueOf(given).isBlank()) {
            ctx.write("[notional] no '" + shareVar + "' in the case data: '" + amountVar + "' not stored");
            return;
        }
        String text = String.valueOf(given).trim();
        BigDecimal amount;
        if (text.endsWith("%")) {
            String tradeId = ctx.find(tradeVar);
            BigDecimal percent = new BigDecimal(text.substring(0, text.length() - 1).trim());
            amount = tradeNotional(tradeId).multiply(percent).divide(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            ctx.write("[notional] " + text + " of trade " + tradeId + " = " + amount(amount));
        } else {
            amount = new BigDecimal(text.replace(",", ""));
        }
        ctx.set(amountVar, amount.stripTrailingZeros().toPlainString());
        ctx.set(amountVar + ".display", amount(amount));
    }

    /** The trade's {@code data.trade.basic.notionalAmount}, read through the API as the maker. */
    private BigDecimal tradeNotional(String tradeId) {
        JsonNode notional = RestApiUtil.get(helper, TradeApiClient.tradePath(tradeId), TestUser.MAKER.email())
                .path("data").path("trade").path("basic").path("notionalAmount");
        if (!notional.isNumber()) {
            throw new AssertionError("[PlaywrightTradeProvisioningSteps] trade " + tradeId
                    + " has no numeric data.trade.basic.notionalAmount: " + notional);
        }
        return notional.decimalValue();
    }

    /** An amount as the portal shows it with the en-US number format: grouped, two decimals. */
    static String amount(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }

    /** The registered composition, written to the report with the product ID the case sends. */
    private ProductDatFiles composition(String product) {
        ProductDatFiles composition = ProductDatFiles.of(product);
        ctx.write("[" + composition.describe(ctx.find("_tc.productId")) + "]");
        return composition;
    }

    /** The case's product ID (productId of its test data), else the composition's default one. */
    private String productId(ProductDatFiles composition) {
        return composition.productId(ctx.find("_tc.productId"));
    }

    /** The case's dat file (datFile of its test data), else the composition's default one. */
    private String datPath(ProductDatFiles composition) {
        return composition.path(ctx.find("_tc.datFile"));
    }
}
