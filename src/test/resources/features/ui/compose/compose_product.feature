@playwright @ADO#compose_product
Feature: Product Compose E2E
  As a Trader
  I want to configure product event definitions in the Compose page
  So that FX TRF products reflect the correct event canvas and lifecycle event configuration

  Background:
    Given a new product live in the compose page
    And maker is logged in to the trade portal

  @ADO#positive @ADO#smoke
  Scenario: [TC-COMPOSE-PRODUCT-UI-001] can update the product details and persist successfully
    Given navigates and opens the detail page for the product
    When perform amendment on product description
    Then the product is updated successfully
    And back and revisit detail page for the product
    Then the product description is displayed as amended value successfully

  @ADO#positive @ADO#smoke
  Scenario: [TC-COMPOSE-PRODUCT-UI-002] can add new event for product and persist successfully
    Given navigates and opens the detail page for the product
    When adds the cancellation event to the product canvas
    Then the product is updated successfully

    And back and revisit detail page for the product
    Then the cancellation event is available on the product canvas

  @ADO#positive @ADO#regression
  Scenario: [TC-COMPOSE-PRODUCT-UI-003] can remove event for product and persist successfully
    Given navigates and opens the detail page for the product
    And seed compose product with initial EarlyTermination event configuration
    When removes the EarlyTermination event from the product canvas
    Then the product is updated successfully
    And the EarlyTermination event is not available on the product canvas

  @ADO#positive @ADO#regression
  Scenario: [TC-COMPOSE-PRODUCT-UI-004] can update the product event details and persist successfully
    Given navigates and opens the detail page for the product
    And seed compose product with initial EarlyTermination event configuration
    # TRANSCRIPTION INCOMPLETE: IMG_6313 cuts off line 40 and subsequent content.
