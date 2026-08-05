# FKGE Dependency & Impact Model

**Deliverable 5.** Dependency is the reproduction-bearing subset of provenance; impact is its
forward dual. Both are exact transitive closures — computed, never estimated.

## 1. Dependency

> `dependency(N) =` the transitive UPSTREAM closure of *N* under **composition ∪ derivation
> only** — the families that determine whether *N* can be **reproduced**.

Evidence and intent are excluded: they *justify* *N* but are not required to *rebuild* it.
Hence `dependency(N) ⊆ provenance(N)`. This separation answers "what must I have to run this"
distinctly from "why do I trust this."

```
DependencySet = (subject:      NodeId,
                 nodes:        [GraphNode],   // topologically ordered (deepest deps first)
                 edges:        [GraphEdge],
                 criticalPath: [GraphNode],   // the longest dependency chain
                 asOf:         LogPosition)
```

**Topological order** is well-defined: composition and derivation edges are intrinsic and
hash-pinned, and a hash-pinned ref can only target an *already-existing* revision, so the
intrinsic-dependency subgraph is acyclic by construction (a cycle would require a revision to
contain its own future hash). The order is stabilized by `(LogPosition, RevisionHash)`.

**Critical path** = the longest chain from *N* to a leaf dependency, found by a deterministic
longest-path over the DAG (memoized DFS). It is the dependency most work rests on — the first
thing to harden.

## 2. Impact (blast radius)

> `impact(N) =` the transitive DOWNSTREAM closure of *N* under
> `{composition, derivation, evidence, intent}` — everything that has *N* in its provenance.

By the **duality law**, `impact` and `provenance` are the same relation transposed:
`X ∈ impact(N) ⟺ N ∈ provenance(X)`. FKGE computes both with one traversal engine, so they can
never disagree.

```
Impact = (subject:      NodeId,
          dependents:   [GraphNode],   // the blast radius, ordered
          criticalPath: [GraphNode],   // longest downstream chain
          byKind:       {Kind → count},// how many artifacts/claims/decisions/observations affected
          asOf:         LogPosition)
```

**Blast-radius analysis:** "if this dataset is corrupt, what is affected?" =
`impact(dataset)` — every model indexing it, every agent using those models, every evaluation
over them, every deployment resting on those evaluations, every commit recording those
artifacts. Because deployments (`Deployment`) and commits are downstream Decision/Artifact
nodes, the blast radius naturally reaches *shipped systems*.

## 3. Influence (structural rank)

Influence ranks nodes by **how load-bearing** they are — deterministically, without any
probabilistic centrality:

> `influence(N) = |impact(N)|` (count of distinct downstream dependents), with ties broken by
> **critical-path membership count** (how many other nodes' critical paths pass through *N*),
> then by `(LogPosition, RevisionHash)`.

This is a closed-form function of the graph — no damping factor, no iteration to a fixed point,
no eigenvectors. PageRank-style centrality is **rejected**: its damping constant is a heuristic
and its value is an approximation, both forbidden by the determinism mandate.

## 4. Why exact, not estimated

Edges have direction and fixed meaning, and the graph is finite, so reachability is decidable
and the closures are exact sets. "X is impacted by N" is a *proof of a path*, never a
probability. Dependency and impact are thus **reproducible**: same log prefix ⇒ identical sets,
identical critical paths, identical influence ranking.
