@playwright @ADO#trade_creation_approval @ADO#trade_lifecycle
Feature: Trade Creation Approval E2E
  As a Checker
  I want to approve and reject submitted trades in the portal
  So that new trades become Live or return to Draft and are visible in the blotter

  @ADO#positive @ADO#smoke @capability#create
  Scenario Outline: [TC-TRADE-CREATION-APPROVAL-<product_type>-UI-001] <product_type> Trade is Live after Maker submits and Checker approves
    Given a Draft Pending Approval '<product_type>' trade exists in the blotter
    When checker is logged in to the trade portal
    And approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade is Live and marked as New

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-CREATION-APPROVAL-<product_type>-UI-002] <product_type> Trade reverts to Draft when Checker rejects the submission
    Given a Draft Pending Approval '<product_type>' trade exists in the blotter
    When checker is logged in to the trade portal
    When rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the trade is Draft and marked as New

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-CREATION-APPROVAL-UI-003] StepIn Full trade is Live after Maker submits and Checker approves
    Given a Draft Pending Approval 'FX_TRF' StepIn Full trade exists in the blotter

    When checker is logged in to the trade portal
    And approves the trade from the trade detail page
    Then the trade is approved successfully
    And the StepIn Full trade is Live in trade details

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-CREATION-APPROVAL-UI-004] StepIn Partial trade reverts to Draft after Maker submits and Checker rejects
    Given a Draft Pending Approval 'FX_TRF' StepIn Partial trade exists in the blotter

    When checker is logged in to the trade portal
    And rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the StepIn Partial trade is Draft in trade details

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-CREATION-APPROVAL-UI-005] Approve and Reject actions are absent for the Maker who submitted the trade
    Given a Draft Pending Approval 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then cannot approve or reject the Pending Approval trade

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-CREATION-APPROVAL-UI-006] Applicable lifecycle actions are available for Maker on Live trade
    Given a Live 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then can perform lifecycle actions on the Live trade
