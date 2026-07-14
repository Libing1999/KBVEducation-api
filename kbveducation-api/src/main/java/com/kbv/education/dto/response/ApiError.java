package com.kbv.education.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Structured error detail carried inside {@link ApiResponse#getError()}.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /** Machine-readable error code, e.g. {@code RESOURCE_NOT_FOUND}. */
    private final String code;

    /** HTTP status code. */
    private final int status;

    /** Request path that produced the error. */
    private final String path;

    /** Field-level validation violations, when applicable. */
    @Singular("fieldError")
    private final List<FieldValidationError> fieldErrors;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FieldValidationError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}
