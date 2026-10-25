@api @ADO#currency_management
Feature: Currency Management API
  As an API consumer
  I want a single currency list endpoint that returns the full governed currency code list
  So that portal currency selection can use backend-managed currency reference data consistently

  @ADO#positive @ADO#smoke
  Scenario Outline: [TC-CURRENCY-MANAGEMENT-API-001] Currency list endpoint returns the governed currency code list with the expected count
    When retrieves the full governed currency code list
    Then the governed currency code list is returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(DISTINCT currency_base) AS count FROM ref_currencies WHERE NULLIF(TRIM(currency_base), '') IS NOT NULL|
      | count |
