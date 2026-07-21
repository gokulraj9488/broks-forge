# Developer Guide — Brok's Forge

> **Brok's Forge** — _The Engineering Platform for AI Agents._
> Modular monolith. Java 21 · Spring Boot 3.4.1 · PostgreSQL/Flyway · Redis · Next.js 15/React 19.

Welcome. This guide takes you from a clean machine to a running stack, a tour of the codebase, and a
worked example of adding a feature end-to-end. Read it alongside the standards docs below.

Related: [`./CODING_STANDARDS.md`](./CODING_STANDARDS.md) · [`./API_GUIDELINES.md`](./API_GUIDELINES.md) · [`./CONTRIBUTING.md`](./CONTRIBUTING.md) · [`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md)

---

## 1. Prerequisites

| Tool             | Version            | Notes                                              |
|------------------|--------------------|----------------------------------------------------|
| JDK              | **21** (LTS)       | Temurin recommended; `java -version` must show 21  |
| Maven            | use `./mvnw`       | Wrapper is committed; no global Maven needed       |
| Docker + Compose | latest             | Postgres, Redis, and full-stack `up`               |
| Node.js          | **20+**            | For the Next.js 15 frontend                        |
| npm              | 10+                | Or pnpm if you prefer; lockfile is npm             |
| Git              | any recent         |                                                    |

Optional but recommended: a Postgres client (`psql`/TablePlus), IntelliJ IDEA with the Lombok and
MapStruct support enabled (annotation processing **on**).

---

## 2. Clone

```bash
git clone git@github.com:broksforge/broks-forge.git
cd broks-forge
cp .env.example .env        # then fill in the values from §3
```

Repository top level:

```
broks-forge/
├── backend/        Spring Boot app (com.broksforge.*)
├── frontend/       Next.js 15 app
├── docker-compose.yml
├── .env.example
└── docs/           you are here
```

---

## 3. Environment variables

Configuration is 12-factor: everything comes from the environment. Populate `.env` (consumed by Docker
Compose and the backend). **Never commit real secrets.**

| Variable                              | Required | Example                                        | Purpose                                              |
|---------------------------------------|----------|------------------------------------------------|------------------------------------------------------|
| `SPRING_DATASOURCE_URL`               | yes      | `jdbc:postgresql://postgres:5432/broksforge`   | JDBC URL (host `postgres` inside Compose)            |
| `SPRING_DATASOURCE_USERNAME`          | yes      | `broksforge`                                   | DB user                                              |
| `SPRING_DATASOURCE_PASSWORD`          | yes      | `change-me-locally`                            | DB password                                          |
| `SPRING_DATA_REDIS_HOST`              | yes      | `redis`                                        | Redis host                                           |
| `SPRING_DATA_REDIS_PORT`              | yes      | `6379`                                         | Redis port                                           |
| `BROKSFORGE_SECURITY_JWT_SECRET`      | yes      | `base64:Yk9...` (≥ 32 bytes)                   | HMAC signing key for JWT (jjwt 0.12.6)               |
| `BROKSFORGE_SECURITY_JWT_TTL`         | no       | `PT1H`                                         | Access-token lifetime (ISO-8601 duration)            |
| `BROKSFORGE_SECURITY_ENCRYPTION_KEY`  | yes      | `base64:32-byte-key`                           | AES key encrypting provider API keys at rest         |
| `BROKSFORGE_AGENT_HEALTH_ENABLED`     | no       | `true`                                         | Toggle periodic agent endpoint health checks         |
| `BROKSFORGE_AGENT_HEALTH_INTERVAL`    | no       | `PT30S`                                        | Health-check interval                                |
| `BROKSFORGE_AGENT_HEALTH_TIMEOUT`     | no       | `PT5S`                                         | Per-check timeout                                    |
| `SPRING_PROFILES_ACTIVE`              | no       | `local`                                        | Active Spring profile                                |
| `NEXT_PUBLIC_API_BASE_URL`            | yes      | `http://localhost:8080/api/v1`                 | Frontend → backend base URL                          |

Generate the two security keys (32 random bytes, base64):

```bash
# JWT signing secret
echo "base64:$(openssl rand -base64 32)"
# AES-256 encryption key for provider credentials
echo "base64:$(openssl rand -base64 32)"
```

> `BROKSFORGE_SECURITY_ENCRYPTION_KEY` encrypts model-provider API keys before they hit the database.
> If it's missing or changes, the app cannot decrypt stored keys — see [pitfalls](#9-common-pitfalls).

Example `.env` for local development:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/broksforge
SPRING_DATASOURCE_USERNAME=broksforge
SPRING_DATASOURCE_PASSWORD=change-me-locally
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
BROKSFORGE_SECURITY_JWT_SECRET=base64:Yk9uVjJ4d1JxT2c5c0ZkR2hKa0xtTnBRc1R1Vw==
BROKSFORGE_SECURITY_JWT_TTL=PT1H
BROKSFORGE_SECURITY_ENCRYPTION_KEY=base64:Q2hhbmdlVGhpc0tleUluUHJvZHVjdGlvbjEyMzQ=
BROKSFORGE_AGENT_HEALTH_ENABLED=true
BROKSFORGE_AGENT_HEALTH_INTERVAL=PT30S
BROKSFORGE_AGENT_HEALTH_TIMEOUT=PT5S
SPRING_PROFILES_ACTIVE=local
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

---

## 4. Run the whole stack with Docker Compose

The fastest path to a working environment:

```bash
docker compose up --build
```

This builds and starts Postgres, Redis, the backend, and the frontend. On first boot it may take a few
minutes to compile the backend and install frontend deps.

Once healthy:

| Service        | URL                                   |
|----------------|---------------------------------------|
| Frontend       | http://localhost:3000                 |
| Backend API    | http://localhost:8080/api/v1          |
| Swagger UI     | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON   | http://localhost:8080/v3/api-docs     |
| Actuator health| http://localhost:8080/actuator/health |

Tear down (keep volumes) / wipe the database:

```bash
docker compose down            # stop, keep data
docker compose down -v         # stop and DROP the Postgres volume (fresh DB next boot)
```

### How Flyway runs

Flyway runs **automatically on backend startup**, before the JPA `EntityManagerFactory` initializes.
Migrations live in `backend/src/main/resources/db/migration` and are named
`V<n>__<description>.sql` (e.g. `V31__create_evaluation_jobs.sql`). Flyway applies any pending
migrations in order and records them in `flyway_schema_history`.

Because `spring.jpa.hibernate.ddl-auto=validate`, Hibernate then verifies that every entity matches the
now-migrated schema. **Any drift fails startup** — that is intentional (see
[`CODING_STANDARDS.md` §4](./CODING_STANDARDS.md#4-entities)).

```bash
# Inspect applied migrations
docker compose exec postgres psql -U broksforge -d broksforge \
  -c "select version, description, success, installed_on from flyway_schema_history order by installed_rank;"
```

> Migrations are immutable once merged. Never edit an applied `V*` file — add a new versioned
> migration instead.

---

## 5. Running services locally (without full Compose)

Run only the infra in Docker, and the apps on your host for fast iteration:

```bash
# 1. Start just Postgres + Redis
docker compose up -d postgres redis

# 2. Backend (port 8080) — picks up .env / exported vars
cd backend
./mvnw spring-boot:run
```

```bash
# 3. Frontend (port 3000) in another terminal
cd frontend
npm install
npm run dev
```

Useful backend commands:

```bash
./mvnw clean verify           # compile + run all tests
./mvnw test -Dtest=EvaluationJobServiceTest
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 6. Project tour by module

Every feature module lives under `com.broksforge.modules.<feature>` and follows the same
`domain / repository / service / web(+dto)` layering described in
[`CODING_STANDARDS.md` §2](./CODING_STANDARDS.md#2-java--naming).

**Cross-cutting (`com.broksforge`)**

| Package                  | Responsibility                                                            |
|--------------------------|---------------------------------------------------------------------------|
| `common.domain`          | `BaseEntity`, `SoftDeletableEntity`                                        |
| `common.exception`       | `ApiException` hierarchy, `ErrorCode`, `GlobalExceptionHandler`           |
| `common.web`             | `PageResponse`, `MessageResponse`                                         |
| `common.validation`      | Custom constraints (`@ValidEndpointUrl`, ...)                            |
| `common.security`        | `SecurityUtils`, role helpers                                            |
| `common.observability`   | Request-id / MDC, logging utilities                                      |
| `common.persistence`     | `JsonMetadataConverter`, base specifications                             |
| `common.util`            | `SlugGenerator`, `StringUtils`                                            |
| `config` (+`properties`) | `@ConfigurationProperties` (`BroksforgeSecurityProperties`, agent health) |
| `security.jwt`           | JWT issuance/verification (jjwt 0.12.6)                                   |
| `security.apikey`        | API-key authentication filter                                            |

**Phase 3 feature modules (`com.broksforge.modules.*`)**

| Module        | What it owns                                                                                   |
|---------------|-----------------------------------------------------------------------------------------------|
| `dataset`     | Datasets and import (CSV/JSON); `DATASET_IMPORT_FAILED` on bad input                           |
| `prompt`      | Prompts and immutable prompt **versions** (`PROMPT_VERSION_NOT_FOUND`)                         |
| `model`       | Provider SPI: `ModelInvoker`, `AgentEndpointInvoker`; `LlmProvider` (OpenAI/Anthropic/Groq/Ollama/Gemini/OpenRouter/DeepSeek) |
| `evaluation`  | `EvaluationJob → EvaluationRun → EvaluationResult`, `EvaluationProfile`, `EvaluationMetricType`; lifecycle `PENDING→RUNNING→COMPLETED/FAILED/CANCELLED` |
| `benchmark`   | Benchmark definitions and runs across providers/models                                        |
| `regression`  | Regression detection over runs                                                                 |
| `analytics`   | Aggregated metrics                                                                             |
| `report`      | Report generation in **JSON / CSV / HTML**                                                     |
| `search`      | Cross-resource search                                                                          |
| `dashboard`   | Dashboard composition / saved views                                                           |

---

## 7. Adding a feature end-to-end

Worked example: add **Evaluation Profiles** to the `evaluation` module. Follow the same order every
time — schema first, UI last.

**Step 1 — Migration (source of truth).** Create the next versioned file. Use `TIMESTAMPTZ`, explicit
types and lengths.

```sql
-- backend/src/main/resources/db/migration/V32__create_evaluation_profiles.sql
CREATE TABLE evaluation_profiles (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    project_id      UUID         NOT NULL,
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    metric_type     VARCHAR(64)  NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_evaluation_profiles_project_slug UNIQUE (project_id, slug)
);
CREATE INDEX ix_evaluation_profiles_project ON evaluation_profiles (project_id);
```

**Step 2 — Entity** (matches the migration *exactly*):

```java
@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "evaluation_profiles",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_evaluation_profiles_project_slug", columnNames = {"project_id", "slug"}),
       indexes = @Index(name = "ix_evaluation_profiles_project", columnList = "project_id"))
public class EvaluationProfile extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false)      private UUID projectId;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "slug", nullable = false, length = 200) private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 64)
    private EvaluationMetricType metricType;
}
```

**Step 3 — Repository:**

```java
public interface EvaluationProfileRepository
        extends JpaRepository<EvaluationProfile, UUID>, JpaSpecificationExecutor<EvaluationProfile> {

    Optional<EvaluationProfile> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    boolean existsBySlugIgnoreCaseAndProjectIdAndDeletedFalse(String slug, UUID projectId);
}
```

**Step 4 — Service** (`@Transactional`, constructor injection, typed errors, slug generation):

```java
@Service
@Transactional(readOnly = true)
public class EvaluationProfileService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationProfileService.class);
    private final EvaluationProfileRepository repository;
    private final EvaluationProfileMapper mapper;

    public EvaluationProfileService(EvaluationProfileRepository repository, EvaluationProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public EvaluationProfileResponse create(UUID orgId, UUID projectId, CreateEvaluationProfileRequest req) {
        String name = StringUtils.trimToNull(req.name());
        String slug = SlugGenerator.uniqueSlug(name,
                c -> repository.existsBySlugIgnoreCaseAndProjectIdAndDeletedFalse(c, projectId));

        EvaluationProfile profile = mapper.toEntity(req);
        profile.setOrganizationId(orgId);
        profile.setProjectId(projectId);
        profile.setSlug(slug);

        EvaluationProfile saved = repository.save(profile);
        log.info("Created evaluation profile orgId={} projectId={} profileId={}", orgId, projectId, saved.getId());
        return mapper.toResponse(saved);
    }
}
```

**Step 5 — DTOs** (request omits server-controlled fields):

```java
public record CreateEvaluationProfileRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull EvaluationMetricType metricType) {}

public record EvaluationProfileResponse(
        UUID id, String name, String slug, EvaluationMetricType metricType, Instant createdAt) {}
```

**Step 6 — Mapper:**

```java
@Mapper(componentModel = "spring")
public interface EvaluationProfileMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    EvaluationProfile toEntity(CreateEvaluationProfileRequest request);

    EvaluationProfileResponse toResponse(EvaluationProfile entity);
}
```

**Step 7 — Controller** (thin; see [`API_GUIDELINES.md`](./API_GUIDELINES.md)):

```java
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-profiles")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Evaluation Profiles")
public class EvaluationProfileController {

    private final EvaluationProfileService service;

    public EvaluationProfileController(EvaluationProfileService service) { this.service = service; }

    @Operation(summary = "Create an evaluation profile")
    @PostMapping
    public ResponseEntity<EvaluationProfileResponse> create(
            @PathVariable UUID organizationId, @PathVariable UUID projectId,
            @Valid @RequestBody CreateEvaluationProfileRequest request) {
        SecurityUtils.requireCurrentUserId();
        EvaluationProfileResponse created = service.create(organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "List evaluation profiles")
    @GetMapping
    public ResponseEntity<PageResponse<EvaluationProfileResponse>> list(
            @PathVariable UUID organizationId, @PathVariable UUID projectId,
            @ParameterObject EvaluationProfileFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(service.search(organizationId, projectId, filter, pageable)));
    }
}
```

**Step 8 — Frontend hook + page:**

```ts
// frontend/hooks/use-evaluation-profiles.ts
export function useEvaluationProfiles(projectId: string, filter: ProfileFilter) {
  return useQuery({
    queryKey: evaluationProfileKeys.list(projectId, filter),
    queryFn: () => fetchEvaluationProfiles(projectId, filter),
    staleTime: 30_000,
  });
}
```

The page component handles loading / empty / error / success states per
[`CODING_STANDARDS.md` §14](./CODING_STANDARDS.md#14-component-structure--ui-states).

**Step 9 — Docs / ADR.** Update Swagger annotations (done inline), add an entry to
[`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md) if the change is architecturally significant, and
write an ADR for any non-trivial decision. Follow [`./CONTRIBUTING.md`](./CONTRIBUTING.md) for branch,
commit, and PR conventions.

---

## 8. Debugging tips

- **Swagger first.** Reproduce any API question against http://localhost:8080/swagger-ui.html before
  writing client code.
- **Correlate by request id.** Every response carries `X-Request-Id`; grep your backend logs for it to
  see the full trace of one request (see [`API_GUIDELINES.md` §11](./API_GUIDELINES.md#11-correlation--request-id-headers)).
- **Inspect the DB:**

  ```bash
  docker compose exec postgres psql -U broksforge -d broksforge
  \dt                                   -- list tables
  \d+ evaluation_jobs                   -- describe a table (compare against the entity)
  ```

- **SQL logging** during a tricky query — temporarily set in `application-local.yml`:

  ```yaml
  logging.level.org.hibernate.SQL: DEBUG
  logging.level.org.hibernate.orm.jdbc.bind: TRACE
  ```

- **Watch Flyway** on boot: the startup log prints which migrations were applied or skipped.
- **Tests over guesses.** Reproduce a backend bug with a `@DataJpaTest`/`@WebMvcTest` first; it becomes
  the regression test (see [`CODING_STANDARDS.md` §15](./CODING_STANDARDS.md#15-testing--naming)).

---

## 9. Common pitfalls

- **Entity / migration drift (`ddl-auto=validate`).** The single most common startup failure. The
  message looks like *"Schema-validation: wrong column type / missing column ..."*. Fix the **entity or
  the migration** so they match exactly — column name, type, nullability, length. Never disable
  validation to make it pass.

- **Missing `BROKSFORGE_SECURITY_ENCRYPTION_KEY`.** The app fails to start, or model-provider creation
  fails, because provider API keys are encrypted with this AES key. Set a 32-byte base64 key (see §3).
  If you rotate it, previously stored provider keys can no longer be decrypted — re-enter them.

- **`TIMESTAMP` vs `TIMESTAMPTZ`.** Always use `TIMESTAMPTZ` in migrations and `Instant`/`OffsetDateTime`
  in Java. A plain `TIMESTAMP` column will both drift against the `Instant` mapping and silently drop
  timezone information.

- **Missing `BROKSFORGE_SECURITY_JWT_SECRET` or too-short key.** jjwt 0.12.6 rejects HMAC keys shorter
  than the algorithm requires; use ≥ 32 bytes.

- **Editing an applied migration.** Flyway validates checksums; modifying a `V*` file that's already in
  `flyway_schema_history` fails on the next boot. Add a new migration instead, or `docker compose down
  -v` to rebuild from scratch locally.

- **Enum stored as ordinal.** All entity enums use `@Enumerated(EnumType.STRING)` (`VARCHAR`).
  Reordering an enum with ordinal storage corrupts data.

- **Field injection / fat controllers.** Use constructor injection; keep persistence and business logic
  in services. A controller touching a repository is a review reject.

- **Returning bare arrays.** Collection endpoints return `PageResponse<T>`, never a raw JSON array (see
  [`API_GUIDELINES.md` §5](./API_GUIDELINES.md#5-response-envelope-conventions)).

---

## 10. Where to go next

- Conventions you must follow: [`./CODING_STANDARDS.md`](./CODING_STANDARDS.md)
- HTTP contract: [`./API_GUIDELINES.md`](./API_GUIDELINES.md)
- Failure modes and error codes: [`./ERROR_HANDLING_GUIDE.md`](./ERROR_HANDLING_GUIDE.md)
- Branching, commits, PRs, ADRs: [`./CONTRIBUTING.md`](./CONTRIBUTING.md)
- The big picture: [`./MASTER_ARCHITECTURE.md`](./MASTER_ARCHITECTURE.md)
