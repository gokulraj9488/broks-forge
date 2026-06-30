package com.broksforge.common.exception;

/**
 * Thrown when an operation conflicts with the current state of a resource
 * (e.g. a duplicate unique value). Translated to HTTP 409.
 */
public class ResourceConflictException extends ApiException {

    public ResourceConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ResourceConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
