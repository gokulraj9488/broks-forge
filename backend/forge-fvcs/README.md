# Forge Version Control System (FVCS)

Version control for AI-engineering knowledge, over the frozen Forge Kernel and Forge Knowledge System.
A standalone Maven library that consumes **only public APIs** (`forge-knowledge` 1.0.0 and, transitively,
the kernel) and adds its versioning vocabulary through the knowledge system's public `OntologyModule`
SPI — modifying neither foundation.

Design documents: [`../../docs/v2/fvcs/`](../../docs/v2/fvcs/README.md) — theory, versioning ontology,
conflict model + merge strategy, architecture, and the architecture review.

## What it provides

- **Repository** (`repo/`) — the version-control surface: `snapshot`, `commit`, `branch`/`branchFrom`,
  `head`, `checkout`/`checkoutAt`, `history`, `diff`, `merge`, `tag`, `checkCompatibility`.
- **History engine** (`history/`) — the commit DAG projected from the log; ancestors and LCA merge base.
- **Diff engine** (`diff/`) — per-continuant change sets between snapshots.
- **Merge engine** (`merge/`) — deterministic three-way merge with structural conflict classification.
- **Compatibility engine** (`compat/`) — structural replaceability → an evidenced `CompatibilityVerdict`.
- **FVCS ontology** (`ontology/`) — `Commit`, `Tag`, `CompatibilityVerdict` + `parent`/`records`/`marks`,
  composed onto the frozen ontology via the SPI.

## Build

```bash
# Prereqs: kernel and forge-knowledge installed to the local repo (mvn -o install in each).
cd backend/forge-fvcs
mvn -o test        # compile + 13 tests, offline
```

## Example

```java
Repository repo = Repository.open(kernel, org, actor);
KnowledgeGraph kg = repo.knowledge();

KnowledgeObject provider = kg.define(ObjectTypes.PROVIDER, obj("name","anthropic"));
KnowledgeObject model    = kg.define(ObjectTypes.MODEL, obj("model_id","sonnet-5"), Link.of(Verbs.USES, provider));
KnowledgeObject prompt   = kg.define(ObjectTypes.PROMPT, obj("text","v1"));

Branch main = repo.branch("main");
CommitRef c1 = repo.commit(main, repo.snapshot("s1", List.of(provider, model, prompt)), "initial");

Branch exp = repo.branchFrom("exp/cheaper", c1);
// ...diverge on each branch, then:
MergeResult mr = repo.merge(main, exp, "merge cheaper model");
if (!mr.clean()) mr.conflicts().forEach(System.out::println);   // structural conflicts block

repo.tag("release/1.0.0", repo.head(main).orElseThrow(), TagRole.RELEASE, "GA");
SnapshotRef past = repo.checkoutAt(main, position).orElseThrow(); // deterministic time travel
```

## Boundaries

Versioning and history only. **No** UI, visualization, Forge Graph, or applications (later phases).
Depends on the knowledge system; applications depend on FVCS, never the reverse.
