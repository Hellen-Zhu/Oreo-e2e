@playwright @ADO#trade_step_out_full @ADO#trade_lifecycle
Feature: Trade Step Out Full E2E
  As a Trader
  I want to view the correct trade status after a Step Out Full event is processed
  So that I can confirm the trade has been fully stepped out in the system

  @ADO#positive @ADO#smoke @capability#stepOutFull
  Scenario Outline: [TC-TRADE-STEP-OUT-FULL-<product_type>-UI-001] should able to perform Step Out Full event for <product_type> trade successfully
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform the Step Out Full event on the '<product_type>' trade
    Then the step out full is completed successfully
    And the original trade is closed as Novated

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
