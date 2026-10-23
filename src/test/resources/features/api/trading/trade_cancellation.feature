@api @ADO#trade_cancellation @ADO#trade_lifecycle
Feature: Trade Cancellation API
  As a Trader
  I want to trigger a cancellation event on a trade via the API
  So that the cancellation is accepted by the backend and the trade transitions to the correct state

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-API-001] Cancellation event is accepted for a live <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for Cancellation event
    And performs the Cancellation event via api
    Then the Cancellation event is executed successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-CANCELLATION-API-002] Backend rejects a cancel request submitted by a checker for FX_TRF trade
    Given a live 'FX_TRF' trade is created via api
    When checker has valid data for Cancellation event
    And performs the Cancellation event via api
    Then the Cancellation event is rejected with permission denied

  @ADO#negative @ADO#regression @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-API-003] Cancellation is rejected for a Draft Pending Approval <product_type> trade
    Given a '<product_type>' trade is submitted to pending approval via api
    When maker has valid data for Cancellation event
    And performs the Cancellation event via api
    Then the Cancellation event is rejected with a business rule violation

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-API-004] Cancellation is rejected for a dead FX_TRF trade
    Given a dead 'FX_TRF' trade is created via api
    When maker has valid data for Cancellation event
    And performs the Cancellation event via api
    Then the Cancellation event is rejected with a business rule violation
