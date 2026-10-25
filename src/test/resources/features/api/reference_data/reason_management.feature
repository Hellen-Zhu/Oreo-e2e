@api @ADO#reason_management
Feature: Reason Management API
  As a trade operations admin
  I want to manage lifecycle event reasons via API
  So that valid reason codes are available for trade cancellations and terminations

  @ADO#positive @ADO#smoke
  Scenario Outline: [TC-REASON-MANAGEMENT-API-001] List all reasons returns a non-empty collection with correct total count
    When retrieves all available trade lifecycle event reasons
    Then all configured reasons are returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(*) AS count FROM reasons|
      | count |

  @ADO#positive @ADO#smoke
  Scenario: [TC-REASON-MANAGEMENT-API-002] New reason is created and persisted after valid request
    Given admin has valid data for a new trade lifecycle event reason
    When creates the new reason
    Then the reason should be created and persisted successfully
