@playwright @ADO#trade_amendment @ADO#trade_lifecycle
Feature: Trade Amendment E2E
  As a Trader
  I want to amend an FX TRF trade from the trade detail page
  So that amendment are approved by a Checker and the correct values are displayed after each decision

  @ADO#positive @ADO#smoke @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-UI-001] Maker Should be able to perform Amendment for a live <product_type> Trade
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    And perform amendment for Counterparty on the trade
    Then the amendment is submitted successfully
    And the trade is amended to the updated Counterparty value for approval

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-AMENDMENT-UI-002] Checker Should be able to perform Amendment for a live Trade
    Given a Live 'FX_TRF' trade exists in the blotter
    And checker is logged in to the trade portal
    And perform amendment for Counterparty on the trade
    Then the amendment is submitted successfully
    And the trade is amended to the updated Counterparty value and keeps as Live

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-AMENDMENT-UI-003] Should be able to perform Amendment for a saved Draft Trade
    Given a saved Draft 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    And perform amendment for Counterparty for the draft trade
    Then the amendment is submitted successfully
    And the trade is amended to the updated Counterparty value and keeps as Draft
