@api @ADO#trade_portfolio_reassignment @ADO#trade_lifecycle
Feature: Trade Portfolio Reassignment Event API
  As a Trader
  I want to trigger portfolio reassignment events for live trades via API
  So that the trade's portfolio assignment is updated accurately in the system

  @ADO#positive @ADO#smoke @capability#portfolioReassignment
  Scenario Outline: [TC-TRADE-PORTFOLIO-REASSIGNMENT-<product_type>-API-001] PortfolioReassignment event updates portfolio assignment for an active <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for PortfolioReassignment event
    And performs the PortfolioReassignment event via api
    Then the PortfolioReassignment event is executed successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-PORTFOLIO-REASSIGNMENT-API-002] Invalid PortfolioReassignment event for a trade is rejected
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data for PortfolioReassignment event
    And performs the PortfolioReassignment event via api
    Then the PortfolioReassignment event is rejected with a validation error
