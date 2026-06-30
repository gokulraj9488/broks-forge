# Résumé Points — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/RESUME_POINTS.md` |
> | **Purpose** | Crisp, honest, quantified-where-true résumé bullets and summaries |
> | **Audience** | The engineer writing a résumé / LinkedIn / portfolio; reviewers cross-checking claims |

Every bullet uses **strong verb + what + how + impact**. Numbers marked **(verified)** are counted
from the repo or stated in the architecture docs; numbers marked **(illustrative)** describe shape or
intent and are *not* measured benchmark results — never present an illustrative figure as a measured
metric. When in doubt, prefer the qualitative phrasing.

**Verified facts** (counted from the codebase / source-of-truth docs as of the v1.0.0 release):
~335 backend Java files · 25 append-only Flyway migrations (`V1`–`V25`) · 22 backend modules · 27 REST
controllers · 4 delivery phases · 9 evaluation metric types · 13 `LlmProvider` enum constants (7
provider targets named as shipped-recognized: OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter,
DeepSeek) · AES-256-GCM credential encryption · 20 seeded knowledge-graph nodes + 20 edges · 7-stage
execution-timeline vocabulary · `OWNER > ADMIN > MEMBER` RBAC. **Honesty note:** the backend test
suite is *not yet implemented* (`src/test` is empty); the testing *strategy* is documented but
coverage is future work — do not claim test coverage.

---

## One-liner

> Designed and built **Brok's Forge**, a multi-tenant, provider-agnostic AI-agent engineering
> platform (Java 21 / Spring Boot 3.4.1, PostgreSQL, Next.js 15) — a modular monolith spanning agent
> registry, an evaluation pipeline architected for millions of results, benchmarking, regression
> detection, and an on-read AI engineering advisor — across 4 phases, 22 modules and 25 append-only
> migrations.

---

## Length variants

### 1-line (header / LinkedIn headline)
Built a multi-tenant, provider-agnostic AI-agent **engineering platform** (Spring Boot + Next.js):
agent registry, evaluation-at-scale pipeline, benchmarking/regression, and an on-read advisor — clean
modular-monolith architecture with security and reproducibility built in.

### 3-bullet (résumé project entry)
- **Architected a modular-monolith AI platform** (Java 21 / Spring Boot 3.4.1, PostgreSQL+Flyway,
  Redis, Next.js 15) — 22 modules, ~335 Java files, 25 append-only migrations — with strict id-only
  module boundaries so any module can be extracted to a service mechanically. *(verified)*
- **Designed an evaluation pipeline built to scale to millions of results** (`EvaluationJob →
  EvaluationRun → EvaluationResult`) with precomputed summaries and a queue-ready executor seam, plus
  a provider-agnostic `ModelInvoker` SPI that makes adding an LLM provider a migration-free, code-only
  change. *(verified design; "millions" is the design target, illustrative as a measured number)*
- **Hardened a multi-tenant SaaS by construction** — `(id, projectId, organizationId)` tuple guards
  (IDOR-as-404), AES-256-GCM credential encryption, SSRF outbound-URL guard, RBAC, and
  correlation-ID/structured-logging/Prometheus observability. *(verified)*

### Full (portfolio / detailed résumé) — grouped by theme below.

---

## Architecture

- **Architected Brok's Forge as a modular monolith** of 22 independent feature/infra modules under a
  Clean-Architecture layering (web → service → domain → repository), so the system ships as one
  deployable today yet splits along documented fault lines later. *(verified: 22 modules)*
- **Enforced strict module boundaries** — no cross-module JPA associations, no shared repositories,
  cross-module access only by UUID id + published application services — turning future microservice
  extraction from "archaeology" into a mechanical replace-call-with-network-call. *(verified design;
  [ADR 0001](./adr/0001-modular-monolith.md))*
- **Established `Agent` as the central aggregate root** every module attaches to by `agentId` /
  `agentVersionId`, giving the platform one stable concept of "the thing under test" instead of each
  module inventing its own. *([ADR 0002](./adr/0002-agent-as-central-entity.md))*
- **Authored 15 Architecture Decision Records and a source-of-truth Master Architecture doc**,
  recording alternatives considered and trade-offs owned for every significant decision. *(verified:
  ADRs 0001–0015)*

## Backend (Java / Spring)

- **Built a Java 21 / Spring Boot 3.4.1 backend** (~335 Java files, 27 REST controllers) with thin
  controllers, rich `@Transactional` services, immutable record DTOs and MapStruct mappers at the
  edge. *(verified: ~335 files, 27 controllers)*
- **Made mass-assignment structurally impossible** by shaping request DTOs as Java records that omit
  every server-controlled field (ids, tenancy keys, audit columns, status pointers, `version`), so a
  client physically cannot set them. *(verified design)*
- **Standardized error handling** behind a single `GlobalExceptionHandler` and a stable `ApiError` /
  `ErrorCode` contract — typed exceptions render consistent responses and **no stack trace ever
  leaks**. *(verified design)*
- **Designed a provider-agnostic `ModelInvoker` SPI** with a `ModelInvocationService` dispatcher and a
  shipped `AgentEndpointInvoker` that evaluates any agent over its registered HTTP endpoint; adding a
  provider is code-only with no schema change. *([ADR 0006](./adr/0006-provider-agnostic-model-invocation.md))*

## AI / Evaluation

- **Designed the evaluation engine as a fan-out hierarchy** (`EvaluationJob → EvaluationRun →
  EvaluationResult`) sized for `items × metrics` cardinality, with insert-only hot paths,
  partition-by-job layout, and precomputed job summaries so downstream readers touch one row per job
  instead of millions. *(verified design; "millions" is the design target — illustrative as a number;
  [ADR 0005](./adr/0005-evaluation-job-as-top-level-aggregate.md))*
- **Built a pluggable metric engine** of 9 evaluation metric types (EXACT_MATCH, CONTAINS,
  REGEX_MATCH, JSON_VALID, LENGTH, LATENCY, COST, TOKEN_COUNT, NON_EMPTY) resolved from a strategy
  registry — adding a metric is one enum constant + one bean, no migration. *(verified: 9 metrics)*
- **Implemented benchmarking, regression detection, and analytics** over precomputed job summaries:
  6 benchmark comparison axes (agent/version/prompt/model/dataset/profile) with leaderboards,
  baseline-vs-candidate regression checks, and cost/latency/token trend analytics. *(verified)*
- **Built an on-read AI Engineering Advisor** — five pure per-domain sub-advisors (Prompt, Model,
  Cost, Agent, RAG) composed by `AdvisorService` — producing recommendations carrying *why / what
  changed / how to fix / expected improvement / confidence / severity / evidence*, computed on read so
  they never drift. *([ADR 0011](./adr/0011-ai-engineering-advisor.md))*
- **Implemented a pure `RootCauseEngine`** that turns red results into diagnoses (timeout / HTTP error
  / empty output / JSON-invalid / exact-match miss / latency / cost / token) over published evaluation
  reads, and an **AI Debugger** reconstructing a 7-stage per-run execution timeline — honestly marking
  unobservable stages `NOT_INSTRUMENTED` rather than faking them. *([ADR 0012](./adr/0012-root-cause-analysis-engine.md),
  [ADR 0014](./adr/0014-ai-debugger-and-tracing-seam.md))*
- **Modeled an Engineering Knowledge Graph** (20 seeded nodes + 20 typed edges, `node_key`-addressed)
  with an `occurrence_count` learning seam that advisor/root-cause findings increment on read.
  *(verified: 20 + 20; [ADR 0013](./adr/0013-engineering-knowledge-graph.md))*

## Security

- **Implemented tenant isolation as IDOR-as-404** — every aggregate resolved by its full
  `(id, projectId, organizationId)` tuple via per-aggregate access guards, so a foreign id is a 404,
  never a 403 that leaks existence. *(verified design)*
- **Encrypted agent usage credentials with AES-256-GCM** (random 96-bit IV per value, 128-bit auth
  tag, 256-bit env-supplied key, version-stamped ciphertext for rotation) — write-only over the API,
  never returned, never logged — while keeping one-way hashing (BCrypt / SHA-256) for verification
  secrets. *([ADR 0003](./adr/0003-credential-encryption-vs-hashing.md))*
- **Defended against SSRF on user-supplied agent endpoints** with two layers: syntactic
  `@ValidEndpointUrl` on write and a runtime `OutboundUrlGuard` that re-resolves the host and blocks
  private/loopback/link-local/metadata targets on *every* outbound call. *([ADR 0004](./adr/0004-ssrf-protection-for-agent-endpoints.md))*
- **Built RBAC and stateless auth** — `OWNER > ADMIN > MEMBER` org roles enforced centrally in the
  service layer, JWT access tokens with rotating refresh tokens (password change revokes all
  sessions), SHA-256 API keys shown once, and security headers (CSP / HSTS / `frame-ancestors`) on
  every response. *(verified design)*
- **Neutralized export-time injection** — CSV/formula-injection and HTML-XSS-safe rendering in report
  exports. *(verified design)*

## Data

- **Made the database the source of truth** — Flyway-owned schema with `ddl-auto=validate` so Hibernate
  never mutates the schema and a entity/schema mismatch fails the boot; 25 append-only migrations that
  are never edited. *(verified: 25 migrations)*
- **Modeled immutable, versioned artifacts for reproducibility** — `Dataset → DatasetVersion →
  DatasetItem` and `Prompt → PromptVersion` (with `{{variable}}` templating, activate/rollback,
  version compare) — so an evaluation pinned to a `*VersionId` is forever reproducible. *([ADR 0007](./adr/0007-immutable-versioned-datasets.md),
  [ADR 0008](./adr/0008-prompt-templating-and-versioning.md))*
- **Designed a universal table skeleton** (UUID PK via `gen_random_uuid()`, optimistic `version`,
  audit columns, soft-delete, text-backed enums, tenancy columns + FKs + indexes) applied
  consistently across every table. *(verified design)*

## Frontend

- **Built a Next.js 15 / React 19 / TypeScript web app** (App Router, TailwindCSS, shadcn-style Radix
  UI, TanStack Query, Zustand, React Hook Form + Zod) covering auth, dashboard, agents, datasets,
  prompts, evaluations, benchmarks, regressions, analytics, reports, advisor, root-cause, debugger and
  knowledge workspaces. *(verified: routes present in `src/app/(dashboard)`)*
- **Implemented a resilient data layer** — axios interceptors with transparent token refresh,
  protected routes, and per-resource TanStack Query hooks against the typed REST surface.
  *(verified design)*

## DevOps / Release

- **Containerized the full stack** with Docker Compose (`api`, `postgres`, `redis`, `web`), 12-factor
  env config, and fail-fast startup when a required secret (JWT signing key, encryption key) is
  absent. *(verified)*
- **Made the release operable** — added Prometheus metrics via Micrometer (`/actuator/prometheus`,
  ADMIN-guarded), native ECS-JSON structured logging in the container profile, and Kubernetes-grade
  liveness/readiness probes (readiness reflects DB reachability). *([ADR 0015](./adr/0015-production-observability-metrics-and-structured-logging.md))*
- **Propagated correlation/request IDs** through MDC, every log line, and response headers
  (`X-Correlation-Id` / `X-Request-Id`), with an OpenTelemetry-ready `TraceRecorder` seam for future
  spans. *(verified design)*

## Docs

- **Wrote the engineering documentation suite** — a source-of-truth Master Architecture, an
  onboarding Engineering Handbook, project rules, coding/security/testing/performance/API/error guides,
  a roadmap, a feature-decision log, and 15 ADRs — to a standard where a new senior engineer can ship
  a correct contribution without chat history. *(verified: doc set present in `docs/`)*

---

## Tailoring tips (use, don't paste blindly)

- **AI-eval roles (OpenAI/Anthropic/etc.):** lead with the evaluation pipeline, provider-agnostic SPI,
  on-read advisor, and the honest observability/`NOT_INSTRUMENTED` stance.
- **Platform/infra roles:** lead with modular-monolith boundaries, the queue-ready executor seam,
  schema-as-truth + Flyway discipline, and the release observability work.
- **Security-leaning roles:** lead with IDOR-as-404, AES-256-GCM, SSRF guard, RBAC, and secret
  handling.
- **Always keep the honesty notes:** "millions" is a design target, provider-direct clients are an
  unshipped extension point, and the backend test suite is not yet implemented. Strong reviewers
  reward calibrated claims and punish inflated ones.
