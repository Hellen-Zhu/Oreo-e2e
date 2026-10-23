@playwright @ADO#trade_approve_reject @ADO#trade_lifecycle
Feature: Trade Approval-Reject E2E
  As a Checker
  I want to approve and reject FX TRF trades in the portal
  So that trades progress through the correct trade states and are visible in the blotter

  @ADO#positive @ADO#smoke @capability#create
  Scenario Outline: [TC-TRADE-APPROVAL-REJECT-<product_type>-UI-001] <product_type> Trade is Live after Maker submits and Checker approves
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
  Scenario Outline: [TC-TRADE-APPROVAL-REJECT-<product_type>-UI-002] <product_type> Trade reverts to Draft when Checker rejects the submission
    Given a Draft Pending Approval '<product_type>' trade exists in the blotter
    When checker is logged in to the trade portal
    When rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the trade is reverted to Draft in trade details

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @capability#stepInFull
  Scenario Outline: [TC-TRADE-APPROVAL-REJECT-<product_type>-UI-003] <product_type> StepIn Full trade is Live after Maker submits and Checker approves
    Given a Draft Pending Approval '<product_type>' StepIn Full trade exists in the blotter

    When checker is logged in to the trade portal
    And approves the trade from the trade detail page
    Then the trade is approved successfully
    And the StepIn Full trade is Live in trade details

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @capability#stepInPartial
  Scenario Outline: [TC-TRADE-APPROVAL-REJECT-<product_type>-UI-004] <product_type> StepIn Partial trade reverts to Draft after Maker submits and Checker rejects
    Given a Draft Pending Approval '<product_type>' StepIn Partial trade exists in the blotter

    When checker is logged in to the trade portal
    And rejects the pending trade from trade portal
    Then the trade is rejected successfully
    And the StepIn Partial trade is reverted to Draft

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECT-UI-006] Approve and Reject actions are absent for the Maker who submitted the trade
    Given a Draft Pending Approval 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then cannot approve or reject the Pending Approval trade

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-APPROVAL-REJECT-UI-007] Applicable lifecycle actions are available for Maker on Live trade
    Given a Live 'FX_TRF' trade exists in the blotter
    And maker is logged in to the trade portal
    Then can perform lifecycle actions on the Live trade
