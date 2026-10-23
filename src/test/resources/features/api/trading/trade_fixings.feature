@api @ADO#trade_fixings @ADO#trade_lifecycle
Feature: Trade Fixings API
  As a Trader
  I want to retrieve online fixing data for a submitted trade
  So that the trade details contain the data required for review

  @ADO#positive @ADO#regression @capability#fixings
  Scenario Outline: [TC-TRADE-FIXINGS-<product_type>-API-001] Online fixing data is available for a submitted <product_type> trade
    Given a '<product_type>' trade is submitted to pending approval via api
    When maker retrieves online fixings for the trade
    Then online fixings are returned successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
