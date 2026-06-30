# Brok's Forge v1.0.0 — Release Notes

**The Engineering Platform for AI Agents.**
_Released 2026-07-01 · Java 21 / Spring Boot 3.4.1 · Next.js 15 / React 19 · PostgreSQL 16 + Flyway · Redis 7_

Brok's Forge 1.0.0 is the first general-availability release. It turns a registry of AI agents
into a full engineering platform: register an agent, measure it, compare versions, debug a failing
run, and guard against regressions — all strictly multi-tenant, framework- and provider-agnostic.

---

## Headline capabilities

- **Register and manage agents** — framework-neutral metadata, deployment versions, encrypted
  usage credentials, and health checks.
- **Measure systematically** — versioned immutable datasets, versioned prompts, and an evaluation
  engine built to scale to millions of results.
- **Compare and guard** — benchmarks with leaderboards and baseline-vs-candidate regression checks
  across latency, cost, quality, and token usage.
- **Understand and explain** — an AI Engineering Advisor, root-cause analysis, an Engineering
  Knowledge Graph, and a stage-by-stage AI Debugger.
- **Operate with confidence** — analytics with trends, JSON/CSV/HTML reports, global search, a
  unified dashboard, and an ADMIN-guarded Prometheus metrics endpoint.

---

## What's inside — the four phases

| Phase | Theme | Migrations | Highlights |
| ----- | ----- | ---------- | ---------- |
| **P1** | Foundation | `V1`..`V5` | Auth (JWT + refresh rotation, BCrypt), users, organizations, projects, API keys, multi-tenant core. |
| **P2** | Agent Registry | `V6`..`V10` | Agents, versions, AES-256-GCM-encrypted credentials, health checks, tags. |
| **P3** | Intelligence Layer | `V11`..`V23` | Datasets, prompts, provider-agnostic model SPI, evaluation, benchmark, regression, analytics, report, search, dashboard. |
| **P4** | AI Engineering Advisor | `V24`..`V25` | Advisor, root-cause, knowledge graph, AI Debugger, tracing seam, Prometheus metrics + structured logging. |

See [CHANGELOG.md](./CHANGELOG.md) for the itemized list and the
[Roadmap](./docs/ROADMAP.md) for what's next.

---

## Install & upgrade

This is the first release, so there is nothing to upgrade from — a clean install:

### 1. Prerequisites

- **Docker** + Docker Compose (PostgreSQL 16 and Redis 7 are orchestrated for you).
- `openssl` to generate secrets.
- For local (non-container) development: **JDK 21** and **Node.js 20+**.

### 2. Configure the required environment

Copy the example file and set the two required Base64 secrets. They **must differ**:

```bash
cp .env.example .env

# JWT signing secret (HS256, >= 32 bytes)
openssl rand -base64 48      # -> JWT_SECRET

# AES-256-GCM credential-encryption key (exactly 32 bytes)
openssl rand -base64 32      # -> ENCRYPTION_KEY
```

The frontend is built with the public build arg **`NEXT_PUBLIC_API_BASE_URL`**
(defaults to `http://localhost:8080`).

> Losing `ENCRYPTION_KEY` makes stored agent credentials unrecoverable. Treat it as a long-lived
> secret and source it from a secrets manager in production.

### 3. Start the stack

```bash
docker compose up --build
```

On startup, Flyway applies migrations `V1`..`V25` and the backend boots under
`ddl-auto=validate`. Once up:

- **Web app:** <http://localhost:3000>
- **API + OpenAPI UI:** <http://localhost:8080>
- **Health:** <http://localhost:8080/actuator/health>
- **Metrics (ADMIN-guarded):** `GET /actuator/prometheus`

---

## Required accounts / keys

**None beyond the two secrets above.** Brok's Forge ships provider-agnostic: no third-party API
key is required to run the platform. The model SPI supports OpenAI, Anthropic, Groq, Ollama,
Gemini, OpenRouter, and DeepSeek, but you only supply a provider key when you actually register an
agent that calls one. Those usage secrets are stored encrypted (AES-256-GCM) and are write-only
over the API.

---

## Known limitations

These are accepted, documented trade-offs for 1.0, each with a path forward
(see [docs/SECURITY_GUIDE.md §15](./docs/SECURITY_GUIDE.md#15-known-limitations--hardening-roadmap)
and the [Roadmap](./docs/ROADMAP.md)):

- **Synchronous evaluation executor** — evaluation jobs run synchronously today behind a
  queue-ready seam; async workers / durable queues land in a later phase (P7).
- **No telemetry exporters wired** — the `TraceRecorder` SPI and `ExecutionStage` vocabulary exist,
  but no OpenTelemetry exporters are connected yet, so some AI Debugger stages report
  `NOT_INSTRUMENTED`. Live tracing arrives in P5.
- **No rate limiting yet** — the `RATE_LIMITED` `ErrorCode` is reserved; per-principal / per-IP
  limits are planned.
- **Encryption key in process env** — production should move to KMS / envelope encryption; the
  versioned ciphertext format already supports key rotation.
- **SSRF guard does not fully stop DNS rebinding** — IP pinning / egress proxy is the planned
  defense-in-depth.

---

## Documentation & links

- [README and docs index](./docs)
- [Contributing](./CONTRIBUTING.md) · [Code of Conduct](./CODE_OF_CONDUCT.md) · [Support](./SUPPORT.md)
- [Security Policy](./SECURITY.md) · [Security Guide](./docs/SECURITY_GUIDE.md)
- [Changelog](./CHANGELOG.md) · [Roadmap](./docs/ROADMAP.md)

---

## License

Brok's Forge is released under the [Apache License 2.0](./LICENSE).
Copyright 2026 Brok's Forge contributors.
