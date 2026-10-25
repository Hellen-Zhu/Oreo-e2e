@api @ADO#trade_blotter
Feature: Trade Blotter API
  As a user
  I want to save, publish, list, and import blotters through the API
  So that personal blotter views and marketplace copies stay consistent

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-BLOTTER-API-001] Should able to create and persist a new blotter
    Given maker have valid data for blotter creation
    When create the blotter via api
    Then the blotter should be created and persisted successfully

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-API-002] Should able to publish a saved blotter to marketplace
    Given a blotter was created via api
    When maker have valid data to publish the blotter
    And publish the saved blotter to marketplace
    Then marketplace blotter was published successfully

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-API-003] Should able to re-publish a saved blotter to marketplace and update version
    Given a blotter was published as public via api
    When maker have valid data to publish the blotter
    And re-publish the saved blotter to marketplace
    Then marketplace blotter was published successfully and version updated

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-API-004] Should able to import a marketplace blotter
    Given a blotter was published as public via api
    When user have valid data to import the blotter
    And import marketplace blotter as personal copy
    Then marketplace blotter was imported successfully

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-API-005] Should unable to import a non-public blotter
    Given a blotter was created via api
    When maker have invalid data to import the blotter
    And import blotter as personal copy
    Then the import request is rejected with a validation error

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-API-006] Should able to update a blotter as public marketplace blotter
    Given a blotter was created via api
    When maker have valid data to update blotter
    And update the saved blotter to public
    Then marketplace blotter was updated successfully

  @ADO#positive @ADO#regression
  Scenario Outline: [TC-TRADE-BLOTTER-API-007] Should able to retrieve all blotters with correct total count
    When maker retrieves all available trade blotters
    Then all blotters are retrieved successfully
    And all configured blotters are returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(*) AS count FROM uc_user_blotter WHERE user_id in ('default','maker@example.com')|
      | count |

  @ADO#positive @ADO#regression
  Scenario Outline: [TC-TRADE-BLOTTER-API-008] Should able to retrieve all public blotters with correct total count
    When maker retrieves all available public trade blotters
    Then all blotters are retrieved successfully
    And all configured public blotters are returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(*) AS count FROM uc_blotter_marketplace|
      | count |
