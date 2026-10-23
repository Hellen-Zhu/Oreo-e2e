@playwright @ADO#trade_partial_termination @ADO#trade_lifecycle
Feature: Trade Partial Termination E2E
  As a Trader
  I want to view the updated notional and trade state after a PartialTermination event is processed
  So that I can confirm the trade reflects the correct remaining position

  @ADO#positive @ADO#smoke @capability#partialTermination
  Scenario Outline: [TC-TRADE-PARTIAL-TERMINATION-<product_type>-UI-001] Should able to perform PartialTermination for a live <product_type> trade successfully
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform Partial Termination event on the '<product_type>' trade
    Then the partial termination is completed successfully
    And the trade notional reflects the partial termination reduction in trade detail page

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
