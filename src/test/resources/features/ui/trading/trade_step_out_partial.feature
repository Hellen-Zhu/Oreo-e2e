@playwright @ADO#trade_step_out_partial @ADO#trade_lifecycle
Feature: Trade Step Out Partial E2E
  As a Trader
  I want to view the correct trade status after a Step Out Partial event is processed
  So that I can confirm the trade has been partially stepped out in the system

  @ADO#positive @ADO#smoke @capability#stepOutPartial
  Scenario Outline: [TC-TRADE-STEP-OUT-PARTIAL-<product_type>-UI-001] should able to perform Step Out Partial event for <product_type> trade successfully
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform the Step Out Partial event on the '<product_type>' trade
    Then the step out partial is completed successfully
    And the trade remains Live and is marked as Novated

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
