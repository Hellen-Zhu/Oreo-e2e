@playwright @ADO#trade_novation_partial_remaining @ADO#trade_lifecycle
Feature: Trade Partial Novation Remaining Party E2E
  As a Trader
  I want to view the correct trade status after a Partial Novation Remaining event is processed
  So that I can confirm the trade has been partially novated in the system

  @ADO#positive @ADO#smoke @capability#partialNovationRemaining
  Scenario Outline: [TC-TRADE-PARTIAL-NOVATION-REMAINING-<product_type>-UI-001] should able to perform Partial Novation event for <product_type> trade successfully
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform the Partial Novation Remaining event on the '<product_type>' trade from portal page
#    Then the partial novation remaining is completed successfully
    Then trade is partially novated with remaining notional retained and counterparty transferred for '<product_type>'

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#smoke
  Scenario: [TC-TRADE-PARTIAL-NOVATION-REMAINING-UI-002] should able to perform Partial Novation event from the trade detail page for FX_PSCRIPT_FSKO trade successfully
    Given a Live 'FX_PSCRIPT_FSKO' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform the Partial Novation Remaining event on the 'FX_PSCRIPT_FSKO' trade from detail page
    Then trade is partially novated with remaining notional retained and counterparty transferred for 'FX_PSCRIPT_FSKO'
