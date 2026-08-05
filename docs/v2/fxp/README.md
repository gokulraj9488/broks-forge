# Forge Experience Platform (FXP) — Phase 5

The application layer that proves the platform solves real AI-engineering problems through coherent
experiences. Built on the frozen Kernel, Knowledge System, FVCS, and FKGE through **public APIs only**;
it holds no engineering logic, stores nothing, and modifies no frozen layer. Integrations are edge
adapters; the platform never depends on them.

> **The law of this phase:** applications orchestrate; the platform reasons. Every FXP answer is one the
> platform can independently prove and reproduce.

## Deliverables

| # | Deliverable | Where |
|---|---|---|
| 1 | Experience Model | [EXPERIENCE-MODEL.md](EXPERIENCE-MODEL.md) |
| 2 | Workflow Catalog | [WORKFLOW-CATALOG.md](WORKFLOW-CATALOG.md) |
| 3 | UX Architecture | [UX-ARCHITECTURE.md](UX-ARCHITECTURE.md) |
| 4 | API Specification | [API-SPEC.md](API-SPEC.md) |
| 5 | CLI Specification | [CLI-SPEC.md](CLI-SPEC.md) |
| 6 | SDK Specifications | [SDK-SPECS.md](SDK-SPECS.md) |
| 7 | Reference Integrations | [INTEGRATIONS.md](INTEGRATIONS.md) |
| 8 | Implementation | [`backend/forge-fxp/`](../../../backend/forge-fxp/README.md) |
| 9 | Automated Tests | `backend/forge-fxp/src/test` (19 tests) |
| 10 | End-to-End Demonstrations | [`workflow/ReferenceWorkflowsTest`](../../../backend/forge-fxp/src/test/java/com/broksforge/fxp/workflow/ReferenceWorkflowsTest.java) (W1–W3) |
| 11 | Experience Review Report | [EXPERIENCE-REVIEW.md](EXPERIENCE-REVIEW.md) |
| 12 | Deployment Guide | [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md) |
| 13 | Operations Guide | [OPERATIONS-GUIDE.md](OPERATIONS-GUIDE.md) |
| 14 | Developer Documentation | [DEVELOPER-GUIDE.md](DEVELOPER-GUIDE.md) |

## The platform on one screen

- **Four experiences by mode of work:** Studio (write), Explorer (understand), Review (judge),
  Copilot (ask). Anything else is a composition of these.
- **One conceptual API** (`ForgeClient`), mirrored by CLI, SDKs, and REST — so surfaces cannot drift and
  no hidden logic can accrete.
- **Copilot contract:** the LLM explains, FKGE proves. The model sees only proofs, never the graph, and
  is never invoked without one — so it cannot invent engineering truth; every answer carries its proof.
- **Everything reproducible:** every read carries the `asOf` log position it was computed at.
- **Integrations are one-way adapters:** external event → lawful Forge fact; the platform depends on
  none of them.

## Reference workflows (implemented & tested)
1. **W1** change → version → evaluate → claim → decide → promote → **complete explanation**.
2. **W2** incident → root cause → provenance → responsible evaluation → **reproducible explanation**.
3. **W3** "why is this model in production?" → a deterministic, evidence-backed dossier.

## Scope note
The Java conceptual API, the four experiences, the Copilot grounding contract, the CLI, and reference
integration adapters are **implemented and tested** here (19 tests, offline). The REST server and the
Python/TypeScript SDKs are **specified** against the same conceptual API (they are thin transport
bindings with no logic of their own) and verified by the shared conformance suite (the reference
workflows). This honours "all SDKs expose the same conceptual API" by making the implemented Java API
the single definition every binding mirrors.

## Status
Experience model derived from real engineering work before any code; implementation consumes only public
platform APIs (import-audited: zero frozen internals); one real defect (stale projection) caught by the
tests and fixed; adversarially reviewed by an eight-role panel with no meaningful weakness remaining.
**19 tests green**; the four frozen layers are byte-for-byte unchanged.
