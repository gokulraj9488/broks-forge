# Error Handling Guide — Brok's Forge

> **Brok's Forge** — _The Engineering Platform for AI Agents._
> One exception hierarchy, one error code enum, one response shape, zero leaked internals.

Errors in Brok's Forge are **typed, mapped, and uniform**. Services throw `ApiException` subclasses
carrying a stable `ErrorCode`; a single `@RestControllerAdvice` translates every failure into the
shared `ApiError` JSON body. This guide is the contract for both throwers and the handler, and is
kept in lockstep with the real implementation in `common/exception/`.

Related: [`./CODING_STANDARDS.md`](./CODING_STANDARDS.md) · [`./API_GUIDELINES.md`](./API_GUIDELINES.md) · [`./DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)

---

## 1. Principles

1. **Throw, don't return.** A service signals failure by throwing an `ApiException` subclass — never by
   returning `null`, an empty `Optional`, or a magic value.
2. **Stable codes.** Every error carries an `ErrorCode`. Clients branch on `code`, never on the
   human-readable `message`, which may change.
3. **No leaks.** Stack traces, SQL, class names, framework internals, and secrets **never** reach the
   client. Server faults log full detail and return a generic `500`.
4. **One place maps HTTP.** Only `ErrorCode` (and, by extension, `GlobalExceptionHandler`) knows about
   `HttpStatus`. Services throw a typed exception with a code; the code defines the status.

---

## 2. Exception hierarchy

`ApiException` (`common/exception/ApiException.java`) is a **concrete** class — not abstract — that
carries an `ErrorCode` and derives its `HttpStatus` from it. It can be thrown directly, but every
module instead throws one of five small subclasses, each of which defaults to one `ErrorCode`/status
while still accepting an explicit `ErrorCode` when a more specific one applies:

```
RuntimeException
└── ApiException(ErrorCode errorCode, String message[, Throwable cause])
    ├── ResourceNotFoundException   → defaults to NOT_FOUND            (404)
    ├── ResourceConflictException   → defaults to CONFLICT             (409)
    ├── BadRequestException         → defaults to BAD_REQUEST          (400)
    ├── UnauthorizedException       → defaults to UNAUTHORIZED         (401)
    └── ForbiddenException          → defaults to FORBIDDEN            (403)
```

```java
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
```

### Constructors & factories (how services throw)

Each subclass has a single-arg `(String message)` constructor that binds its default `ErrorCode`, and
a two-arg `(ErrorCode, String message)` constructor for a more specific code that still maps to the
same status family:

```java
// 404 — convenience factory builds a consistent message, defaults to NOT_FOUND
throw ResourceNotFoundException.of("EvaluationJob", jobId);
// → "EvaluationJob not found: 01J9X4..." , code NOT_FOUND

// 404 — a domain-specific code that still maps to 404
throw new ResourceNotFoundException(ErrorCode.KNOWLEDGE_PATTERN_NOT_FOUND,
        "No knowledge node with key " + nodeKey);

// 409 — explicit code + message
throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
        "An evaluation job with slug '" + slug + "' already exists in this project");

// 400 — malformed/illegal but well-formed JSON
throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
        "size must be between 1 and 100");

// 401 — caller is not authenticated for this action
throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");

// 403 — authenticated but not allowed (RBAC / cross-tenant)
throw new ForbiddenException(ErrorCode.INSUFFICIENT_PERMISSIONS,
        "User is not permitted to delete evaluation jobs in this project");
```

```java
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s not found: %s".formatted(resource, identifier));
    }
}
```

---

## 3. The `ApiError` schema

Every error response — without exception — has this shape:

```json
{
  "timestamp": "2026-07-01T09:20:11Z",
  "status": 409,
  "error": "Conflict",
  "code": "SLUG_ALREADY_EXISTS",
  "message": "An evaluation job with slug 'nightly-regression-eval' already exists in this project",
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs"
}
```

| Field       | Type        | Notes                                                                 |
|-------------|-------------|-----------------------------------------------------------------------|
| `timestamp` | string      | ISO-8601 UTC (`Instant`)                                               |
| `status`    | number      | HTTP status, matches the response line                                |
| `error`     | string      | HTTP reason phrase (`Conflict`, `Not Found`, ...)                     |
| `code`      | string      | Stable `ErrorCode` name — **this is what clients branch on**          |
| `message`   | string      | Human-readable, safe-to-display; never contains internals             |
| `path`      | string      | Request URI                                                           |
| `errors`    | array       | Per-field validation details; **omitted entirely** (not `[]`) when empty — the payload is annotated `@JsonInclude(NON_NULL)` |

`errors[]` entries are `{ "field": "...", "rejectedValue": ..., "message": "..." }` — the actual
value the client sent is echoed back alongside the field name and message, so a UI can re-render the
offending input without a second round trip.

---

## 4. `ErrorCode` → `HttpStatus` table

`ErrorCode` (`common/exception/ErrorCode.java`) is a single enum; each constant pins exactly one
`HttpStatus` via `@Getter`-exposed `httpStatus`. This is the canonical, current table — copied
directly from the enum, not an aspirational contract.

| `ErrorCode`                     | HTTP  | When                                                              |
|----------------------------------|-------|--------------------------------------------------------------------|
| `VALIDATION_ERROR`               | `400` | `@Valid`/Bean Validation failed on a request body or parameters   |
| `BAD_REQUEST`                    | `400` | Well-formed but illegal argument                                  |
| `MALFORMED_REQUEST`              | `400` | Unparseable/invalid JSON body                                     |
| `UNAUTHORIZED`                   | `401` | Generic authentication failure                                    |
| `INVALID_CREDENTIALS`            | `401` | Wrong email/password on login                                     |
| `TOKEN_EXPIRED`                  | `401` | JWT / refresh / verification / reset token has expired            |
| `TOKEN_INVALID`                  | `401` | JWT / refresh / verification / reset token is malformed or unknown |
| `ACCOUNT_DISABLED`               | `401` | Account is locked or disabled                                     |
| `FORBIDDEN`                      | `403` | Generic authorization failure                                     |
| `INSUFFICIENT_PERMISSIONS`       | `403` | Authenticated but lacks the required role/membership              |
| `NOT_FOUND`                      | `404` | Resource absent, or not visible to the caller's tenant             |
| `CONFLICT`                       | `409` | Generic uniqueness/state conflict (incl. optimistic-lock and data-integrity failures) |
| `EMAIL_ALREADY_EXISTS`           | `409` | Registration with an email already in use                         |
| `SLUG_ALREADY_EXISTS`            | `409` | `(project_id, slug)`-style uniqueness violation                    |
| `ALREADY_MEMBER`                 | `409` | User is already a member of the organization                      |
| `RATE_LIMITED`                   | `429` | Caller exceeded the Redis-backed auth rate limit                  |
| `UNSUPPORTED_MEDIA_TYPE`         | `415` | `Content-Type` not supported                                      |
| `METHOD_NOT_ALLOWED`             | `405` | HTTP verb not supported on the matched route                      |
| `INTERNAL_ERROR`                 | `500` | Unhandled fault; details logged, generic message returned          |
| **Agent Registry (Phase 2)**     |       |                                                                    |
| `INVALID_ENDPOINT_URL`           | `400` | Agent endpoint URL fails `@ValidEndpointUrl`                       |
| `CREDENTIAL_TYPE_MISMATCH`       | `400` | Credential payload doesn't match the agent's declared `authType`   |
| `AGENT_VERSION_ALREADY_EXISTS`   | `409` | Duplicate version identifier for an agent                          |
| `AGENT_ARCHIVED`                 | `409` | Operation not allowed on an archived agent                         |
| `CREDENTIAL_ENCRYPTION_ERROR`    | `500` | AES-256-GCM encrypt/decrypt failure                                 |
| **Intelligence Layer (Phase 3)** |       |                                                                    |
| `DATASET_IMPORT_FAILED`          | `400` | Dataset CSV/JSON unparseable or fails shape validation              |
| `DATASET_EMPTY`                  | `400` | Dataset version has zero items                                     |
| `DATASET_VERSION_REQUIRED`       | `400` | An operation needs a dataset version that wasn't supplied           |
| `PROMPT_TEMPLATE_INVALID`        | `400` | `{{variable}}` template fails syntax validation                    |
| `PROMPT_NO_ACTIVE_VERSION`       | `409` | Prompt has no active version to render/evaluate                    |
| `EVALUATION_CONFIG_INVALID`      | `400` | Evaluation job request references an invalid combination           |
| `EVALUATION_JOB_NOT_RUNNABLE`    | `409` | Start requested while job not `PENDING`/`FAILED`                   |
| `EVALUATION_PROFILE_INVALID`     | `400` | Evaluation profile has no metrics or a bad threshold                |
| `BENCHMARK_CONFIG_INVALID`       | `400` | Benchmark comparison request is malformed                          |
| `REGRESSION_CONFIG_INVALID`      | `400` | Regression check request is malformed                              |
| `UNSUPPORTED_REPORT_FORMAT`      | `400` | Requested report format not in `{JSON, CSV, HTML}`                |
| `REPORT_GENERATION_FAILED`       | `500` | Report rendering failed server-side                                |
| `MODEL_PROVIDER_UNSUPPORTED`     | `400` | `LlmProvider` has no registered `ModelInvoker`                     |
| `MODEL_INVOCATION_FAILED`        | `502` | Upstream provider/agent endpoint rejected or timed out the call     |
| **AI Engineering Advisor (Phase 4)** |   |                                                                    |
| `ADVISOR_INPUT_INSUFFICIENT`     | `400` | Not enough evaluation history to produce a recommendation           |
| `ROOT_CAUSE_INPUT_INVALID`       | `400` | Job/regression is not in a state root-cause analysis can run on     |
| `DEBUG_TIMELINE_UNAVAILABLE`     | `409` | Run hasn't executed yet — no timeline to reconstruct                |
| `KNOWLEDGE_PATTERN_NOT_FOUND`    | `404` | No knowledge node with the given `nodeKey`                          |
| **Email transport**              |       |                                                                    |
| `EMAIL_SEND_FAILED`              | `500` | `SmtpEmailService` failed to dispatch a message                     |

```java
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

    // ... Phase 2/3/4 domain codes (see table above) ...

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;
    ErrorCode(HttpStatus httpStatus) { this.httpStatus = httpStatus; }
}
```

> **Note on 400 vs 422:** Brok's Forge deliberately returns **`400 Bad Request`** (code
> `VALIDATION_ERROR`) for a request body that parses as JSON but fails Bean Validation, rather than
> `422 Unprocessable Entity`. Both are defensible REST choices; 400 was chosen for consistency with
> `MALFORMED_REQUEST` (unparseable JSON) so clients only need to branch on `code`, not on `code` *and*
> status. `422`/`UNPROCESSABLE_ENTITY` is not used anywhere in this codebase — don't reintroduce it
> without updating this table and every client that branches on status.

---

## 5. `GlobalExceptionHandler` mapping

A single `@RestControllerAdvice` (`common/exception/GlobalExceptionHandler.java`) translates every
exception. It maps our typed exceptions directly and catches the framework/security/JPA exceptions we
expect, with a final fallback that hides internals. Every handler funnels through the same private
`build(...)` helper, so the response shape is identical regardless of which handler fired.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Our typed domain exceptions — the exception's ErrorCode drives the status
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getHttpStatus().is5xxServerError()) {
            log.error("API exception [{}] at {}", ex.getErrorCode(), request.getRequestURI(), ex);
        } else {
            log.debug("API exception [{}] at {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    // 2. Bean validation on @Valid @RequestBody / @Validated path params → 400 with per-field errors
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
        // ... maps jakarta.validation.ConstraintViolation → the same ErrorCode.VALIDATION_ERROR / 400
    }

    // 3. Malformed body / bad params → 400
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

    // 4. Spring Security → 401 / 403 (never echo the internal reason)
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

    // 5. Persistence — never leak the SQL/constraint name
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "The operation violates a data constraint", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, "The resource was modified concurrently; please retry", request, null);
    }

    // 6. Fallback — log full detail, return a sanitized 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
    }

    // Every handler above funnels through this single builder — one response shape, always.
    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request,
                                           List<ApiError.FieldValidationError> fieldErrors) { /* ... */ }
}
```

Handler order of precedence: Spring resolves `@ExceptionHandler` methods by the most specific matching
exception type, so `ApiException` and each framework-specific handler match before the generic
`Exception` fallback, which only fires for anything unmapped.

---

## 6. No internal details leak — the rules

- **Never** include stack traces, exception class names, SQL, file paths, or bean names in `message`.
- The `500` message is a constant: `"An unexpected error occurred"`. Diagnostics live in logs,
  correlated by the `X-Correlation-Id` / `X-Request-Id` headers (see
  [`API_GUIDELINES.md`](./API_GUIDELINES.md)).
- `DataIntegrityViolationException` is mapped to a generic `409` (`ErrorCode.CONFLICT`); the
  constraint name is logged server-side (`ex.getMostSpecificCause().getMessage()`), never returned to
  the client. When a friendlier message is warranted, the service catches the specific case *before*
  hitting the DB (e.g. `existsBySlug...`) and throws `ResourceConflictException(SLUG_ALREADY_EXISTS, ...)`
  itself.
- Secrets (JWTs, API keys, agent credentials, `BROKSFORGE_SECURITY_*` values) appear in **neither**
  responses **nor** logs — see [`SECURITY_GUIDE.md` §5](./SECURITY_GUIDE.md) and §11.

---

## 7. When to add a new `ErrorCode`

Add a new `ErrorCode` when **all** of the following hold:

1. The condition is a distinct business outcome a client could reasonably branch on.
2. No existing code already expresses it (don't add a new `*_ALREADY_EXISTS` when `SLUG_ALREADY_EXISTS`
   or the generic `CONFLICT` fits).
3. You can pin it to exactly one `HttpStatus`.

When adding one:

- Add the constant to the `ErrorCode` enum with its `HttpStatus`.
- Add a row to [the table in §4](#4-errorcode--httpstatus-table).
- Reference it from `@ApiResponse` annotations on the endpoints that can return it.
- If it changes externally observable behavior of a stable endpoint, note it in `CHANGELOG.md` /
  [`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md).

Do **not** add codes for transient/internal conditions that should be a generic `500`
(`ErrorCode.INTERNAL_ERROR`).

---

## 8. Phase 3/4 domain error signaling

Phase 3 modules (`dataset`, `prompt`, `model`, `evaluation`, `benchmark`, `regression`, `analytics`,
`report`, `search`, `dashboard`) and Phase 4 modules (`advisor`, `rootcause`, `debugger`, `knowledge`)
throw the same five `ApiException` subclasses with the domain codes from §4. They validate state
transitions in the **service**, before any side effects:

```java
@Transactional
public EvaluationRunResponse startRun(UUID orgId, UUID projectId, UUID jobId) {
    EvaluationJob job = requireJob(orgId, projectId, jobId);   // 404 if absent / cross-tenant

    if (!job.getStatus().isRunnable()) {                       // PENDING or FAILED only
        throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                "Evaluation job " + jobId + " is " + job.getStatus() + " and cannot be started");
    }

    job.setStatus(EvaluationStatus.RUNNING);                   // PENDING→RUNNING→COMPLETED/FAILED/CANCELLED
    EvaluationRun run = runService.enqueue(job);
    log.info("Evaluation run started orgId={} projectId={} jobId={} runId={}",
            orgId, projectId, jobId, run.getId());
    return mapper.toRunResponse(run);
}
```

Other representative cases:

```java
// dataset import — wrap the parse failure, never bubble the raw cause to the client
catch (DatasetParseException e) {
    log.warn("Dataset import failed projectId={} datasetId={}", projectId, datasetId, e);
    throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
            "Dataset could not be imported: row " + e.getRow() + " is invalid");
}

// prompt module
throw ResourceNotFoundException.of("PromptVersion", versionId);          // code NOT_FOUND

// model SPI — no invoker registered for the provider
throw new BadRequestException(ErrorCode.MODEL_PROVIDER_UNSUPPORTED,
        "No ModelInvoker registered for provider " + provider);

// model SPI — upstream agent endpoint failed → 502, internals stay in the logs
throw new ApiException(ErrorCode.MODEL_INVOCATION_FAILED,
        "The agent endpoint could not complete the request");

// Phase 4 — root-cause engine has nothing to analyze yet
throw new BadRequestException(ErrorCode.ROOT_CAUSE_INPUT_INVALID,
        "Job " + jobId + " has not produced any failed runs to diagnose");
```

---

## 9. Example `ApiError` payloads

**404 — not found / cross-tenant**

```json
{
  "timestamp": "2026-07-01T09:22:03Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "EvaluationJob not found: 01J9X4QK2M0N5W8B3F7AZ2C6T1",
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9X4QK2M0N5W8B3F7AZ2C6T1"
}
```

**409 — domain state conflict**

```json
{
  "timestamp": "2026-07-01T09:23:47Z",
  "status": 409,
  "error": "Conflict",
  "code": "EVALUATION_JOB_NOT_RUNNABLE",
  "message": "Evaluation job 01J9X4QK2M0N5W8B3F7AZ2C6T1 is RUNNING and cannot be started",
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9X4.../runs"
}
```

**400 — validation**

```json
{
  "timestamp": "2026-07-01T09:24:55Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "errors": [
    { "field": "email", "rejectedValue": "not-an-email", "message": "must be a well-formed email address" },
    { "field": "password", "rejectedValue": null, "message": "must not be blank" }
  ]
}
```

**500 — sanitized fallback (full detail is in the logs, keyed by request id)**

```json
{
  "timestamp": "2026-07-01T09:25:30Z",
  "status": 500,
  "error": "Internal Server Error",
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "path": "/api/v1/organizations/8c.../projects/3a.../reports"
}
```

---

<sub>Brok's Forge — Error Handling Guide. Kept in lockstep with `common/exception/ErrorCode.java` and
`GlobalExceptionHandler.java`; when this document and the code disagree, the code wins — fix this
document, not the working handler.</sub>
