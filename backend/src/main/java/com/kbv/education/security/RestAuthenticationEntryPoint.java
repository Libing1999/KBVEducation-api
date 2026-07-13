package com.kbv.education.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.response.ApiError;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON {@link ApiResponse} 401 (instead of the servlet default) when
 * an unauthenticated request hits a protected endpoint.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
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
