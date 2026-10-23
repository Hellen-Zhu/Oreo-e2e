@playwright @ADO#trade_amendment @ADO#trade_lifecycle
Feature: Trade Amendment E2E
  As a Trader
  I want to amend an FX TRF trade from the trade detail page
  So that amendment are approved by a Checker and the correct values are displayed after each decision

  @ADO#positive @ADO#smoke @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-UI-001] <product_type> Trade moves to Pending Approval after maker submits amendment
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal

    When perform amendment for Deal Date on the trade
    Then the amendment request is pending checker approval in trade details
    And Deal Date field shows the amended value in the trade detail page

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#positive @ADO#smoke @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-UI-002] <product_type> Trade returns to Live after checker approves amendment
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    And perform amendment for Counterparty on the trade
    And checker is logged in to the trade portal

    When approves the pending trade from trade portal
    Then the trade is approved successfully
    And the trade remains Live and is marked as Amended
    And the trade details show the amended Counterparty value

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression @capability#amendment
  Scenario Outline: [TC-TRADE-AMENDMENT-<product_type>-UI-003] <product_type> Trade reverts to Live after checker rejects amendment
    Given a Live '<product_type>' trade exists in the blotter
    And maker is logged in to the trade portal
    When perform amendment for Risk Portfolio on the trade

    When checker is logged in to the trade portal
    And rejects the trade from the trade detail page
    Then the trade is rejected successfully
    And the trade remains Live in trade details

    And Risk Portfolio field shows the original value in the trade detail page

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |

  @ADO#negative @ADO#regression
  Scenario: [TC-TRADE-AMENDMENT-UI-004] Checker not able to amend a live trade without maker permission
    Given a Live 'FX_TRF' trade exists in the blotter
    When checker is logged in to the trade portal
    And perform amendment for Risk Portfolio on the trade
    Then the trade remains Live and is marked as Amended
