@playwright @ADO#trade_amendment_approval @ADO#trade_lifecycle
Feature: Trade Amendment Approval E2E
  As a Checker
  I want to approve and reject trade amendments in the portal
  So that a Live trade keeps the amended or the original values after each decision

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-AMENDMENT-APPROVAL-UI-001] Trade returns to Live after checker approves amendment
    Given a Live 'FX_TRF' trade with a pending Amendment exists in the blotter
    And checker is logged in to the trade portal
    When approves the trade from the trade detail page
    Then the trade is approved successfully
    And the trade remains Live in trade details
    And the trade details show the amended Counterparty value

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-APPROVAL-UI-002] Trade reverts to Live after checker rejects amendment
    Given a Live 'FX_TRF' trade with a pending Amendment exists in the blotter
    When checker is logged in to the trade portal
    And rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the trade remains Live in trade details
    And the trade details show the original Counterparty value
