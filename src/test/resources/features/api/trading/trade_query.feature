@api @ADO#trade_query @ADO#trade_lifecycle
Feature: Trade Query API
  As a Trader
  I want to retrieve a submitted trade
  So that the trade schedule is available for review before checker approval

  @ADO#positive @ADO#regression @capability#create
  Scenario Outline: [TC-TRADE-QUERY-<product_type>-API-001] Submitted <product_type> trade details are available for review before checker approval
    Given a '<product_type>' trade is submitted to pending approval via api
    When maker retrieves the trade
    Then the submitted '<product_type>' trade schedule is available for review

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_CO           |
      | FX_FSB          |
      | FX_PSCRIPT_TRF  |
      | FX_ARO          |
      | FX_ASO          |
      | FXBO            |
      | FX_FF           |
      | FX_FVA          |
      | FX_OOS          |
      | FX_RAO          |
      | FX_RVO          |
      | FX_AO           |
      | FXStructure     |
      | FX_ACCUMULATOR  |
      | FX_PSCRIPT_EASO |
      | FX_PSCRIPT_BOBO |
      | FX_PSCRIPT_CCK  |
      | FX_PSCRIPT_VBD  |
