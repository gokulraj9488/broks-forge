# Architecture Diagrams — Brok's Forge

> A consolidated, visual reference for **Brok's Forge — The Engineering Platform for AI Agents.**
> Every diagram is **Mermaid** (renders on GitHub) and is accurate to the codebase as of **v1.0.0**
> (Phases 1–4 delivered; Phase 5 observability). For the prose architecture see
> [MASTER_ARCHITECTURE.md](MASTER_ARCHITECTURE.md); for the operational runbook see
> [ENGINEERING_HANDBOOK.md](ENGINEERING_HANDBOOK.md).

- Audience: engineers, reviewers, and anyone evaluating the system end to end.
- Stack: Java 21 / Spring Boot 3.4.1 (modular monolith) · PostgreSQL 16 + Flyway (V1–V25) · Redis 7 ·
  Next.js 15 / React 19.

**Contents**

1. [System architecture](#1-system-architecture)
2. [Component / module map](#2-component--module-map)
3. [Entity-relationship model](#3-entity-relationship-model)
4. [Deployment topology](#4-deployment-topology)
5. [Request flow (sequence)](#5-request-flow-sequence)
6. [Evaluation flow (sequence)](#6-evaluation-flow-sequence)
7. [Agent registration flow (sequence)](#7-agent-registration-flow-sequence)
8. [Advisor flow (sequence)](#8-advisor-flow-sequence)

---

## 1. System architecture

The end-to-end runtime: a browser talks to the **Next.js** web app, which calls the **Spring Boot
API** over HTTPS; the API owns **PostgreSQL** (source of truth) and **Redis** (cache / token store).
All **outbound** calls to user-supplied agent endpoints and model providers pass through the
`OutboundUrlGuard` (SSRF defence) before leaving the platform.

```mermaid
flowchart TD
    User([User / Browser])

    subgraph Client [Client tier]
        Web["Next.js 15 web app<br/>App Router · TanStack Query · Zustand"]
    end

    subgraph App [Application tier — stateless, horizontally scalable]
        API["Spring Boot 3.4 API<br/>Security · Modules · MapStruct · OpenAPI"]
        Guard{{"OutboundUrlGuard<br/>(SSRF defence)"}}
    end

    subgraph Data [Data tier]
        PG[("PostgreSQL 16<br/>source of truth · Flyway V1–V25")]
        Redis[("Redis 7<br/>cache / token store")]
    end

    subgraph External [Outbound — untrusted]
        Agents["Agent endpoints &<br/>model providers"]
    end

    User -->|HTTPS| Web
    Web -->|"REST /api/v1 · JWT / API key"| API
    API -->|JPA / HikariCP| PG
    API -->|Lettuce| Redis
    API --> Guard
    Guard -->|"only public targets<br/>(private/loopback blocked)"| Agents

    Obs["/actuator/health · /actuator/prometheus<br/>structured ECS logs + X-Correlation-Id"]
    API -.exposes.-> Obs
```

*Browser → Next.js → Spring Boot API → Postgres/Redis. Health, Prometheus metrics and correlated
structured logs are exposed for operators; every outbound call is filtered by the SSRF guard.*

---

## 2. Component / module map

The **modular monolith**: feature modules under `com.broksforge.modules` reference each other **only**
by published service APIs and id (never by reaching into another module's persistence), so any module
can later be extracted into a microservice. Cross-cutting `common` / `config` / `security` /
`observability` are shared infrastructure. Dependencies point inward and the graph stays **acyclic**.

```mermaid
flowchart TD
    subgraph Cross [Cross-cutting infrastructure]
        common["common<br/>BaseEntity · errors · web · validation"]
        security["security<br/>JWT + API-key auth · RBAC"]
        observability["common.observability<br/>ExecutionStage · TraceRecorder (no-op)"]
        config["config<br/>Security · CORS · OpenAPI · Redis · @ConfigurationProperties"]
    end

    subgraph P1 [Phase 1 — Foundation]
        user[user]
        organization[organization]
        project[project]
        apikey[apikey]
    end

    subgraph P2 [Phase 2 — Agent Registry]
        agent["agent<br/>central aggregate"]
    end

    subgraph P3 [Phase 3 — Intelligence Layer]
        dataset[dataset]
        prompt[prompt]
        model["model<br/>ModelInvoker SPI"]
        evaluation[evaluation]
        benchmark[benchmark]
        regression[regression]
        analytics[analytics]
        report[report]
        search[search]
        dashboard[dashboard]
    end

    subgraph P4 [Phase 4 — AI Engineering Advisor]
        advisor[advisor]
        rootcause[rootcause]
        debugger[debugger]
        knowledge["knowledge<br/>graph · leaf"]
    end

    organization --> user
    project --> organization
    apikey --> project

    agent --> project

    evaluation --> agent
    evaluation --> dataset
    evaluation --> prompt
    evaluation --> model
    benchmark --> evaluation
    regression --> evaluation
    analytics --> evaluation
    report --> evaluation
    dashboard --> evaluation
    search --> agent

    advisor --> evaluation
    advisor --> prompt
    advisor --> agent
    advisor --> knowledge
    rootcause --> advisor
    rootcause --> evaluation
    rootcause --> regression
    rootcause --> knowledge
    debugger --> evaluation
```

*Foundation modules + the central `agent` + the Phase 3 intelligence modules + the Phase 4
advisor/rootcause/debugger/knowledge modules, over shared common/security/config/observability. Edges
are published-service reads, never cross-module JPA.*

---

## 3. Entity-relationship model

The key tables and their relationships. Tenancy flows `organizations → projects → {agents, datasets,
prompts, evaluation_jobs, …}`; datasets/prompts/agents carry **immutable versions**; evaluation is the
`Job → Run → Result` fan-out tree. The Phase 4 **knowledge graph** (`knowledge_nodes` /
`knowledge_edges`) is platform-global reference data and is intentionally **not** tenant-scoped (shown
detached). Columns are abbreviated to the load-bearing ones.

```mermaid
erDiagram
    organizations ||--o{ projects : "owns"
    organizations ||--o{ organization_members : "has"
    projects ||--o{ api_keys : "issues"

    projects ||--o{ agents : "contains"
    agents ||--o{ agent_versions : "versioned by"
    agents ||--o{ agent_credentials : "secured by"
    agents ||--o{ agent_health_checks : "observed by"
    agents ||--o{ agent_tags : "labelled by"

    projects ||--o{ datasets : "contains"
    datasets ||--o{ dataset_versions : "versioned by"
    dataset_versions ||--o{ dataset_items : "holds"

    projects ||--o{ prompts : "contains"
    prompts ||--o{ prompt_versions : "versioned by"

    projects ||--o{ evaluation_profiles : "defines"
    projects ||--o{ evaluation_jobs : "runs"
    evaluation_jobs ||--o{ evaluation_runs : "fans out to"
    evaluation_runs ||--o{ evaluation_results : "scored by"

    agents ||--o{ evaluation_jobs : "evaluated in"
    agent_versions ||--o{ evaluation_jobs : "pinned by"
    dataset_versions ||--o{ evaluation_jobs : "pinned by"
    prompt_versions ||--o{ evaluation_jobs : "pinned by"
    evaluation_profiles ||--o{ evaluation_jobs : "configured by"
    dataset_items ||--o{ evaluation_runs : "input for"

    projects ||--o{ benchmarks : "contains"
    benchmarks ||--o{ benchmark_entries : "ranks"
    evaluation_jobs ||--o{ benchmark_entries : "entered as"

    projects ||--o{ regression_checks : "contains"
    evaluation_jobs ||--o{ regression_checks : "baseline / candidate"

    projects ||--o{ reports : "exports"

    knowledge_nodes ||--o{ knowledge_edges : "source / target"

    organizations {
        uuid id PK
        string slug
        uuid owner_id
        string status
    }
    projects {
        uuid id PK
        uuid organization_id FK
        string slug
    }
    agents {
        uuid id PK
        uuid project_id FK
        uuid organization_id FK
        string framework
        string endpoint_url
        uuid current_active_version_id
        string health_status
    }
    agent_versions {
        uuid id PK
        uuid agent_id FK
        string model
        string provider
        boolean active
    }
    agent_credentials {
        uuid id PK
        uuid agent_id FK
        string ciphertext "AES-256-GCM"
        int key_version
    }
    agent_health_checks {
        uuid id PK
        uuid agent_id FK
        string status
        int http_status
        bigint latency_ms
    }
    datasets {
        uuid id PK
        uuid project_id FK
        string format
    }
    dataset_versions {
        uuid id PK
        uuid dataset_id FK
        int version_number "immutable"
    }
    dataset_items {
        uuid id PK
        uuid dataset_version_id FK
        text input
        text expected
    }
    prompts {
        uuid id PK
        uuid project_id FK
    }
    prompt_versions {
        uuid id PK
        uuid prompt_id FK
        text body "{{variables}}"
        boolean active
    }
    evaluation_profiles {
        uuid id PK
        uuid project_id FK
    }
    evaluation_jobs {
        uuid id PK
        uuid project_id FK
        uuid agent_version_id FK
        uuid dataset_version_id FK
        uuid prompt_version_id FK
        string status
        jsonb summary "precomputed"
    }
    evaluation_runs {
        uuid id PK
        uuid evaluation_job_id FK
        uuid dataset_item_id FK
        bigint latency_ms
        bigint cost_micros
        string status
    }
    evaluation_results {
        uuid id PK
        uuid evaluation_run_id FK
        string metric_type
        boolean passed
        numeric score
    }
    benchmarks {
        uuid id PK
        uuid project_id FK
    }
    benchmark_entries {
        uuid id PK
        uuid benchmark_id FK
        uuid evaluation_job_id FK
    }
    regression_checks {
        uuid id PK
        uuid project_id FK
        uuid baseline_job_id FK
        uuid candidate_job_id FK
    }
    reports {
        uuid id PK
        uuid project_id FK
        string format
    }
    knowledge_nodes {
        uuid id PK
        string node_key UK
        string node_type
        bigint occurrence_count
    }
    knowledge_edges {
        uuid id PK
        uuid source_node_id FK
        uuid target_node_id FK
        string relation
        int weight
    }
```

*Tenancy: `organizations → projects → {agents, datasets, prompts, evaluation_jobs, benchmarks,
regression_checks, reports}`. Immutable version tables (`agent_versions`, `dataset_versions` /
`dataset_items`, `prompt_versions`) are pinned by an `evaluation_job` so any result is reproducible.
The knowledge graph is platform-global reference data.*

---

## 4. Deployment topology

Docker Compose runs four services on one bridge network with two named volumes. The API depends on
Postgres and Redis being **healthy**; the frontend bakes `NEXT_PUBLIC_API_BASE_URL` at build time.
The API tier is **stateless** (stateless JWT / API-key auth, no in-process session), so it scales
**horizontally** behind a load balancer — the dashed replica shows that path.

```mermaid
flowchart TD
    subgraph host [Docker host]
        subgraph net ["broksforge-net (bridge network)"]
            FE["frontend<br/>broksforge-frontend<br/>Next.js 15 · :3000"]
            BE["backend<br/>broksforge-backend<br/>Spring Boot 3.4 · :8080"]
            BE2["backend replica<br/>(stateless — scale-out path)"]
            PGsvc["postgres<br/>broksforge-postgres<br/>postgres:16-alpine · :5432"]
            RDsvc["redis<br/>broksforge-redis<br/>redis:7-alpine · :6379"]
        end
        PGvol[("postgres-data<br/>volume")]
        RDvol[("redis-data<br/>volume")]
    end

    Browser([Browser]) -->|":3000"| FE
    FE -->|"NEXT_PUBLIC_API_BASE_URL :8080"| BE
    BE -->|"depends_on: healthy"| PGsvc
    BE -->|"depends_on: healthy"| RDsvc
    BE2 -.->|"horizontal scale<br/>(no sticky sessions)"| PGsvc
    BE2 -.-> RDsvc
    PGsvc --- PGvol
    RDsvc --- RDvol
```

*Compose services `postgres` + `redis` + `backend` + `frontend` on `broksforge-net`, with
`postgres-data` / `redis-data` volumes. Required env: `JWT_SECRET`, `ENCRYPTION_KEY`,
`NEXT_PUBLIC_API_BASE_URL`. Stateless API replicas scale linearly behind a load balancer.*

---

## 5. Request flow (sequence)

Every request passes the **correlation-id filter** first (assigns/propagates `X-Correlation-Id` +
`X-Request-Id` into the MDC), then authentication (JWT bearer **or** API key), then the thin controller
delegates to a service that enforces the `(id, projectId, organizationId)` **access guard** before any
repository call. The correlation id is echoed back on the response.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as CorrelationIdFilter
    participant A as Auth (JWT / API-key)
    participant Ctl as Controller
    participant Svc as Service
    participant G as AccessGuard
    participant R as Repository
    participant DB as PostgreSQL

    C->>F: HTTP request
    F->>F: assign/propagate correlationId + requestId → MDC
    F->>A: continue filter chain
    A->>A: validate JWT bearer or X-API-Key
    alt unauthenticated
        A-->>C: 401 (X-Correlation-Id)
    else authenticated
        A->>Ctl: dispatch (principal set)
        Ctl->>Svc: use case (actorId, orgId, projectId, …)
        Svc->>G: require (id, projectId, organizationId) + role
        alt tuple miss or insufficient role
            G-->>Svc: throw NotFound / Forbidden
            Svc-->>Ctl: ApiError(code)
            Ctl-->>C: 404 / 403 (X-Correlation-Id)
        else authorized
            Svc->>R: query / persist (tenant-scoped)
            R->>DB: SQL (parameter-bound)
            DB-->>R: rows
            R-->>Svc: entities / projections
            Svc-->>Ctl: response DTO
            Ctl-->>C: 200/201 + X-Correlation-Id / X-Request-Id
        end
    end
```

*Correlation-id filter → JWT/API-key auth → controller → service + access guard → repository → DB →
response. A foreign id resolves to 404 (no existence leak); the correlation id is on every response.*

---

## 6. Evaluation flow (sequence)

Creating and running an evaluation job. The job **pins immutable versions** (agent / dataset / prompt),
then the executor invokes the agent endpoint **per dataset row — through the SSRF guard, and crucially
OUTSIDE any DB transaction** so a slow agent can't hold a pooled connection. Metrics are scored and the
run/result persisted in a **short** transaction; the job summary is precomputed.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Svc as EvaluationService
    participant Ex as EvaluationExecutor
    participant G as OutboundUrlGuard
    participant Ag as Agent endpoint
    participant ME as Metric engine (pure)
    participant DB as PostgreSQL

    C->>Svc: POST …/evaluation-jobs (agent, dataset, prompt?, profile?)
    Svc->>DB: resolve & pin dataset/prompt versions, create job (PENDING)
    C->>Svc: POST …/{id}/run
    Svc->>Ex: execute(job)
    Ex->>DB: mark job RUNNING (short tx)
    loop each dataset item (bounded by max-items-per-job)
        Ex->>DB: load item + mark run RUNNING (short tx, then release)
        Ex->>G: check(agent endpoint URL)
        G-->>Ex: allowed (private/loopback blocked)
        Ex->>Ag: invoke (timeouts, NO DB tx held)
        Ag-->>Ex: output + latency / tokens / cost
        Ex->>ME: score metrics(output, expected, profile)
        ME-->>Ex: EvaluationResult[] (pass/fail, score)
        Ex->>DB: persist run + results (short tx, batched)
    end
    Ex->>DB: compute & store EvaluationSummary, mark COMPLETED
    Svc-->>C: job summary (paginate runs / results separately)
```

*Create job → resolve & pin versions → executor invokes the agent per row **outside** any DB
transaction (through the SSRF guard, with timeouts) → score metrics → persist run/result in a short tx
→ precomputed summary.*

---

## 7. Agent registration flow (sequence)

Onboarding an agent: register it, add a version and activate it (one active version per agent), set an
encrypted credential (write-only, **never** returned or logged), and run a health check whose outbound
probe is gated by the `OutboundUrlGuard`.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (ADMIN)
    participant Ctl as AgentController
    participant Svc as AgentService
    participant Enc as CredentialEncryptionService
    participant G as OutboundUrlGuard
    participant Ag as Agent endpoint
    participant DB as PostgreSQL

    C->>Ctl: POST …/agents (framework, endpoint, auth type)
    Ctl->>Svc: createAgent(...)
    Svc->>DB: insert agent (slug unique per project)

    C->>Ctl: POST …/agents/{id}/versions (activate immediately)
    Svc->>DB: insert version, set current_active_version_id (one active)

    C->>Ctl: POST …/agents/{id}/credentials (raw secret)
    Svc->>Enc: encrypt(secret) → v{n}:{iv}:{ct+tag}
    Enc-->>Svc: ciphertext (AES-256-GCM)
    Svc->>DB: store ciphertext only
    Svc-->>C: masked hint + keyVersion (raw secret never returned)

    C->>Ctl: POST …/agents/{id}/health-check
    Svc->>G: check(endpoint URL)
    G-->>Svc: allowed (else BlockedTargetException)
    Svc->>Ag: probe (connect/read timeout)
    Ag-->>Svc: HTTP status + latency
    Svc->>DB: record agent_health_check, update health_status + availability %
    Svc-->>C: status, latency, rolling availability
```

*Register agent → add version (activate) → set encrypted credential (write-only) → health check via
`OutboundUrlGuard`. Credentials are encrypted at rest and never returned or logged.*

---

## 8. Advisor flow (sequence)

The **AI Engineering Advisor** is a recommendation engine, not a chatbot. The thin `AdvisorService`
loads recent jobs via **published services only**, hands already-loaded data to **pure sub-advisors**
that emit `Recommendation`s linked to the knowledge graph by key, records each observed pattern into
the graph (best-effort occurrence bump — the only write on this read path), then ranks and returns the
report. Nothing is persisted as a recommendation — it's **computed on read** so it can't drift.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctl as AdvisorController
    participant Svc as AdvisorService
    participant Eval as EvaluationService (published)
    participant Sub as Sub-advisors (pure, no I/O)
    participant KG as KnowledgeGraphService
    participant DB as PostgreSQL

    C->>Ctl: GET …/advisor
    Ctl->>Svc: adviseProject(actorId, orgId, projectId)
    Svc->>Eval: recent completed jobs (tenant-scoped read)
    Eval->>DB: query (paginated, projections)
    DB-->>Eval: EvaluationJobResponse[]
    Eval-->>Svc: jobs
    Svc->>Sub: ModelAdvisor.analyze(jobs) · CostAdvisor.analyze(jobs)
    Sub-->>Svc: Recommendation[] (why · whatChanged · howToFix · expectedImprovement · confidence · severity · knowledgeKey)
    loop each recommendation with a knowledgeKey
        Svc->>KG: recordObservation(knowledgeKey) (best-effort, bounded)
        KG->>DB: increment occurrence_count
    end
    Svc->>Svc: rank by severity, build severityBreakdown
    Svc-->>Ctl: AdvisoryReportResponse
    Ctl-->>C: 200 (computed on read — nothing persisted)
```

*GET advisor → load recent jobs via published services → pure sub-advisors produce `Recommendation`s →
record observations in the knowledge graph (bounded, best-effort) → ranked advisory report.*

---

> These diagrams are intended to render anywhere Mermaid is supported (GitHub, most IDEs, MkDocs /
> Docusaurus with the Mermaid plugin). If you change the schema or a module boundary, update the
> relevant diagram here alongside [MASTER_ARCHITECTURE.md](MASTER_ARCHITECTURE.md).
