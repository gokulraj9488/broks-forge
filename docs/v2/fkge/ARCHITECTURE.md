# FKGE Architecture

**Deliverable 7 (Public APIs) + the engine decomposition.** FKGE is a pure, read-only
projection layer over the frozen kernel, knowledge system, and FVCS. It consumes **only their
public APIs** and stores nothing.

## 1. Layering

```
            ┌──────────────────────── KnowledgeGraphEngine (Query Engine / façade) ────────────────────────┐
            │  provenanceOf · dependenciesOf · impactOf · explain · rootCause · evidenceFor · confidenceOf  │
            │  neighborhood · influence · criticalPath · similarTo · trace · reachable · whatChanged · … │
            └───────┬───────────┬───────────┬───────────┬───────────┬───────────┬───────────┬────────────┘
      Provenance  Dependency  Impact   Explanation   Reasoning    Search    Projection   (engines)
            └───────┴───────────┴───────────┴─────┬─────┴───────────┴───────────┴────────────┘
                                         Traversal Engine  (neighbors · closure · path)
                                                   │
                                             GraphIndex  (deterministic fold of the log)
                                                   │
      ┌────────────────────────────────────────── public APIs only ──────────────────────────────────────┐
      │  FVCS Repository (kernel, org, ontology, knowledge, history, diff)                                  │
      │  Forge Kernel (log, traverse, closure, resolveAt, revision)   Forge Knowledge (view, ontology)     │
      └────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Dependency direction is strictly downward. Nothing FKGE does is visible to the layers below it;
applications (Phase 5) will depend on FKGE, never the reverse.

## 2. GraphIndex — the one projection

`GraphIndex` folds `kernel.log(org)` **once** into an in-memory, immutable, typed adjacency
structure. It is a *deterministic function of the log* — the reproducibility theorem's index
(THEORY §2): fully discardable, no hidden mutable state.

- `of(repo)` / `of(kernel, org, ontology)` — latest state.
- `asOf(…, LogPosition)` — folds only entries at or before the position (time travel).

For each `LogEntry`, in position order:
- `NodePut(node, revision)` → the node's latest `GraphNode` (Kind, subtype, `ObjectType` via
  `ontology.resolve`, `RevisionHash`, `LogPosition`, authoring `ActorId`, payload); and every
  revision hash is mapped → its `NodeId` (so intrinsic `Ref`s resolve to continuants).
- Intrinsic edges from the latest `revision.refs()` (`Ref` → `GraphEdge`, resolving target hash
  → NodeId).
- `EdgeAsserted` / `EdgeRetracted` → live extrinsic edges (asserted minus retracted, in order).

Reads: `node(id)`, `nodes()`, `nodesOfType`, `nodesOfKind`, `out(id)`, `in(id)`,
`resolve(hash)`, all returning total-ordered, immutable views.

## 3. Engines

| Engine | Responsibility | Consumes |
|--------|----------------|----------|
| **TraversalEngine** | `neighbors`, `closure`, `path` — the three atoms | GraphIndex |
| **ProvenanceEngine** | UPSTREAM closure {comp,deriv,evid,intent} + closure-hash certificate | Traversal, `kernel.closure` |
| **DependencyEngine** | UPSTREAM closure {comp,deriv}, topo order, critical path | Traversal |
| **ImpactEngine** | DOWNSTREAM dual, blast radius, influence rank | Traversal |
| **ExplanationEngine** | proof-tree walk, leaf classification, completeness/gaps | Traversal |
| **ReasoningEngine** | confidence (`min`) + causal trace (LogPosition-checked) | Traversal, payloads |
| **SearchEngine** | neighborhood-signature similarity, pattern detection | GraphIndex |
| **ProjectionEngine** | deterministic summaries of nodes/closures | GraphIndex |

All engines are stateless over the immutable `GraphIndex`; each result carries its `asOf`.

## 4. Public API (deliverable 7)

Package `com.broksforge.fkge`. The façade and result types are the public surface:

```java
// Façade — the Query Engine
final class KnowledgeGraphEngine {
    static KnowledgeGraphEngine open(Repository repo);
    static KnowledgeGraphEngine open(ForgeKernel kernel, OrgId org, Ontology ontology);
    static KnowledgeGraphEngine open(Repository repo, LensModule... extensions);

    Provenance     provenanceOf(NodeId n);
    DependencySet  dependenciesOf(NodeId n);
    Impact         impactOf(NodeId n);              // == blastRadius
    Explanation    explain(NodeId n);
    Explanation    whyApproved(NodeId decision);
    List<GraphNode> evidenceFor(NodeId claim);
    CausalTrace    rootCause(NodeId incident);
    ConfidenceResult confidenceOf(NodeId n);
    Neighborhood   neighborhood(NodeId n, int depth);
    Influence      influence(NodeId n);
    List<GraphNode> criticalPath(NodeId n);
    List<GraphNode> similarTo(NodeId n);
    Optional<Path> trace(NodeId from, NodeId to, Lens lens);
    boolean        reachable(NodeId from, NodeId to, Lens lens);
    List<GraphNode> similarTo(NodeId n);
    List<Pattern>  patterns(int minCount);
    Summary        summarize(NodeId n);
    ChangeSet      whatChanged(CommitRef a, CommitRef b);  // delegates to FVCS diff
    KnowledgeGraphEngine asOf(LogPosition p);              // re-bind the engine to an earlier prefix

    GraphIndex index();                             // escape hatch for custom traversal
    LensRegistry lenses();
}
```

Result records (`Provenance`, `DependencySet`, `Impact`, `Explanation`, `CausalTrace`,
`ConfidenceResult`, `Neighborhood`, `Influence`, `Path`, `Summary`, `GraphNode`, `GraphEdge`)
are immutable and each carries the `asOf` `LogPosition` where a question was answered — a
**citable, re-verifiable answer** (THEORY §2).

## 5. Extension SPI

```java
@FunctionalInterface interface LensModule { void contribute(LensRegistry registry); }
```

New engineering questions that reduce to a family-set + direction are added as `Lens` values
through a `LensModule`, composed at `open(...)` — mirroring the knowledge system's
`OntologyModule`. The engine core is never modified to add a lens-expressible capability. This
is the sanctioned, explicit extension path; a capability that *cannot* be expressed as a lens or
built from the public primitives would be a foundation gap to file as an amendment, not to hack
in.

## 6. What is deliberately absent

No storage, no write path, no cache that changes results, no UI/visualization/graph-editor, no
AI assistant, no applications. FKGE is exactly one thing: **deterministic graph reasoning over
the existing graph.**
