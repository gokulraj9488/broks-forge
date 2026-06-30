package com.broksforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all domain/application exceptions. Carries a stable
 * {@link ErrorCode} that the {@code GlobalExceptionHandler} translates into an
 * HTTP status and structured error payload.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
