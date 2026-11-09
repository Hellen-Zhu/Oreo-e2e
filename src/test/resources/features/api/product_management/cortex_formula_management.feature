@api @ADO#cortex_formula_management @ADO#product_management
Feature: Cortex Formula Configuration API
  As a product configuration consumer
  I want Cortex formula definitions to come from the backend
  So that formula choices are rendered without hardcoded frontend metadata

  @ADO#positive @ADO#smoke @ADO#regression @capability#cortexFormula
  Scenario Outline: [TC-CORTEX-FORMULA-MANAGEMENT-<product_type>-API-001] Product receives its complete active Cortex formula catalogue
    When retrieves Cortex formula configurations for product '<product_type>'
    Then the 'Cortex formula catalogue' is retrieved successfully

    Examples:
      | product_type   |
      | FX_TRF         |
      | FX_CO          |
      | FX_FSB         |
      | FX_PSCRIPT_TRF |
