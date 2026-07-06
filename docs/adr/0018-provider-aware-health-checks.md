# 18. Provider-aware health checks and credential connection testing

- Status: Accepted
- Date: 2026-07-05

## Context

Health checks previously issued a bare `GET` against the agent's registered
`endpoint_url`. That is wrong for most real targets: a Spring Boot service exposes
health at `/actuator/health`, a FastAPI/LangGraph service at `/health`, and an
agent that is effectively a hosted LLM answers a completion, not a health path. A
`GET /` against any of these often returns 404/405 and reports a healthy agent as
unhealthy.

Separately, an agent could be registered with "Authentication = API Key" but there
was no way to **verify a credential actually works** before relying on it — the
only probe was the agent-level health check, which uses the saved active
credential and always persists a health-check row.

Both concerns are the same shape: an authenticated, SSRF-guarded outbound probe
whose *method and URL depend on the target*.

## Decision

**Provider-aware health probes.** A pure `HealthProbePlanner` chooses the probe
from the agent's `framework` and its active version's `provider`:

- `SPRING_AI` → `GET {base}/actuator/health`
- `LANGGRAPH` / `LANGCHAIN` / `CREWAI` / `AUTOGEN` / `PYDANTIC_AI` /
  `SEMANTIC_KERNEL` / `LLAMA_INDEX` → `GET {base}/health`
- hosted LLM providers (`OPENAI`, `ANTHROPIC`, `GROQ`, `GOOGLE_GEMINI`,
  `OPENROUTER`, `DEEPSEEK`, `MISTRAL`, `COHERE`, `AZURE_OPENAI`) → a tiny
  `POST` completion to the endpoint
- anything else → the historical plain `GET` of the endpoint

An endpoint that already targets a health path (ends with `/health` or
`/actuator/health`) is honoured verbatim. Health paths are composed from the
endpoint's scheme+authority, since health endpoints live at the server root. The
chosen `HealthProbeStrategy` and the effective `probe_url` are persisted on each
`agent_health_checks` row (V29) and returned in the API, so the history is
transparent. `AgentHealthService` now loads the active `AgentVersion` (provider +
model live there, not on `Agent`) and passes it to the executor.

**Credential connection testing.** A `CredentialConnectionTester` performs a
one-shot probe of the agent endpoint with a supplied header set and classifies the
result from a *credential* point of view (a 401/403 is "authentication rejected",
not merely "degraded"). Two entry points:

- `POST …/credentials/{id}/test` tests a **saved** credential and records the
  outcome (`last_tested_at`, `last_test_success`, `last_test_http_status`,
  `last_test_message` — V28) so the UI can show a connection status.
- `POST …/credentials/test` **dry-runs** an unsaved credential from the request
  body, so a user can verify a secret before storing it. The secret is used only
  for the probe and never persisted.

Credentials also gained a human `label` and a configurable `header_prefix`
(e.g. `Authorization: Bearer <secret>`), plus in-place `update` (rotate the secret
or edit metadata) distinct from the existing replace.

**Both** paths reuse `OutboundUrlGuard` before every call (SSRF defence,
[ADR 0004](./0004-ssrf-protection-for-agent-endpoints.md)) and the existing
`RestClient` timeout pattern. Secrets remain write-only and encrypted
([ADR 0003](./0003-credential-encryption-vs-hashing.md)); a connection test
returns only a verdict, never the secret.

## Consequences

**Positive**
- Health reflects reality across the supported frameworks/providers instead of
  false-negatives from `GET /`.
- Users can verify credentials before and after saving, closing the
  "registered but never usable" gap, with a visible connection status.
- No schema change to `agents`; provider awareness is derived from existing
  fields. All new columns are nullable, so historical rows stay valid.

**Negative / trade-offs**
- The "tiny POST completion" probes the **agent's** endpoint with a minimal
  payload rather than the provider's own health API — the platform invokes agents
  over HTTP and holds no provider base URLs/keys. Provider-direct `ModelInvoker`
  clients ([ADR 0006](./0006-provider-agnostic-model-invocation.md)) remain a
  documented future extension; a completion probe costs a token or two.
- Health/credential probes still run inside the request; they are manual,
  low-volume admin actions, so this is acceptable (async scheduling is future work).

## Future impact

- When provider-direct invokers land, the planner can point `POST_COMPLETION` at
  the provider's real endpoint without changing callers.
- A background scheduler can reuse the DB-free `AgentHealthCheckExecutor.probe`
  and the planner verbatim.
