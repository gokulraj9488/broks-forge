# Error Handling Guide — Brok's Forge

> **Brok's Forge** — _The Engineering Platform for AI Agents._
> One exception hierarchy, one error code enum, one response shape, zero leaked internals.

Errors in Brok's Forge are **typed, mapped, and uniform**. Services throw `ApiException` subclasses
carrying a stable `ErrorCode`; a single `@RestControllerAdvice` translates every failure into the
shared `ApiError` JSON body. This guide is the contract for both throwers and the handler.

Related: [`./CODING_STANDARDS.md`](./CODING_STANDARDS.md) · [`./API_GUIDELINES.md`](./API_GUIDELINES.md) · [`./DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)

---

## 1. Principles

1. **Throw, don't return.** A service signals failure by throwing an `ApiException` subclass — never by
   returning `null`, an empty `Optional`, or a magic value.
2. **Stable codes.** Every error carries an `ErrorCode`. Clients branch on `code`, never on the
   human-readable `message`, which may change.
3. **No leaks.** Stack traces, SQL, class names, framework internals, and secrets **never** reach the
   client. Server faults log full detail and return a generic `500`.
4. **One place maps HTTP.** Only the `GlobalExceptionHandler` knows about `HttpStatus`. Services know
   `ErrorCode`s; the code defines the status.

---

## 2. Exception hierarchy

All domain exceptions extend `ApiException`, which holds an `ErrorCode` (and optionally a per-field
`errors` list). The `ErrorCode` defines the resulting `HttpStatus`.

```
RuntimeException
└── ApiException(ErrorCode code, String message)
    ├── ResourceNotFoundException     → 404
    ├── ResourceConflictException     → 409
    ├── BadRequestException           → 400
    ├── UnauthorizedException         → 401
    └── ForbiddenException            → 403
```

```java
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;

    protected ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
```

### Constructors & factories (how services throw)

```java
// 404 — convenience factory builds a consistent message
throw ResourceNotFoundException.of("EvaluationJob", jobId);
// → "EvaluationJob with id 01J9X4... not found", code RESOURCE_NOT_FOUND

// 409 — explicit code + message
throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
        "An evaluation job with slug '" + slug + "' already exists in this project");

// 400 — malformed/illegal but well-formed JSON
throw new BadRequestException(ErrorCode.INVALID_ARGUMENT,
        "size must be between 1 and 100");

// 401 — caller is not authenticated for this action
throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "API key is invalid or expired");

// 403 — authenticated but not allowed (RBAC / cross-tenant)
throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
        "User is not permitted to delete evaluation jobs in this project");
```

```java
public final class ResourceNotFoundException extends ApiException {
    private ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " with id " + id + " not found");
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
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs",
  "errors": []
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
| `errors`    | array       | Per-field validation details; empty `[]` when not applicable          |

`errors[]` entries are `{ "field": "...", "message": "..." }`.

---

## 4. `ErrorCode` → `HttpStatus` table

`ErrorCode` is a single enum; each constant pins exactly one `HttpStatus`. This is the canonical table.

| `ErrorCode`                     | HTTP  | When                                                              |
|---------------------------------|-------|------------------------------------------------------------------|
| `VALIDATION_FAILED`             | `422` | Bean-validation constraints on a parseable body failed           |
| `INVALID_ARGUMENT`              | `400` | Well-formed but illegal argument (bad page size, bad sort field) |
| `MALFORMED_REQUEST`             | `400` | Unparseable/invalid JSON, wrong types, missing body              |
| `INVALID_CREDENTIALS`           | `401` | Missing/invalid/expired JWT or API key                           |
| `ACCESS_DENIED`                 | `403` | Authenticated but lacks role / wrong tenant                      |
| `RESOURCE_NOT_FOUND`            | `404` | Resource absent or not visible to caller's tenant                |
| `SLUG_ALREADY_EXISTS`           | `409` | `(project_id, slug)` uniqueness violation                        |
| `RESOURCE_CONFLICT`             | `409` | Generic uniqueness/state conflict                                |
| `OPTIMISTIC_LOCK_CONFLICT`      | `409` | Concurrent modification (`@Version` mismatch)                    |
| `DATA_INTEGRITY_VIOLATION`      | `409` | DB constraint (FK/unique/not-null) violated                      |
| `RATE_LIMITED`                  | `429` | Caller exceeded rate limit                                       |
| `INTERNAL_ERROR`                | `500` | Unhandled fault; details logged, generic message returned        |
| **Phase 3 domain codes**        |       |                                                                  |
| `EVALUATION_JOB_NOT_RUNNABLE`   | `409` | Start requested while job not `PENDING`/`FAILED`                 |
| `EVALUATION_RUN_NOT_CANCELLABLE`| `409` | Cancel requested on a terminal run                               |
| `DATASET_IMPORT_FAILED`         | `422` | Dataset file unparseable / schema mismatch                       |
| `PROMPT_VERSION_NOT_FOUND`      | `404` | Referenced prompt version does not exist                         |
| `UNSUPPORTED_PROVIDER`          | `400` | `LlmProvider` has no registered `ModelInvoker`                   |
| `MODEL_INVOCATION_FAILED`       | `502` | Upstream provider rejected/timed out the call                    |
| `BENCHMARK_ALREADY_RUNNING`     | `409` | A benchmark for the target is already in progress                |
| `REPORT_FORMAT_UNSUPPORTED`     | `400` | Requested report format not in `{JSON, CSV, HTML}`              |

```java
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // Phase 3 — domain
    EVALUATION_JOB_NOT_RUNNABLE(HttpStatus.CONFLICT),
    EVALUATION_RUN_NOT_CANCELLABLE(HttpStatus.CONFLICT),
    DATASET_IMPORT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    PROMPT_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST),
    MODEL_INVOCATION_FAILED(HttpStatus.BAD_GATEWAY),
    BENCHMARK_ALREADY_RUNNING(HttpStatus.CONFLICT),
    REPORT_FORMAT_UNSUPPORTED(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
```

---

## 5. `GlobalExceptionHandler` mapping

A single `@RestControllerAdvice` translates everything. It maps our typed exceptions directly and
catches the framework/JPA exceptions we expect, with a final fallback that hides internals.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Our typed domain exceptions — code drives the status
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        HttpStatus status = ex.code().status();
        if (status.is5xxServerError()) {
            log.error("Server error code={} path={}", ex.code(), req.getRequestURI(), ex);
        } else {
            log.warn("Handled error code={} path={} msg={}", ex.code(), req.getRequestURI(), ex.getMessage());
        }
        return ApiError.of(status, ex.code(), ex.getMessage(), req).toResponse();
    }

    // 2. Bean validation on @Valid @RequestBody → 422 with per-field errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        List<FieldErrorEntry> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorEntry(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ApiError.of(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", req).withErrors(fields).toResponse();
    }

    // 3. Malformed body / type mismatch → 400
    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest req) {
        return ApiError.of(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request body is malformed or contains an invalid value", req).toResponse();
    }

    // 4. Spring Security → 401 / 403 (do not echo internal reasons)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Authentication failed path={}", req.getRequestURI());
        return ApiError.of(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                "Authentication is required or has failed", req).toResponse();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ApiError.of(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action", req).toResponse();
    }

    // 5. Data integrity (FK/unique/not-null) → 409, never leak the SQL
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest req) {
        log.warn("Data integrity violation path={}", req.getRequestURI(), ex);
        return ApiError.of(HttpStatus.CONFLICT, ErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with the current state of the resource", req).toResponse();
    }

    // 6. Optimistic locking → 409
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLock(ObjectOptimisticLockingFailureException ex,
                                               HttpServletRequest req) {
        return ApiError.of(HttpStatus.CONFLICT, ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "The resource was modified concurrently; reload and retry", req).toResponse();
    }

    // 7. Fallback — log full detail, return a sanitized 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected server error path={}", req.getRequestURI(), ex);
        return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", req).toResponse();
    }
}
```

Handler order of precedence: specific `ApiException` and framework handlers match first; the
`Exception` fallback only fires for anything unmapped. The fallback message is always generic — the
real cause is in the logs, keyed by `X-Request-Id`.

---

## 6. No internal details leak — the rules

- **Never** include stack traces, exception class names, SQL, file paths, or bean names in `message`.
- The `500` message is a constant: `"An unexpected error occurred"`. Diagnostics live in logs
  correlated by `X-Request-Id` (see [`API_GUIDELINES.md` §11](./API_GUIDELINES.md#11-correlation--request-id-headers)).
- `DataIntegrityViolationException` is mapped to a generic `409`; we do not surface the constraint name
  to the client (it would expose schema internals). When a friendlier message is warranted, the service
  catches the specific case *before* hitting the DB (e.g. `existsBySlug...`) and throws
  `ResourceConflictException(SLUG_ALREADY_EXISTS, ...)` itself.
- Secrets (JWTs, API keys, `apiKey` fields, `BROKSFORGE_SECURITY_*`) appear in **neither** responses
  **nor** logs.

---

## 7. When to add a new `ErrorCode`

Add a new `ErrorCode` when **all** of the following hold:

1. The condition is a distinct business outcome a client could reasonably branch on.
2. No existing code already expresses it (don't add `NAME_ALREADY_EXISTS` when `SLUG_ALREADY_EXISTS`
   or `RESOURCE_CONFLICT` fits).
3. You can pin it to exactly one `HttpStatus`.

When adding one:

- Add the constant to the `ErrorCode` enum with its `HttpStatus`.
- Add a row to the [table in §4](#4-errorcode--httpstatus-table) and document it.
- Reference it from `@ApiResponse` annotations on the endpoints that can return it.
- If it changes externally observable behavior of a stable endpoint, note it in the changelog /
  [`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md).

Do **not** add codes for transient/internal conditions that should be a `500`.

---

## 8. Phase 3 domain error signaling

Phase 3 modules (`dataset`, `prompt`, `model`, `evaluation`, `benchmark`, `regression`, `analytics`,
`report`, `search`, `dashboard`) throw `ApiException` subclasses with the domain codes from §4. They
validate state transitions in the **service**, before any side effects.

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
throw ResourceNotFoundException.of("PromptVersion", versionId);   // PROMPT_VERSION_NOT_FOUND via RESOURCE_NOT_FOUND
// or, for a domain-specific code:
throw new ResourceNotFoundException(ErrorCode.PROMPT_VERSION_NOT_FOUND,
        "Prompt version " + versionId + " not found for prompt " + promptId);

// model SPI — no invoker registered for the provider
throw new BadRequestException(ErrorCode.UNSUPPORTED_PROVIDER,
        "No ModelInvoker registered for provider " + provider);     // OPENAI/ANTHROPIC/GROQ/OLLAMA/GEMINI/OPEN_ROUTER/DEEPSEEK

// model SPI — upstream provider failed → 502, internals stay in the logs
throw new ApiException(ErrorCode.MODEL_INVOCATION_FAILED,
        "The model provider could not complete the request") {};
```

---

## 9. Example `ApiError` payloads

**404 — not found / cross-tenant**

```json
{
  "timestamp": "2026-07-01T09:22:03Z",
  "status": 404,
  "error": "Not Found",
  "code": "RESOURCE_NOT_FOUND",
  "message": "EvaluationJob with id 01J9X4QK2M0N5W8B3F7AZ2C6T1 not found",
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9X4QK2M0N5W8B3F7AZ2C6T1",
  "errors": []
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
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9X4.../runs",
  "errors": []
}
```

**422 — validation**

```json
{
  "timestamp": "2026-07-01T09:24:55Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/organizations/8c.../projects/3a.../model-providers",
  "errors": [
    { "field": "name", "message": "must not be blank" },
    { "field": "baseUrl", "message": "must be a valid endpoint URL" },
    { "field": "provider", "message": "must not be null" }
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
  "path": "/api/v1/organizations/8c.../projects/3a.../reports",
  "errors": []
}
```
