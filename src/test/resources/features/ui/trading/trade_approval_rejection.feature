@playwright @ADO#trade_approval_rejection @ADO#trade_lifecycle
Feature: Trade APPROVAL-REJECTION E2E
  As a Checker
  I want to approve and reject FX TRF trades in the portal
  So that trades progress through the correct trade states and are visible in the blotter

  @ADO#positive @ADO#smoke @capability#create
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION-<product_type>-UI-001] <product_type> Trade is Live after Maker submits and Checker approves
    Given a Draft Pending Approval '<product_type>' trade exists in the blotter
    When checker is logged in to the trade portal
    And approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade is Live and marked as New

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION-<product_type>-UI-002] <product_type> Trade reverts to Draft when Checker rejects the submission
    Given a Draft Pending Approval '<product_type>' trade exists in the blotter
    When checker is logged in to the trade portal
    When rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the trade is Draft and marked as New

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-003] StepIn Full trade is Live after Maker submits and Checker approves
    Given a Draft Pending Approval 'FX_TRF' StepIn Full trade exists in the blotter

    When checker is logged in to the trade portal
    And approves the trade from the trade detail page
    Then the trade is approved successfully
    And the StepIn Full trade is Live in trade details

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-004] StepIn Partial trade reverts to Draft after Maker submits and Checker rejects
    Given a Draft Pending Approval 'FX_TRF' StepIn Partial trade exists in the blotter

    When checker is logged in to the trade portal
    And rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the StepIn Partial trade is Draft in trade details

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-005] Approve and Reject actions are absent for the Maker who submitted the trade
    Given a Draft Pending Approval 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then cannot approve or reject the Pending Approval trade

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-006] Applicable lifecycle actions are available for Maker on Live trade
    Given a Live 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then can perform lifecycle actions on the Live trade

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-007] Trade returns to Live after checker approves amendment
    Given a live 'FX_TRF' trade with a pending Amendment exists
    And checker is logged in to the trade portal
    When approves the trade from the trade detail page
    Then the trade is approved successfully
    And the trade remains Live in trade details
    And the trade details show the amended Counterparty value

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-008] Trade reverts to Live after checker rejects amendment
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When checker is logged in to the trade portal
    And rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the trade remains Live in trade details
    And the trade details show the original Counterparty value

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-009] Trade transitions to Dead status after checker confirms cancellation approval
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade is closed as Cancelled

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-010] Trade reverts to Live status after checker confirms cancellation rejection
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the trade is Live and marked as New

  @ADO#positive @ADO#regression @wip
  Scenario: [TC-TRADE-APPROVAL-REJECTION-UI-011] Checker bulk approves multiple pending cancellation trades in one action
    Given checker is logged in to the trade portal
    And multiple 'FX_TRF' trades in Pending Approval status with Cancellation tasks are visible in the blotter
    When selects all pending cancellation trades and submits a bulk approval
    Then all selected trades are closed as cancelled in the blotter
