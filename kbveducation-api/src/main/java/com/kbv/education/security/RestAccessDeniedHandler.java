package com.kbv.education.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.response.ApiError;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON {@link ApiResponse} 403 when an authenticated user lacks the
 * required role/authority.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = ApiError.builder()
                .code(code.name())
                .status(code.getStatus().value())
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(code.getDefaultMessage(), error));
    }
}
