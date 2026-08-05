# FKGE Provenance Model

**Deliverable 4.** Provenance of a node *N* is its complete, causally-ordered derivation
history — *where it came from, through what, decided by whom, on what evidence* — **certified
by a content hash** so it is verifiable, not merely reconstructable.

## 1. Definition

> `provenance(N) =` the transitive UPSTREAM closure of *N* under
> `{composition, derivation, evidence, intent}`, bounded by causal order
> (`LogPosition(ancestor) ≤ LogPosition(N)`).

Each ancestor carries **who** (the `ActorId` of its authoring log entry) and **when** (its
`LogPosition` / valid-time), because every node is a kernel fact and every kernel fact is
attributed and bitemporal.

```
Provenance = (subject:     NodeId,
              ancestors:   [GraphNode],     // causally ordered (LogPosition, RevisionHash)
              edges:       [GraphEdge],     // the typed edges actually used
              certificate: RevisionHash,    // kernel closure hash of the subject revision
              asOf:        LogPosition)
```

## 2. Closure vs provenance vs dependency

| | Set | Follows | Answers |
|--|-----|---------|---------|
| **Kernel closure** | revisions *N* structurally rests on | kernel `closure(hash)` | *what* (reproducibility set) |
| **Dependency** | rebuild-required ancestors | composition ∪ derivation | *what must I have to run it* |
| **Provenance** | full justification set | + evidence ∪ intent | *what + how + why + who* |

`dependency(N) ⊆ provenance(N)`. Dependency is the reproduction-bearing subset; provenance adds
the *reasons* (evidence) and the *will* (intent).

## 3. The certificate (verifiability)

The kernel is content-addressed and hash-chained, so the **closure hash** of *N*'s revision is
a Merkle commitment to everything *N* structurally rests on. FKGE attaches it as
`certificate`:

- Recomputing `kernel.closure(N.hash)` and re-hashing must reproduce `certificate`. If it does
  not, the provenance is **stale or tampered** — a detectable, not silent, condition.
- Two nodes with the same certificate have **identical** structural provenance (content
  addressing ⇒ dedup), which is how lineage reconstruction proves "these two models were built
  from exactly the same inputs."

Provenance is therefore a **proof**, aligning with FVCS's reproducibility certificate and the
kernel's Law 7.

## 4. Construction (deterministic)

```
provenanceOf(N, asOf):
    reach ← closure(N, {composition,derivation,evidence,intent}, UPSTREAM, asOf)  # visited-set BFS
    ancestors ← reach.nodes \ {N}, sorted by (LogPosition, RevisionHash)
    assert every a in ancestors has LogPosition(a) ≤ LogPosition(N)   # causal soundness
    certificate ← kernel.closure(N.hash) folded to its root hash
    return Provenance(N, ancestors, reach.edges, certificate, asOf)
```

- **Causal soundness check**: an ancestor with a *later* LogPosition than *N* is impossible for
  intrinsic refs (a ref pins an already-existing revision) and, for extrinsic edges, indicates
  a data anomaly — reported, never hidden.
- **Determinism / termination**: as in the traversal engine (visited-set, total-order sort).

## 5. Lineage reconstruction

Lineage is provenance restricted to the **derivation** family (`derived_from`,
`fine_tuned_from`, `forked_from`, `supersedes`, `executed`, `generated_by`, `produced_by`),
yielding the ancestry chain of a model/dataset/prompt. Because derivation edges are intrinsic
and hash-pinned, the lineage is exact and reproducible.
