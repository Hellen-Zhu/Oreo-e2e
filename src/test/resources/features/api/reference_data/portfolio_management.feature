@api @ADO#portfolio_management
Feature: Portfolio Management API
  As an API consumer
  I want portfolio list endpoints with optional keyword search and detail retrieval by id
  So that I can browse and retrieve portfolio reference data consistently

  @ADO#positive @ADO#smoke
  Scenario: [TC-PORTFOLIO-MANAGEMENT-API-001] Portfolio list endpoint returns paged response without keyword
    When retrieves portfolios with paging parameters
    Then portfolios list should be retrieved successfully

  @ADO#positive @ADO#regression
  Scenario: [TC-PORTFOLIO-MANAGEMENT-API-002] Portfolio list endpoint applies keyword search
    When searches portfolios by keyword
    Then portfolios search results should match requested keyword

  @ADO#positive @ADO#smoke
  Scenario: [TC-PORTFOLIO-MANAGEMENT-API-003] Portfolio detail endpoint returns detail for an existing id
    When retrieves portfolio detail by id
    Then portfolio detail should be retrieved successfully
