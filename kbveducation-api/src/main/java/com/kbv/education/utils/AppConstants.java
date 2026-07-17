package com.kbv.education.utils;

/**
 * Application-wide constants: API path prefixes, pagination defaults, and
 * security-related header names. Centralised to avoid magic strings.
 */
public final class AppConstants {

    private AppConstants() {
    }

    // API base paths
    public static final String API_BASE = "/api";
    public static final String AUTH_BASE = API_BASE + "/auth";
    public static final String ADMIN_BASE = API_BASE + "/admin";

    // Pagination defaults
    public static final int DEFAULT_PAGE_NUMBER = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "desc";

    // Security
    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    // Public endpoints (no authentication required)
    public static final String[] PUBLIC_ENDPOINTS = {
            AUTH_BASE + "/login",
            AUTH_BASE + "/refresh",
            AUTH_BASE + "/forgot-password",
            API_BASE + "/settings/public",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };
}
