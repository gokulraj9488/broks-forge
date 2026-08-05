# Forge Version Control System (FVCS) — Phase 3

Version control for **AI-engineering knowledge** — prompts, agents, models, datasets, evaluations,
policies, whole systems, *and their relationships* — with provenance and reproducibility preserved.
Git versions files; FVCS versions engineering truth. Built entirely on the frozen Forge Kernel and
Forge Knowledge System through **public APIs only**; it modifies neither and adds its vocabulary through
the knowledge system's public `OntologyModule` SPI.

Produced concepts-before-code: the theory and architecture were made internally consistent and
adversarially reviewed before implementation.

## Deliverables

| # | Deliverable | Where |
|---|---|---|
| 1 | Version Control Theory | [THEORY.md](THEORY.md) |
| 2 | Canonical Vocabulary | [THEORY.md §15](THEORY.md) |
| 3 | Versioning Ontology | [VERSIONING-ONTOLOGY.md](VERSIONING-ONTOLOGY.md) |
| 4 | Conflict Taxonomy | [CONFLICT-MODEL.md §2](CONFLICT-MODEL.md) |
| 5 | Merge Strategy | [CONFLICT-MODEL.md §4](CONFLICT-MODEL.md) |
| 6 | Repository Architecture | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 7 | Public APIs | [ARCHITECTURE.md §4](ARCHITECTURE.md) + Javadoc in [`backend/forge-fvcs`](../../../backend/forge-fvcs/README.md) |
| 8 | Implementation | [`backend/forge-fvcs/`](../../../backend/forge-fvcs/README.md) |
| 9 | Automated Tests | `backend/forge-fvcs/src/test` (13 tests) |
| 10 | Architecture Review Report | [ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md) |
| 11 | Future Compatibility Strategy | this document, §Future Compatibility |

## The system on one screen

- **Version** = an immutable content-addressed state (`RevisionHash` / closure hash / snapshot hash).
- **Snapshot** = an `ArtifactPackage` pinning object revisions (the tree) — reused from the frozen
  ontology.
- **Branch** = a kernel `Name` (the only mutable state, CAS-advanced).
- **Commit** = a `Decision` that `records` a snapshot and names its `parent`(s) — an act of will,
  epistemically distinct from the snapshot it records.
- **History** = the commit DAG projected from the append log; deterministic, append-only, never rewritten.
- **Merge** = a commit with ≥2 parents, three-way against the LCA base; conflicts are **structural**
  (block), **semantic**, or **operational** (warn).
- **Reproducibility** = a snapshot's closure hash is the kernel's reproducibility certificate.

FVCS invents no storage: branches are Names, snapshots are packages, commits are Decisions, history is a
projection. It adds three types (`Commit`, `Tag`, `CompatibilityVerdict`) and three verbs
(`parent`, `records`, `marks`) — additively, via the public SPI.

## Future Compatibility Strategy

**Governance:** FVCS grows additively (new tag roles, commit metadata keys, lineage verbs) via registry
data and the SPI. It never modifies the kernel or knowledge system; a genuine deficiency would be a
**stop-and-file-an-amendment** event, never a silent extension.

**Deferred (documented, in priority order):**
1. Recursive merge for criss-cross history (multiple merge bases) and octopus (>2-way) merges.
2. A **semantic merge engine**: deep closure-consistency of a merged snapshot; divergent-claim
   reconciliation (`supersedes`); logical-name add/add detection.
3. A **runtime compatibility engine**: capability-claim and policy/cost-budget checks against a target
   Environment, beyond structural replaceability.
4. **Cherry-pick / revert** as applying a `ChangeSet` as a new commit; **rebase** as replaying commits
   onto a new base (new commits — history is never rewritten).
5. **Drift detection**: diff running observations against a pinned snapshot (declarative reconcile).
6. **Whole-graph snapshots** and scoped/partial snapshots with a staging (workspace) projection.

**Explicitly not built** (per mandate): UI, visualization, Forge Graph, applications. Reviews reuse the
frozen AI-PR decisions (`proposes`/`approves`/`rejects`).

## Status

Theory derived from first principles and adversarially reviewed
([ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md)) through eight version-control lenses; one determinism
defect found and fixed, the rest documented scope boundaries. Foundation implemented and green (**13
tests**), using only public APIs, with the kernel and knowledge system untouched.
