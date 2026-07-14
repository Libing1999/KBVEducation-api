package com.kbv.education.exception;

/**
 * Thrown for invalid client input that isn't a bean-validation violation.
 * Maps to HTTP 400.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
