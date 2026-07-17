package com.kbv.education.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.response.ApiError;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory sliding-window rate limiter on the unauthenticated, enumerable
 * auth endpoints (login/refresh/forgot-password) — the highest-value target
 * given there's no rate limiting anywhere in the app otherwise. Single-
 * instance only (a multi-instance deployment would need a shared store like
 * Redis instead of this in-process map) — a documented limitation
 * proportionate to introducing rate limiting at all versus adding new
 * infrastructure the app doesn't otherwise have.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password");
    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> requestLog = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + request.getRequestURI();
        long now = Instant.now().toEpochMilli();
        CopyOnWriteArrayList<Long> hits = requestLog.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        hits.removeIf(t -> now - t > WINDOW_MILLIS);

        if (hits.size() >= MAX_REQUESTS) {
            ErrorCode code = ErrorCode.RATE_LIMITED;
            response.setStatus(code.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = ApiError.builder()
                    .code(code.name())
                    .status(code.getStatus().value())
                    .path(request.getRequestURI())
                    .build();
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code.getDefaultMessage(), error));
            return;
        }

        hits.add(now);
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
