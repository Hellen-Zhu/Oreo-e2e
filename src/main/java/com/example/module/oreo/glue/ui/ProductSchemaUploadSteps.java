package com.example.module.oreo.glue.ui;

import com.microsoft.playwright.Locator;
import com.example.genie.module.database.glue.DatabaseGlue;
import com.example.module.oreo.utils.CleanupTestDataUtil;
import com.example.module.oreo.utils.DateUtil;
import com.example.module.oreo.utils.ProductApiClient;
import com.example.module.oreo.playwright.PlaywrightServiceResolver;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused") // Cucumber invokes step methods via reflection
public class ProductSchemaUploadSteps {

    private static final Logger log = LoggerFactory.getLogger(ProductSchemaUploadSteps.class);
    private static final Pattern PRODUCT_EDIT_URL_PATTERN = Pattern.compile(".*/product/([^/]+)/edit(?:[?#].*)?$");

    private PlaywrightServiceResolver.Context ctx;
    private DatabaseGlue databaseGlue;
    /**
     * The second rendered into every schema file the scenario uploads: a second upload of the same file is the same
     * product and version (the duplicate-version cases).
     */
    private long currentSysSeconds;

    @Before({"@playwright"})
    public void beforeScenario(Scenario scenario) {
        this.ctx = PlaywrightServiceResolver.from(scenario);
        this.databaseGlue = new DatabaseGlue();
        this.databaseGlue.before(scenario);
        this.currentSysSeconds = DateUtil.now().getEpochSecond();
    }

    @After(value = "@ADO#product_schema_upload", order = 2)
    public void deleteProductAfterTest() {
        log.info("[ProductSchemaUploadSteps] Cleaning up product data");
        String productId = ctx.get("createdProductId");
        try {
            CleanupTestDataUtil.cleanupTestProduct(databaseGlue, productId);
        } catch (AssertionError e) {
            log.warn("[ProductSchemaUploadSteps] SQL cleanup failed for productId '{}': {}",
                    productId, e.getMessage());
        }
    }

    /**
     * Uploads the schema file from current scenario test data into the schema upload form.
     *
     * <p>Example:
     * <pre>
     *   When schema file from current scenario 'schema.filePath' is uploaded for validation
     * </pre>
     */
    @When("^schema file from current scenario '([^']*)' is uploaded for validation$")
    public void schemaFileFromCurrentScenarioIsUploadedForValidation(String dotPath) {
        String classpathFile;
        try {
            classpathFile = ctx.get(dotPath);
        } catch (AssertionError e) {
            throw new AssertionError("No schema file path found in current scenario for key: " + dotPath, e);
        }

        if (classpathFile == null || classpathFile.isBlank()) {
            throw new AssertionError("No schema file path found in current scenario for key: " + dotPath);
        }

        Path uploadFile = ProductApiClient.schemaFile(classpathFile, currentSysSeconds);
        Locator input = ctx.service().getLocator("schema_upload.file_input");
        input.setInputFiles(uploadFile);

        log.info("[ProductSchemaUploadSteps] Uploaded file '{}' for schema validation (resolved path: {})",
                classpathFile, uploadFile);
    }

    @When("^schema preview is requested and completed$")
    public void schemaPreviewIsRequestedAndCompleted() {
        ctx.clickAndWaitForResponse("schema_upload.preview_btn", ProductApiClient.SCHEMA_PARSE_PATH, "POST");
    }

    @Then("^current product identifier is captured from edit url when available$")
    public void captureCurrentProductIdentifierFromEditUrlWhenAvailable() {
        String currentUrl = ctx.service().getPage().url();
        String productId = extractProductIdFromEditUrl(currentUrl);
        if (productId != null && !productId.isBlank()) {
            ctx.set("createdProductId", productId);
        }
    }

    private String extractProductIdFromEditUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Matcher matcher = PRODUCT_EDIT_URL_PATTERN.matcher(url);
        return matcher.matches() ? matcher.group(1) : null;
    }

}
