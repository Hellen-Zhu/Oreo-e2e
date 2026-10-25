@api @ADO#trade_audit @ADO#trade_lifecycle @expected#none
Feature: Audit Trail API
  As a Trader
  I want the system to maintain a complete and accurate audit trail of all trade lifecycle events
  So that I can review the history of trade events for compliance and analysis purposes

  @ADO#smoke @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-001] audit_entries records SUBMIT after a trade creation
    Given a 'FX_TRF' trade is submitted to pending approval via api
    Then exactly one new audit record exists for action 'SUBMIT' on the created trade
    And the audit_entries record contains all mandatory fields

  @ADO#smoke @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-002] audit_entries records CHECKER_APPROVED after checker approves a trade
    Given a 'FX_TRF' trade is submitted to pending approval via api
    When checker approves the pending task
    Then exactly one new audit record exists for action 'CHECKER_APPROVED' on the created trade
    And the audit_entries record contains all mandatory fields

  @ADO#regression @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-003] audit_entries records CHECKER_REJECTED after checker rejects a trade
    Given a 'FX_TRF' trade is submitted to pending approval via api
    When checker rejects the pending task
    Then exactly one new audit record exists for action 'CHECKER_REJECTED' on the created trade
    And the audit_entries record contains all mandatory fields

  @ADO#regression @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-004] audit_entries records AMENDMENT after a trade is amended
    When a live 'FX_TRF' trade with a pending Amendment exists
    Then exactly one new audit record exists for action 'AMENDMENT' on the created trade
    And the audit_entries record contains all mandatory fields

  @ADO#regression @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-005] audit_entries records CANCELLATION after a trade is cancelled
    Given a live 'FX_TRF' trade with a pending Cancellation exists
    When checker approves the pending task
    Then exactly one new audit record exists for action 'CANCELLATION' on the created trade
    And the audit_entries record contains all mandatory fields

  @ADO#regression @ADO#negative
  Scenario: [TC-TRADE-AUDIT-API-006] Batch event rejected when a trade ID is invalid, audit_entries unchanged
    Given a live 'FX_TRF' trade is created via api
    When checker has invalid Cancellation data for a non-existent trade
    And performs the Cancellation event via api
    And no cancellation record is written to audit_entries for that trade
    And the live trade state is preserved in the database

  @ADO#smoke @ADO#positive
  Scenario: [TC-TRADE-AUDIT-API-007] All lifecycle events produce audit records for a trade after dual-write removal
    Given a live 'FX_TRF' trade with a pending Cancellation exists
    When checker approves the pending task
    Then four lifecycle event audit records exist for the trade
