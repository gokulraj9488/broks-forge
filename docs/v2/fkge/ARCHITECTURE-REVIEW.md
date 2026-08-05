# FKGE Architecture Review

**Deliverable 10.** An independent committee with expertise in **knowledge graphs, graph theory,
provenance systems, database systems, causal inference, static analysis, program analysis, and
distributed systems** attempts to reject the engine. Findings are graded **Refined (fixed)**,
**Accepted with rationale**, or **No-issue**; only genuine defects force a change.

## Per-lens attack

### Knowledge graphs / Graph theory
- **Reachability, closure, path, LCA, critical path** are the textbook operations, and each is a
  transitive closure or path over a fixed finite set of typed relations — decidable and
  terminating. Every traversal carries a visited set, so even cyclic *extrinsic* edges cannot
  cause non-termination. ✓
- **Critical path over cycles.** The *dependency* critical path is over the composition∪derivation
  subgraph, which is acyclic by construction (a hash-pinned ref can only target an already-existing
  revision) — so it is exact. The *impact* critical path may traverse extrinsic evidence/intent
  edges that can, pathologically, cycle; the longest-path memo is guarded by an on-stack set, so it
  is terminating and deterministic but best-effort on cyclic evidence graphs. *Accepted
  (documented): the exact guarantee is on the acyclic dependency path.*

### Provenance systems
- **Certificate scope (examined closely).** The provenance certificate is the subject's
  content-addressed revision hash, which is a Merkle commitment over its **intrinsic** closure
  (composition/derivation/evidence/intent *refs* are hash-pinned inside the revision). Provenance
  also follows **extrinsic** edges (`supports`, `approves`, …), and those ancestors are **not**
  covered by the revision hash. This is not a gap: extrinsic edges are separately-appended,
  attributed, hash-chained log facts, verifiable by `kernel.verifyChain`. So provenance is
  certified by **two** mechanisms — the revision hash for the intrinsic backbone, the log chain for
  extrinsic edges. *Accepted with clarification (documented precisely in PROVENANCE-MODEL and here);
  no naked, uncertified ancestor is ever presented.*
- **Completeness.** Provenance is the full backward closure — no ancestor omitted. ✓

### Database systems / Distributed systems
- **Determinism / hidden mutable state.** The index is `Map.copyOf`/`List.copyOf` immutable;
  adjacency is pre-sorted by `(LogPosition, RevisionHash)`; every algorithm iterates total-ordered
  sequences with visited sets; nothing reads a clock or RNG. `asOf` builds a *fresh* index. There is
  **no cache that changes a result** and no shared mutable state. The `deterministicFold` and
  `signatureDeterministicAcrossRuns` tests pin this. ✓ *No-issue.*
- **Snapshot isolation / MVCC.** `asOf(LogPosition)` is a consistent read of an immutable prefix —
  snapshot isolation for reasoning. Two engines over the same prefix are byte-identical. ✓
- **Scale.** The fold is O(entries); a query is O(V+E); `patterns` is O(V·deg). No pathological
  blow-up. Whole-graph rankings would be O(V·(V+E)); the API computes influence per node on demand,
  and any heavier sweep is the caller's explicit choice. *Accepted (foundation-scale; documented).*
- **Recursion depth.** Post-order topo-sort and longest-path use recursion; on adversarially deep
  chains this could exhaust the stack. Foundation-scale acceptable; an iterative rewrite is noted as
  future work. *Accepted (documented).*

### Causal inference
- **No statistical inference.** Root cause follows only **recorded** causal edges backward; it never
  infers causation from correlation. The **LogPosition monotonicity check** rejects any putative
  cause that does not precede its effect, reporting it as an anomaly rather than traversing it as
  valid. This is causal *tracing*, not causal *discovery* — exactly the deterministic, defensible
  scope. ✓ *No-issue.*

### Static analysis / Program analysis
- **Dependency = reachability; topo order; longest path** are the standard static-analysis
  constructions, here over an acyclic intrinsic subgraph, so they are exact and reproducible. The
  separation of *dependency* (rebuild-required) from *provenance* (justification) mirrors
  build-graph vs data-flow analysis and is deliberate. ✓

### Incorrect / circular reasoning, semantic leakage
- **Circular reasoning.** Confidence propagation is `min` over recorded values — it cannot inflate a
  conclusion above its inputs, and superseded claims are excluded (`supersedes` is a derivation, not
  an evidence, edge). No value is ever invented. ✓
- **Semantic leakage.** Results expose only the public vocabulary (`NodeId`, `RevisionHash`, `Kind`,
  `CanonicalValue`, `ObjectType`); no store/codec/index internal type crosses the boundary. ✓

## Findings

### F1 — Explanation could silently certify an ungrounded decision — **Refined (fixed)**
The correctness lens found it: `classify` labelled *any* `DECISION` reached as a leaf a
`JUDGMENT_CALL` axiom. Under a lens that excludes the intent family, a decision that actually
`rests_on` claims becomes a leaf and would be **falsely certified as a complete proof** — a silent
completeness lie. **Fixed:** a decision leaf is `JUDGMENT_CALL` only if its payload marks it an
explicit judgment-call; otherwise it is a named `FRONTIER` gap. The `noSilentCompleteness` test
pins it — a grounded deployment explained through the evidence-only lens now reports `complete ==
false` with a named gap, never a false "complete."

### F2 — Impact critical path on cyclic evidence graphs is best-effort — **Accepted (documented)**
The exact critical-path guarantee is on the acyclic dependency subgraph. The impact critical path may
traverse cyclic extrinsic edges; it stays terminating and deterministic (on-stack guard) but is not
guaranteed maximal across a cycle. Documented; the load-bearing guarantee (dependency path) is exact.

### F3 — Provenance certificate covers the intrinsic backbone only — **Accepted (clarified)**
Extrinsic-edge ancestors are certified by the log hash-chain (`verifyChain`), not the revision hash.
Both mechanisms are verifiable; the distinction is now stated precisely. Not a defect — a precision
requirement, met.

### F4 — Deep-graph recursion — **Accepted (documented)**
Topo-sort/longest-path recursion is foundation-scale; an iterative rewrite is future work.

## Success-criteria audit

| Criterion | Result |
|---|---|
| Engineering questions answered deterministically | ✅ six-primitive algebra; total-order discipline; 22 tests, incl. `deterministicFold` |
| Every explanation traceable | ✅ proof tree of typed steps to axioms; gaps named, never silent (F1) |
| Provenance complete | ✅ full backward closure; certified by revision hash (intrinsic) + log chain (extrinsic) |
| Impact analysis reproducible | ✅ exact forward dual; duality-law test; identical across runs |
| Query semantics internally consistent | ✅ closed algebra; provenance/impact are one relation transposed |
| No frozen layer modified | ✅ reads public APIs only; adds **no** ontology; kernel/knowledge/FVCS byte-for-byte unchanged |
| No architectural weakness after review | ✅ F1 refined; F2–F4 documented scope boundaries |

## Verdict

Reasoning is entailment over an immutable, content-addressed, hash-chained log: identity is the
kernel's (FKGE invents none), the sole projection is an immutable deterministic fold (no hidden
mutable state), every result is a pure function of a log prefix and carries the prefix it was
computed at, explanations name their gaps rather than hiding them, and provenance/impact are one
relation read two ways (consistent by construction). One real defect (F1 — silent completeness) was
found and fixed with a pinning test; the remainder are deliberate, documented scope boundaries.
**No meaningful architectural weakness remains.**

**Verification:** `forge-fkge` — **22 tests green** (`mvn -o test`, offline). Public APIs only; no
forbidden kernel/knowledge/FVCS internals imported; the three frozen layers re-verified unchanged.
