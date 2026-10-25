@api @ADO#trade_novation_remaining @ADO#trade_lifecycle
Feature: Trade NovationRemaining API
  As a Maker
  I want to trigger NovationRemaining events for Live FX trades via API
  So that trades are novated with the original trade retaining the remaining notional and a new trade created for the novated portion

  @ADO#positive @ADO#smoke @capability#novationRemaining
  Scenario Outline: [TC-TRADE-NOVATION-REMAINING-<product_type>-API-001] Should be able to perform NovationRemaining event for a live <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for NovationRemaining event
    And performs the NovationRemaining event via api
    Then the NovationRemaining event is executed successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
