@api @ADO#trade_amendment @ADO#trade_lifecycle
Feature: Trade Amendment API
  As a Trader
  I want to trigger trade amendment events for trades via API
  So that the trade's details are updated accurately in the system

  @ADO#positive @ADO#smoke @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-API-001] <product_type> trade terms are updated after Amendment event is processed
    Given a live '<product_type>' trade is created via api
    When maker has valid data for Trade Amendment
    And performs the Trade Amendment action via api
    Then the '<product_type>' trade should be updated with the amended terms

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-API-002] Amendment event is rejected for a <product_type> trade already pending checker approval
    Given a '<product_type>' trade pending amendment approval is created via api
    When maker has valid data for Trade Amendment
    And performs the Trade Amendment action via api
    Then the Amendment request is rejected with a conflict

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-API-003] Amendment of a Dead FX_TRF trade is rejected with a business rule violation
    Given a dead 'FX_TRF' trade is created via api
    When maker has valid data for Trade Amendment
    And performs the Trade Amendment action via api
    Then the Trade Amendment request is rejected with a business rule violation
