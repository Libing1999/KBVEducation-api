package com.kbv.education.exception;

import lombok.Getter;

/**
 * Base type for all handled application exceptions. Carries an {@link ErrorCode}
 * so the {@link GlobalExceptionHandler} can derive the HTTP status and a
 * machine-readable code without instanceof ladders.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
