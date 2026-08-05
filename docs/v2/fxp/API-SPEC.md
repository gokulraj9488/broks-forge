# Forge Experience Platform — API Specification

**Deliverable 4.** One conceptual API (`ForgeClient`), realized as a Java library and mirrored by the
CLI, SDKs, and REST. This document specifies the **conceptual API** (authoritative) and the **REST
binding** (transport). The Java library is the reference implementation; the REST server is a thin
adapter over it and is specified here for deployment.

## 1. Conceptual API (authoritative)

```
ForgeClient
  studio()     : StudioService      // author facts + versions
  explorer()   : ExplorerService    // read/understand (FKGE)
  review()     : ReviewService      // judge + record decisions
  copilot(m)   : ForgeCopilot       // grounded Q&A (LLM narrates FKGE proofs)
  reproduce(o) : ReproduceResult    // kernel reproduce
  validate()   : PlatformHealth     // chain verify + integrity scan
  search(text) : [KnowledgeObject]  // object search (read projection)
```

Every read returns a proof object carrying `asOf` (a `LogPosition`). The API holds no engineering
logic — each method is a thin orchestration of the platform's public APIs.

## 2. REST binding

Base path `/{version}/orgs/{org}`; the conceptual methods map to resources:

| Conceptual call | REST |
|-----------------|------|
| `studio().create` | `POST /objects` |
| `studio().revise` | `POST /objects/{id}/revisions` |
| `studio().commit` | `POST /commits` |
| `studio().tag` | `POST /tags` |
| `explorer().provenance(id)` | `GET /objects/{id}/provenance` |
| `explorer().impact(id)` | `GET /objects/{id}/impact` |
| `explorer().dependencies(id)` | `GET /objects/{id}/dependencies` |
| `explorer().rootCause(id)` | `GET /objects/{id}/root-cause` |
| `explorer().confidence(id)` | `GET /objects/{id}/confidence` |
| `explorer().evidence(id)` | `GET /objects/{id}/evidence` |
| `explorer().explain(id)` | `GET /objects/{id}/explanation` |
| `review().reviewCommit(a,b)` | `GET /reviews/commit?from={a}&to={b}` |
| `review().approve(id)` | `POST /objects/{id}/approvals` |
| `copilot().ask(id, intent)` | `POST /copilot/ask` |
| `reproduce(id)` | `POST /objects/{id}/reproduce` (long-running) |
| `validate()` | `GET /health` |
| `search(text)` | `GET /objects?q={text}` |

### 2.1 Authentication
Bearer token (OIDC/JWT) in `Authorization`. The authenticated principal becomes the kernel
`ActorId` on every write — there is no anonymous append. Tokens are validated at the gateway.

### 2.2 Versioning
URI-versioned (`/v2/…`). The conceptual API is the contract; a breaking change is a new URI major
version. Response bodies are additive within a major version.

### 2.3 Pagination
Cursor-based: `?limit=&cursor=`. The cursor encodes a `LogPosition`, so pagination is a stable
window over an immutable prefix — a page never shifts under the reader.

### 2.4 Filtering
`?type=`, `?kind=`, `?subtype=`, `?q=` (substring). Filters are applied over the read view; they
narrow access, never change an answer.

### 2.5 `asOf` (reproducibility)
Any read accepts `?asOf={logPosition}`. Omitted = latest. Every response includes the `asOf` it was
computed at, so a response is a permanent, reproducible citation. `GET …?asOf=N` twice is
byte-identical.

### 2.6 Streaming
Notifications stream over Server-Sent Events at `GET /events?match=…`, backed by the kernel
`subscribe` operation. A dropped connection is recovered by re-reading from a `LogPosition` — the log
is the event stream, so no event is lost.

### 2.7 Long-running operations
`reproduce` returns `202 Accepted` with `Location: /operations/{id}`; poll `GET /operations/{id}`
for `{status, result}`. Deterministic: the same target reproduces to the same result.

### 2.8 Error model
```json
{ "error": { "code": "CLAIM_LAW", "message": "...", "asOf": 42 } }
```
Codes are the platform's own (`CLAIM_LAW`, `DECISION_LAW`, `CAS_FAILURE`, `UNKNOWN_NODE`, …) surfaced
verbatim plus HTTP status: `400` (validation/law), `404` (unknown node/name), `409` (CAS conflict on
branch advance), `401/403` (auth), `422` (unsupported request). The platform's laws are the API's
error semantics — FXP invents no error meaning.

## 3. Guarantees the API inherits from the platform
Deterministic (pure functions of a log prefix), explainable (proofs attached), reproducible (`asOf`),
evidence-backed (Laws 5/6), history- and version-aware (FVCS), attributed (actor on every write).
