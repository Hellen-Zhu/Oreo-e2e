@api @ADO#product_ids_management
Feature: Product IDs Management API
  As an API consumer
  I want a single product id list endpoint that returns the full governed product id list
  So that portal product id selection can use backend-managed product reference data consistently

  @ADO#positive @ADO#smoke
  Scenario Outline: [TC-PRODUCT-IDS-MANAGEMENT-API-001] Product id list endpoint returns the governed product id list with the expected count
    When retrieves available product ids
    Then the governed product id list is returned with a total count of '<count>'

    Examples:
      #@sql|SELECT COUNT(*) AS count FROM (SELECT DISTINCT product_id FROM ref_product_ids) t|
      | count |
