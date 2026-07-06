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
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_LOCKED(HttpStatus.TOO_MANY_REQUESTS),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    INSUFFICIENT_PERMISSIONS(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),

    CONFLICT(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ALREADY_MEMBER(HttpStatus.CONFLICT),

    // --- Agent Registry (Phase 2) ---
    INVALID_ENDPOINT_URL(HttpStatus.BAD_REQUEST),
    CREDENTIAL_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
    AGENT_VERSION_ALREADY_EXISTS(HttpStatus.CONFLICT),
    AGENT_ARCHIVED(HttpStatus.CONFLICT),
    CREDENTIAL_ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // --- Intelligence Layer (Phase 3) ---
    DATASET_IMPORT_FAILED(HttpStatus.BAD_REQUEST),
    DATASET_EMPTY(HttpStatus.BAD_REQUEST),
    DATASET_VERSION_REQUIRED(HttpStatus.BAD_REQUEST),
    PROMPT_TEMPLATE_INVALID(HttpStatus.BAD_REQUEST),
    PROMPT_NO_ACTIVE_VERSION(HttpStatus.CONFLICT),
    EVALUATION_CONFIG_INVALID(HttpStatus.BAD_REQUEST),
    EVALUATION_JOB_NOT_RUNNABLE(HttpStatus.CONFLICT),
    EVALUATION_PROFILE_INVALID(HttpStatus.BAD_REQUEST),
    BENCHMARK_CONFIG_INVALID(HttpStatus.BAD_REQUEST),
    REGRESSION_CONFIG_INVALID(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_REPORT_FORMAT(HttpStatus.BAD_REQUEST),
    REPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    MODEL_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST),
    MODEL_INVOCATION_FAILED(HttpStatus.BAD_GATEWAY),

    // --- AI Engineering Advisor (Phase 4) ---
    ADVISOR_INPUT_INSUFFICIENT(HttpStatus.BAD_REQUEST),
    ROOT_CAUSE_INPUT_INVALID(HttpStatus.BAD_REQUEST),
    DEBUG_TIMELINE_UNAVAILABLE(HttpStatus.CONFLICT),
    KNOWLEDGE_PATTERN_NOT_FOUND(HttpStatus.NOT_FOUND),

    // --- Email transport ---
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
