# FKGE Explanation Model

**Deliverable 3.** An explanation is a **proof tree**: a finite, acyclic, ordered walk from a
node back to the facts that justify it. Its axioms are primary Observations, primary Artifacts,
and explicit judgment-calls. If a walk cannot reach an axiom, the explanation **names the gap**
— it never falls silent.

## 1. Structure

```
Explanation = (root:      NodeId,
               steps:     [ExplanationStep],   // ordered, acyclic
               leaves:    [LeafClassification],
               complete:  boolean,             // every leaf is a proper axiom
               gaps:      [String],            // named, if incomplete
               asOf:      LogPosition)

ExplanationStep = (from: NodeId, verb: Verb, family: EdgeFamily, to: NodeId, depth: int)

LeafClassification = one of {
   OBSERVATION       // reality recorded — a primary fact (axiom)
   PRIMARY_ARTIFACT  // an Artifact with no further derivation/composition (axiom)
   JUDGMENT_CALL     // a Decision that cites no prior claim (axiom: "a human decided")
   FRONTIER          // a node that should be grounded but its walk ended unresolved (GAP)
}
```

## 2. Which families license "because"

The lens is chosen by the *kind of why*:

| Question about | Families followed (UPSTREAM) |
|----------------|------------------------------|
| existence / shape | composition ∪ derivation |
| a belief (Claim) | evidence |
| a decision (Decision) | intent (→ cited Claims), then evidence |
| an event (Observation/Incident) | causality (IN, toward the cause) |

A `whyApproved(decision)` explanation follows `intent` from the Decision to the Claims it
`rests_on`, then `evidence` from each Claim to its Observations — bottoming out at recorded
runs/feedback (axioms) or at a `judgment-call: true` Decision (also an axiom).

## 3. Construction (deterministic)

```
explain(root, lens, asOf):
    visited ← ∅ ;  steps ← [] ;  leaves ← []
    stack ← [(root, depth 0)]                 # DFS; BFS is equivalent for the tree set
    while stack not empty:
        (n, d) ← pop
        if n ∈ visited: continue              # acyclicity + termination
        visited ← visited ∪ {n}
        out ← edges(n, lens.families, UPSTREAM) sorted by (LogPosition, RevisionHash)
        if out is empty:
            leaves ← leaves ∪ classifyLeaf(n)
        else:
            for e in out: steps ← steps ∪ step(e, d);  push (e.to, d+1)
    complete ← every leaf ∈ {OBSERVATION, PRIMARY_ARTIFACT, JUDGMENT_CALL}
    gaps     ← [describe(n) for FRONTIER leaves]
    return Explanation(root, steps, leaves, complete, gaps, asOf)
```

- **Termination**: finite graph + visited-set.
- **Determinism**: neighbor expansion is sorted by the kernel's total order; the step list is
  therefore identical across runs.
- **Completeness**: computed, not assumed. A `FRONTIER` leaf (e.g. a Claim whose evidence walk
  ended without reaching an Observation) is reported in `gaps`, so an explanation is **never
  silently incomplete** — directly answering the "incomplete explanations" adversarial attack.

## 4. Leaf classification

- `Kind.OBSERVATION` → `OBSERVATION` (axiom).
- `Kind.ARTIFACT` with no outgoing composition/derivation edge → `PRIMARY_ARTIFACT` (axiom).
- `Kind.DECISION` with `judgment-call: true` in payload, or no `rests_on`/`cites` edge →
  `JUDGMENT_CALL` (axiom — the terminal "because a human decided").
- `Kind.CLAIM` reached as a leaf but with no evidence edge → `FRONTIER` (gap: an ungrounded
  belief; by Law 5 this should not occur for law-abiding data, so it is a data-quality signal).

## 5. Why this is honest

Because the kernel now **enforces Laws 5 & 6 at append** (every Claim has evidence; every
Decision cites claims or is a judgment-call), a law-abiding graph yields **complete**
explanations by construction. When an explanation is *incomplete*, it is evidence of a data
defect, and the model surfaces exactly where — it does not paper over it.
