package com.broksforge.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Translates every exception raised by the application into a consistent
 * {@link ApiError} payload. No stack traces or internal details ever leak to
 * clients; unexpected failures are logged server-side and surfaced as a
 * generic 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----------------------------------------------------------------------
    // Domain / application exceptions
    // ----------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("API exception [{}] at {}", ex.getErrorCode(), request.getRequestURI(), ex);
        } else {
            log.debug("API exception [{}] at {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    // ----------------------------------------------------------------------
    // Bean Validation
    // ----------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<ApiError.FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> ApiError.FieldValidationError.builder()
                        .field(lastNode(violation.getPropertyPath().toString()))
                        .rejectedValue(violation.getInvalidValue())
                        .message(violation.getMessage())
                        .build())
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    // ----------------------------------------------------------------------
    // Malformed requests
    // ----------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest request) {
        return build(ErrorCode.MALFORMED_REQUEST, "Malformed or unreadable request body", request, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequestParams(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), request, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "The requested resource was not found", request, null);
    }

    // ----------------------------------------------------------------------
    // Security
    // ----------------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password", request, null);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiError> handleDisabled(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCOUNT_DISABLED, "Account is not active", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHORIZED, "Authentication failed", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.INSUFFICIENT_PERMISSIONS, "You do not have permission to perform this action",
                request, null);
    }

    // ----------------------------------------------------------------------
    // Persistence
    // ----------------------------------------------------------------------

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "The operation violates a data constraint", request, null);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, "The resource was modified concurrently; please retry", request, null);
    }

    // ----------------------------------------------------------------------
    // Fallback
    // ----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request,
                                           List<ApiError.FieldValidationError> fieldErrors) {
        HttpStatus status = code.getHttpStatus();
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code.name())
                .message(message)
                .path(request.getRequestURI())
                .errors(fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private ApiError.FieldValidationError toFieldError(FieldError fieldError) {
        return ApiError.FieldValidationError.builder()
                .field(fieldError.getField())
                .rejectedValue(fieldError.getRejectedValue())
                .message(fieldError.getDefaultMessage())
                .build();
    }

    private String lastNode(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx >= 0 ? propertyPath.substring(idx + 1) : propertyPath;
    }
}
