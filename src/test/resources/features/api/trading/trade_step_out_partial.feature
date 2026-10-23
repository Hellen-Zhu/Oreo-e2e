@api @ADO#trade_step_out_partial @ADO#trade_lifecycle
Feature: Trade Step Out Partial API
  As a Trader
  I want the system to process StepOutPartial lifecycle events for live FX trades via API
  So that the original trade retains the remaining notional and a new trade is created for the stepped-out portion

  @ADO#positive @ADO#smoke @capability#stepOutPartial
  Scenario Outline: [TC-TRADE-STEP-OUT-PARTIAL-<product_type>-API-001] Original trade remains Live as Novated after StepOutPartial for <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for StepOutPartial event
    And performs the StepOutPartial event via api
    Then the StepOutPartial event is executed successfully
    And the original '<product_type>' trade remains Live as Novated after StepOutPartial

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
