@playwright @ADO#trade_novation_remaining @ADO#trade_lifecycle
Feature: Trade Novation Remaining E2E
  As a Trader
  I want to view the correct trade status after a Novation Remaining event is processed
  So that I can confirm the trade has been partially novated in the system

  @ADO#positive @ADO#smoke @capability#novationRemaining
  Scenario Outline: [TC-TRADE-NOVATION-REMAINING-<product_type>-UI-001] should able to perform Novation Remaining event for <product_type> trade successfully
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform the Novation Remaining event on the '<product_type>' trade
    Then the novation remaining is completed successfully
    And the original trade is closed as Novated and new novated trade is live

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
