# Project Story — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/PROJECT_STORY.md` |
> | **Purpose** | The narrative arc — the problem, the build, the hard parts, the trade-offs, the outcome |
> | **Audience** | Hiring managers and staff engineers evaluating the engineer behind this project |
> | **Companion docs** | [MASTER_ARCHITECTURE.md](./MASTER_ARCHITECTURE.md) (source of truth), [ROADMAP.md](./ROADMAP.md), [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md), [adr/](./adr/README.md) |

This is the story of *why* Brok's Forge exists, *how* it was built, and *what was hard*. Every claim
here traces to the architecture docs and the code; where a number is illustrative rather than
measured from a benchmark run, it says so.

---

## 1. The problem

Teams shipping AI agents keep hitting the same three questions, and most cannot answer any of them
with evidence:

1. **"Is this version actually better than the last one?"** A prompt got reworded, a model got
   swapped, a retriever got tuned — and the only signal is vibes and a handful of cherry-picked
   examples. There is no reproducible, objective comparison.
2. **"Why did *this* run fail?"** A result comes back red. Red is not actionable. Was it a timeout?
   An HTTP error? Malformed JSON? A prompt that contradicts itself? The failure is visible but the
   *cause* is buried.
3. **"What is this agent costing us, and where is that trending?"** Latency, token usage and spend
   drift quietly until a bill or an SLA forces a reckoning.

Underneath those is a deeper one: **"Can I reproduce a result months later?"** If the dataset or the
prompt can change underneath an evaluation, every historical number becomes unverifiable.

Application frameworks — Spring AI, LangGraph, CrewAI, AutoGen, PydanticAI, Semantic Kernel, plain
REST services — help you *build* an agent. None of them is the **system of record for whether the
agent is any good**. That gap is the entire reason for Brok's Forge.

---

## 2. Why a platform, not a script

A pile of evaluation scripts answers the questions *once*. A platform answers them *continuously,
for many agents, across a team, with isolation and history*. The decision to build a platform forced
a set of properties that a script never has to care about:

- **Multi-tenancy.** Many organizations and projects share one deployment; one tenant must never see
  another's data, even by accident, even via a guessed id.
- **Reproducibility.** An evaluation run today must mean the same thing when re-read next quarter,
  which means the inputs it referenced cannot have shifted.
- **Provider- and framework-neutrality.** The platform cannot bet on one LLM vendor or one agent
  framework, because its users haven't.
- **Scale headroom.** Evaluation is fundamentally `items × metrics` per request, run across many
  agents — the data model has to be shaped for millions of rows from day one, even if the volume
  isn't there yet.
- **Operability.** A production service needs to be debuggable and observable without leaking
  secrets.

Brok's Forge is delivered as a **modular monolith** (Java 21 / Spring Boot 3.4.1, PostgreSQL +
Flyway, Redis, Next.js 15): the operational simplicity of one deployable today, with module
boundaries strict enough that any module can later be extracted into a service *mechanically rather
than archaeologically* (see [ADR 0001](./adr/0001-modular-monolith.md)).

---

## 3. The phased build (1 → 4 → release)

The platform was built in deliberate phases, each additive — no phase removed or downgraded a
working capability ([ROADMAP.md](./ROADMAP.md)). Flyway migrations are append-only, so the schema's
own history (`V1`…`V25`) is the build log.

### Phase 1 — Foundation (`V1`–`V5`)
Identity and the tenancy backbone: `auth`, `user`, `organization` (+ members), `project`, `apikey`.
JWT access tokens with rotating refresh tokens, BCrypt passwords, SHA-256 API keys, the
`OWNER > ADMIN > MEMBER` role hierarchy, and the cross-cutting skeleton every later table inherits —
UUID keys, audit columns, optimistic `version`, soft deletes, a stable `ApiError` contract,
correlation/request IDs, and `ddl-auto=validate` schema discipline.

### Phase 2 — The Agent Registry (`V6`–`V10`)
`Agent` becomes the **central aggregate root** every other module attaches to
([ADR 0002](./adr/0002-agent-as-central-entity.md)). Framework-agnostic agent metadata, immutable
**versioned deployments** with an activate/rollback active-version pointer, **AES-256-GCM-encrypted
credentials** (write-only, never returned, never logged), and **SSRF-guarded health checks** against
user-supplied endpoint URLs.

### Phase 3 — The Intelligence Layer (`V11`–`V23`)
The phase that turns a *registry* into an *engineering platform*: `dataset`, `prompt`, `model`,
`evaluation`, `benchmark`, `regression`, `analytics`, `report`, `search`, `dashboard`. Immutable
versioned datasets and prompts (`{{variables}}`), a provider-agnostic `ModelInvoker` SPI, and the
`EvaluationJob → EvaluationRun → EvaluationResult` pipeline with reusable profiles, a pluggable
metric engine, precomputed summaries, benchmarks/leaderboards, regression detection, analytics and
JSON/CSV/HTML reports.

### Phase 4 — The AI Engineering Advisor (`V24`–`V25`)
The step from *measuring* agents to *advising* on them: `knowledge`, `advisor`, `rootcause`,
`debugger`, plus a `common.observability` tracing seam. An on-read recommendation engine of pure
per-domain advisors (Prompt / Model / Cost / Agent / RAG), a pure `RootCauseEngine`, an AI Debugger
execution timeline, and a persisted Engineering Knowledge Graph — all computed on read except the
graph, which is the only Phase-4 persistence (two tables).

### Toward the Version 1.0.0 release — production observability (`ADR 0015`)
The release adds the **two production table-stakes** for observability: Prometheus metrics via
Micrometer (`/actuator/prometheus`, ADMIN-guarded) and native structured (ECS-JSON) logging in the
container profile, plus Kubernetes-grade liveness/readiness probes — metrics and logs, deliberately
*not* distributed tracing, which stays a wired-up-later seam.

The platform today is **22 backend feature/infra modules, ~335 backend Java files, 25 Flyway
migrations, 27 REST controllers**, and a Next.js 15 web app spanning auth, dashboard, agents,
datasets, prompts, evaluations, benchmarks, regressions, analytics, reports, advisor, root-cause,
debugger and knowledge workspaces.

---

## 4. The hardest problems solved

These are the parts a reviewer should care about — where the design did real work.

### 4.1 Provider- and framework-agnosticism *at the schema level*
The naive version couples evaluation to one vendor SDK and one framework's types, and every new
provider becomes a cross-cutting rewrite. Instead:

- **Frameworks and providers are text-backed enums.** Adding "LangGraph 0.4" or DeepSeek is a
  **code-only change with no migration** — the schema stores a string, not a vendor type.
- **All invocation goes through a `ModelInvoker` SPI** behind a `ModelInvocationService` dispatcher
  ([ADR 0006](./adr/0006-provider-agnostic-model-invocation.md)). Callers — notably the evaluation
  executor — depend on the dispatcher, never on a concrete invoker.
- The **shipped** concrete invoker is `AgentEndpointInvoker`: it calls the agent's *own registered
  HTTP endpoint* as a black box, so **any agent that speaks HTTP can be evaluated** regardless of
  framework. Provider-direct clients (OpenAI/Anthropic/Groq/…) are a documented SPI extension point,
  *not yet shipped* — an honesty point the docs are explicit about.

The payoff: the platform is neutral by construction, and the riskiest future change (a new provider)
is the cheapest kind — additive, migration-free, no caller edits.

### 4.2 Tenant isolation that doesn't leak existence
Multi-tenant SaaS lives and dies on isolation. The chosen mechanism is uniform and boring on
purpose: **every aggregate is loaded by its full `(id, projectId, organizationId)` tuple** through a
per-aggregate access guard. A foreign id is therefore *indistinguishable from a missing one* — it
resolves to **404, never a 403 that confirms the resource exists**. That single rule closes IDOR and
cross-tenant enumeration in one move, and it's applied identically in every module, including the
Phase-4 advisors (which read only through published, tenant-scoped services).

### 4.3 Evaluation at scale *without holding a DB connection across network calls*
This is the signature data-modeling decision ([ADR 0005](./adr/0005-evaluation-job-as-top-level-aggregate.md)).
Evaluation is `items × metrics` per request; a flat `Evaluation` table turns every list and
dashboard into a `GROUP BY` over millions of rows. Instead the pipeline is a fan-out tree:

```
EvaluationJob   1   — config + status + a precomputed summary (cost/latency/quality)
  └ EvaluationRun   N   — exactly one per dataset item (the unit of work, partial-failure isolation)
      └ EvaluationResult  M — exactly one per metric per run (the atomic score)
```

Why it scales: the hot path is **insert-only and partitionable by job**; **summaries are
precomputed** so every downstream reader (benchmark, regression, analytics, dashboard) reads *one
row per job, not millions*; and a single failed item fails its run, not the whole job. Crucially,
the fan-out lives behind one seam, `EvaluationJobExecutor` — the *only* component that turns a job
into runs. It executes **synchronously today**, which is the honest known limitation: a long job
holds the request thread and the per-job item count is bounded (`EVALUATION_MAX_ITEMS_PER_JOB`,
default 500). But because nothing outside the executor assumes synchronous completion, moving it
behind a queue + worker fleet is a localized infrastructure change — the `Job → Run → Result` schema,
the domain model and the API do not move. The design pays the connection-pool-discipline tax up
front (precompute summaries, keep the transaction off the network call's critical path) so the
expensive change is deferred, not designed-in.

### 4.4 Advice that can never drift
Phase 4 had a trap to avoid: a `recommendations` table written by a scheduler immediately *drifts*
from the data it describes (a recommendation about a job whose model later changed becomes a lie) and
needs invalidation machinery; a conversational LLM "assistant" is non-deterministic, costly and
untestable. The decision ([ADR 0011](./adr/0011-ai-engineering-advisor.md)): **recommendations and
root-cause findings are computed on read and never persisted** — exactly like benchmark leaderboards.
The analyzers (five pure sub-advisors and a pure `RootCauseEngine`) take already-loaded inputs and
return value records, mirroring the pure metric engine, so they are deterministic and trivially
unit-testable. Advice is therefore *always current* at zero schema cost, and every finding carries a
fixed, renderable shape: **why / what changed / how to fix / expected improvement / confidence /
severity / evidence**, plus an optional link into the knowledge graph.

### 4.5 Honest observability
It would be easy to fake a per-stage trace timeline. The AI Debugger reconstructs a run timeline over
a canonical seven-stage vocabulary (`PROMPT → MEMORY → RETRIEVER → TOOLS → MODEL → PARSER → OUTPUT`)
from *persisted* data — and the stages the platform cannot yet observe (`MEMORY`, `RETRIEVER`,
`TOOLS`) are reported **`NOT_INSTRUMENTED`**, never invented
([ADR 0014](./adr/0014-ai-debugger-and-tracing-seam.md)). A dependency-free `TraceRecorder` SPI
(no-op default) is the single drop-in point for real spans. The same honesty governs the whole
observability story: correlation/request IDs flow through every log line and response header today;
Prometheus metrics and structured logs land for the release ([ADR 0015](./adr/0015-production-observability-metrics-and-structured-logging.md));
distributed tracing exporters are explicitly *deferred*, not pretended.

---

## 5. Key trade-offs (made deliberately, documented honestly)

| Trade-off | What was chosen | What was given up | Why it's right here |
|---|---|---|---|
| **Modular monolith vs microservices** | One deployable, strict module boundaries | Independent scaling/deploy per module today | Ops simplicity now; mechanical extraction later because modules reference by id + published services, never by JPA join |
| **Synchronous evaluation executor** | In-process execution behind a queue-ready seam | Async throughput; bounded items/job | Ships the Intelligence Layer without building distributed infra before there's volume; the seam makes the upgrade contained |
| **Encrypt (not hash) agent credentials** | AES-256-GCM, reversible, versioned ciphertext | The simplicity of one-way hashing | The platform must *replay* the credential upstream — a hash is unusable as an outbound secret |
| **On-read advice (no recommendations table)** | Pure analyzers, derived per request | Recommendation history out of the box | Advice can never drift; zero schema cost; deterministic and testable |
| **Reference by id + published services** | Clean, extractable boundaries | An occasional extra query vs a JPA join | Boundary integrity is the asset that makes future extraction cheap |
| **`NOT_INSTRUMENTED` honesty** | Report stages we can't see as unobserved | A more impressive-looking full timeline | Trust: a debugger that lies is worse than one that admits its limits |
| **Metrics + logs, not traces, for v1** | Prometheus + structured logging | Per-stage spans / flame graphs at release | Smallest addition that makes the service genuinely operable; the trace seam is ready |

A reviewer-facing honesty note that the docs themselves keep: **the repository ships no automated
backend test suite yet** (the testing *strategy* is documented in `TESTING_STRATEGY.md`, but
`src/test` is currently empty). The architecture is deliberately built *for* testability — pure
analyzers, pure metric engine, thin controllers, SPI seams — but the test code is future work, and
this story does not claim coverage that doesn't exist.

---

## 6. The outcome

Brok's Forge closes the loop the problem opened with. A team can register an agent, point real
datasets and prompts at it, run an objective evaluation, and then answer — *with evidence* — the
questions that started this:

- **"Is this version better?"** → benchmark two job summaries on the same dataset/prompt/profile;
  read the leaderboard.
- **"Why did this fail?"** → open the failed run's root-cause findings and the AI Debugger timeline.
- **"What is it costing?"** → analytics trends for cost, latency and tokens.
- **"Can I reproduce it?"** → every job pins immutable dataset and prompt *versions*; the inputs
  cannot shift under it.

And one step further than measurement: the advisor turns "pass rate is 72%" into "switch model X→Y
for an estimated quality gain at comparable cost — here is the evidence and the confidence."

What this project demonstrates about the engineer behind it: the ability to take an ambiguous,
trend-chasing domain and impose **durable architecture** on it — strict boundaries, schema-as-truth,
security-by-construction, scale headroom designed in, and an unusually disciplined separation of
*what ships now* from *what's a documented seam for later*. The decisions are not just made; they are
written down as ADRs with alternatives considered and trade-offs owned. That is the signal: not a
demo, but a platform built the way a staff engineer would build one.

---

<sub>Brok's Forge — Project Story. Narrative companion to the Master Architecture; all claims trace to the docs and code as of the Version 1.0.0 release.</sub>
