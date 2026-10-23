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
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @capability#fixings
  Scenario Outline: [TC-TRADE-QUERY-<product_type>-API-002] Submitted <product_type> trade fixing data is available in the query response
    Given a '<product_type>' trade is submitted to pending approval via api
    When maker retrieves the trade
    And the queried trade contains empty fixing data
    And fixing data is prepared
    And maker retrieves the trade
    Then the queried trade contains expected fixing data

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |
