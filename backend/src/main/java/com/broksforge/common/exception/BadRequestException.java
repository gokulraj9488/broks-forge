package com.broksforge.common.exception;

/**
 * Thrown when the request is syntactically valid but semantically incorrect.
 * Translated to HTTP 400.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
