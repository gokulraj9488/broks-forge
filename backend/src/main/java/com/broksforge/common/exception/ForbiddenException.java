package com.broksforge.common.exception;

/**
 * Thrown when an authenticated caller lacks permission for the requested
 * action. Translated to HTTP 403.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
