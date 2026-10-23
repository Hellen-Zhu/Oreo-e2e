@api @ADO#refdata_sync_management
Feature: Refdata Sync Management API
  As an internal ops caller
  I want to trigger reference data sync manually via API
  So that counterparty and portfolio reference data can be refreshed in a controlled way

  @ADO#positive
  Scenario Outline: [TC-REFDATA-SYNC-MANAGEMENT-API-<case>] <dataset> manual sync request is accepted successfully
    When triggers reference data sync
    Then the reference data sync should be accepted successfully

    Examples:
      | case | dataset      |
      | 001  | Counterparty |
      | 002  | Portfolio    |
      | 003  | Currency     |
