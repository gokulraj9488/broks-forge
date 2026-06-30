# 1. Modular monolith over microservices

- Status: Accepted
- Date: 2026-07-01

## Context

Brok's Forge will grow to many feature areas — agent registry, evaluation, benchmarking,
prompt management, tracing, an AI debugger, RAG/memory inspectors, reporting, analytics, an
SDK and a CLI. Each area is non-trivial. We must choose a deployment and code-organisation
topology that lets a small team move fast now without painting us into a corner at scale.

The two ends of the spectrum:

- **Distributed microservices from day one** — independent deployability and scaling, at the
  cost of network boundaries, distributed transactions, eventual consistency, duplicated
  cross-cutting concerns (auth, auditing, observability), and heavy operational overhead before
  there is any traffic to justify it.
- **A single, well-modularised deployable (modular monolith)** — one process, in-process calls,
  ACID transactions, and one place for cross-cutting concerns, while still enforcing strong
  internal module boundaries.

## Decision

Build Brok's Forge as a **modular monolith**:

- Feature modules live under `com.broksforge.modules.<feature>` (`auth`, `user`,
  `organization`, `project`, `apikey`, `agent`). Each owns its domain, repositories, services
  and web layer.
- Modules collaborate **only** through published application services and by **referencing other
  aggregates by id** — never by reaching into another module's entities or tables. (Example:
  `AgentVersion` holds an `agentId`, not a JPA association graph into another module.)
- Cross-cutting concerns are centralised: security, auditing (`BaseEntity` + JPA auditing),
  error handling, validation, observability (correlation/request IDs), encryption.
- Authorization is enforced in one place per boundary (`OrganizationAccessService`,
  `AgentAccessGuard`) rather than scattered across controllers.

These constraints keep each module's "seam" clean enough that it can later be extracted into an
independent service with minimal rewiring.

## Consequences

**Positive**
- One transaction boundary; no distributed-transaction complexity for ordinary operations.
- A single auth, audit and observability implementation reused by every module.
- Fast local development and a single `docker compose up`.
- Refactoring across modules is a compiler-checked operation, not a cross-service contract change.

**Negative / trade-offs**
- The whole application scales and deploys as a unit (acceptable at current scale; mitigated by
  stateless design and horizontal replicas).
- Module boundaries are a discipline, not a hard runtime wall — they must be enforced in review
  (no cross-module entity references, no shared repositories).

**Future**
- A module can be extracted to its own service by replacing its in-process service calls with a
  network client and its id-references with API calls, because no module depends on another's
  persistence model today.
