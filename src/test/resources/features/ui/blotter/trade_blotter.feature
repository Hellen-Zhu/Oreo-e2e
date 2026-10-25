@playwright @ADO#trade_blotter
Feature: Trade Blotter E2E
  As a user
  I want to save and import blotters from the portal
  So that my personal blotter views and marketplace copies are usable in the UI

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-BLOTTER-UI-001] Should able to create a new blotter and access it from the blotter list
    Given admin is logged in to the trade portal
    When create a custom blotter from trade portal
    Then the blotter was created successfully
    And the blotter is accessible with customized columns in the blotter list

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-UI-002] Should able to create a public blotter and access it from the marketplace
    Given admin is logged in to the trade portal
    When create a public custom blotter from trade portal
    Then the blotter was created successfully
    And the blotter is accessible in the marketplace list

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-UI-003] Import a marketplace blotter and get an editable saved copy
    Given a public blotter trade exists in the marketplace
    And user is logged in to the trade portal
    When import the public blotter from marketplace
    Then blotter is imported successfully
    And the imported blotter is accessible in the transaction blotter list

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-UI-004] Should able to edit a blotter and see the changes reflected in the blotter list
    Given a blotter trade exists in the marketplace
    And admin is logged in to the trade portal
    And show the blotter in trade portal page
    When edit the blotter to add customized column to show in the trade portal page
    Then the blotter was updated successfully
    And the blotter is accessible with customized columns in the blotter list

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-BLOTTER-UI-005] Should able to delete a blotter and see the changes reflected in the blotter list
    Given a blotter trade exists in the marketplace
    And admin is logged in to the trade portal
    And show the blotter in trade portal page
    When delete the blotter from the trade portal page
    Then the blotter was deleted successfully
    And the blotter is not accessible in the blotter list
