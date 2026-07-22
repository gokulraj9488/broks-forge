# API Guidelines — Brok's Forge

> **Brok's Forge** — _The Engineering Platform for AI Agents._
> REST over HTTP/JSON. Java 21 · Spring Boot 3.4.1 Web MVC · springdoc-openapi 2.7.0.

This is the contract every HTTP endpoint in Brok's Forge follows. Consistency here is what lets the
Next.js client, the generated OpenAPI SDK, and external integrators treat all modules uniformly.

Related: [`./CODING_STANDARDS.md`](./CODING_STANDARDS.md) · [`./ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md) · [`./DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)

---

## 1. Versioning

- All endpoints are served under **`/api/v1`**. The version is in the URL path, never a header or
  query param.
- `v1` is stable: we add fields and endpoints, we do not remove or rename fields or change types within
  a major version. Breaking changes ship under a new prefix (`/api/v2`) alongside `v1`.
- New optional response fields are **not** a breaking change; clients must ignore unknown fields.

---

## 2. Resource nesting & path conventions

Resources are tenant-scoped under organization and project. Paths are lowercase, plural, and
kebab-cased for multi-word resources.

```
/api/v1/organizations/{organizationId}/projects/{projectId}/datasets
/api/v1/organizations/{organizationId}/projects/{projectId}/prompts/{promptId}/versions
/api/v1/organizations/{organizationId}/projects/{projectId}/model-providers
/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-jobs
/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-jobs/{jobId}/runs
/api/v1/organizations/{organizationId}/projects/{projectId}/benchmarks
/api/v1/organizations/{organizationId}/projects/{projectId}/reports
```

Rules:

- Path segments identify resources; **never** put verbs in paths. Use the HTTP method for the action.
- Path variable names are `camelCase` and match controller method params:
  `{organizationId}`, `{projectId}`, `{jobId}`.
- A controller's base path is declared once on the class:

```java
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-jobs")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Evaluation Jobs", description = "Create and manage evaluation jobs")
public class EvaluationJobController { ... }
```

---

## 3. HTTP method semantics

| Method   | Use                                  | Idempotent | Request body | Success status |
|----------|--------------------------------------|------------|--------------|----------------|
| `GET`    | Read a resource or list              | Yes        | No           | `200`          |
| `POST`   | Create a child resource / action     | No         | Yes          | `201` / `200`  |
| `PUT`    | Full replace of a resource           | Yes        | Yes          | `200`          |
| `PATCH`  | Partial update                       | No*        | Yes          | `200`          |
| `DELETE` | Remove (soft-delete) a resource      | Yes        | No           | `204`          |

\* `PATCH` is not required to be idempotent; design it to be where practical.

- Action-style operations that don't map to CRUD use `POST` on a sub-path noun, e.g.
  `POST .../evaluation-jobs/{jobId}/runs` (start a run), `POST .../evaluation-jobs/{jobId}:cancel` is
  **not** used — prefer a status sub-resource or a dedicated child collection.

---

## 4. Status codes

| Code  | Meaning / when we return it                                                          |
|-------|--------------------------------------------------------------------------------------|
| `200` | OK — successful `GET`, `PUT`, `PATCH`, or `POST` that returns a body                  |
| `201` | Created — `POST` that created a resource; include the created representation         |
| `204` | No Content — successful `DELETE`, or `POST`/`PUT` with nothing to return             |
| `400` | Bad Request — malformed JSON, wrong types, missing required body                     |
| `401` | Unauthorized — missing/invalid/expired bearer token or API key                       |
| `403` | Forbidden — authenticated but not allowed (RBAC / cross-tenant access)               |
| `404` | Not Found — resource doesn't exist or isn't visible to the caller's tenant           |
| `409` | Conflict — uniqueness violation (slug taken), optimistic-lock conflict               |
| `429` | Too Many Requests — rate limit exceeded; includes `Retry-After`                      |
| `500` | Internal Server Error — unexpected server fault; generic message, details logged      |

> Validation policy: a body that is **syntactically invalid** (not parseable, wrong JSON types) is
> `400`/`MALFORMED_REQUEST`. A body that parses but **fails `jakarta.validation` constraints** is also
> `400`, with code `VALIDATION_ERROR` and a populated `errors[]` array — Brok's Forge does not use
> `422`. See [`ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md) for the exact mapping.

We do **not** "hide" a `404` as `403` or vice versa: cross-tenant access to a resource that exists but
belongs to another org returns `404` (the resource does not exist *for you*), enforced by the
tenant-scoped repository query.

---

## 5. Response envelope conventions

There are exactly two response shapes:

1. **Single resource** → the raw resource record. No wrapper.

```json
{
  "id": "01J9X4QK2M0N5W8B3F7AZ2C6T1",
  "name": "Nightly regression eval",
  "slug": "nightly-regression-eval",
  "status": "PENDING",
  "createdAt": "2026-07-01T09:15:42Z"
}
```

2. **Collections** → `PageResponse<T>` (from `com.broksforge.common.web`). Never return a bare JSON
   array.

```java
@GetMapping
@Operation(summary = "List evaluation jobs")
public ResponseEntity<PageResponse<EvaluationJobResponse>> list(
        @PathVariable UUID organizationId,
        @PathVariable UUID projectId,
        @ParameterObject EvaluationJobFilter filter,
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {

    Page<EvaluationJobResponse> page = service.search(organizationId, projectId, filter, pageable);
    return ResponseEntity.ok(PageResponse.of(page));
}
```

Simple acknowledgements use `MessageResponse` (`{ "message": "..." }`); creates return `201` with the
resource and a `Location` header:

```java
@PostMapping
public ResponseEntity<EvaluationJobResponse> create(
        @PathVariable UUID organizationId,
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateEvaluationJobRequest request) {

    UUID userId = SecurityUtils.requireCurrentUserId();
    EvaluationJobResponse created = service.create(organizationId, projectId, request, userId);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
    return ResponseEntity.created(location).body(created);
}

@DeleteMapping("/{jobId}")
public ResponseEntity<Void> delete(
        @PathVariable UUID organizationId, @PathVariable UUID projectId, @PathVariable UUID jobId) {
    service.delete(organizationId, projectId, jobId);
    return ResponseEntity.noContent().build();
}
```

---

## 6. Pagination, sorting, filtering

Lists use Spring's `Pageable`, bound with `@ParameterObject @PageableDefault`.

| Param    | Meaning                                       | Default          |
|----------|-----------------------------------------------|------------------|
| `page`   | Zero-based page index                         | `0`              |
| `size`   | Page size (capped at 100)                     | `20`             |
| `sort`   | `field,(asc\|desc)`, repeatable               | `createdAt,desc` |

Filtering is expressed as a typed `@ParameterObject` filter record, translated to a
`Specification` in the repository layer (see [`CODING_STANDARDS.md` §5](./CODING_STANDARDS.md#5-repositories)):

```java
public record EvaluationJobFilter(
        @Schema(description = "Free-text search over name") String q,
        EvaluationStatus status,
        UUID profileId) {}
```

`PageResponse` exposes `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`:

```http
GET /api/v1/organizations/8c.../projects/3a.../evaluation-jobs?status=RUNNING&page=0&size=20&sort=createdAt,desc
```

```json
{
  "content": [
    {
      "id": "01J9X4QK2M0N5W8B3F7AZ2C6T1",
      "name": "Nightly regression eval",
      "slug": "nightly-regression-eval",
      "status": "RUNNING",
      "createdAt": "2026-07-01T09:15:42Z"
    },
    {
      "id": "01J9X51T7E9Q0R2V4H6KP8M3D5",
      "name": "Latency benchmark — gpt vs claude",
      "slug": "latency-benchmark-gpt-vs-claude",
      "status": "RUNNING",
      "createdAt": "2026-07-01T08:02:10Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

## 7. Idempotency

- `GET`, `PUT`, `DELETE` are idempotent by design.
- Resource creation is de-duplicated by **business uniqueness** (e.g. the
  `(project_id, slug)` unique constraint). A repeated create of the same logical resource yields
  `409 SLUG_ALREADY_EXISTS`, not a duplicate.
- For expensive, non-idempotent `POST`s (starting an evaluation run, importing a dataset), clients may
  send an **`Idempotency-Key`** header; the server returns the original result for a repeated key
  within the retention window instead of starting duplicate work.

```http
POST /api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9.../runs
Idempotency-Key: 6f1c0b9e-7d2a-4c8e-9b11-2f0a5d3e4c7a
```

---

## 8. Request validation

- Request bodies are `@Valid @RequestBody` records annotated with `jakarta.validation`
  (`@NotBlank`, `@Size`, `@NotNull`, `@Pattern`, and custom constraints like `@ValidEndpointUrl`).
- Request records **omit server-controlled fields** (`organizationId`, `ownerId`, `status`, `slug`) to
  prevent mass-assignment; those come from the path or are set by the service.
- Constraint violations produce a structured `400`/`VALIDATION_ERROR` with a per-field `errors[]` array (see below).
- Path/query binding errors (e.g. a non-UUID `{jobId}`) produce `400`.

```java
public record CreateModelProviderRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LlmProvider provider,                 // OPENAI, ANTHROPIC, GROQ, OLLAMA, GEMINI, OPEN_ROUTER, DEEPSEEK
        @NotBlank @ValidEndpointUrl String baseUrl,
        @NotBlank @Size(max = 4096) String apiKey) {}
```

---

## 9. OpenAPI / Swagger documentation

springdoc 2.7.0 generates the spec; documentation is mandatory, not optional.

- Swagger UI: **`/swagger-ui.html`**; raw spec: `/v3/api-docs`.
- Every controller has `@Tag`; every endpoint has `@Operation(summary = ..., description = ...)`.
- DTO fields carry `@Schema` with a `description` and a realistic `example`.
- Document non-2xx outcomes with `@ApiResponse` so the generated SDK and client teams see them.
- `@ParameterObject` exposes filter and pageable params correctly in the UI.

```java
@Operation(summary = "Start an evaluation run",
           description = "Transitions the job to RUNNING and enqueues result computation.")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Run started"),
    @ApiResponse(responseCode = "409", description = "Job is not in a runnable state"),
    @ApiResponse(responseCode = "404", description = "Job not found in this project")
})
@PostMapping("/{jobId}/runs")
public ResponseEntity<EvaluationRunResponse> startRun(...) { ... }
```

---

## 10. Security: bearer tokens, API keys, RBAC

- Two authentication schemes, both declared in OpenAPI as security schemes:
  - **`bearerAuth`** — JWT (jjwt 0.12.6) in `Authorization: Bearer <token>` for interactive users.
  - **`apiKeyAuth`** — long-lived API key in the `X-Api-Key` header for machine/CI callers.
- Every controller is `@PreAuthorize("isAuthenticated()")` at minimum; method-level
  `@PreAuthorize` enforces RBAC for sensitive actions.
- The current principal is read via `SecurityUtils.requireCurrentUserId()` — never trust a user id from
  the request body or query string.
- Tenant isolation is enforced in the service/repository layer by scoping every query to
  `organizationId` + `projectId`; a token valid for one org cannot read another's data.

```java
@PreAuthorize("hasRole('PROJECT_ADMIN')")
@DeleteMapping("/{jobId}")
public ResponseEntity<Void> delete(...) { ... }
```

Authentication failures return `401`; authorization (role/tenant) failures return `403`.

---

## 11. Correlation / request-id headers

- Every request is assigned a correlation id. If the client sends **`X-Request-Id`**, the server
  honors it; otherwise the server generates one. It is echoed on every response and stamped into the
  SLF4J MDC so logs are traceable end-to-end.
- Clients should propagate `X-Request-Id` across calls (the axios client does this automatically) and
  include it when reporting issues.

```http
HTTP/1.1 201 Created
X-Request-Id: 6f1c0b9e-7d2a-4c8e-9b11-2f0a5d3e4c7a
Location: /api/v1/organizations/8c.../projects/3a.../evaluation-jobs/01J9X4QK2M0N5W8B3F7AZ2C6T1
```

---

## 12. Error responses

All errors use the shared `ApiError` body produced by the `GlobalExceptionHandler`. The full schema,
the `ErrorCode` → `HttpStatus` mapping, and per-exception handling live in
[`ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md). Representative `400` validation error:

```json
{
  "timestamp": "2026-07-01T09:20:11Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/organizations/8c.../projects/3a.../evaluation-jobs",
  "errors": [
    { "field": "name", "rejectedValue": "", "message": "must not be blank" },
    { "field": "profileId", "rejectedValue": null, "message": "must not be null" }
  ]
}
```

---

## 13. Deprecation policy

- Endpoints and fields are deprecated, not deleted, within a major version.
- A deprecated endpoint is annotated `@Deprecated` in code and `@Operation(deprecated = true)` so
  Swagger flags it, and returns a **`Deprecation: true`** header plus a **`Sunset: <RFC 1123 date>`**
  header indicating the earliest removal date.
- Minimum deprecation window is **90 days**; removals only happen at a major version bump.
- Deprecations are announced in the changelog and cross-referenced in
  [`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md).

```http
HTTP/1.1 200 OK
Deprecation: true
Sunset: Tue, 30 Sep 2026 00:00:00 GMT
Link: </api/v2/.../evaluation-jobs>; rel="successor-version"
```
