@playwright @ADO#trade_allocation @ADO#trade_lifecycle
Feature: Trade Allocation E2E
  As a Trader
  I want to allocate a Live FX TRF trade in the portal
  So that the notional is distributed across sub-trades and submitted for Checker approval

  @ADO#positive @ADO#smoke @capability#allocation
  Scenario Outline: [TC-TRADE-ALLOCATION-<product_type>-UI-001] Live <product_type> trade splits into two equal sub-trades after 50/50 allocation
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform equal allocation for the trade
    Then the allocation is completed successfully
    And the trade is allocated to Dead in the trade details

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-ALLOCATION-UI-002] Live trade splits into four sub-trades after 30/30/30/10 allocation successfully
    Given a Live 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform unequal allocation for the trade
    Then the allocation is completed successfully
    And the trade is allocated to Dead in the trade details
