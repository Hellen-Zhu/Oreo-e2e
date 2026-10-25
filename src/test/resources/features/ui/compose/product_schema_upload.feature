@playwright @ADO#product_schema_upload
Feature: Product Schema Upload E2E
  As a Product Administrator
  I want to upload and confirm product JSON schema files in Product Composer
  So that extracted schema metadata and fields can be reviewed before commit and versions are traceable

  Background:
    Given admin is logged in to the trade portal

  @ADO#positive @ADO#regression @ADO#smoke @capability#productUpload
  Scenario Outline: [TC-PRODUCT-SCHEMA-UPLOAD-<product_type>-UI-001] should able to create <product_type> product with valid json schema successfully
    When uploaded a valid json schema file and reviewed parsed schema preview
    Then should be able to save the product successfully
    And the product is accessible in the product list and detail page

    Examples:
      | product_type    |
      | FX_TRF          |
      | FX_PSCRIPT      |
      | FX_PSCRIPT_FSKO |
      | FX_PSCRIPT_TRF  |

  @ADO#positive @ADO#regression @ADO#smoke
  Scenario: [TC-PRODUCT-SCHEMA-UPLOAD-UI-002] Re-uploading same CorrOption version requires overwrite confirmation before save
    When an existing schema version is ready for overwrite re-upload
    Then overwrite is acknowledged for duplicate schema version submission
    And duplicate schema version is saved successfully and recorded in schema history

  @ADO#positive @ADO#regression @ADO#smoke
  Scenario: [TC-PRODUCT-SCHEMA-UPLOAD-UI-003] Editing product fields from detail page is persisted successfully
    When a product schema is uploaded and committed successfully
    Then the first product field display label is updated and saved from product detail page
    And the updated display label remains after reopening product fields editor
