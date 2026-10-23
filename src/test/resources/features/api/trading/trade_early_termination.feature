@api @ADO#trade_early_termination @ADO#trade_lifecycle
Feature: Trade Early Termination API
  As a Trader
  I want the system to process EarlyTermination lifecycle events for trades
  So that the trade status is correctly updated to Terminated when an early termination occurs

  @ADO#positive @ADO#smoke @capability#earlyTermination
  Scenario Outline: [TC-TRADE-EARLY-TERMINATION-<product_type>-API-001] <product_type> trade status is updated to Terminated when EarlyTermination event is received
    Given a live '<product_type>' trade is created via api
    When maker has valid data for EarlyTermination event
    And performs the EarlyTermination event via api
    And the EarlyTermination event processed successfully
    And maker retrieves the trade
    Then the trade basic event status is 'Terminated'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#regression @capability#earlyTermination
  Scenario Outline: [TC-TRADE-EARLY-TERMINATION-<product_type>-API-002] Malformed EarlyTermination event is rejected with error recorded for a <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has malformed data for EarlyTermination event
    And performs the EarlyTermination event via api
    Then the EarlyTermination event is rejected with a validation error

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#regression @capability#earlyTermination
  Scenario Outline: [TC-TRADE-EARLY-TERMINATION-<product_type>-API-003] Duplicate EarlyTermination event is rejected with error recorded for a <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for EarlyTermination event
    And performs the EarlyTermination event via api
    When maker has duplicate EarlyTermination event data
    And performs the EarlyTermination event via api
    Then the EarlyTermination event is rejected with a business rule violation

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
