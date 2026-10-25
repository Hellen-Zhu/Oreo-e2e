@api @ADO#marketer_management
Feature: Marketer Management API
  As a Data Owner
  I want to retrieve marketer reference data through the standardized refdata path
  So that marketer consumers keep the same business behavior after ownership alignment

  @ADO#positive @ADO#smoke
  Scenario Outline: [TC-MARKETER-MANAGEMENT-API-001] Active marketers can be retrieved successfully
    When retrieves all active marketers
    Then all active marketers are returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(*) AS count FROM marketers WHERE data ->> 'active' = 'true'|
      | count |

  @ADO#positive @ADO#smoke
  Scenario: [TC-MARKETER-MANAGEMENT-API-002] An existing marketer can be retrieved by id
    When retrieves the marketer by an existing id
    Then the marketer record is returned with the current business semantics

  @ADO#negative @ADO#regression
  Scenario: [TC-MARKETER-MANAGEMENT-API-003] A non-existing marketer id is rejected with not-found response
    When retrieves the marketer by a non-existing id
    Then the marketer lookup should be rejected with validation error

  @ADO#positive @ADO#smoke
  Scenario: [TC-MARKETER-MANAGEMENT-API-004] Marketer search returns results that match the keyword criteria
    When searches marketers by keyword
    Then the marketer search results match the keyword criteria
