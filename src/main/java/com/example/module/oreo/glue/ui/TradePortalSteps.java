package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.KeyboardModifier;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.module.oreo.utils.DateUtil;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;

import java.util.List;

public class TradePortalSteps {

    private static final Logger log = LoggerFactory.getLogger(TradePortalSteps.class);

    private PlaywrightServiceResolver.Context ctx;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) { this.ctx = PlaywrightServiceResolver.from(scenario); }


    @Then("^the trade row action menu for '([^']*)' contains the event '([^']*)'$")
    public void assertTradeRowActionMenuContainsEvent(String ctxKey, String eventName) {
        // Open the menu using the existing shared step
        openActionMenuForAllTrades(ctxKey);

        // Resolve menu item from element repo — element name = trade_portal.row_action_{eventnamelowercase}
        String elementKey = "trade_portal.row_action_" + eventName.toLowerCase().replaceAll("\\s+", "");
        Locator menuItem = ctx.service().getLocator(elementKey);
        ctx.waitForVisible(menuItem);
        log.info("[TradePortalSteps] Action menu item '{}' (element='{}') confirmed visible for ctx['{}']='{}'.",
                eventName, elementKey, ctxKey, ctx.get(ctxKey));
    }

    @When("^navigate to trade detail and wait for page ready for stored variable '([^']*)'$")
    public void navigateToTradeDetail(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        ctx.clickAndWaitForResponse("trade_portal.row_action_view_details", "/api/v1/trades/" + tradeId, "GET");
    }

    @When("^double click to navigate to trade detail and wait for page ready for stored variable '([^']*)'$")
    public void doubleClickToViewTradeDetail(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        ctx.doubleClickAndWaitForResponse("trade_portal.all_trades_blotter_row", "/api/v1/trades/" + tradeId, "GET");
    }

    /**
     * Refreshes and waits until blotter {@code blotterId} ({@code BLT-DEFAULT-NEW-TODAY}, ...) shows the refreshed
     * trades: the request the click sent, its whole body, then its "Refreshing..." gone. The indicator alone could be
     * checked before it appears, the response alone ends before the blotter redraws.
     */
    @When("^refresh trade blotter and wait for blotter '([^']*)' to be ready$")
    public void refreshTradeBlotterAndWaitFor(String blotterId) {
        ctx.clickAndWaitForResponse("trade_portal.refresh_btn", "/api/v1/blotter/trades", "POST");
        ctx.assertEventuallyHidden(ctx.getLocatorWithParam("trade_portal.blotter_view_refreshing_state", blotterId));
    }


    @When("^open action menu item for new today blotter from stored variable '([^']*)'$")
    public void openActionMenuForAllTrades(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        log.info("[TradePortalSteps] Right-clicking trade row in Validation Blotter for ctxKey='{}', tradeId='{}'", ctxKey, tradeId);
        ctx.write("Right-clicking trade row in Validation Blotter for ctxKey='" + ctxKey + "', tradeId='" + tradeId + "'.");
        openActionMenuInBlotter("trade_portal.all_trade_blotter", tradeId);
    }


    @When("^select trade stored as '([^']*)' in blotter for bulk action$")
    public void selectTradeForBulkAction(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        selectTradeRowForBulkAction("trade_portal.all_trade_blotter", tradeId);
        ctx.set("lastBulkSelectedTradeId", tradeId);
    }


    @When("^search for trade stored as '([^']*)' in blotter$")
    public void searchForTradeInBlotter(String ctxKey) {
        String tradeId = ctx.get(ctxKey);
        ctx.write("Searching for trade in blotter with ctx['" + ctxKey + "'] = '" + tradeId + "'.");
        log.info("[TradePortalSteps] Typing ctx['{}']='{}' into blotter search", ctxKey, tradeId);
        fillBlotterSearchInput(tradeId);
    }

    /**
     * Fills the search and waits until the blotter has filtered: the first row is the searched trade. The row
     * assertions read row 0 once, so returning before the filter applies would read another trade.
     */
    private void fillBlotterSearchInput(String tradeId) {
        ctx.innerInput("trade_portal.search_input").fill(tradeId);
        Locator firstRow = ctx.service().getLocator("trade_portal.all_trades_blotter_row").first();
        ctx.assertEventuallyContains(firstRow, tradeId);
    }

    /**
     * Asserts the text of cell {@code column} in the row whose {@code keyColumn} cell shows exactly the value of
     * stored variable {@code keyCtxKey}. Columns are {@code data-col} values ({@code id}, {@code trade.basic.eventStatus}).
     */
    @Then("^blotter '([^']*)' row with column '([^']*)' from stored variable '([^']*)' has column '([^']*)' as '([^']*)'$")
    public void assertBlotterCellText(String rowKey, String keyColumn, String keyCtxKey, String column, String expected) {
        Locator cell = cellOfRow(rowKey, keyColumn, keyCtxKey, column);
        assertCellValue(rowKey, keyColumn, keyCtxKey, column, expected, ctx.text(cell));
    }

    /** As above, for a column that shows a {@code ui-badge} ({@code trade.basic.status}): asserts its label. */
    @Then("^blotter '([^']*)' row with column '([^']*)' from stored variable '([^']*)' has badge in column '([^']*)' as '([^']*)'$")
    public void assertBlotterCellBadge(String rowKey, String keyColumn, String keyCtxKey, String column, String expected) {
        Locator cell = cellOfRow(rowKey, keyColumn, keyCtxKey, column);
        assertCellValue(rowKey, keyColumn, keyCtxKey, column, expected, ctx.badgeLabel(cell));
    }

    private Locator cellOfRow(String rowKey, String keyColumn, String keyCtxKey, String column) {
        Locator row = ctx.rowWithCell(rowKey, "trade_portal.blotter_cell", keyColumn, ctx.get(keyCtxKey));
        return ctx.cellOf(row, "trade_portal.blotter_cell", column);
    }

    private void assertCellValue(String rowKey, String keyColumn, String keyCtxKey, String column, String expected,
                                 String actual) {
        String key = ctx.get(keyCtxKey);
        ctx.write(String.format("Blotter row %s='%s' column '%s' = '%s'", keyColumn, key, column, actual));
        if (!expected.equals(actual)) {
            throw new AssertionError(String.format(
                    "[TradePortalSteps] blotter '%s' row %s='%s' column '%s': expected '%s' but got '%s'",
                    rowKey, keyColumn, key, column, expected, actual));
        }
    }

    @Then("^blotter row '([^']*)' index (\\d+) column '([^']*)' is '([^']*)'$")
    public void assertBlotterColumnValue(String rowTestId, int rowIndex, String colName, String expectedValue) {
        log.info("[TradePortalSteps] Asserting Blotter[{}][{}][{}] = '{}'", rowTestId, rowIndex, colName, expectedValue);
        assertCell(rowTestId, rowIndex, colName, expectedValue);
    }


    @Then("^blotter row '([^']*)' index (\\d+) column '([^']*)' matches stored variable '([^']*)'$")
    public void assertBlotterColumnMatchesCtx(String rowTestId, int rowIndex, String colName, String ctxKey) {
        String expectedValue = ctx.get(ctxKey);
        log.info("[TradePortalSteps] Asserting Blotter[{}][{}][{}] matches ctx['{}'] = '{}'",
                rowTestId, rowIndex, colName, ctxKey, expectedValue);
        assertCell(rowTestId, rowIndex, colName, expectedValue);
    }

    @Then("^blotter row '([^']*)' index (\\d+) column '([^']*)' is today in format '([^']*)'$")
    public void assertBlotterColumnIsTodayInFormat(String rowTestId, int rowIndex, String colName, String pattern) {
        String expectedValue = DateUtil.format(DateUtil.today(), pattern);

        ctx.write("Expected today's date for blotter cell in format '" + pattern + "' is '" + expectedValue + "'.");
        log.info("[TradePortalSteps] Asserting today's date for Blotter[{}][{}][{}] with pattern '{}' => '{}'",
                rowTestId, rowIndex, colName, pattern, expectedValue);

        assertCell(rowTestId, rowIndex, colName, expectedValue);
    }

    @Then("^blotter '([^']*)' with dynamic value from stored variable '([^']*)' contains column '([^']*)'$")
    public void assertBlotterContainsColumn(String blotterElementName, String dynamicCtxKey, String colName) {

        Locator blotter = ctx.getLocatorFromStoredVariable(blotterElementName, dynamicCtxKey);
        log.info("[TradePortalSteps] Asserting Blotter '{}' contains column '{}'",
                blotter, colName);

        blotter.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(ctx.timeoutMillis()));

        Locator column = blotter.locator("th[data-col='" + colName + "']");

        ctx.write(String.format(
                "Blotter '%s' contains column '%s'.",
                blotterElementName,
                colName));

        PlaywrightAssertions.assertThat(column).hasCount(1);
    }

    // ── Private helpers ─────────────────────────────────────────────

    /** Resolves the nth row's cell and asserts its trimmed text equals {@code expectedValue}. */
    private void assertCell(String rowTestId, int rowIndex, String colName, String expectedValue) {
        String cellSelector = colName.contains(".")
                ? "td[data-col='" + colName + "']"
                : "td[data-col='" + colName + "'], td[data-col='trade.basic." + colName + "']";

        Locator cell = ctx.service().getLocator(rowTestId)
                .nth(rowIndex)
                .locator(cellSelector);
        cell.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(ctx.timeoutMillis()));
        // ui-badge renders text as a DOM property (badge.label), not as DOM content.
        // Use JS evaluate to read the property when an ui-badge is present.
        String actualValue = (String) cell.evaluate(
                "el => { const badge = el.querySelector('ui-badge'); " +
                        "const span = el.querySelector('span'); " +
                        "return badge ? badge.label : span ? span.textContent.trim() : el.textContent.trim(); }");
        ctx.write(String.format("Blotter cell actual value = '%s'", actualValue));
        log.info("[TradePortalSteps] Blotter[{}][{}][{}] actual = '{}'", rowTestId, rowIndex, colName, actualValue);
        if (!expectedValue.equals(actualValue)) {
            throw new AssertionError(String.format(
                    "[TradePortalSteps] blotter row '%s' index %d column '%s': expected '%s' but got '%s'",
                    rowTestId, rowIndex, colName, expectedValue, actualValue));
        }
    }

    /**
     * Finds the first visible row containing {@code tradeId} in the given blotter and
     * right-clicks that row to open the action menu.
     */
    private void openActionMenuInBlotter(String blotterTestId, String tradeId) {
        Locator matchedRow = ctx.service().getLocator(blotterTestId)
                .getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(tradeId))
                .first();

        matchedRow.scrollIntoViewIfNeeded();

        ctx.waitForVisible(matchedRow);

        matchedRow.click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
    }

    private void selectTradeRowForBulkAction(String blotterTestId, String tradeId) {
        Locator row = ctx.service().getLocator(blotterTestId)
                .getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(tradeId))
                .first();
        ctx.waitForVisible(row);
        row.scrollIntoViewIfNeeded();

        Locator.ClickOptions clickOptions = new Locator.ClickOptions();
        clickOptions.setModifiers(List.of(KeyboardModifier.CONTROL));

        row.click(clickOptions);
    }
}
