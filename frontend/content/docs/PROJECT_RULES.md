# Project Rules — Brok's Forge

> **Brok's Forge — The Engineering Platform for AI Agents.**
> A Java 21 / Spring Boot 3.4.1 modular monolith with a Next.js 15 frontend.

These are the **non-negotiable engineering rules** for Brok's Forge. They exist to keep the
platform safe, multi-tenant, and architecturally coherent as it grows from the Foundation
(P1) and Agent Registry (P2) phases into the Intelligence Layer (P3) and beyond.

A rule here is not a preference. If a change conflicts with one of these rules, the change is
wrong — not the rule. Exceptions require a new [ADR](./adr/README.md) that explicitly records
the trade-off and is reviewed and accepted before the code merges.

Each rule below is stated as **Rule → Rationale**. Rules are grouped under the category they
primarily govern.

---

## Database

### DB-1. Never modify a previously released Flyway migration; only add new ones.
Migrations `V1`..`V10` are released and **immutable**. New schema changes are appended as
`V11`, `V12`, … (P3 begins at `V11`). Never edit, reorder, renumber, or delete a released
migration, and never change its checksum.
**Rationale:** Flyway validates checksums on startup. Editing a released migration breaks every
environment that already applied it and makes history non-reproducible. Append-only migrations
are the only way to guarantee that any database — dev, CI, or production — can be rebuilt
deterministically.

### DB-2. The database is the source of truth; `ddl-auto=validate` always.
Entities must match the Flyway-defined schema **exactly**. Hibernate runs with
`spring.jpa.hibernate.ddl-auto=validate` and must never generate or update schema.
**Rationale:** With `validate`, a mismatch between an `@Entity` and its table fails fast at
startup instead of silently drifting. Schema is owned by reviewed migrations, not by entity
annotations.

### DB-3. Every new table carries the mandatory column and constraint set.
New tables must have: a **UUID primary key**, **audit columns** (`created_at`, `updated_at`,
and where applicable `created_by`/`updated_by`), an **optimistic-lock `version`** column, and
**soft-delete** support (`deleted_at` / `is_deleted`) where the entity is logically deletable.
**Rationale:** Uniformity makes auditing, concurrency control, and recoverable deletes work the
same way across every module. UUID PKs avoid id guessing and enable id-only cross-module
references (see ARCH-1).

### DB-4. Add the indexes and constraints that protect data integrity and performance.
Foreign-key columns, frequently filtered columns, and tenant-scoping columns
(`organization_id`, `project_id`) must be **indexed**. Invariants must be enforced in the
schema with **FK**, **CHECK**, and **UNIQUE** constraints — not only in application code.
**Rationale:** Constraints in the database are the last line of defense and hold even when a bug
or a future code path bypasses the service layer. Indexes keep tenant-scoped queries fast as
data grows toward the millions of rows the evaluation subsystem is designed for.

### DB-5. Soft-deleted rows stay queryable and auditable; deletes are reversible by default.
Logical deletion sets the soft-delete marker; it does not issue a physical `DELETE`. Queries
must respect the soft-delete filter.
**Rationale:** AI-engineering data (evaluations, benchmarks, datasets) is evidence. Accidental
loss is unacceptable, and "what changed and when" must remain answerable.

---

## Security

### SEC-1. Never store secrets in plaintext.
**Verification secrets** (passwords, API key secrets, refresh/reset/verification tokens) are
**hashed** (BCrypt for passwords, SHA-256 for high-entropy tokens). **Usage secrets** (agent
credentials we must replay to a third party) are **encrypted** with **AES-256-GCM** via
`CredentialEncryptionService`.
**Rationale:** A read-only database leak must not reveal a usable secret. Hashing fits values we
only ever check; encryption fits values we must reproduce. See
[ADR 0003](./adr/0003-credential-encryption-vs-hashing.md).

### SEC-2. The encryption key comes only from the environment and is never committed or logged.
The 256-bit key is supplied through `BROKSFORGE_SECURITY_ENCRYPTION_KEY` only. Ciphertext is
versioned (`v<keyVersion>:<base64(iv)>:<base64(ct+tag)>`) to allow rotation.
**Rationale:** Hardcoded or logged keys nullify encryption. Environment-supplied, version-stamped
keys let production hold the key in a KMS/secrets manager and rotate without a schema change.

### SEC-3. Never log or return a secret.
Secrets must never appear in logs, exception messages, API responses, or DTOs. API responses
expose only metadata (auth type, username, header name, a **masked** hint, key version).
Decryption happens solely for internal outbound calls.
**Rationale:** Logs and responses are the most common accidental exfiltration paths. Credentials
are write-only over the API by design.

### SEC-4. Never bypass RBAC or tenant isolation.
All access is checked through `OrganizationAccessService.requireRole` /
`requireMembership` with the role hierarchy **OWNER > ADMIN > MEMBER**. Access guards load
resources by the **full scoping tuple** `(id, projectId, organizationId)`, never by `id` alone.
**Rationale:** Loading by `id` alone is the classic **IDOR** bug — one tenant reads another
tenant's data. Scoped loads make tenant isolation a property of the query, not of a forgotten
`if` check.

### SEC-5. Guard every user-controlled outbound URL against SSRF.
Agent endpoint URLs are validated syntactically with `@ValidEndpointUrl` and checked at call
time by `OutboundUrlGuard`, which blocks loopback, link-local, private/site-local, unique-local,
any-local, multicast, and cloud-metadata targets (configurable for local `dev`).
**Rationale:** User-supplied outbound URLs are an SSRF vector (e.g. cloud metadata, internal
services). The platform must never become a confused deputy. See
[ADR 0004](./adr/0004-ssrf-protection-for-agent-endpoints.md).

### SEC-6. Never expose stack traces or internal details in API responses.
All errors are returned as a structured `ApiError` carrying a stable `ErrorCode` enum value and
a safe message. Stack traces, SQL, and framework internals never reach the client.
**Rationale:** Stack traces leak implementation details, library versions, and sometimes data —
useful only to an attacker. Stable error codes give clients something reliable to branch on.

---

## Architecture

### ARCH-1. Never introduce cross-module JPA associations or shared repositories.
Modules under `com.broksforge.modules.<feature>` reference each other **only** by UUID id and
through **published services**. No `@ManyToOne`/`@OneToMany` across module boundaries, no
importing another module's repository or entity.
**Rationale:** Id-only references keep each module's persistence model private, so a module can
later be extracted to its own service by swapping an in-process call for a network call. See
[ADR 0001](./adr/0001-modular-monolith.md).

### ARCH-2. `Agent` is the central aggregate root; new modules attach to it by id.
Evaluation, benchmark, regression, analytics, prompt, dataset, and model modules attach to an
agent (and, where relevant, an `agentVersionId`) by id.
**Rationale:** A single, stable join target keeps the platform coherent instead of fragmenting
into per-module notions of "the thing under test." See
[ADR 0002](./adr/0002-agent-as-central-entity.md).

### ARCH-3. Never break existing architecture or remove/simplify existing features.
New work is **additive**. Do not delete or downgrade a working capability, weaken a guard, or
relax a constraint to make a feature easier. Refactors that change public behavior require an
ADR and explicit review.
**Rationale:** Brok's Forge is a platform; downstream modules and users depend on existing
contracts. "Make it simpler by removing it" silently breaks consumers and erodes trust.

### ARCH-4. Cross-cutting concerns live in one place.
Security, auditing (`BaseEntity` + JPA auditing), error handling, validation, encryption, and
observability are implemented once in `common`/`config`/`security` and reused — not
re-implemented per module.
**Rationale:** One implementation means one place to fix a vulnerability or a bug, and uniform
behavior across every endpoint.

### ARCH-5. New SPI integrations go through the published abstraction, not a leaky concrete type.
Model providers are added behind the provider-agnostic `ModelInvoker` SPI; agents are invoked
through `AgentEndpointInvoker`. Adding OpenAI / Anthropic / Groq / Ollama / Gemini / OpenRouter /
DeepSeek is a code-only change behind the interface.
**Rationale:** A stable SPI keeps evaluation, benchmarking, and regression independent of any
one vendor's SDK and makes new providers cheap and isolated.

### ARCH-6. Concurrency and scale are designed in, not retrofitted.
Long-running work (the evaluation pipeline `EvaluationJob → EvaluationRun → EvaluationResult`)
must keep a **queue-ready seam** even when executed synchronously today, and must be designed to
scale to **millions** of results.
**Rationale:** Retrofitting async/horizontal execution after coupling it to a request thread is
expensive and risky. The seam lets P6 add async workers without reworking the domain.

---

## API

### API-1. All endpoints live under `/api/v1` with nested org/project paths.
Resources are addressed through their tenant hierarchy
(`/api/v1/organizations/{orgId}/projects/{projectId}/...`).
**Rationale:** The URL itself encodes the tenant scope that the access guards enforce (SEC-4),
making isolation explicit and consistent.

### API-2. Never bypass Bean Validation.
Every request DTO is a **record** annotated with Bean Validation. Controllers validate input;
services may add invariants but never assume input is clean.
**Rationale:** Validation at the edge is the cheapest place to reject bad input and the only
guaranteed gate before data reaches the domain. Bypassing it invites injection, corrupt state,
and inconsistent errors.

### API-3. Request DTOs omit server-set fields; responses use the standard envelopes.
Request DTOs must not accept server-owned fields (ids, audit columns, version, owner). Lists
return `PageResponse<T>`; errors return `ApiError` + `ErrorCode`.
**Rationale:** Accepting server-set fields is a mass-assignment vulnerability. Standard envelopes
give every client one predictable pagination and error shape.

### API-4. Secrets are write-only across the API.
Credential and key material can be submitted but never read back; responses return masked
metadata only (see SEC-3).
**Rationale:** There is no legitimate API reason to read a stored secret back; removing the path
removes the leak.

---

## Process

### PROC-1. Every feature answers a real AI-engineering problem.
A feature must articulate the concrete AI-engineering problem it solves (e.g. "compare two prompt
versions on a fixed dataset," "detect a quality regression between a baseline and a candidate").
**Rationale:** Brok's Forge is an engineering platform for AI agents, not a generic CRUD app.
Features without a real problem add surface area and maintenance cost for no user value.

### PROC-2. Every feature updates documentation.
Code changes ship with the matching updates to `ROADMAP.md`, `FEATURE_DECISIONS.md`, this file
when a rule changes, and any feature/module docs.
**Rationale:** Undocumented behavior is unmaintainable and undiscoverable. Docs are part of the
deliverable, not a follow-up.

### PROC-3. Every notable decision adds or updates an ADR, and ADRs stay current.
Architectural or security-significant decisions are recorded in `docs/adr` and cross-linked from
`FEATURE_DECISIONS.md`. ADRs are immutable once accepted; a changed decision adds a **new**,
superseding ADR.
**Rationale:** ADRs are the institutional memory of *why*. Keeping them current — and never
rewriting history — lets future contributors trust the record.

### PROC-4. Every phase passes an architecture + security self-review before completion.
A phase is not "done" until it has passed the self-review checklist below covering tenant
isolation, secret handling, migration discipline, and module boundaries.
**Rationale:** Catching an isolation or secret-handling defect at phase close is far cheaper than
after release. The review makes the rules above verifiable rather than aspirational.

---

## How rules are enforced

These rules are enforced by a combination of automated gates, machine-checked invariants, and
human review.

1. **`ddl-auto=validate` (machine-checked).** The application refuses to start if any entity
   diverges from the Flyway schema, enforcing **DB-1**, **DB-2**, and entity↔migration parity
   automatically in every environment and in CI.

2. **Flyway checksum validation (machine-checked).** Flyway fails startup if a released
   migration's checksum changed, enforcing the append-only rule (**DB-1**) without relying on
   reviewer vigilance.

3. **Bean Validation + global exception handling (machine-checked).** Controller-level validation
   (**API-2**) and the central `ApiError`/`ErrorCode` handler (**SEC-6**) are wired once in
   `config`/`common`, so the standard behavior is the default path.

4. **Access guards and the outbound guard (machine-checked at runtime).**
   `OrganizationAccessService` and `(id, projectId, organizationId)` scoped loads enforce
   **SEC-4**; `OutboundUrlGuard` + `@ValidEndpointUrl` enforce **SEC-5**.

5. **Code review.** Every change is reviewed against this document. Reviewers specifically block
   cross-module JPA associations / shared repositories (**ARCH-1**), feature removal or
   simplification (**ARCH-3**), secrets in logs/responses (**SEC-3**), and missing table
   invariants (**DB-3**, **DB-4**).

6. **Phase self-review checklist (PROC-4).** Before a phase is marked complete, the author
   confirms each item:

   ```text
   [ ] Migrations are append-only; no released V1..Vn was modified; new ones are Vn+1…
   [ ] Every new entity matches its migration exactly (app boots under ddl-auto=validate).
   [ ] Every new table has: UUID PK, audit columns, version, soft-delete, indexes,
       and FK/CHECK/UNIQUE constraints.
   [ ] No cross-module JPA associations or shared repositories; references are UUID + service.
   [ ] No secret is stored in plaintext, logged, or returned (masked metadata only).
   [ ] Encryption key is read from the environment; nothing key-related is committed.
   [ ] RBAC enforced via OrganizationAccessService; all loads are (id, projectId, orgId).
   [ ] User-controlled outbound URLs pass @ValidEndpointUrl and OutboundUrlGuard.
   [ ] All endpoints are under /api/v1, use PageResponse<T>, and never leak stack traces.
   [ ] Request DTOs are validated records that omit server-set fields.
   [ ] No existing feature was removed, simplified, or had a guard weakened.
   [ ] Docs updated (ROADMAP, FEATURE_DECISIONS) and ADRs added/kept current.
   [ ] The feature answers a stated, real AI-engineering problem.
   ```

See also: [ROADMAP.md](./ROADMAP.md) · [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md) ·
[CONTRIBUTING.md](./CONTRIBUTING.md) · [ADRs](./adr/README.md)
