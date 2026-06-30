package com.broksforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in every API error payload.
 *
 * <p>Clients should switch on {@link #name()} rather than parsing human-readable
 * messages, which are free to change.</p>
 */
@Getter
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    INSUFFICIENT_PERMISSIONS(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),

    CONFLICT(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ALREADY_MEMBER(HttpStatus.CONFLICT),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
