package com.example.module.oreo.utils;

import com.example.genie.module.database.glue.DatabaseGlue;
import com.example.genie.module.rest.service.RequestResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CleanupTestDataUtil {
    private static final Logger log = LoggerFactory.getLogger(CleanupTestDataUtil.class);

    private CleanupTestDataUtil() {
        // Utility class
    }

    public static void cleanupTestProduct(DatabaseGlue databaseGlue, String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }

        String escapedProductId = DatabaseSqlUtil.escapeSqlLiteral(productId);
        String deleteUserInputsSql = "DELETE FROM product_event_user_inputs WHERE product_event_config_id IN "
                + "(SELECT id FROM product_event_config WHERE product_id = '" + escapedProductId + "')";
        String deleteEventConfigSql = "DELETE FROM product_event_config WHERE product_id = '" + escapedProductId + "'";
        String deleteFieldConfigSql = "DELETE FROM product_field_config WHERE product_id = '" + escapedProductId + "'";
        String deleteSchemaVersionsSql = "DELETE FROM product_schema_versions WHERE product_id = '" + escapedProductId + "'";
        String deleteCatalogueSql = "DELETE FROM product_catalogue WHERE product_id = '" + escapedProductId + "'";
        DatabaseSqlUtil.executeSqlUpdates(
                databaseGlue,
                deleteUserInputsSql,
                deleteEventConfigSql,
                deleteFieldConfigSql,
                deleteSchemaVersionsSql,
                deleteCatalogueSql
        );
    }

    public static void deleteBlotter(RequestResponseHelper helper, String blotterId, String user) {
        if (blotterId == null || blotterId.isBlank()) {
            log.info("[CleanupTestDataUtil] No blotterId provided. Skipping deletion.");
            return;
        }

        String path = "/uc/blotters/" + blotterId + "?deletePublishedCopy=true";
        try {
            RestApiUtil.delete(helper, path, user);
            log.info("[CleanupTestDataUtil] Deleted blotter successfully. blotterId={}", blotterId);
        } catch (AssertionError e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // idempotent cleanup: already deleted -> treat as success
            if (msg.contains("User blotter not found")) {
                log.info("[CleanupTestDataUtil] Blotter not found for deletion. It may have already been deleted. blotterId={}", blotterId);
                return;
            }
            throw e;
        }
    }
}
