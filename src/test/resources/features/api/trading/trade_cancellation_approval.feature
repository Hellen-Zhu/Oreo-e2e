@api @ADO#trade_cancellation_approval @ADO#trade_lifecycle
Feature: Trade Cancellation Approval API
  As a Checker
  I want to approve or reject trade cancellations via API
  So that the 4-eyes control is enforced and a cancelled trade becomes Dead or stays Live

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-APPROVAL-<product_type>-API-001] Checker approves <product_type> cancellation and trade transitions to Dead
    Given a live '<product_type>' trade with a pending Cancellation exists
    When checker has valid data for cancellation approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the trade basic status is 'Dead'

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#positive @ADO#regression @capability#cancellation
  Scenario Outline: [TC-TRADE-CANCELLATION-APPROVAL-<product_type>-API-002] Checker rejects <product_type> cancellation and trade reverts to Live
    Given a live '<product_type>' trade with a pending Cancellation exists
    When checker has valid data for cancellation rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the trade basic status is 'Live'

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-APPROVAL-API-003] Maker is blocked from self-approving a FX_TRF cancellation task
    Given a live 'FX_TRF' trade with a pending Cancellation exists
    When maker has valid data for cancellation self-approval
    And performs the approval for the request
    Then the 'cancellation self-approval' request is rejected with permission denied

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-CANCELLATION-APPROVAL-API-004] Checker bulk approves all pending FX_TRF cancellation tasks in one transaction
    Given multiple live 'FX_TRF' trades have pending cancellation requests
    When checker has valid data for bulk cancellation approval
    And performs the bulk approval for the request
    Then the 'cancellation tasks' are approved successfully
