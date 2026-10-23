@playwright @ADO#trade_portfolio_reassignment @ADO#trade_lifecycle
Feature: Trade Portfolio Reassignment E2E
  As a Trader
  I want to view the updated portfolio assignment in the trade view after a PortfolioReassignment event is processed
  So that portfolio changes for CorrOption trades are accurately reflected in the UI

  @ADO#positive @ADO#smoke @capability#portfolioReassignment
  Scenario Outline: [TC-TRADE-PORTFOLIO-REASSIGNMENT-<product_type>-UI-001] Updated portfolio is displayed for <product_type> trade after maker triggers PortfolioReassignment
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform Portfolio Reassignment event on the '<product_type>' trade
    Then the portfolio reassignment is completed successfully
    And the trade remains Live and is marked as Amended
    And the trade details show the updated portfolio assignment

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
