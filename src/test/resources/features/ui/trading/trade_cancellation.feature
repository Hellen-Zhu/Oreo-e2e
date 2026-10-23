@playwright @ADO#trade_cancellation @ADO#trade_lifecycle
Feature: Trade Cancellation E2E
  As a Trader
  I want to cancel a live trade from the trade portal
  So that the cancellation persists correctly and the trade transitions through the correct lifecycle states

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-UI-001] <product_type> Trade moves to Pending Approval after maker submits cancellation from trade portal page
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform cancellation for the trade from trade portal
    Then the cancellation is submitted successfully
    And the cancellation request is pending checker approval

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-UI-002] Cancellation option is available for a Draft Pending Approval trade for maker
    Given a Draft Pending Approval 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then the Cancellation option is available for the trade


  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-UI-003] Checker sees Approve and Reject action options on a pending cancellation trade row
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And the Approve and Reject option is available for the trade

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-UI-004] <product_type> Trade transitions to Dead status after checker confirms cancellation approval
    Given a '<product_type>' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade is closed as Cancelled

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-UI-005] <product_type> Trade reverts to Live status after checker confirms cancellation rejection
    Given a '<product_type>' trade in Pending Approval status with a Cancellation task is visible in the blotter

    When checker is logged in to the trade portal
    And rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the trade is Live and marked as New

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-UI-006] Approve and Reject action options are absent for the trade submitter
    Given a 'FX_TRF' trade in Pending Approval status with a Cancellation task is visible in the blotter
    Then the Approve and Reject option is not available for the trade

  @ADO#positive @ADO#regression @wip
  Scenario: [TC-TRADE-CANCELLATION-UI-007] Checker bulk approves multiple pending cancellation trades in one action
    Given checker is logged in to the trade portal
    And multiple 'FX_TRF' trades in Pending Approval status with Cancellation tasks are visible in the blotter
    When selects all pending cancellation trades and submits a bulk approval
    Then all selected trades are closed as cancelled in the blotter

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-<product_type>-UI-008] <product_type> Trade moves to Pending Approval after maker submits cancellation from trade detail page
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal

    When perform cancellation for the trade from trade detail page
    Then the cancellation is submitted successfully
    And the cancellation request is pending checker approval in trade details

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-UI-010] Cancellation option is not available for a Dead trade for maker
    Given a Dead 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then the Cancellation option is not available for the trade
