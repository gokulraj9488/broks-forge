package com.broksforge.common.exception;

/**
 * Thrown when authentication is required but missing or invalid.
 * Translated to HTTP 401.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
