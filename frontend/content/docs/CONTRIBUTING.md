# Contributing to Brok's Forge

Thanks for contributing to **Brok's Forge — The Engineering Platform for AI Agents**. This guide
covers how to set up the project, the conventions we hold every change to, and the bar a change
must clear before it merges.

Before you start, read the rules that govern this codebase — they are not optional:

- [PROJECT_RULES.md](./PROJECT_RULES.md) — the non-negotiable engineering rules.
- [ROADMAP.md](./ROADMAP.md) — where the project is and where it's going.
- [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md) — the decision log.
- [adr/README.md](./adr/README.md) — Architecture Decision Records.
- [CODING_STANDARDS.md](./CODING_STANDARDS.md) — detailed code-style conventions.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | **21** (LTS) | Backend (`com.broksforge`, Spring Boot 3.4.1). |
| **Node.js** | **20+** | Frontend (Next.js 15 / React 19 / TypeScript). |
| **Docker** + Docker Compose | current | PostgreSQL, Redis, and one-command local stack. |
| `openssl` | any | Generating local secrets (commands below). |
| `git` | any | Version control and semantic commits. |

A POSIX shell is assumed in the examples; on Windows use Git Bash or WSL for the `openssl`
commands.

---

## Local setup

### 1. Generate local secrets

Both the JWT secret and the credential-encryption key must be supplied via the environment — they
are **never** committed (see [PROJECT_RULES.md → SEC-2](./PROJECT_RULES.md)). Generate a fresh
256-bit value for each:

```bash
# JWT signing secret
openssl rand -base64 32

# AES-256-GCM credential-encryption key (must be a separate value)
openssl rand -base64 32
```

Put them in a local `.env` file (which is git-ignored — never commit it):

```bash
# .env (local only — do not commit)
BROKSFORGE_SECURITY_JWT_SECRET=<paste first `openssl rand -base64 32` value>
BROKSFORGE_SECURITY_ENCRYPTION_KEY=<paste second `openssl rand -base64 32` value>

# Datasource / cache (defaults shown; match docker-compose.yml)
POSTGRES_DB=broksforge
POSTGRES_USER=broksforge
POSTGRES_PASSWORD=broksforge
SPRING_PROFILES_ACTIVE=dev
```

> The two values **must differ**. Losing `BROKSFORGE_SECURITY_ENCRYPTION_KEY` makes stored agent
> credentials unrecoverable; rotating it is supported via the version-stamped ciphertext format
> (see [ADR 0003](./adr/0003-credential-encryption-vs-hashing.md)).

### 2. Start the stack

```bash
docker compose up
```

This brings up **PostgreSQL** and **Redis** and runs the backend. On startup:

- **Flyway** applies migrations `V1`..`Vn` to the database.
- Hibernate runs with **`ddl-auto=validate`** and refuses to start if any entity diverges from the
  schema — your first signal that an entity and its migration are out of sync.

### 3. Run the frontend (if not containerized in your setup)

```bash
cd frontend
npm install
npm run dev
```

### 4. Useful checks

- API docs (springdoc / OpenAPI UI) are served by the backend once it is up.
- Actuator health and info endpoints are exposed for liveness checks.

---

## Branch & commit conventions

### Branches

Branch off the default branch using a typed, descriptive name:

```text
feat/evaluation-job-executor
fix/dataset-csv-import-encoding
docs/roadmap-phase4
refactor/prompt-version-compare
chore/bump-springdoc
```

### Commits — Conventional / semantic commits

Use **semantic commit** messages: `type(scope): summary`.

```text
feat(evaluation): add EvaluationJob → Run → Result pipeline
fix(agent): scope health-check load by (id, projectId, organizationId)
docs(adr): add ADR 0005 for async eval workers
refactor(prompt): extract {{variable}} renderer
test(dataset): cover immutable DatasetVersion invariants
chore(deps): bump Flyway to latest patch
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `perf`, `chore`, `build`, `ci`.
Keep the summary imperative and under ~72 characters; explain *why* in the body when it isn't
obvious. Reference the migration range or ADR in the body when relevant.

---

## Definition of done

A change is **done** only when **all** of the following are true:

- [ ] **Tests** — new behavior is covered; existing tests pass; tenant-isolation and validation
      paths are exercised where relevant.
- [ ] **Schema parity** — the app boots under `ddl-auto=validate`; every new entity matches its
      migration; migrations are append-only (no released `V1..Vn` touched).
- [ ] **Security** — no secret stored in plaintext, logged, or returned; RBAC via
      `OrganizationAccessService`; all loads scoped by `(id, projectId, organizationId)`; outbound
      URLs guarded.
- [ ] **API contract** — endpoints under `/api/v1`, validated request **records** that omit
      server-set fields, `PageResponse<T>` for lists, `ApiError`/`ErrorCode` for failures, no stack
      traces leaked.
- [ ] **Docs** — [ROADMAP.md](./ROADMAP.md) and [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md)
      updated; module/feature docs updated.
- [ ] **ADRs** — any architectural/security decision is recorded as a new ADR and cross-linked from
      [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md); existing ADRs are not rewritten.
- [ ] **Self-review** — the author has walked the
      [phase self-review checklist](./PROJECT_RULES.md) (PROC-4) for the touched area.
- [ ] **No regressions** — no existing feature removed, simplified, or weakened
      ([ARCH-3](./PROJECT_RULES.md)).

---

## Code style

Detailed conventions live in [CODING_STANDARDS.md](./CODING_STANDARDS.md). The essentials:

- **Java 21** idioms: records for DTOs, sealed types where they clarify intent, pattern matching.
- **Lombok** for boilerplate; **MapStruct** for entity↔DTO mapping (no hand-rolled mappers where a
  MapStruct mapper fits).
- **Bean Validation** on every request DTO; never trust unvalidated input.
- Constructor injection only; no field injection.
- Keep controllers thin, services transactional, repositories query-only.
- **Frontend**: TypeScript strict, Tailwind + shadcn/ui for components, TanStack Query for server
  state, Zustand for client state, React Hook Form + Zod for forms and validation.
- Never log secrets, tokens, or decrypted credentials.

---

## How to add a new module

Brok's Forge is a **modular monolith**. A new feature is a new module under
`com.broksforge.modules.<feature>`. Follow this layout and these rules.

### 1. Folder layout

```text
com.broksforge.modules.<feature>
├── domain/        # @Entity classes (extend BaseEntity), domain enums, value objects
├── repository/    # Spring Data repositories — this module's tables only
├── service/       # application services (the module's published API to other modules)
└── web/           # @RestController endpoints
    └── dto/       # request/response records with Bean Validation
```

### 2. Migration numbering

- Add a **new** migration with the next number — never edit a released one
  ([DB-1](./PROJECT_RULES.md)). P1 used `V1..V5`, P2 used `V6..V10`, P3 starts at `V11`; continue
  the sequence.
- Migrations are **append-only**. If you need to change a column, write a new `ALTER` migration.

### 3. Entity ↔ migration parity

- The **database is the source of truth.** Write the migration first, then make the entity match it
  exactly — column names, nullability, types, defaults.
- Every new table must include: **UUID PK**, **audit columns**, optimistic **`version`**,
  **soft-delete**, the right **indexes**, and **FK / CHECK / UNIQUE** constraints
  ([DB-3, DB-4](./PROJECT_RULES.md)).
- The app boots under `ddl-auto=validate`; a mismatch fails fast — use that as your check.

### 4. Module boundaries

- Reference other modules **only by UUID id and through their published services**. Do **not** add
  cross-module JPA associations and do **not** depend on another module's repository or entity
  ([ARCH-1](./PROJECT_RULES.md), [ADR 0001](./adr/0001-modular-monolith.md)).
- Where the feature operates on an agent, attach by `agentId` / `agentVersionId`
  ([ADR 0002](./adr/0002-agent-as-central-entity.md)).

### 5. Security & API wiring

- Enforce access with `OrganizationAccessService.requireRole` / `requireMembership` and load
  resources by the full `(id, projectId, organizationId)` tuple ([SEC-4](./PROJECT_RULES.md)).
- Expose endpoints under `/api/v1` on the nested org/project path, return `PageResponse<T>` for
  lists, and surface failures as `ApiError` + `ErrorCode`.
- Any user-controlled outbound URL goes through `@ValidEndpointUrl` + `OutboundUrlGuard`
  ([SEC-5](./PROJECT_RULES.md), [ADR 0004](./adr/0004-ssrf-protection-for-agent-endpoints.md)).
- Any usage secret is encrypted (AES-256-GCM), write-only, masked in responses
  ([SEC-1..SEC-3](./PROJECT_RULES.md), [ADR 0003](./adr/0003-credential-encryption-vs-hashing.md)).

### 6. Registration is automatic — do not wire anything by hand

You do **not** manually register components, repositories, or configuration. Spring's
**component scan** discovers `@Component`/`@Service`/`@Repository`/`@RestController` under
`com.broksforge`, and **`@ConfigurationPropertiesScan`** discovers `@ConfigurationProperties`
beans. Place your classes in the package layout above and they are picked up automatically — there
is no central registry to edit.

---

## Review checklist

Reviewers (and authors, before requesting review) confirm:

- [ ] Migrations are **append-only**; no released migration was modified, renumbered, or deleted.
- [ ] App boots under **`ddl-auto=validate`**; every new entity matches its migration exactly.
- [ ] New tables have **UUID PK, audit columns, version, soft-delete, indexes, and
      FK/CHECK/UNIQUE** constraints.
- [ ] **No cross-module JPA associations or shared repositories**; references are UUID id +
      published service.
- [ ] **No secret** is stored in plaintext, logged, or returned; responses carry masked metadata
      only; the encryption key is read from the environment.
- [ ] **RBAC** is enforced via `OrganizationAccessService`; all loads are scoped by
      `(id, projectId, organizationId)`.
- [ ] User-controlled outbound URLs pass **`@ValidEndpointUrl` + `OutboundUrlGuard`**.
- [ ] Endpoints are under **`/api/v1`**, use **`PageResponse<T>`**, return **`ApiError`/`ErrorCode`**,
      and never leak stack traces.
- [ ] Request DTOs are **validated records** that omit server-set fields.
- [ ] **No existing feature** was removed, simplified, or weakened.
- [ ] **Docs and ADRs** are updated and cross-linked; the change answers a real AI-engineering
      problem.

---

By contributing, you agree to keep Brok's Forge **secure, multi-tenant, and architecturally
coherent**. When in doubt, prefer the conservative, additive path and open an ADR.

See also: [PROJECT_RULES.md](./PROJECT_RULES.md) · [ROADMAP.md](./ROADMAP.md) ·
[FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md) · [adr/README.md](./adr/README.md) ·
[CODING_STANDARDS.md](./CODING_STANDARDS.md)
