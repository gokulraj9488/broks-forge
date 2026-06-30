# Interview Guide — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/INTERVIEW_GUIDE.md` |
> | **Purpose** | Likely interview questions with strong, specific answers grounded in this project |
> | **Audience** | The engineer prepping to discuss Brok's Forge in interviews |
> | **Source of truth** | [MASTER_ARCHITECTURE.md](./MASTER_ARCHITECTURE.md), the [ADRs](./adr/README.md), [SECURITY_GUIDE.md](./SECURITY_GUIDE.md) |

Use the answers as *scaffolding*, not a script — say them in your own voice and be ready to go one
level deeper than each one. Where an answer states a limitation, **say the limitation out loud**:
strong interviewers reward calibrated honesty.

---

## How to frame the project in 30 seconds

> Brok's Forge is an engineering platform for AI agents — the system of record for whether an agent
> is any good. You register an agent, point versioned datasets and prompts at it, run objective
> evaluations, benchmark variants, catch regressions, and get on-read recommendations and root-cause
> diagnoses. It's a multi-tenant modular monolith — Java 21 / Spring Boot, PostgreSQL + Flyway, Redis,
> Next.js 15 — built provider- and framework-agnostic, with the evaluation pipeline shaped to scale to
> millions of results behind a queue-ready seam. Four phases: foundation, agent registry, the
> intelligence layer, and the AI engineering advisor.

---

## 1. System design

**Q: Walk me through the high-level architecture.**
A modular monolith of independent feature modules (`auth`, `organization`, `project`, `agent`,
`dataset`, `prompt`, `model`, `evaluation`, `benchmark`, `regression`, `analytics`, `report`,
`search`, `dashboard`, `advisor`, `rootcause`, `debugger`, `knowledge`, …) over shared cross-cutting
infra (`common`, `config`, `security`). Each module is layered web → service → domain → repository,
dependencies pointing downward. Modules never share repositories or hold cross-module JPA
associations; they reference each other by UUID id and read through published application services.
`Agent` is the central aggregate root everything attaches to. The DB (PostgreSQL, Flyway) is the
source of truth with `ddl-auto=validate`.

**Q: Why a modular monolith and not microservices?**
We wanted the operational simplicity of one deployable now, with the *option value* of microservices
later. The key is that the module boundaries are real: id-only references + published services mean
extraction is mechanical — replace an in-process call with a network call, keep the schema. There are
no hidden joins to untangle ([ADR 0001](./adr/0001-modular-monolith.md)). Premature microservices
would have meant distributed transactions and ops overhead before there was scale to justify them.

**Q: What would you extract first if you had to split it?**
The Evaluation Engine (`evaluation` + `model`) — it's the highest-volume path and the
`EvaluationJobExecutor` is already the queue/worker seam, so it becomes a worker fleet behind a queue
with no domain change. After that, Insights (`benchmark`/`regression`/`analytics`/`report`) as a
downstream read-side service, and Identity/Tenancy as the natural auth boundary.

## 2. API design

**Q: How are your APIs structured?**
Versioned under `/api/v1`, tenant-nested under
`/api/v1/organizations/{orgId}/projects/{projectId}/<resource>` so tenancy is in the path and every
handler resolves the full identity tuple. Controllers are thin: authenticate
(`@PreAuthorize("isAuthenticated()")`), resolve the caller, delegate to a service, map to a record
DTO. Pagination is Spring `Pageable` + a `PageResponse<T>` envelope. Errors are a single stable
`ApiError` shape `{ timestamp, status, error, code, message, path, errors[] }`. Knowledge-graph
endpoints are *not* tenant-nested because the graph is platform-global reference data.

**Q: How do you prevent mass assignment / over-posting?**
Request DTOs are Java records that *omit every server-controlled field* — ids, tenancy keys, audit
columns, status pointers, `version`. A client physically cannot set `organizationId`, `ownerId`,
`status` or `createdBy`; MapStruct maps only declared request fields, and services set the rest.

## 3. Security

**Q: How do you do tenant isolation / prevent IDOR?**
Every aggregate is loaded by its full `(id, projectId, organizationId)` tuple through a per-aggregate
access guard. A foreign id is indistinguishable from a missing one, so it resolves to **404, not a
403 that confirms existence** — that closes IDOR and cross-tenant enumeration in one rule, applied
identically everywhere, including the Phase-4 advisors (which read only through tenant-scoped
published services).

**Q: Why encrypt agent credentials instead of hashing them?**
Because we must *replay* the credential to the agent's endpoint at call time — a one-way hash is
unusable as an outbound secret. So verification secrets (passwords, API keys, reset/verify tokens)
are hashed (BCrypt / SHA-256), but agent *usage* credentials are encrypted with AES-256-GCM: random
96-bit IV, 128-bit auth tag, 256-bit env-supplied key, version-stamped ciphertext for rotation.
They're write-only over the API, never returned (only a masked hint), never logged
([ADR 0003](./adr/0003-credential-encryption-vs-hashing.md)).

**Q: How do you handle SSRF? The agent endpoints are user-supplied URLs you call outbound.**
Two layers: `@ValidEndpointUrl` does syntactic validation on write (scheme/shape/length), and
`OutboundUrlGuard` does runtime validation at call time — it re-resolves the host and blocks
private/loopback/link-local/metadata targets by default (e.g. `169.254.169.254`). The guard runs on
*every* outbound call — health checks and the evaluation `AgentEndpointInvoker` alike. It's
overridable per environment (`AGENT_HEALTH_ALLOW_PRIVATE_TARGETS` / `MODEL_ALLOW_PRIVATE_TARGETS`) for
local dev ([ADR 0004](./adr/0004-ssrf-protection-for-agent-endpoints.md)).

**Q: Walk me through RBAC.**
Platform roles `USER`/`ADMIN` on the user, plus org roles `OWNER > ADMIN > MEMBER` compared with
`isAtLeast(...)`. Authorization is enforced in the *service* layer (`OrganizationAccessService
.requireRole/.requireMembership`), not just controllers, so it can't be bypassed via an alternate
call path. JWT access tokens are short-lived; refresh tokens are opaque, server-side, and rotated on
every refresh; changing a password revokes all sessions.

**Q: Why is CSRF disabled?**
It's a stateless, token-in-header API — tokens travel in `Authorization` / `X-API-Key` headers that
browsers don't attach automatically, so the CSRF threat model doesn't apply. Tokens are stored by the
SPA and sent explicitly under a strict CSP.

## 4. Data modeling & scale

**Q: Your evaluation table will be huge. How is it modeled and why does it scale?**
As a fan-out tree: `EvaluationJob` (1) → `EvaluationRun` (one per dataset item) → `EvaluationResult`
(one per metric per run). The cardinality is `items × metrics`. It scales because: the hot path is
*insert-only* and *partitionable by job* (the FKs are indexed); the job carries a *precomputed
summary* so every downstream reader — benchmarks, regression, analytics, dashboard — reads one row
per job, not millions; and a single failed item fails *its run*, not the whole job
([ADR 0005](./adr/0005-evaluation-job-as-top-level-aggregate.md)). Lists and dashboards never `GROUP
BY` over the leaf rows.

**Q: What did you give up with that design?**
More tables and joins, and *write amplification* — one row per run *and* per metric means a job emits
a lot of rows. We accept that as the cost of queryability and per-`agentVersionId` attribution, and
mitigate with the precomputed summary and future partitioning/retention. The summary is denormalized
state the executor must keep consistent.

**Q: How do you guarantee an evaluation is reproducible months later?**
Datasets and prompts are *immutable, versioned* artifacts. A job pins a `datasetVersionId` and a
`promptVersionId`; importing a corrected CSV produces a *new* version rather than mutating the old one,
so the inputs can never shift under a historical result
([ADR 0007](./adr/0007-immutable-versioned-datasets.md), [ADR 0008](./adr/0008-prompt-templating-and-versioning.md)).

## 5. Provider-agnostic design

**Q: How do you support many LLM providers without coupling?**
A `ModelInvoker` SPI behind a `ModelInvocationService` dispatcher keyed by the `LlmProvider` enum.
Callers (notably the evaluation executor) depend on the dispatcher, never a concrete invoker. The
shipped concrete invoker is `AgentEndpointInvoker`, which calls the agent's *own registered HTTP
endpoint* as a black box — so any agent that speaks HTTP is evaluable regardless of framework.
Providers and frameworks are *text-backed enums*, so adding one is a code-only change with no
migration ([ADR 0006](./adr/0006-provider-agnostic-model-invocation.md)).

**Q: Be honest — are the provider-direct clients (OpenAI/Anthropic) actually built?**
No, and the docs say so. Provider-direct invokers are a *documented SPI extension point* — implement
`ModelInvoker`, register the bean, the dispatcher routes to it — but they're not shipped yet. The real,
key-free execution path today is the agent's HTTP endpoint. I'd rather be precise about that than
overclaim.

## 6. The advisor / Phase 4

**Q: Is the "AI advisor" an LLM chatbot?**
No — deliberately. It's a *recommendation engine*: five pure, per-domain analyzers (Prompt, Model,
Cost, Agent, RAG) composed by `AdvisorService`. Recommendations are *computed on read and never
persisted* — like benchmark leaderboards — so they can never drift. A persisted recommendations table
would go stale (advice about a job whose model later changed becomes a lie) and need a scheduler +
invalidation; an LLM assistant would be non-deterministic and untestable. Every recommendation carries
a fixed shape: why / what changed / how to fix / expected improvement / confidence / severity /
evidence ([ADR 0011](./adr/0011-ai-engineering-advisor.md)).

**Q: How does the AI Debugger avoid lying about stages it can't see?**
It reconstructs a 7-stage timeline (`PROMPT → MEMORY → RETRIEVER → TOOLS → MODEL → PARSER → OUTPUT`)
from *persisted* run data. It populates `PROMPT`, `MODEL`, `PARSER`, `OUTPUT` from what it actually
has, and reports `MEMORY`/`RETRIEVER`/`TOOLS` as **`NOT_INSTRUMENTED`** — a first-class status — rather
than inventing durations. A no-op `TraceRecorder` SPI is the drop-in point for real spans later
([ADR 0014](./adr/0014-ai-debugger-and-tracing-seam.md)).

## 7. Testing strategy

**Q: How do you test this? What's your coverage?**
The architecture is built *for* testability — the metric engine, the five sub-advisors and the
`RootCauseEngine` are *pure* (no I/O): they take already-loaded inputs and return value records, so
they're deterministic unit targets; controllers are thin; provider behavior sits behind an SPI that's
trivial to fake; access guards centralize the security-critical paths. **Honest status:** the
documented testing strategy (unit for pure logic, slice tests for web, integration with Testcontainers
for repositories/security) is the *plan*, but the repository does not yet ship an automated backend
test suite — `src/test` is currently empty. I wouldn't claim coverage that doesn't exist; building the
suite against those seams is the next quality investment, and the design was made to make it cheap.

## 8. Observability

**Q: How would you debug a production incident here?**
Every request carries a correlation id and a request id, both put into the SLF4J MDC, surfaced on
every log line, and returned in `X-Correlation-Id` / `X-Request-Id` headers — so you can trace an
incident end to end without exposing any secret. For the v1 release we added Prometheus metrics via
Micrometer (`/actuator/prometheus` — JVM, per-URI HTTP latency/error rate, HikariCP pool, Flyway) and
ECS-JSON structured logs in the container profile, plus liveness/readiness probes where readiness
reflects DB reachability ([ADR 0015](./adr/0015-production-observability-metrics-and-structured-logging.md)).

**Q: Do you have distributed tracing?**
Not wired yet — that's a deliberate scope boundary. The stack is OpenTelemetry-*ready*: the
correlation id is the natural trace seam and the `TraceRecorder` SPI is the drop-in point, but no OTLP
exporter or collector ships in v1. Adding one is a config-and-dependency change that also lights up the
AI Debugger's `NOT_INSTRUMENTED` stages.

## 9. "Tell me about a hard trade-off"

**The synchronous evaluation executor.** Evaluation must eventually run async on horizontally
scalable workers to sustain millions of results. Building that distributed infrastructure up front
would have delayed the entire Intelligence Layer and added ops complexity before there was volume to
justify it. So I implemented execution *synchronously today*, but designed the boundary as a
*queue-ready seam*: `EvaluationJobExecutor` consumes a job as a self-contained unit of work, status
transitions are explicit, and nothing outside the executor assumes synchronous completion. The cost I
accepted: a long job holds the request thread and items/job are bounded (default 500). The payoff:
swapping in durable queues + async workers later is an infrastructure change behind the seam, not a
domain rewrite — the `Job → Run → Result` schema and the API don't move
([ADR 0005](./adr/0005-evaluation-job-as-top-level-aggregate.md), FD-008). That's the pattern I'm
proud of: ship value now, but make the expensive future change *contained* by isolating it behind one
seam.

---

## Questions THEY might ask about this repo (and your honest answers)

- **"Why are there no tests?"** The suite isn't built yet; the design is deliberately testable (pure
  engines, SPI seams, thin controllers). It's my stated next quality investment, and I won't pretend
  coverage exists.
- **"Millions of evaluations — have you actually run that?"** No. "Millions" is the *design target*
  the schema and executor seam are shaped for, not a measured load test. The shape (insert-only,
  partition-by-job, precomputed summaries) is what makes that scale reachable.
- **"Is the knowledge graph real ML/learning?"** No — it's seeded relational reference data (20 nodes,
  20 edges) with an `occurrence_count` that findings increment. That counter is the *seam* for future
  learning, not a learned model today. Stated honestly in the docs.
- **"Spring AI is a dependency — what does it do?"** It's a client-chat dependency only — a *candidate*
  backing for a future `ModelInvoker`, not a coupling. No platform logic depends on it.
- **"Why is the README banner 'Milestone 1: Foundation' if you've shipped four phases?"** The README
  banner lagged the build; the authoritative state is the Master Architecture and Roadmap (Phases 1–4
  delivered, v1 release adds production observability). Good catch to surface that drift.

## Questions YOU can ask the interviewer

- How does the team draw the line between *measuring* agent quality and *advising* on it today? Where
  does on-read derivation stop scaling for you?
- How do you handle reproducibility of evaluations — do you pin immutable dataset/prompt versions, or
  is that a gap?
- Where do you sit on the provider-agnostic vs. deep-single-provider trade-off for your eval stack?
- What's your appetite for monolith-with-clean-seams vs. microservices at the current stage, and what
  forced (or would force) the split?
- How do you keep advice/recommendations from drifting from the data they describe?
