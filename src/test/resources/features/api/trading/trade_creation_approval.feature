@api @ADO#trade_creation_approval @ADO#trade_lifecycle
Feature: Trade Creation Approval API
  As a Checker
  I want to approve or reject submitted trades via API
  So that the 4-eyes control is enforced and a new trade becomes Live or returns to Draft

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-CREATION-APPROVAL-<product_type>-API-001] Checker approval of <product_type> trade creation transitions trade to Live
    Given a '<product_type>' trade is submitted to pending approval via api
    When checker has valid data for trade creation approval
    And performs the approval for the request
    When maker retrieves the trade
    Then the trade basic status is 'Live'

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-CREATION-APPROVAL-<product_type>-API-002] Checker rejection of <product_type> trade creation returns trade to Draft
    Given a '<product_type>' trade is submitted to pending approval via api
    When checker has valid data for trade creation rejection
    And performs the rejection for the request
    When maker retrieves the trade
    Then the trade basic status is 'Draft'

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-CREATION-APPROVAL-API-003] Maker is blocked from self-approving a trade creation task
    Given a 'FX_TRF' trade is submitted to pending approval via api
    When maker has valid data for trade creation self-approval
    And performs the approval for the request
    Then the 'trade creation self-approval' request is rejected with permission denied
