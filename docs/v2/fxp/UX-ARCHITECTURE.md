# Forge Experience Platform — UX & System Architecture

**Deliverable 3.** How the experiences are built so that **every component consumes only public
platform APIs** and none holds engineering logic.

## 1. Layering

```
        Surfaces          Web UI      CLI       SDKs (Java·Python·TS)      REST clients
                             │          │               │                      │
        Conceptual API  ───────────────────  ForgeClient  ───────────────────────
                                              (one API; all surfaces mirror it)
                             │          │               │                      │
        Experiences     Studio      Explorer         Review               Copilot
        (orchestration) (write)     (read/FKGE)      (decisions)          (grounded)
                                                  │
        Platform (frozen, public APIs only)  FKGE → FVCS → Knowledge → Kernel
                                                  ▲
        Integrations (edge adapters)   GitHub·GitLab·Jenkins·K8s·OpenAI·Anthropic·Ollama·VectorDB
                                       (one-way: adapters call the platform; never the reverse)
```

Dependency direction is strictly downward and one-way. The web frontend talks only to the REST API;
REST, CLI, and SDKs talk only to `ForgeClient`; `ForgeClient` talks only to the platform's public
APIs. **No arrow points up**, and no component reaches around `ForgeClient` into platform internals.

## 2. Frontend architecture (specified)

- **Model:** a thin, stateless SPA. It holds *no* engineering logic and *no* derived truth — it
  renders `GroundedAnswer`/proof objects returned by REST. Every view has a **"show proof"**
  affordance that displays the FKGE proof and the `asOf` position behind the rendered claim.
- **Composition:** one component per experience (Studio, Explorer, Review, Copilot), each backed by
  the matching REST resource. Views are projections of platform records; there is no client-side
  computation of provenance/impact/confidence — those arrive already proven.
- **Determinism in the UI:** because every response carries `asOf`, a shared link reproduces the
  exact view. "Bookmarkable truth."
- **Accessibility:** semantic HTML, keyboard-first navigation, WCAG-AA contrast, text alternatives
  for every graph visual (the proof is *text first*, visual second — a graph is a rendering of a
  list of typed steps, never the source).

## 3. Backend services

Each experience is a **stateless service** over `ForgeClient`. Statelessness is possible because the
kernel log is the only state; a service instance can be recreated and will fold the same answer.

- `StudioService` — authoring; the only write path (through `KnowledgeGraph`/`Repository`).
- `ExplorerService` — read projections over `FKGE`.
- `ReviewService` — decisions over subjects (AI-PR triad).
- `CopilotService` — grounding + narration; holds the `LanguageModel` adapter.

## 4. API gateway, authentication, authorization

- **Gateway:** terminates TLS, routes to services, applies rate limits and pagination defaults, and
  is the single place the REST contract (versioning, error model, streaming) is enforced.
- **Authentication:** bearer tokens (OIDC/JWT) at the gateway; the authenticated principal becomes
  the kernel `ActorId`. **Every write is attributed** — there is no anonymous append, because the
  kernel records an actor on every fact. Identity is thus not an FXP concern layered on top; it is
  the platform's provenance made external.
- **Authorization:** policy-based, evaluated at the gateway/service edge — *who may write which
  object types, who may approve deployments, who may read which org*. Authorization gates **access**;
  it never alters **answers**. Because the org boundary is a kernel invariant (one log per org),
  tenant isolation is enforced by the platform, not re-implemented.

## 5. Plugin & extension model

Extensions are additive and ride the platform's existing SPIs — FXP invents no new extension
mechanism that could bypass the platform:

- **Vocabulary plugins** → knowledge `OntologyModule` (new object/relation types).
- **Reasoning plugins** → FKGE `LensModule` (new engineering questions).
- **Reproducers** → kernel `Reproducer` SPI.
- **Integration adapters** → FXP `*Adapter` SPIs (§edge).
- **Copilot models** → FXP `LanguageModel` SPI.

A plugin that would require changing a frozen layer is rejected at review and routed to the
amendment process.

## 6. Notifications
Built on the kernel `subscribe` operation: a notification is a `SubscriptionProgram` matching a log
predicate (e.g. "a `Deployment` targeting `prod` was appended") that emits to a channel. Deterministic
and replayable — a missed notification is recovered by folding the log, because the log *is* the
event stream.

## 7. Search
Search is a read projection: object search over `KnowledgeView` (by type/subtype/payload), structural
search over `FKGE.similarTo`/`patterns`, and history search over `FVCS`. No separate search index is
authoritative; any index is a discardable derived view of the log (rebuildable, never a source of
truth).

## 8. Caching
Caching is safe **only because answers are content-addressed and `asOf`-stamped**: an answer for
`(query, asOf)` is immutable, so it caches forever with the `asOf` as the key. Caches accelerate;
they can never change an answer, and a cold cache reproduces the identical result. No cache is a
source of truth.

## 9. Deployment architecture
Stateless services behind the gateway, scaled horizontally; the kernel store (Postgres) is the single
stateful tier. Because services are pure functions of the log, scaling is trivial and blue/green
deploys carry no migration risk in the app tier. (Details: [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md).)

## 10. The one architectural rule
Every box above is testable against a single predicate: **does it compute an engineering answer the
platform cannot independently prove?** If yes, it is wrong. FXP's job is to *route, shape, and
present* proofs — never to originate them.
