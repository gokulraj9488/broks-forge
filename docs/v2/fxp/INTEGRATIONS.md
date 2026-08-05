# Forge Experience Platform — Reference Integrations

**Deliverable 7.** Integrations are **edge adapters**. An adapter translates an external event into
lawful Forge facts through the public write API. The dependency is strictly one-way — **adapter →
platform** — so the platform never depends on any external system, and every ingested fact is
thereafter a first-class, attributed, explainable, reproducible platform citizen.

## The adapter law

```
external system ──(adapter: public write API)──▶ Forge facts ──▶ (indistinguishable from hand-authored)
        ▲                                                              │
        └──────────── the platform NEVER calls back out ──────────────┘
```

An adapter may only **read** the external system and **write** platform facts (via `StudioService`).
It never grants the platform a compile-time or runtime dependency on the external system, and it never
computes an engineering answer — it records inputs; the platform reasons.

## Implemented reference adapters

| Adapter | Stands in for | Maps to | Implemented |
|---------|---------------|---------|-------------|
| `GitSourceControlAdapter` | GitHub, GitLab, any Git host | external commit → `Prompt`/artifact revision | ✅ [`integrate/`](../../../backend/forge-fxp/src/main/java/com/broksforge/fxp/integrate/) |
| `LocalModelProviderAdapter` | OpenAI, Anthropic, Ollama | model invocation → `Run` observation `executed` an agent | ✅ |

Both are tested ([`IntegrationAdapterTest`](../../../backend/forge-fxp/src/test/java/com/broksforge/fxp/IntegrationAdapterTest.java)):
the ingested fact is a lawful platform object whose provenance/explanation work like any other. No
network is required to prove the boundary — the adapter's only capability is translation.

## Specified adapters (same pattern)

Each of these is the identical shape — read external, write a platform fact through Studio — differing
only in the external transport and the target object type:

| Integration | External event | Forge fact recorded |
|-------------|----------------|---------------------|
| **GitHub / GitLab** | PR opened / commit pushed | `Prompt`/`Dataset`/`Agent` revision; a `Deployment` proposal on merge |
| **Jenkins / CI-CD** | pipeline stage result | `Run` observation; `EvaluationVerdict` claim on the gate |
| **Kubernetes** | rollout status | `Run`/`Incident` observation against the `Deployment` decision |
| **OpenAI / Anthropic / Ollama** | model call | `Run` observation `executed` an `Agent` |
| **Vector databases** | index build / query | `KnowledgeBase` `indexes` a `Dataset`; retrieval `Run` |
| **CI/CD (generic)** | promotion event | `Promotion` decision, `resting_on` the gate's claims |

Transports differ (webhooks, polling, SDKs); the mapping to a platform fact is identical, which is why
two implemented references are sufficient to prove the whole class.

## Why this keeps the platform independent

- **No inbound coupling:** the platform module graph (kernel → knowledge → FVCS → FKGE) has no
  dependency on `com.broksforge.fxp.integrate`; adapters live in the application layer and depend
  downward only.
- **Facts, not opinions:** an adapter records observations and artifacts; the *beliefs* (claims) and
  *decisions* about them are authored under the laws (5/6), so an integration can never smuggle an
  ungrounded conclusion into the platform.
- **Auditable ingestion:** because every ingested fact is attributed (the adapter runs as an actor) and
  hash-chained, an integration's entire footprint is itself explainable and reproducible.

## Failure & idempotency
Adapters are re-runnable: content-addressing dedups identical artifacts (same content → same hash), and
a re-ingested observation is a new attributed fact (reality can recur). A failed adapter run leaves the
log consistent — there are no partial writes across the append boundary.
