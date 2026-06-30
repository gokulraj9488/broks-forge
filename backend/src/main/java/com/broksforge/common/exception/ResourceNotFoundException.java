package com.broksforge.common.exception;

/**
 * Thrown when a requested resource does not exist (or is not visible to the
 * caller). Translated to HTTP 404.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s not found: %s".formatted(resource, identifier));
    }
}
