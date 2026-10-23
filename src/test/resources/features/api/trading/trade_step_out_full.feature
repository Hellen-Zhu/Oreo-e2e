@api @ADO#trade_step_out_full @ADO#trade_lifecycle
Feature: Trade Step Out Full API
  As a Trader
  I want the system to process StepOutFull lifecycle events for live FX trades via API
  So that the original trade is fully novated and a new trade is created for the stepped-out position

  @ADO#positive @ADO#smoke @capability#stepOutFull
  Scenario Outline: [TC-TRADE-STEP-OUT-FULL-<product_type>-API-001] Original trade closes as Novated after StepOutFull for <product_type> trade
    Given a live '<product_type>' trade is created via api
    When maker has valid data for StepOutFull event
    And performs the StepOutFull event via api
    Then the StepOutFull event is executed successfully
    And the original trade is closed as Novated after StepOutFull

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
