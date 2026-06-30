package com.broksforge.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Canonical error payload returned by every endpoint on failure.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Standard error response")
public class ApiError {

    @Schema(description = "Time the error occurred", example = "2026-06-30T12:00:00Z")
    private final Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "HTTP status reason phrase", example = "Bad Request")
    private final String error;

    @Schema(description = "Stable machine-readable error code", example = "VALIDATION_ERROR")
    private final String code;

    @Schema(description = "Human-readable description of the error")
    private final String message;

    @Schema(description = "Request path that produced the error", example = "/api/v1/auth/login")
    private final String path;

    @Schema(description = "Field-level validation errors, when applicable")
    private final List<FieldValidationError> errors;

    /**
     * A single field-level validation failure.
     */
    @Getter
    @Builder
    @Schema(name = "FieldValidationError")
    public static class FieldValidationError {

        @Schema(description = "Name of the offending field", example = "email")
        private final String field;

        @Schema(description = "Rejected value")
        private final Object rejectedValue;

        @Schema(description = "Validation message", example = "must be a well-formed email address")
        private final String message;
    }
}
