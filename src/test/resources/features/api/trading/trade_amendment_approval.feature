@api @ADO#trade_amendment_approval @ADO#trade_lifecycle
Feature: Trade Amendment Approval API
  As a Checker
  I want to approve or reject trade amendments via API
  So that the 4-eyes control is enforced and a Live trade keeps the amended or the original fields

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-APPROVAL-API-001] Maker is blocked from self-approving a trade amendment task
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When maker has valid data for amendment self-approval
    And performs the approval for the request
    Then the 'amendment self-approval' request is rejected with permission denied

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-APPROVAL-API-002] Checker approval of amendment persists amended fields and returns trade to Live
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When checker has valid data for amendment approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the amended fields are persisted on the trade

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-APPROVAL-API-003] Checker rejection of amendment returns trade to Live with original fields intact
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When checker has valid data for amendment rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the original trade fields are preserved after amendment rejection
