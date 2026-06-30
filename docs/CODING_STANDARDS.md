# Coding Standards — Brok's Forge

> **Brok's Forge** — _The Engineering Platform for AI Agents._
> Modular monolith. Java 21 · Spring Boot 3.4.1 · PostgreSQL/Flyway · Redis · Next.js 15/React 19.

This document is the canonical reference for how we write code in Brok's Forge. It is enforced in
code review and, where possible, by the build (compiler args, Flyway `validate`, Checkstyle/ESLint).
When a rule here conflicts with personal preference, the rule wins.

Related: [`./API_GUIDELINES.md`](./API_GUIDELINES.md) · [`./ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md) · [`./DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)

---

## 1. Guiding Principles

1. **The migration is the source of truth.** `spring.jpa.hibernate.ddl-auto=validate` means an entity
   that disagrees with its Flyway migration fails application startup. Never "fix" a mismatch by
   relaxing validation.
2. **Layers are one-directional.** `web → service → repository → domain`. A controller never touches a
   repository or an `EntityManager`; a domain entity never imports from `web`.
3. **Records for data, classes for behavior.** All DTOs are immutable `record`s. Entities are mutable
   JPA classes; everything else trends toward immutability.
4. **Fail loud with typed errors.** Throw an `ApiException` subclass with a stable `ErrorCode`. Never
   return `null` to signal "not found", never swallow an exception.
5. **Never leak secrets or internals.** Not in logs, not in responses, not in stack traces sent to
   clients.

---

## 2. Java — Naming

| Element            | Convention            | Example                                            |
|--------------------|-----------------------|----------------------------------------------------|
| Class / interface  | `PascalCase`          | `EvaluationJobService`, `ModelInvoker`             |
| Method / field     | `camelCase`           | `findRunnableJobs`, `organizationId`               |
| Constant           | `UPPER_SNAKE_CASE`    | `DEFAULT_PAGE_SIZE`, `MAX_PROMPT_LENGTH`           |
| Package            | `lowercase` (no `_`)  | `com.broksforge.modules.evaluation.service`        |
| Enum constant      | `SCREAMING_SNAKE`     | `PENDING`, `OPENAI`, `OPEN_ROUTER`                 |
| Type parameter     | single capital        | `JpaRepository<E, UUID>`                            |
| Test method        | see §15               | `create_whenSlugTaken_throwsResourceConflict`      |

Package layout for a feature module lives under `com.broksforge.modules.<feature>`:

```
com.broksforge.modules.evaluation
├── domain/        EvaluationJob, EvaluationRun, EvaluationResult, EvaluationProfile
│                  + enums: EvaluationStatus, EvaluationMetricType
├── repository/    EvaluationJobRepository, EvaluationJobSpecifications
├── service/       EvaluationJobService (@Service, @Transactional)
└── web/           EvaluationJobController, EvaluationJobMapper
    └── dto/       CreateEvaluationJobRequest, EvaluationJobResponse (records)
```

Cross-cutting code lives outside modules:

```
com.broksforge.common        domain, exception, web (PageResponse, MessageResponse),
                             validation, security, observability, persistence, util
com.broksforge.config        configuration + *Properties classes
com.broksforge.security      jwt, apikey
```

---

## 3. Formatting & Imports

- **4-space indentation**, no tabs. Braces on the same line (K&R).
- **No wildcard imports.** `import java.util.*;` is a build failure. Configure your IDE: IntelliJ →
  _Editor → Code Style → Java → Imports → Class count to use import with '\*'_ = `999`.
- One top-level type per file; file name matches the type.
- Max line length 120; break long fluent chains one call per line.
- Static imports allowed only for assertions, `Comparator` helpers, and MapStruct/Mockito DSL.

---

## 4. Entities

Entities extend `BaseEntity` (UUID id, `createdAt`, `updatedAt`) or `SoftDeletableEntity`
(adds `deleted`, `deletedAt`). They must match their migration **exactly** — column name, SQL type,
nullability, and length.

**DO**

```java
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "evaluation_jobs",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_evaluation_jobs_project_slug",
        columnNames = {"project_id", "slug"}),
    indexes = {
        @Index(name = "ix_evaluation_jobs_project", columnList = "project_id"),
        @Index(name = "ix_evaluation_jobs_status",  columnList = "status")
    })
public class EvaluationJob extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationStatus status = EvaluationStatus.PENDING;

    @Embedded
    private RetryPolicy retryPolicy;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "metadata", columnDefinition = "TEXT")
    private Map<String, String> metadata = new HashMap<>();
}
```

The matching migration must agree to the letter:

```sql
-- V31__create_evaluation_jobs.sql
CREATE TABLE evaluation_jobs (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL,
    project_id      UUID        NOT NULL,
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_evaluation_jobs_project_slug UNIQUE (project_id, slug)
);
CREATE INDEX ix_evaluation_jobs_project ON evaluation_jobs (project_id);
CREATE INDEX ix_evaluation_jobs_status  ON evaluation_jobs (status);
```

**Entity rules**

- Enums are persisted as `VARCHAR` via `@Enumerated(EnumType.STRING)` — **never** `ORDINAL`
  (reordering an enum would silently corrupt data).
- Always set `@Column` `nullable` and, for strings, `length` explicitly. Defaults are wrong as often
  as they are right.
- JSON map columns use `JsonMetadataConverter` (`@Convert`) into a `TEXT` column — not `jsonb`,
  because `ddl-auto=validate` plus Hibernate's `jsonb` mapping is brittle across drivers.
- Timestamps are `TIMESTAMPTZ` in Postgres and `Instant`/`OffsetDateTime` in Java — **never**
  `TIMESTAMP` / `LocalDateTime` (see [common pitfalls](./DEVELOPER_GUIDE.md#common-pitfalls)).
- No business logic in setters. No `@OneToMany` cascade `REMOVE` to other aggregates — soft-delete via
  the `deleted` flag instead.

**DON'T**

```java
@Enumerated(EnumType.ORDINAL)          // breaks on enum reordering
private EvaluationStatus status;

@Column private String name;            // missing nullable + length → drift vs migration
```

---

## 5. Repositories

```java
public interface EvaluationJobRepository
        extends JpaRepository<EvaluationJob, UUID>, JpaSpecificationExecutor<EvaluationJob> {

    Optional<EvaluationJob> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    boolean existsBySlugIgnoreCaseAndProjectIdAndDeletedFalse(String slug, UUID projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EvaluationJob j set j.deleted = true, j.deletedAt = :now "
         + "where j.projectId = :projectId and j.deleted = false")
    int softDeleteByProjectId(UUID projectId, Instant now);
}
```

- Scope every read by tenant: include `ProjectId`, `OrganizationId`, and `DeletedFalse` in derived
  query names so cross-tenant leaks are impossible by construction.
- Dynamic search/filter goes through `JpaSpecificationExecutor` + a `final` `*Specifications` class
  with a static `build(...)`:

```java
public final class EvaluationJobSpecifications {

    private EvaluationJobSpecifications() {}

    public static Specification<EvaluationJob> build(UUID projectId, EvaluationJobFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("projectId"), projectId));
            p.add(cb.isFalse(root.get("deleted")));
            if (filter.status() != null) {
                p.add(cb.equal(root.get("status"), filter.status()));
            }
            if (StringUtils.hasText(filter.q())) {
                p.add(cb.like(cb.lower(root.get("name")), "%" + filter.q().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
```

---

## 6. Services

- **Constructor injection only** — no `@Autowired` fields. With a single constructor, `@Autowired`
  is even unnecessary. Lombok `@RequiredArgsConstructor` is acceptable but explicit constructors are
  preferred in services with non-trivial wiring.
- `@Transactional` on write methods; `@Transactional(readOnly = true)` on reads.
- The service is the only place that throws domain errors and applies access guards.

**DO**

```java
@Service
@Transactional(readOnly = true)
public class EvaluationJobService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationJobService.class);

    private final EvaluationJobRepository repository;
    private final EvaluationJobMapper mapper;

    public EvaluationJobService(EvaluationJobRepository repository, EvaluationJobMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public EvaluationJobResponse create(UUID orgId, UUID projectId, CreateEvaluationJobRequest req) {
        String name = StringUtils.trimToNull(req.name());
        String slug = SlugGenerator.uniqueSlug(name,
                candidate -> repository.existsBySlugIgnoreCaseAndProjectIdAndDeletedFalse(candidate, projectId));

        EvaluationJob job = mapper.toEntity(req);
        job.setOrganizationId(orgId);
        job.setProjectId(projectId);
        job.setSlug(slug);
        job.setStatus(EvaluationStatus.PENDING);

        EvaluationJob saved = repository.save(job);
        log.info("Created evaluation job orgId={} projectId={} jobId={} slug={}",
                orgId, projectId, saved.getId(), slug);
        return mapper.toResponse(saved);
    }

    public EvaluationJob requireJob(UUID orgId, UUID projectId, UUID jobId) {
        return repository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(jobId, projectId, orgId)
                .orElseThrow(() -> ResourceNotFoundException.of("EvaluationJob", jobId));
    }
}
```

**DON'T**

```java
@Autowired private EvaluationJobRepository repository;   // field injection — banned

public EvaluationJob find(UUID id) {
    return repository.findById(id).orElse(null);          // never return null for "not found"
}
```

### Transaction boundaries

- A transaction is one unit of work and starts at the service method. Do not start transactions in
  controllers or mappers.
- Avoid calling another `@Transactional` public method on the same bean (self-invocation bypasses the
  proxy). Extract to a collaborator instead.
- Don't make remote calls (model providers via `ModelInvoker`/`AgentEndpointInvoker`, Redis) while
  holding a DB transaction open longer than necessary — load/validate, commit, then call out.

---

## 7. DTOs (records)

- Every request/response is a `record`. Validation annotations live on request records; response
  records carry `@Schema` for OpenAPI and **never** contain secrets.
- **Request records OMIT server-controlled fields** (`ownerId`, `organizationId`, `status`, `slug`,
  timestamps) to prevent mass-assignment.

**DO**

```java
public record CreateEvaluationJobRequest(

        @NotBlank @Size(max = 200)
        @Schema(example = "Nightly regression eval")
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        @Schema(example = "01J9X4...")
        UUID profileId

) {}

public record EvaluationJobResponse(
        UUID id,
        String name,
        String slug,
        EvaluationStatus status,
        Instant createdAt
) {}
```

```java
public record CreateModelProviderRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LlmProvider provider,
        @NotBlank @ValidEndpointUrl String baseUrl,
        @NotBlank @Size(max = 4096) String apiKey   // accepted on write, encrypted, never returned
) {}
```

**DON'T**

```java
// Mass-assignment hole: client could set ownerId/status/slug themselves.
public record CreateEvaluationJobRequest(
        String name, UUID ownerId, UUID organizationId, EvaluationStatus status, String slug) {}

// Secret leak: apiKey must never appear in a response record.
public record ModelProviderResponse(UUID id, String name, String apiKey) {}
```

---

## 8. Mapping (MapStruct 1.6.3)

```java
@Mapper(componentModel = "spring")
public interface EvaluationJobMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)      // set by the service, not the request
    @Mapping(target = "slug", ignore = true)
    EvaluationJob toEntity(CreateEvaluationJobRequest request);

    EvaluationJobResponse toResponse(EvaluationJob entity);

    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "profileName", source = "profile.name")
    EvaluationRunResponse toRunResponse(EvaluationRun entity, EvaluationProfile profile);
}
```

- `componentModel = "spring"` always — mappers are injected like any other bean.
- `unmappedTargetPolicy = IGNORE` is set globally via a compiler arg in the build; do not repeat it on
  every mapper. Still, use explicit `@Mapping(... ignore = true)` for server-controlled fields so the
  intent is documented and review-visible.
- Hand-written mapping logic in a controller is a code-review reject. If MapStruct can't express it,
  add a `default` method on the mapper interface.

---

## 9. Optional, null handling, immutability

- Return `Optional<T>` from repositories; in services convert to a value or throw. **Never** return
  `Optional` from a controller or store it in a field/collection.
- Normalize incoming strings with `StringUtils.trimToNull(...)` at the service boundary so `""` and
  `"  "` collapse to `null` consistently.
- Prefer immutable locals (`final`), `List.of(...)`, `Map.copyOf(...)` for returned collections.
- Use `Objects.requireNonNullElse` / `requireNonNull` for invariants; do not write defensive `if (x ==
  null) return;` that hides bugs.

```java
String description = StringUtils.trimToNull(req.description());   // "" and "   " → null
EvaluationProfile profile = profileRepository.findById(req.profileId())
        .orElseThrow(() -> ResourceNotFoundException.of("EvaluationProfile", req.profileId()));
```

---

## 10. Logging (SLF4J)

- One logger per class: `private static final Logger log = LoggerFactory.getLogger(Foo.class);`.
- Use parameterized logging (`{}`), never string concatenation.
- **Always include correlating ids** — `organizationId`, `projectId`, and the resource id.
- **Never log secrets**: JWTs, API keys, `apiKey` request fields, `BROKSFORGE_SECURITY_*`,
  provider credentials, full request bodies.
- `INFO` for state transitions, `WARN` for handled-but-notable conditions, `ERROR` only for real
  failures (with the exception object as the last arg). `DEBUG` for diagnostics.

**DO**

```java
log.info("Evaluation run started orgId={} projectId={} jobId={} runId={}",
        orgId, projectId, jobId, runId);
log.error("Model invocation failed provider={} jobId={}", provider.name(), jobId, ex);
```

**DON'T**

```java
log.info("Creating provider with key " + req.apiKey());     // secret in logs — forbidden
log.info("Request: " + objectMapper.writeValueAsString(req)); // may contain secrets/PII
```

---

## 11. Comments & Javadoc

- Public APIs of `common`, `security`, and the model-provider **SPI** (`ModelInvoker`,
  `AgentEndpointInvoker`, `LlmProvider`) carry Javadoc explaining contract, thrown exceptions, and
  threading expectations.
- Standard CRUD services/controllers need Javadoc only where behavior is non-obvious.
- Comment **why**, not **what**. Delete commented-out code; git remembers it.
- Use `// TODO(username): ...` with an owner, never an anonymous `TODO`.

```java
/**
 * Invokes a configured LLM provider for a single evaluation result.
 *
 * @throws ModelInvocationException if the provider rejects the request or times out
 * @throws UnsupportedProviderException if {@link LlmProvider} has no registered invoker
 */
ModelInvocationResult invoke(ModelInvocationRequest request);
```

---

## 12. TypeScript / Frontend — Naming & Layout

Stack: Next.js 15 (App Router) · React 19 · TypeScript · Tailwind · shadcn/ui · TanStack Query ·
Zustand · React Hook Form · Zod · axios.

| Element              | Convention                  | Example                              |
|----------------------|-----------------------------|--------------------------------------|
| Component file       | `PascalCase.tsx`            | `EvaluationJobTable.tsx`             |
| Hook file            | `use-*.ts`                  | `use-evaluation-jobs.ts`             |
| Util / lib file      | `kebab-case.ts`             | `format-date.ts`, `api-client.ts`    |
| Component / type     | `PascalCase`                | `EvaluationJobTable`, `EvalJob`      |
| Hook / function      | `camelCase`, `use*` hooks   | `useEvaluationJobs`, `buildQueryKey` |
| Zod schema           | `camelCaseSchema`           | `createEvaluationJobSchema`          |
| Constant             | `UPPER_SNAKE` or `as const` | `DEFAULT_PAGE_SIZE`                  |

Folder layout (App Router):

```
app/(dashboard)/organizations/[orgId]/projects/[projectId]/evaluations/page.tsx
components/evaluation/EvaluationJobTable.tsx
hooks/use-evaluation-jobs.ts
lib/api/evaluation.ts        // axios calls
lib/schemas/evaluation.ts    // zod
```

---

## 13. Hooks, Query Keys, Schemas, Forms

**Query keys** are centralized, hierarchical, and typed — never inline string arrays scattered across
components:

```ts
// lib/query-keys.ts
export const evaluationKeys = {
  all: ['evaluations'] as const,
  list: (projectId: string, filter: EvalFilter) =>
    [...evaluationKeys.all, projectId, 'list', filter] as const,
  detail: (projectId: string, jobId: string) =>
    [...evaluationKeys.all, projectId, 'detail', jobId] as const,
};
```

**Zod schema is the single source of truth** for a form; the TS type is inferred from it:

```ts
// lib/schemas/evaluation.ts
import { z } from 'zod';

export const createEvaluationJobSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200),
  description: z.string().max(1000).optional(),
  profileId: z.string().uuid(),
});

export type CreateEvaluationJobInput = z.infer<typeof createEvaluationJobSchema>;
```

**Hook** wraps TanStack Query and the typed key:

```ts
// hooks/use-evaluation-jobs.ts
export function useEvaluationJobs(projectId: string, filter: EvalFilter) {
  return useQuery({
    queryKey: evaluationKeys.list(projectId, filter),
    queryFn: () => fetchEvaluationJobs(projectId, filter),
    staleTime: 30_000,
  });
}
```

**RHF + Zod resolver:**

```ts
const form = useForm<CreateEvaluationJobInput>({
  resolver: zodResolver(createEvaluationJobSchema),
  defaultValues: { name: '', profileId: '' },
});
```

---

## 14. Component structure & UI states

Every data-bound component handles **loading, empty, error, and success** explicitly — no silent
blank screens.

**DO**

```tsx
export function EvaluationJobTable({ projectId, filter }: Props) {
  const { data, isPending, isError, error } = useEvaluationJobs(projectId, filter);

  if (isPending) return <TableSkeleton rows={5} />;
  if (isError)   return <ErrorState message={getErrorMessage(error)} />;
  if (data.content.length === 0) return <EmptyState title="No evaluation jobs yet" />;

  return (
    <DataTable
      rows={data.content}
      page={data.page}
      totalPages={data.totalPages}
    />
  );
}
```

**DON'T**

```tsx
// No loading/error/empty handling → flashes blank, then crashes on undefined.
export function EvaluationJobTable({ projectId }: Props) {
  const { data } = useEvaluationJobs(projectId, {});
  return <DataTable rows={data.content} />;  // 💥 when data is undefined
}
```

- Keep components presentational; data fetching lives in hooks. Mutations use `useMutation` + query
  invalidation, never manual cache surgery unless there's a measured reason.
- Server state → TanStack Query. Ephemeral client/UI state → Zustand or local `useState`. Don't mirror
  server data into Zustand.

---

## 15. Testing & naming

- Backend: JUnit 5 + Mockito + Spring Boot Test; `@DataJpaTest` for repositories (against a real
  Postgres via Testcontainers), `@WebMvcTest` for controllers, plain unit tests for services.
- Test method names: `method_whenCondition_expectedOutcome`.

```java
@Test
void create_whenSlugAlreadyTaken_throwsResourceConflict() { ... }

@Test
void requireJob_whenJobBelongsToOtherProject_throwsResourceNotFound() { ... }
```

- Every bug fix ships with a regression test reproducing it.
- Frontend: Vitest + React Testing Library; test behavior and the four UI states, not implementation
  details. Test files sit next to the unit: `EvaluationJobTable.test.tsx`.

---

## 16. Checklist before opening a PR

- [ ] Entity matches its Flyway migration exactly (`ddl-auto=validate` passes on boot).
- [ ] No field injection; writes are `@Transactional`, reads `readOnly = true`.
- [ ] Request records omit server-controlled fields; responses contain no secrets.
- [ ] Errors are typed `ApiException` subclasses with a stable `ErrorCode` (see
      [`ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md)).
- [ ] No wildcard imports; 4-space indent; logs include ids and no secrets.
- [ ] Controllers follow [`API_GUIDELINES.md`](./API_GUIDELINES.md); OpenAPI annotations present.
- [ ] Frontend handles loading/empty/error/success; query keys centralized; Zod-validated forms.
- [ ] Tests added/updated and named `method_whenCondition_expectedOutcome`.
