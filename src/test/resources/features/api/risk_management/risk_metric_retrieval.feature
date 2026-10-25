@api @ADO#risk_metric_retrieval
Feature: Risk Metric Retrieval
  As a maker
  I want to retrieve risk metrics for FX TRF trades via bulk and per-metric endpoints
  So that risk exposure data is available for trading decisions and individual metrics can be fetched as a fallback when bulk retrieval fails

  @ADO#positive @ADO#smoke
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-001] All risk metrics are returned for a live trade in a single bulk request
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for bulk risk metrics retrieval
    And performs the bulk risk metrics retrieval request
    Then the risk metrics should be retrieved successfully

  @ADO#positive @ADO#regression
  Scenario Outline: [TC-RISK-METRIC-RETRIEVAL-API-<case>] Supported metric type <metricType> is accepted and returns correct value
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for the '<metricType>' risk metric retrieval
    And performs the single risk metric retrieval request
    Then the '<metricType>' risk metric should be retrieved successfully

    Examples:
      | case | metricType |
      | 002  | DELTA      |
      | 003  | GAMMA      |
      | 004  | VEGA       |
      | 005  | THETA      |
      | 006  | PNL        |

  @ADO#negative @ADO#regression
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-007] Per-metric request is rejected when an unsupported metric type is provided
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for unsupported risk metric retrieval
    And performs the single risk metric retrieval request
    Then the risk metrics request should be rejected
