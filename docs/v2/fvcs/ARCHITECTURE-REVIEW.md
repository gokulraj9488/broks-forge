# FVCS Architecture Review

An independent committee experienced in **Git, Mercurial, Darcs, Perforce, Fossil, Kubernetes, database
MVCC, and distributed systems** attempts to reject the theory and implementation. Findings are graded
**Refined (fixed)**, **Accepted with rationale**, or **No-issue**; only genuine defects force a change.

## Per-lens attack

### Git / Fossil (snapshot DAG, immutable history)
- **branch=ref, commit=object, tree, three-way LCA merge, tags** — all derived, not assumed; the
  four-layer shape (Name → Commit Decision → ArtifactPackage → object Revision) matches Git's
  ref→commit→tree→blob **as a result**. ✓
- **Non-fast-forward safety:** advancing a branch is a kernel CAS repoint; a concurrent advance is a
  `CAS_FAILURE` — exactly a non-ff rejection. ✓
- **History rewrite (rebase/amend):** impossible by construction (append-only, hash-chained). A "rewrite"
  is new commits; the originals remain, attributed. Fossil-aligned. *Accepted* (a feature, not a gap).
- **Octopus merge (>2 parents):** `parent` is ANY-cardinality, so >2 parents are representable; the
  engine merges pairwise. Multi-way is documented future work. *Accepted.*

### Mercurial / Darcs (patch theory)
- FVCS is **snapshot-based**, not patch-commutation-based. This is a deliberate choice: content-addressed
  snapshots yield reproducibility certificates (closure hashes) and avoid patch-theory's conflict
  explosion; cherry-pick becomes "apply a ChangeSet as a new commit" (future). *Accepted with rationale.*

### Perforce (centralized, changelists, locking)
- **Changelist = ChangeSet** (the diff of two snapshots). **No pessimistic locking**: FVCS uses optimistic
  concurrency (CAS), like DVCS/MVCC. *No-issue.*
- **Monorepo scale:** a snapshot pins members explicitly, so a whole-system snapshot is a large
  ArtifactPackage; content-addressing dedups identical members, and *scoped* (partial) snapshots keep
  working sets small. A "snapshot the entire current graph" helper is future. *Accepted* (scale is
  bounded by object count, and members dedup).

### Kubernetes (declarative desired state)
- A **Snapshot is a desired-state manifest** (like a lockfile / K8s manifest); **promotion** re-points a
  higher-tier line at a validated snapshot; **drift** = diff(running observations, pinned snapshot)
  (future). Strong fit. *No-issue.*

### Database MVCC / Distributed systems
- The append log **is** an MVCC store: snapshots are consistent reads (`resolveAt` = snapshot isolation),
  branches are named versions, **CAS prevents lost updates / write-skew** on branch advance. *No-issue.*
- **Content-addressing + hash-chaining** = a Merkle/Certificate-Transparency structure; replication =
  log shipping (kernel export/replay). Cross-org merge = export/import of subgraphs (per-org boundary).
  Causal order = `LogPosition` (per-org total order). *No-issue* (cross-org is a kernel-boundary concern,
  not an FVCS flaw).

## Findings

### F1 — Non-deterministic merged-snapshot hash — **Refined (fixed)**
The determinism lens found it: the merged snapshot was built from `Map.copyOf(mergedMembers)`, whose
iteration order is unspecified; since kernel reference order is significant, the merged `ArtifactPackage`
hash would vary across runs — breaking reproducibility. **Fixed:** merged members are now ordered by
content hash before the snapshot is built. A cross-run test (`mergeDeterministic`) asserts the same
logical merge yields the same snapshot hash on two independent kernels.

### F2 — Merge is member-level, not deep-closure-consistent — **Accepted with rationale**
Merging an edited Prompt with an unedited Agent that references the old prompt yields a snapshot whose
Agent internally pins stale parts — a *semantic* concern. This is inherent to snapshot merge (Git has the
identical property at file level: independently-merged files can be jointly broken). FVCS's answer is the
**semantic/operational warning layer** (Conflict Model §2): the data merges structurally, and re-eval /
compatibility must pass before promotion. Deep closure-consistency validation is a documented semantic
engine (future). Not a correctness defect in member-level merge, which is well-defined.

### F3 — Add/add at logical-name level not flagged — **Accepted (documented)**
Continuant ids are globally unique (minted per creation), so add/add of the *same* continuant cannot
occur; two independently-added objects merge as a union. A *logical-name* collision (two prompts both
role `system`) is therefore not a structural conflict — it is a **semantic** one, detectable by naming
convention (future). Documented in the Conflict Model. *No structural-correctness impact.*

### F4 — Criss-cross history (multiple merge bases) — **Accepted (detected, not guessed)**
Detected and reported as a `CRISS_CROSS` conflict; a recursive/virtual-base merge is documented future
work. FVCS never guesses a base — the safe behavior. *No-issue.*

### F5 — Compatibility engine is structural only — **Accepted (documented)**
Structural replaceability ships; capability-claim and runtime-budget (policy) compatibility are a
documented extension. The result is recorded as an evidenced `CompatibilityVerdict` Claim, never a naked
flag. *Scope boundary, not a flaw.*

## Success-criteria audit

| Criterion | Result |
|---|---|
| Version semantics derived from first principles | ✅ THEORY §1–16 (each concept from kernel/knowledge primitives) |
| Versioned without modifying kernel or ontology | ✅ additive via public SPI; kernel & knowledge system byte-for-byte unchanged; public APIs only |
| Reproducibility preserved | ✅ snapshot closure hash = certificate; `checkout`→`reproduce`; F1 fixed |
| Provenance preserved | ✅ every commit/tag/snapshot is a kernel fact (actor + bitemporal + chain) |
| History deterministic | ✅ content-addressed + position-ordered projection; append-only; `mergeDeterministic` test |
| Merge behavior well-defined | ✅ three-way over the base; every case deterministic or an explicit conflict |
| Conflict semantics explicit | ✅ structural (block) / semantic / operational (warn), classified |
| No architectural weakness after review | ✅ F1 refined; F2–F5 are documented scope boundaries, not defects |

## Verdict

The version semantics are sound: identity is the kernel's (no FVCS-invented identity), history is
append-only and hash-chained (no corruption possible), the only mutable state is branch Names moved by
CAS (no hidden mutable state), merges are deterministic three-way with explicit conflict levels, and
reproducibility is a first-class kernel operation. One determinism defect (F1) was found and fixed; the
remaining findings are deliberate, documented scope boundaries of the foundation. **No meaningful
architectural weakness remains.**

**Verification:** `forge-fvcs` — **13 tests green** (`mvn -o clean test`, offline). Kernel and knowledge
system re-verified unchanged; public APIs only.
