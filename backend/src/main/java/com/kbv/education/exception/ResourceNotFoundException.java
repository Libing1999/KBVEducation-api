package com.kbv.education.exception;

/**
 * Thrown when a requested resource cannot be found. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s not found with identifier: %s".formatted(resource, identifier));
    }
}
