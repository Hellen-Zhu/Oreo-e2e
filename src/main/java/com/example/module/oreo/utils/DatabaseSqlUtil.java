package com.example.module.oreo.utils;

import com.example.genie.module.database.glue.DatabaseGlue;

import java.util.List;

public final class DatabaseSqlUtil {

    private DatabaseSqlUtil() {
        // Utility class
    }

    public static void executeSqlUpdates(DatabaseGlue databaseGlue, String... sqlStatements) {
        try {
            databaseGlue.executeSqlUpdates(List.of(sqlStatements));
        } catch (Throwable e) {
            throw new AssertionError("Failed to execute SQL updates: " + e.getMessage(), e);
        }
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
