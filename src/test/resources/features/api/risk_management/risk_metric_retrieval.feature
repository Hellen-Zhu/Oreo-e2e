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

  @ADO#positive @ADO#smoke
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-002] Requested metric type and its value are returned when a single metric is fetched
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for single risk metric retrieval
    And performs the single risk metric retrieval request
    Then the risk metrics should be retrieved successfully

  @ADO#positive @ADO#regression
  Scenario Outline: [TC-RISK-METRIC-RETRIEVAL-API-<case>] Supported metric type <metricType> is accepted and returns a valid per-metric response
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for the '<metricType>' risk metric retrieval
    And performs the single risk metric retrieval request
    Then the '<metricType>' risk metric should be retrieved successfully

    Examples:
      | case | metricType |
      | 003  | DELTA      |
      | 007  | GAMMA      |
      | 008  | VEGA       |
      | 009  | THETA      |
      | 010  | PNL        |

  @ADO#negative @wip
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-004] Bulk risk metrics request returns a structured error when the risk engine is unavailable
    Given a live 'FX_TRF' trade is created via api
    And the risk engine is unavailable
    When maker has valid data for bulk risk metrics retrieval
    And performs the bulk risk metrics retrieval request
    Then the risk metrics request should be rejected

  @ADO#negative @wip
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-005] Per-metric request returns a structured error when the risk engine is unavailable
    Given a live 'FX_TRF' trade is created via api
    And the risk engine is unavailable
    When maker has valid data for single risk metric retrieval
    And performs the single risk metric retrieval request
    Then the risk metrics request should be rejected

  @ADO#negative @ADO#regression
  Scenario: [TC-RISK-METRIC-RETRIEVAL-API-006] Per-metric request is rejected when an unsupported metric type is provided
    Given a live 'FX_TRF' trade is created via api
    When maker has valid data for unsupported risk metric retrieval
    And performs the single risk metric retrieval request
    Then the risk metrics request should be rejected
