@playwright @ADO#trade_fixings @ADO#trade_lifecycle
Feature: Trade Fixings E2E
  As a Trader
  I want fixing values to be available on the trade details page
  So that the schedule shows the latest fixing data for each trade

  @ADO#positive @ADO#regression @capability#fixings
  Scenario Outline: [TC-TRADE-FIXINGS-<product_type>-UI-001] <product_type> trade detail uses online fixing refresh to populate missing fixings (single fixing)
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When opens trade detail for the trade with fixing data available
    Then fixing data is shown as expected for the '<product_type>' trade

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
