package com.wallet.txhistory.filter;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Path prefixes excluded from authentication and RBAC filters.
 */
public final class FilterPaths {

    static final List<String> EXCLUDED_PREFIXES = List.of(
            "/actuator", "/swagger-ui", "/v3/api-docs", "/h2-console");

    private FilterPaths() {}

    static boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
