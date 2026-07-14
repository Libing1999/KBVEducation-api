package com.kbv.education.exception;

/**
 * Thrown when creating a resource that violates a uniqueness constraint
 * (e.g. duplicate email). Maps to HTTP 409.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
