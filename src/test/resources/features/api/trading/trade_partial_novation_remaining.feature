@api @ADO#trade_partial_novation_remaining @ADO#trade_lifecycle
Feature: Trade PartialNovationRemaining API
  As a Maker
  I want to trigger PartialNovationRemaining events for Live FX trades via API
  So that trades are partially novated with the original trade retaining the remaining notional and a new trade created for the novated portion

  @ADO#positive @ADO#smoke @capability#partialNovationRemaining
  Scenario Outline: [TC-TRADE-PARTIAL-NOVATION-REMAINING-<product_type>-API-001] Should be able to perform PartialNovationRemaining event for a live <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for PartialNovationRemaining event
    And performs the PartialNovationRemaining event via api
    Then the PartialNovationRemaining event is executed successfully
    And original trade is partially novated successfully
    And new trade is created with the correct notional successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-PARTIAL-NOVATION-REMAINING-API-002] PartialNovationRemaining event rejected when triggering trade id not found for a trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with a non-existent trade for PartialNovationRemaining event
    And performs the PartialNovationRemaining event via api
    Then the PartialNovationRemaining event is rejected with a validation error

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-PARTIAL-NOVATION-REMAINING-API-003] PartialNovationRemaining event rejected when notional data is missing for a trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with missing remaining notional for PartialNovationRemaining event
    And performs the PartialNovationRemaining event via api
    Then the PartialNovationRemaining event is rejected with a validation error
