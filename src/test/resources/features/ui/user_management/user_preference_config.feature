@playwright @ADO#user_preference_config
Feature: User Preference Configuration E2E
  As a Trader
  I want my preference settings to take effect immediately and persist reliably
  So that my configured format, theme, and view preferences are consistent across sessions

  Background:
    Given user preferences are set to application defaults
    And a Live 'FX_TRF' trade exists in the blotter
    And user is logged in to the trade portal

  @ADO#positive @ADO#smoke
  Scenario: [TC-USER-PREFERENCE-CONFIG-UI-001] blotter change takes effect and persists after page reload
    When changes the number format preference to 'EU' and date format preference to 'EU'
    Then the number format is applied for 'EU' and date format is applied for 'EU' in the blotter
    And the number and date format preferences persist after refresh for 'EU'

  @ADO#positive @ADO#smoke
  Scenario: [TC-USER-PREFERENCE-CONFIG-UI-002] Preference settings are preserved after logout and re-login
    Given none blotter view is visible in the application
    When updates theme and blotter view preferences
    Then the updated theme and blotter view preferences are applied
    When user logs out and re-login
    Then the updated theme and blotter view preferences persist after re-login
