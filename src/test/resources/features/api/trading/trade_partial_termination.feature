@api @ADO#trade_partial_termination @ADO#trade_lifecycle
Feature: Trade Partial Termination API
  As a Trader
  I want to process PartialTermination lifecycle events for trades via the API
  So that the trade notional is automatically reduced when a partial termination occurs

  @ADO#positive @ADO#smoke @capability#partialTermination
  Scenario Outline: [TC-TRADE-PARTIAL-TERMINATION-<product_type>-API-001] Valid PartialTermination event reduces the <product_type> trade notional by the terminated amount
    Given a live '<product_type>' trade is created via api
    When maker has valid data for PartialTermination event
    And performs the PartialTermination event via api
    Then the PartialTermination event is executed successfully
    When maker retrieves the trade
    And the trade notional is reduced by the terminated amount

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-PARTIAL-TERMINATION-API-002] Malformed PartialTermination event with unrecognised amount field for a trade is rejected
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with unknown amount filed for PartialTermination event
    And performs the PartialTermination event via api
    Then the PartialTermination event is rejected with a validation error

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-PARTIAL-TERMINATION-API-003] PartialTermination event is rejected when newNotional exceeds the original notional for a trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with newNotional exceeds the original notional for PartialTermination event
    And performs the PartialTermination event via api
    Then the PartialTermination event is rejected with a validation error
