# 2. Agent as the central platform entity

- Status: Accepted
- Date: 2026-07-01

## Context

Brok's Forge is "the engineering platform for AI agents". Every planned module — evaluation,
benchmarking, prompt management, tracing, the AI debugger, RAG/memory inspectors, reporting,
analytics, the SDK and CLI — ultimately operates *on an agent*. We need a single, stable concept
those modules attach to. If each module invented its own notion of "the thing under test", the
platform would fragment.

We also must stay **framework agnostic**: an agent may be Spring AI, LangGraph, CrewAI, AutoGen,
PydanticAI, Semantic Kernel, or a plain custom REST service in any language.

## Decision

Make **`Agent` the central aggregate root** of the platform, with these properties:

1. **Framework-neutral model.** An agent is described by metadata (framework, language, endpoint
   URL, auth type, capabilities) — not by any framework's types. Frameworks/providers are
   enumerations stored as text, so new ones are added without a migration. Open-ended
   `customMetadata` (JSON) is the forward-compatibility seam for capabilities not yet first-class.
2. **A stable identity.** Every agent has a UUID. Future modules reference an agent by that id,
   exactly as `AgentVersion`, `AgentCredential`, `AgentHealthCheck` and `AgentTag` already do.
   This is the integration contract: *attach to an agent by id*.
3. **Deployment history as first-class data.** `AgentVersion` records every deployment (model,
   provider, framework version, git SHA, prompt version, environment). Evaluation and benchmarking
   will attach results to a specific `agentVersionId`; tracing will correlate runs to it. The
   `promptVersion` field is already a forward reference to the future Prompt Management module.
4. **Operational truth.** Health checks and an active-version pointer live on the agent so other
   modules can reason about "the currently running version" and "is it reachable".

## Consequences

**Positive**
- One join target for the entire platform. A new module adds a table with an `agent_id` (and,
  where useful, an `agent_version_id`) and immediately participates.
- Framework-agnosticism is preserved: adding "LangGraph 0.3" or a new provider is a code-only
  change.
- Versioning is built in from day one, so result-attribution and rollback are possible without a
  later, painful retrofit.

**Negative / trade-offs**
- `Agent` accretes responsibilities over time; we mitigate this by keeping versions, credentials,
  health and tags as **separate entities/services** around the aggregate rather than fields on it.
- Referencing by id (vs. foreign-key object graphs) means some reads do an explicit second query;
  this is the deliberate cost of keeping module boundaries clean (see ADR 0001).

**Future**
- Evaluation/benchmark/trace tables hang off `agent_id` / `agent_version_id`.
- A public agent registry can be built on the existing `visibility` field.
