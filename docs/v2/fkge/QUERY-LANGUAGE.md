# FKGE Query Language Specification

**Deliverable 2.** A canonical model for asking engineering questions of the graph. Questions
are posed in **engineering language**; the engine compiles them to the six-primitive algebra
of [THEORY §3](THEORY.md).

## 1. The question object

A `Question` is a pure value:

```
Question = (subject: NodeId,
            lens:    Lens,          // which edge families + default direction
            direction: Direction,   // UPSTREAM | DOWNSTREAM (overrides the lens default)
            maxDepth:  int,         // bound; -1 = unbounded (terminates on the finite graph)
            asOf:      LogPosition) // default = latest
```

Every question is evaluated **as-of** an immutable log prefix, so every answer is reproducible
and carries the `asOf` position it was computed at (the reproducibility theorem, THEORY §2).

## 2. Direction — the structural spine

Kernel references point **from a node to what it rests on**:

- `Agent —uses→ Model`, `Model —derived_from→ Model₀`, `Claim —cites→ Observation`,
  `Decision —rests_on→ Claim`, `Commit —parent→ Commit₀`, `Commit —records→ Snapshot`.

Therefore:

| Direction | Follows | Answers | Kernel `Query.Direction` |
|-----------|---------|---------|--------------------------|
| **UPSTREAM** | OUT refs | provenance, dependency, evidence, "why", "what it rests on" | `OUT` |
| **DOWNSTREAM** | IN refs | impact, blast radius, "what rests on this", "who is affected" | `IN` |

The **duality law** `X ∈ impact(N) ⟺ N ∈ provenance(X)` is then just: DOWNSTREAM is the
transpose of UPSTREAM. One traversal engine, two directions — consistency by construction.

## 3. Lenses — engineering meaning over edge families

A `Lens` is `(name, families, defaultDirection)`. The built-ins:

| Lens | Families | Default dir | Question it serves |
|------|----------|-------------|--------------------|
| `PROVENANCE` | composition, derivation, evidence, intent | UPSTREAM | "where did this come from?" |
| `DEPENDENCY` | composition, derivation | UPSTREAM | "what must I have to rebuild this?" |
| `IMPACT` | composition, derivation, evidence, intent | DOWNSTREAM | "what breaks if this changes?" |
| `EVIDENCE` | evidence | UPSTREAM | "what supports this belief?" |
| `INTENT` | intent | UPSTREAM | "why was this decided?" |
| `CAUSALITY` | causality | DOWNSTREAM→cause* | "what caused this?" |
| `COMPOSITION` | composition | UPSTREAM | "what is this made of?" |
| `LINEAGE` | derivation | UPSTREAM | "what was this derived from?" |

\* Causal edges (`caused`, `triggered`, `regressed`) point *from cause to effect*; root-cause
walks **IN** from the effect. LogPosition monotonicity is enforced (a cause precedes its
effect); a violation is reported as an anomaly, never silently traversed.

Lenses are **data**, not code branches, and are extensible through the SPI (§6).

## 4. The engineering-question surface

The façade (`KnowledgeGraphEngine`) exposes named methods that build the right `Question`:

| Method | Compiles to |
|--------|-------------|
| `provenanceOf(n)` | `closure(n, PROVENANCE)` + closure-hash certificate |
| `dependenciesOf(n)` | `closure(n, DEPENDENCY)`, topo-ordered |
| `impactOf(n)` / `blastRadius(n)` | `closure(n, IMPACT)` |
| `criticalPath(n)` | longest chain in `dependenciesOf`/`impactOf` |
| `explain(n)` | proof-tree walk (Explanation model) |
| `whyApproved(decision)` | `path` over `{intent, evidence}` UPSTREAM |
| `evidenceFor(claim)` | `closure(claim, EVIDENCE)` filtered to Observations/Artifacts |
| `rootCause(incident)` | `closure(incident, CAUSALITY, IN)` filtered to Decisions/Runs |
| `confidenceOf(n)` | `fold(min)` over the claim-support closure |
| `neighborhood(n, depth)` | bounded `closure` BOTH directions |
| `influence(n)` | structural rank over `impactOf` |
| `similarTo(n)` | neighborhood-signature match |
| `trace(from, to, lens)` | `path(from, to)` |
| `reachable(from, to, lens)` | boolean `path` existence |
| `whatChanged(a, b)` | **FVCS `diff`** (reused) |
| `summarize(n)` | deterministic projection |

## 5. Semantics & guarantees

- **Termination.** The graph is finite; every traversal carries a visited-set → no infinite
  walks even on cyclic extrinsic edges. `maxDepth` bounds cost further.
- **Determinism.** Result sets and path tie-breaks are ordered by `(LogPosition,
  RevisionHash)`. No dependence on map iteration order, clock, or randomness.
- **As-of.** `asOf` selects the log prefix; the index is folded only over entries at or before
  it. Same prefix + same question ⇒ byte-identical answer.
- **Read-only.** No question ever appends. If a question cannot be answered without writing,
  that is a foundation gap to be filed as an amendment, not worked around.

## 6. Extension SPI

New lenses (and thus new engineering questions) are added **additively** by implementing
`LensModule { void contribute(LensRegistry) }` and registering `Lens` values — the same
composition discipline the knowledge system uses for `OntologyModule`. The engine core is
never edited to add a capability that is expressible as a lens.
