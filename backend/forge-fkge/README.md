# Forge Knowledge Graph Engine (FKGE)

Deterministic graph reasoning over the frozen Forge Kernel, Forge Knowledge System, and FVCS.
A standalone Maven library that consumes **only public APIs** (`forge-fvcs` 1.0.0 and, transitively,
the knowledge system and kernel). It **stores nothing, writes nothing, and adds nothing to any
ontology** — understanding is a read-only projection of the graph the kernel already holds.

Design documents: [`../../docs/v2/fkge/`](../../docs/v2/fkge/README.md) — reasoning theory, query
language, explanation/provenance/dependency models, reasoning algorithms, architecture, and the
architecture review.

## What it provides

- **`KnowledgeGraphEngine`** — the façade / Query Engine: `provenanceOf`, `dependenciesOf`,
  `impactOf`/`blastRadius`, `explain`, `whyApproved`, `evidenceFor`, `rootCause`, `confidenceOf`,
  `influence`, `criticalPath`, `neighborhood`, `trace`/`reachable`, `similarTo`, `patterns`,
  `summarize`, `whatChanged`, `asOf`.
- **`index/`** — `GraphIndex`, the one deterministic fold of the log into an immutable typed
  adjacency structure; `GraphNode`, `GraphEdge`, `Order` (the total-order discipline).
- **`traverse/`** — the three atoms `neighbors`, `closure`, `path` (+ `Reach`, `Path`, `Neighborhood`).
- **`provenance/` · `depend/` · `impact/`** — provenance (certified), dependency (topo + critical
  path), impact (blast radius, duality, influence).
- **`explain/`** — proof trees with classified leaves; gaps named, never silent.
- **`reason/`** — `min`-bound confidence propagation; causal tracing with log-position soundness.
- **`search/`** — structural neighborhood-signature similarity and pattern detection (no embeddings).
- **`project/`** — deterministic node summaries.
- **`query/` · `spi/`** — lenses (engineering questions as data) and the `LensModule` extension SPI.

## Build

```bash
# Prereqs: kernel, forge-knowledge, and forge-fvcs installed to the local repo (mvn -o install).
cd backend/forge-fkge
mvn -o test        # compile + 22 tests, offline
```

## Example

```java
KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(repository);

Provenance prov   = fkge.provenanceOf(model);        // where did this model come from? (certified)
Impact     blast  = fkge.blastRadius(dataset);        // what breaks if this dataset changes?
Explanation why   = fkge.whyApproved(deployment);     // proof tree to observations / judgment-calls
CausalTrace cause = fkge.rootCause(incident);         // recorded causes, log-position-sound
ConfidenceResult c = fkge.confidenceOf(deployment);   // min over supporting claims (weakest link)

fkge.asOf(pastPosition).impactOf(model);              // deterministic time travel
fkge.whatChanged(commitA, commitB);                   // delegated to FVCS diff
```

## Boundaries

Deterministic reasoning only. **No** UI, dashboards, visual editors, AI assistants, or applications
(Phase 5). Depends on FVCS/knowledge/kernel; applications depend on FKGE, never the reverse.
