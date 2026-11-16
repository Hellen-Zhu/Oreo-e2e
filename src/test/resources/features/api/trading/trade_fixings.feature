@api @ADO#trade_fixings @ADO#trade_lifecycle
Feature: Trade Fixings API
  As a Trader
  I want the online fixing refresh to fill the fixings of a submitted trade
  So that every past fixing date of the trade has its fixing value

  @ADO#positive @ADO#regression @capability#fixings
  Scenario Outline: [TC-TRADE-FIXINGS-<product_type>-API-001] Online fixing refresh fills every past fixing date of a submitted <product_type> trade
    Given a '<product_type>' trade is submitted to pending approval via api
    And maker retrieves the trade
    And the queried trade contains empty fixing data
    When maker refreshes the online fixings of the trade
    And maker retrieves the trade
    Then the 'trade details' are retrieved successfully
    And every schedule date before today has a fixing value

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |
