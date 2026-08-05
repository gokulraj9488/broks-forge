# FVCS Architecture

The Forge Version Control System is a library over the frozen Forge Knowledge System (which is itself
over the frozen kernel). It consumes **only public APIs** — `com.broksforge.knowledge.*` and the public
kernel packages — and adds its versioning vocabulary through the knowledge system's `OntologyModule` SPI.
It stores nothing of its own: branches are kernel Names, snapshots are ArtifactPackages, commits are
Decisions, history is a projection.

## 1. Module boundary & dependencies

```
forge-fvcs ──▶ forge-knowledge ──▶ kernel-core ──▶ kernel-api      (public APIs only)
```

Standalone Maven module (no parent, not in any reactor). Depends on `forge-knowledge` 1.0.0 (transitively
the kernel). No Spring, no ORM, no AI. Applications (Phase 5) depend on FVCS; FVCS never depends upward.

## 2. Package structure

```
com.broksforge.fvcs
├── ontology/     FVCS additive vocabulary (via the public SPI — no foundation change)
│   ├── FvcsTypes        Commit, Tag, CompatibilityVerdict (ObjectType constants)
│   ├── FvcsVerbs        parent, records, marks
│   └── FvcsOntology     OntologyModule + composed() = Ontologies.forge ⊕ FVCS
├── repo/         the repository abstraction (the public surface)
│   ├── Repository       open/branch/commit/checkout/history/diff/merge/tag/compat
│   ├── Branch           a line of development (wraps a kernel Name)
│   ├── SnapshotRef      a snapshot (wraps an ArtifactPackage KnowledgeObject)
│   ├── CommitRef        a commit (wraps a Commit KnowledgeObject) + parents/message/snapshot
│   └── TagRef           a tag (wraps a Tag KnowledgeObject + its Name)
├── history/      the version graph
│   ├── CommitNode       projected commit: hash, parents, snapshot, message, author, position
│   ├── HistoryEngine    fold the log → commit DAG + branch heads; ancestors; LCA (merge base)
│   └── MergeBase        result of base finding (single | criss-cross | none)
├── diff/         the diff engine
│   ├── ChangeKind       ADDED | REMOVED | CHANGED | UNCHANGED
│   ├── ObjectChange     per-continuant change (type, old→new revision)
│   └── DiffEngine       three-way and two-way snapshot diff → ChangeSet
├── merge/        the merge engine
│   ├── ConflictLevel    STRUCTURAL | SEMANTIC | OPERATIONAL
│   ├── ConflictKind     MODIFY_MODIFY | MODIFY_REMOVE | ADD_ADD | CRISS_CROSS | …
│   ├── Conflict         one detected conflict (level, kind, object, sides)
│   ├── Resolution       take-ours | take-theirs | a supplied merged revision
│   ├── MergeResult      merged commit (or) conflicts + semantic/operational warnings
│   └── MergeEngine      three-way merge per the Conflict Model
└── compat/       the compatibility engine
    ├── CompatibilityResult   structural compatibility findings
    └── CompatibilityEngine   snapshot/object replaceability → CompatibilityVerdict
```

Dependency graph (acyclic): `repo → {history, diff, merge, compat} → ontology → forge-knowledge`;
`merge → diff, history`; `compat → diff`.

## 3. Repository abstraction

A `Repository` is opened per organization over a `ForgeKernel` and an `ActorId`; it builds the composed
ontology once and wraps a `KnowledgeGraph`. It exposes versioning verbs and nothing else — no persistence,
no mutable fields beyond what the kernel holds. All writes go through `KnowledgeGraph.define`/`relate` +
kernel name repointing, so every FVCS write is validated against the ontology and recorded as a
provenance-stamped, hash-chained fact.

## 4. Public API (surface)

```java
Repository repo = Repository.open(kernel, org, actor);

// snapshots & commits
SnapshotRef snap = repo.snapshot("system-v1", List.of(agentObj, promptObj, modelObj, policyObj));
Branch main      = repo.branch("main");                       // branch/main
CommitRef c1     = repo.commit(main, snap, "initial system"); // Decision + CAS advance

// branching & history
Branch exp       = repo.branchFrom("exp/cheaper-model", c1);
List<CommitNode> hist = repo.history(main);                   // commit DAG, newest first
Optional<CommitRef> head = repo.head(main);

// time travel & checkout (deterministic)
SnapshotRef atC1 = repo.checkout(c1);                         // → the pinned snapshot
Optional<SnapshotRef> past = repo.checkoutAt(main, position); // resolveAt

// diff & merge
ChangeSet cs     = repo.diff(c1, c2);                         // per-object added/removed/changed
MergeResult mr   = repo.merge(main, exp, "merge cheaper model");
if (!mr.clean()) mr.conflicts().forEach(...);                // structural block; else warnings

// tags / releases / baselines, compatibility
TagRef rel       = repo.tag("release/1.0.0", c2, TagRole.RELEASE, "GA");
CompatibilityResult compat = repo.checkCompatibility(snapA, snapB);
```

## 5. Engines

- **HistoryEngine** — folds `kernel.log(org)` once: every `Commit` node with its `parent` edges becomes a
  `CommitNode`; branch/tag Names are heads. Computes ancestors (position-ordered) and the LCA merge base.
  Pure projection; rebuildable; deterministic.
- **DiffEngine** — snapshot diff: set-diff the two ArtifactPackages' `includes` members by continuant,
  then kernel `diff` on changed objects → `ChangeSet` of `ObjectChange`s. Also the three-way variant used
  by merge.
- **MergeEngine** — merge base (HistoryEngine) → three-way change table (Conflict Model §1) → structural
  conflicts (block) or an auto-merged snapshot → semantic + operational scans (warn) → a merge `Commit`
  with ≥2 parents, CAS-advancing the target branch.
- **CompatibilityEngine** — structural replaceability of B for A (object types/interfaces present;
  required composition satisfied), emitting a `CompatibilityVerdict` Claim (evidenced). Semantic/runtime
  compatibility (capability claims, policy budgets) is a documented extension.
- **ReviewEngine** — *not a new class*: reviews are the frozen AI-PR triad (`proposes`/`approves`/
  `rejects` Decisions) driven directly on the `KnowledgeGraph`; FVCS documents the flow rather than
  wrapping it.

## 6. Storage, projection, determinism

- **Storage:** none of its own. Branches/tags = Names (mutable, CAS). Snapshots = ArtifactPackages
  (immutable, content-addressed). Commits/tags/verdicts = knowledge nodes (immutable). Durability,
  tenancy, provenance, audit = the kernel's (in-memory or PostgreSQL, unchanged).
- **Projection:** the commit DAG and branch heads are folded from the log; nothing is cached mutably.
- **Determinism:** all inputs are content hashes; the merge base is position-ordered; `resolveAt` gives
  deterministic historical checkout. History is append-only and hash-chained — never rewritten.

## 7. Scope of the foundation

Implemented: the FVCS ontology module, Repository, snapshots, commits, branches, tags, history + merge
base, diff/ChangeSet, the three-level merge engine with conflict classification, and a structural
compatibility engine. **Not built** (per mandate): UI, visualization, Forge Graph, applications. Reviews
reuse the frozen AI-PR decisions; recursive merge for criss-cross history and semantic/operational
auto-checks beyond structural are documented future work.
