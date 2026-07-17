package com.kbv.education.exception;

import com.kbv.education.dto.response.ApiError;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.LogSeverity;
import com.kbv.education.service.ApplicationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolationException;

import java.util.List;

/**
 * Translates exceptions into the uniform {@link ApiResponse} envelope with an
 * appropriate HTTP status. All controller/service exceptions funnel through
 * here so error shapes stay consistent. Phase 5 Step 7 additionally persists
 * a tiered-severity {@code application_logs} row for the categories worth an
 * admin's attention (unhandled exceptions, auth/authz failures, upload
 * errors) — routine validation misses (400s) are deliberately NOT persisted
 * here, or they'd bury real incidents in the admin viewer's default filter.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApplicationLogService applicationLogService;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Handled ApiException [{}] at {}: {}", code, request.getRequestURI(), ex.getMessage());
        return build(code, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest request) {
        List<ApiError.FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> ApiError.FieldValidationError.builder()
                        .field(v.getPropertyPath().toString())
                        .message(v.getMessage())
                        .rejectedValue(v.getInvalidValue())
                        .build())
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), request, fieldErrors);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        log.warn("Authentication failure at {}: {}", request.getRequestURI(), ex.getMessage());
        applicationLogService.record(LogSeverity.WARNING, "AuthenticationException", ex.getMessage(), null,
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        applicationLogService.record(LogSeverity.WARNING, "AccessDeniedException", ex.getMessage(), null,
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getDefaultMessage(), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                  HttpServletRequest request) {
        log.warn("Upload exceeded max size at {}: {}", request.getRequestURI(), ex.getMessage());
        applicationLogService.record(LogSeverity.WARNING, "MaxUploadSizeExceededException", ex.getMessage(), null,
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        return build(ErrorCode.BAD_REQUEST, "Uploaded file exceeds the maximum allowed size", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        applicationLogService.record(LogSeverity.ERROR, ex.getClass().getSimpleName(), String.valueOf(ex.getMessage()),
                ExceptionUtils.getStackTrace(ex), request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
    }

    private ApiError.FieldValidationError toFieldError(FieldError error) {
        return ApiError.FieldValidationError.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .rejectedValue(error.getRejectedValue())
                .build();
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, HttpServletRequest request,
                                                    List<ApiError.FieldValidationError> fieldErrors) {
        HttpStatus status = code.getStatus();
        ApiError.ApiErrorBuilder errorBuilder = ApiError.builder()
                .code(code.name())
                .status(status.value())
                .path(request.getRequestURI());
        if (fieldErrors != null) {
            errorBuilder.fieldErrors(fieldErrors);
        }
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorBuilder.build()));
    }
}
