package com.kbv.education.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.response.ApiError;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.service.SystemSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When {@code system_settings.maintenance_mode} is on, every request from a
 * non-SUPER_ADMIN caller gets a 503 instead of reaching its controller — an
 * admin can still sign in and flip it back off. Runs after
 * {@link JwtAuthenticationFilter} so the resolved role is available; public
 * endpoints (login, public settings, docs) are exempted so the app can still
 * bootstrap and an admin can still authenticate during maintenance.
 */
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExempt(request) || !systemSettingsService.getActiveEntity().isMaintenanceMode()) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (isAdmin) {
            chain.doFilter(request, response);
            return;
        }

        ErrorCode code = ErrorCode.MAINTENANCE_MODE;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.builder()
                .code(code.name())
                .status(code.getStatus().value())
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code.getDefaultMessage(), error));
    }

    private boolean isExempt(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/") || uri.equals("/api/settings/public")
                || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui") || uri.startsWith("/actuator");
    }
}
