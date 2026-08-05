# FKGE Reasoning Algorithms

**Deliverable 6.** Every algorithm is deterministic, terminating, and reproducible. Each takes
an immutable log prefix (via `asOf`) and returns a byte-identical result for identical input.
No probabilistic reasoning, no learned parameters, no hidden mutable state.

## Global invariants
- **Total order.** Every set returned and every tie broken uses `(LogPosition, RevisionHash)`.
- **Termination.** Every traversal carries a visited-set; the graph is finite.
- **Purity.** Algorithms read the folded index (a deterministic projection of the log) and
  compute; they never append.

The three traversal atoms all reasoning is built from:

```
neighbors(n, families, dir):    edges of n filtered by family, in dir, sorted by total order
closure(n, families, dir):      BFS from n over neighbors, visited-set, → (nodes, edges)
path(a, b, families, dir):      BFS with predecessor map → shortest walk a→b, or "none exists"
```

## 1. Explanation — proof tree
UPSTREAM DFS over the lens families with a visited-set; assemble ordered steps; classify each
leaf as `OBSERVATION | PRIMARY_ARTIFACT | JUDGMENT_CALL | FRONTIER`; `complete` iff no FRONTIER
leaf; report gaps. (Full spec: [EXPLANATION-MODEL.md](EXPLANATION-MODEL.md).)

## 2. Causal tracing — root cause
```
rootCause(effect, asOf):
    reach ← closure(effect, {causality}, IN, asOf)          # follow causes backward
    causes ← reach.nodes \ {effect}, sorted by LogPosition
    anomalies ← [c for c in causes if LogPosition(c) > LogPosition(effect)]   # a cause after its effect
    return CausalTrace(effect, causes, anomalies, asOf)
```
The **LogPosition monotonicity check** is the causal soundness guarantee: a genuine cause
precedes its effect. Violations are surfaced as anomalies, never traversed as valid causation.
Root-cause nodes are typically `Deployment`/`Run`; combined with `detected_by` (RootCause →
Observation) they close the loop from symptom to decision.

## 3. Evidence collection
`closure(claim, {evidence}, UPSTREAM)` then keep leaves of `Kind.OBSERVATION` or primary
`Kind.ARTIFACT`. Transitive: a Claim that `cites` a Claim that `cites` an Observation collects
the Observation. This is the deterministic realization of traceability for beliefs.

## 4. Dependency expansion
`closure(n, {composition, derivation}, UPSTREAM)`, topologically ordered; critical path by
memoized longest-path DFS over the acyclic intrinsic-dependency subgraph.
(Spec: [DEPENDENCY-MODEL.md](DEPENDENCY-MODEL.md).)

## 5. Impact / blast radius
`closure(n, {composition, derivation, evidence, intent}, DOWNSTREAM)`; the forward dual of
provenance; `byKind` tally; downstream critical path.

## 6. Version comparison
Delegated to **FVCS `diff`** — FKGE reuses `Repository.diff(commitA, commitB)` /
`DiffEngine.diff(snapshotA, snapshotB)` rather than reinventing it. "What changed between these
versions?" returns the FVCS `ChangeSet` (ADDED/REMOVED/CHANGED per continuant).

## 7. Semantic filtering
Restrict any node set by `ObjectType` / `Kind` / `subtype`, resolved through the composed
ontology (`Ontology.resolve(kind, subtype)`). E.g. impact filtered to `Kind.DECISION` = "which
shipped decisions are affected."

## 8. Confidence aggregation
```
confidenceOf(n, asOf):
    support ← closure(n, {evidence, intent}, UPSTREAM, asOf) restricted to Kind.CLAIM
    if support empty:
        if n is CLAIM:   return (confidence(n), weakest = n)          # its own recorded value
        if n is OBSERVATION: return (1.0, weakest = n)                # reality
        else: return (UNDEFINED — not a truth-bearer)
    weakest ← argmin over support of confidence(claim), tie-broken by total order
    return ConfidenceResult(n, min = confidence(weakest), weakest, support)
```
`min` is the tight, assumption-free bound: a conjunction of beliefs is at most as strong as its
weakest link. The **product rule is rejected** (it assumes statistical independence — a hidden
heuristic). FKGE never invents or raises a confidence; it only takes the minimum of **recorded**
values. This is arithmetic, not inference.

## 9. Decision-chain reconstruction
`closure(decision, {intent}, UPSTREAM)` gives the Claims a Decision `rests_on`; recursing
through `supersedes`/`cites` and any `approves`/`rejects` (Decision→Decision) edges reconstructs
the full chain of will and belief behind a shipped decision. Terminates at judgment-calls.

## 10. Historical reconstruction
Fold the index only over log entries with `LogPosition ≤ asOf` (or resolve a Name with
`kernel.resolveAt`). Every algorithm above accepts `asOf`, so *any* question can be answered
"as it stood then" — deterministically. This is temporal-uncertainty resolution.

## 11. Influence ranking
`|impact(n)|` with critical-path-membership tie-break — a closed-form structural rank
([DEPENDENCY-MODEL §3](DEPENDENCY-MODEL.md)). No PageRank.

## 12. Structural similarity & pattern detection
```
signature(n) = hash( sorted multiset of (family, direction, neighborType) over n's 1-step edges )
similarTo(n) = { m ≠ n : m.objectType == n.objectType AND signature(m) == signature(n) }
patterns()   = signatures occurring on ≥ k nodes
```
Similarity is **structural and deterministic** — same type, isomorphic typed neighborhood.
**Embeddings / vector similarity are rejected**: learned, opaque, non-reproducible. A signature
is a content hash of sorted typed-edge triples, so it is stable and comparable across runs.

## Determinism proof obligation
For each algorithm: (a) inputs are an immutable log prefix; (b) all iteration is over
total-ordered sequences; (c) no clock/random/map-order dependence. ⇒ identical output for
identical input, forever. This is the reproducibility theorem (THEORY §2) discharged per
algorithm.
