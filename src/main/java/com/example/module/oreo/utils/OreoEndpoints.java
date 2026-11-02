package com.example.module.oreo.utils;

/**
 * The addresses several Java classes share. An address one class uses stays private to that class; the trade request
 * paths stay in {@link TradeApiClient}. API snippets use the built-in REST steps, not this class.
 *
 * <p>A request path is relative to {@code rest.base.url}. The browser sees the same path under {@link #API_PREFIX}, so
 * a UI wait matches {@code API_PREFIX + path}.
 */
public final class OreoEndpoints {

    public static final String API_PREFIX = "/api/v1";

    public static final String BLOTTER_TRADES = API_PREFIX + "/blotter/trades";
    public static final String USER_PREFERENCES = "/uc/api/user-preferences";
    public static final String CHECKER_TASKS_PATH = "/checker/tasks/";

    private OreoEndpoints() {
        // Utility class
    }

    public static String blotterPath(String blotterId) {
        return "/uc/blotters/" + blotterId;
    }

    public static String productByProductIdPath(String productId) {
        return "/products/productId/" + productId;
    }
}
