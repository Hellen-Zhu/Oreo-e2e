@api @ADO#trade_allocation @ADO#trade_lifecycle
Feature: Trade Allocation Event API
  As a Trader
  I want to trigger allocation events for trades via API
  So that trades are split into sub-trades and submitted for Checker approval

  @ADO#positive @ADO#smoke @capability#allocation
  Scenario Outline: [TC-TRADE-ALLOCATION-<product_type>-API-001] 2-way equal allocation executes successfully for Live <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for 2-way equal Allocation event
    And performs the Allocation event via api
    Then the Allocation event is executed successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#positive @ADO#regression @capability#allocation
  Scenario Outline: [TC-TRADE-ALLOCATION-<product_type>-API-002] 3-way unequal allocation executes successfully for <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for 3-way unequal Allocation event
    And performs the Allocation event via api
    Then the Allocation event is executed successfully

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-ALLOCATION-API-003] Allocation rejected with 400 when notional don't sum to parent for FX_TRF trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with notional amounts not summing to parent trade for Allocation event
    And performs the Allocation event via api
    Then the Allocation event is rejected with a validation error

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-ALLOCATION-API-004] Allocation rejected with 400 when sub-trade arrays are empty for trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with an empty sub-trade list for Allocation event
    And performs the Allocation event via api
    Then the Allocation event is rejected with a validation error

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-ALLOCATION-API-005] Allocation rejected with 400 when trade ID does not exist for FX_TRF trade
    When maker has invalid data for a non-existent trade
    And performs the Allocation event via api
    Then the Allocation event is rejected with a validation error

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-ALLOCATION-API-006] Allocation rejected with 400 when FX_TRF trade is already Allocated
    Given a trade is allocated with 2-way equal split via api
    When maker has invalid data with the already-allocated trade
    And performs the Allocation event via api
    Then the Allocation event is rejected with a business rule violation

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-ALLOCATION-API-007] Allocation rejected with 400 when notional exceed parent notional for FX_TRF trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with notional exceeding the parent trade value
    And performs the Allocation event via api
    Then the Allocation event is rejected with a validation error

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-ALLOCATION-API-008] Allocation rejected with 400 when sub-trade array lengths are mismatched for FX_TRF trade
    Given a live 'FX_TRF' trade is created via api
    When maker has invalid data with mismatched sub-trade array lengths
    And performs the Allocation event via api
    Then the Allocation event is rejected with a validation error
