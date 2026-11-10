@playwright @ADO#trade_cancellation_approval @ADO#trade_lifecycle
Feature: Trade Cancellation Approval E2E
  As a Checker
  I want to approve and reject trade cancellations in the portal
  So that a cancelled trade is closed as Dead or stays Live in the blotter

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-CANCELLATION-APPROVAL-UI-001] Trade transitions to Dead status after checker confirms cancellation approval
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade is closed as Cancelled

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-CANCELLATION-APPROVAL-UI-002] Trade reverts to Live status after checker confirms cancellation rejection
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the trade is Live and marked as New

  @ADO#positive @ADO#regression @wip
  Scenario: [TC-TRADE-CANCELLATION-APPROVAL-UI-003] Checker bulk approves multiple pending cancellation trades in one action
    Given checker is logged in to the trade portal
    And multiple 'FX_TRF' trades in Pending Approval status with Cancellation tasks are visible in the blotter
    When selects all pending cancellation trades and submits a bulk approval
    Then all selected trades are closed as cancelled in the blotter
