# Forge Knowledge Graph Engine (FKGE) — Phase 4

Deterministic **reasoning** over the graph the kernel already stores — turning engineering
*knowledge* into engineering *understanding*. FKGE answers the questions a human engineer asks
("why was this approved?", "what depends on this dataset?", "what caused this incident?", "what
is the complete provenance of this model?") as pure, reproducible functions of an immutable log
prefix. Built on the frozen Forge Kernel, Forge Knowledge System, and FVCS through **public APIs
only**; it **stores nothing, writes nothing, and adds nothing to any ontology.**

Produced concepts-before-code: the theory and architecture were made internally consistent and
adversarially reviewed before implementation.

## Deliverables

| # | Deliverable | Where |
|---|---|---|
| 1 | Graph Reasoning Theory | [THEORY.md](THEORY.md) |
| 2 | Query Language Specification | [QUERY-LANGUAGE.md](QUERY-LANGUAGE.md) |
| 3 | Explanation Model | [EXPLANATION-MODEL.md](EXPLANATION-MODEL.md) |
| 4 | Provenance Model | [PROVENANCE-MODEL.md](PROVENANCE-MODEL.md) |
| 5 | Dependency Model | [DEPENDENCY-MODEL.md](DEPENDENCY-MODEL.md) |
| 6 | Reasoning Algorithms | [REASONING-ALGORITHMS.md](REASONING-ALGORITHMS.md) |
| 7 | Public APIs | [ARCHITECTURE.md §4](ARCHITECTURE.md) + Javadoc in [`backend/forge-fkge`](../../../backend/forge-fkge/README.md) |
| 8 | Implementation | [`backend/forge-fkge/`](../../../backend/forge-fkge/README.md) |
| 9 | Automated Tests | `backend/forge-fkge/src/test` (22 tests) |
| 10 | Architecture Review Report | [ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md) |
| 11 | Future Extension Strategy | this document, §Future Extension |

## The engine on one screen

- **Understanding = entailment.** The graph stores *what is*; FKGE derives the closures, paths, and
  projections it entails. It only reads — a view, never an append.
- **Six primitives** close over every engineering question: `resolve`, `neighbors`, `closure`,
  `path`, `project`, `fold`. Provenance/impact/dependency are `closure`; explanation/traceability
  are `path`; summaries/rankings are `project`; history/confidence are `fold`.
- **One projection.** A single deterministic fold of the log into an immutable typed adjacency index
  — no second store, no hidden mutable state.
- **Duality.** `X ∈ impact(N) ⟺ N ∈ provenance(X)` — one relation read two ways.
- **Determinism theorem.** Every result is a pure function of an immutable log prefix and carries the
  `LogPosition` it was computed at — a citable, re-verifiable answer.
- **No probability, no learning.** Confidence propagates as the `min` bound; similarity is a
  structural neighborhood-signature; causality is recorded edges with a log-position soundness check.

## Future Extension Strategy

**Governance.** FKGE grows additively. New engineering questions are new **lenses** contributed
through the public `LensModule` SPI — never edits to the engine core. FKGE adds **no** ontology and
touches no frozen layer; a genuine deficiency in a foundation would be a **stop-and-file-an-amendment**
event, never a silent extension.

**Deferred (documented, in priority order):**
1. **Iterative traversal** for adversarially deep graphs (replace the recursive topo-sort/longest-path).
2. **Whole-graph rankings** (influence/centrality sweeps) with an explicit, discardable derived index.
3. **Impact critical path over cyclic evidence** with a proper DAG condensation (SCC) pass.
4. **Cross-lens explanation composition** (a decision explained through provenance *and* causality at
   once) as a single merged proof tree.
5. **Incremental projection** — fold only the delta since the last `LogPosition` via `subscribe`,
   for large logs (still a pure function of the prefix).
6. **Query-result export** to the knowledge system's canonical bytes for archival/attestation.

**Explicitly not built** (per mandate): UI, dashboards, visual graph editors, AI assistants,
applications — those are Phase 5. FKGE is exclusively deterministic graph reasoning.

## Status

Theory derived from first principles and adversarially reviewed
([ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md)) through eight lenses; one silent-completeness
defect (F1) found and fixed with a pinning test, the rest documented scope boundaries. Foundation
implemented and green (**22 tests**), using only public APIs, with the kernel, knowledge system, and
FVCS untouched.
