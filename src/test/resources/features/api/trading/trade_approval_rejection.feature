@api @ADO#trade_approval_rejection @ADO#trade_lifecycle
Feature: Trade Checker Approval API
  As a Checker
  I want to approve or reject pending trade tasks via API
  So that the 4-eyes control is enforced and trade state transitions are correctly applied

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-001] Maker is blocked from self-approving a trade amendment task
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When maker has valid data for amendment approval
    And performs the approval for the request
    Then the amendment self-approval request is rejected with permission denied

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-002] Checker approval of amendment persists amended fields and returns trade to Live
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When checker has valid data for amendment approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the amended fields are persisted on the trade

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-003] Checker rejection of amendment returns trade to Live with original fields intact
    Given a live 'FX_TRF' trade with a pending Amendment exists
    When checker has valid data for amendment rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the original trade fields are preserved after amendment rejection

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION<product_type>-API-004] Checker approval of <product_type> trade creation transitions trade to Live
    Given a '<product_type>' trade is submitted to pending approval via api
    When checker has valid data for trade creation approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the trade basic status is 'Live'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION<product_type>-API-005] Checker rejection of <product_type> trade creation returns trade to Draft
    Given a '<product_type>' trade is submitted to pending approval via api
    When checker has valid data for trade creation rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the trade basic status is 'Draft'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-006] Maker is blocked from self-approving a trade creation task
    Given a 'FX_TRF' trade is submitted to pending approval via api
    When maker has valid data for trade creation self-approval
    And performs the approval for the request
    Then the trade creation self-approval request is rejected with permission denied

  @ADO#positive @ADO#smoke @capability#cancellation
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION<product_type>-API-007] Checker approves <product_type> cancellation and trade transitions to Dead
    Given a live '<product_type>' trade with a pending Cancellation exists
    When checker has valid data for cancellation approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the trade basic status is 'Dead'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#positive @ADO#regression @capability#cancellation
  Scenario Outline: [TC-TRADE-APPROVAL-REJECTION<product_type>-API-008] Checker rejects <product_type> cancellation and trade reverts to Live
    Given a live '<product_type>' trade with a pending Cancellation exists
    When checker has valid data for cancellation rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the trade basic status is 'Live'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-009] Maker is blocked from self-approving a FX_TRF cancellation task
    Given a live 'FX_TRF' trade with a pending Cancellation exists
    When maker has valid data for cancellation approval
    And performs the approval for the request
    Then the cancellation approval request is rejected with permission denied

  @ADO#positive @ADO#regression
  Scenario: [TC-TRADE-APPROVAL-REJECTIONAPI-010] Checker bulk approves all pending FX_TRF cancellation tasks in one transaction
    Given multiple live 'FX_TRF' trades have pending cancellation requests
    When checker has valid data for bulk cancellation approval
    And performs the bulk approval for the request
    Then all cancellation tasks should be approved successfully
