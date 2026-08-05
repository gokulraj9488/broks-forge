# FVCS Versioning Ontology

FVCS versions engineering knowledge without changing the frozen kernel or knowledge system. It adds a
small, additive vocabulary through the **public `OntologyModule` SPI** — the mechanism the Knowledge
Governance explicitly sanctioned for new types ("New object type / verb → add a registry entry, or ship
an OntologyModule (SPI). Never a framework or kernel change."). The frozen `Ontologies.forge()` and the
`forge-knowledge` code are **untouched**; FVCS *composes* a new ontology:

```java
Ontology.Builder b = Ontology.builder();
Ontologies.canonicalModule().contribute(b);   // the frozen base, unchanged
FvcsOntology.module().contribute(b);           // FVCS additions
Ontology fvcs = b.build();                      // base ⊕ FVCS
```

This is additive composition, not modification — and it is explicit, not silent.

## New object types (3)

| Type | Kind | Subtype | Why a new type is justified |
|---|---|---|---|
| **Commit** | Decision | `commit` | An act of will to checkpoint. Epistemic typing forbids conflating a commit (will) with a Snapshot (designed content, Artifact) — the same principle the ontology rests on. |
| **Tag** | Decision | `tag` | The act of immovably naming/blessing a commit is a will. Roles `lightweight` / `release` / `baseline` (KN-0002) fold Release and Baseline in — no extra types. |
| **CompatibilityVerdict** | Claim | `compatibility-verdict` | "Snapshot/object B may replace A" is an evidenced belief; a naked compatibility flag would violate Law 5. |

Payload schemas:
- `Commit`: `message` (string, required), `branch` (string), `snapshot` (string, the package hash),
  `judgment-call` (bool). A plain commit sets `judgment-call: true` (an authored checkpoint); a validated
  commit instead `rests_on` evaluation verdicts.
- `Tag`: `name` (string, required), `role` (string ∈ {lightweight, release, baseline}), `message`.
- `CompatibilityVerdict`: the Claim triple `statement` / `method` / `confidence` (required, inherited),
  plus `from` / `to` (the compared snapshot hashes).

**Snapshot is not a new type** — it is the frozen `ArtifactPackage` (`includes` ≥1 Artifact revision;
its hash is the tree identity). A commit `records` exactly one snapshot.

## New relationship types (3)

| Verb | Family | From → To | Card. | Intrinsic | Meaning |
|---|---|---|---|---|---|
| `parent` | Derivation | Commit → Commit | `*` | yes | the commit DAG (a merge has ≥2) |
| `records` | Composition | Commit → ArtifactPackage | `!` | yes | commit → the snapshot (tree) it checkpoints |
| `marks` | Intent | Tag → Commit | `!` | yes | tag → the commit it names |

All three are intrinsic refs, so a commit/tag's identity is content-addressed over exactly what it points
at (Merkle), and the target must pre-exist (kernel `MISSING_REFERENCE`) — which is why the commit DAG and
snapshot trees are **acyclic by construction**. `CompatibilityVerdict` reuses the existing `cites`
(evidence) relation for its supporting runs; no new relation is needed for it.

## What is reused unchanged (no additions)

- **Snapshot** = `ArtifactPackage` + `includes` (frozen).
- **Rollback / Promotion / Approval / Deployment** = frozen Decision subtypes; `proposes` / `approves` /
  `rejects` (the AI Pull Request) = frozen Intent relations — this is the **Review** mechanism, unchanged.
- **Lineage** = frozen `derived_from` / `supersedes` / `fine_tuned_from` / `produced_by` plus the new
  `parent`.
- **Environments, Policies, Guardrails, Evaluations, Runs, Verdicts** — versioned like any object; FVCS
  adds no per-object types.

## Snapshot model (the tree)

A Snapshot pins the exact revision of every object in scope:

```
Commit(commit) ──records──▶ ArtifactPackage(snapshot)
                                 │ includes (≥1, intrinsic, hash-pinned)
                                 ├─▶ Prompt@rev
                                 ├─▶ Agent@rev
                                 ├─▶ Model@rev
                                 ├─▶ Dataset@rev
                                 └─▶ Policy@rev …
Commit ──parent──▶ Commit(parent) ──parent──▶ …        (the DAG)
branch/<line>  ─(kernel Name, CAS)─▶  Commit(head)
```

Because `includes` targets `ARTIFACT` (frozen), snapshots pin **artifacts** — prompts, agents, models,
datasets, evaluations-as-definitions, policies, guardrails, knowledge-bases, workflows, packages — i.e.
exactly the *configuration* you version. Observations (Runs), Claims (Verdicts), and Decisions (Commits)
are **history**, reachable from the commit and its evidence, not pinned as versioned config. This cleanly
separates "the versioned system" (artifacts) from "what happened to it" (observations/claims/decisions).

## Consistency & validation

The composed ontology passes the same `Ontology.builder().build()` self-consistency check as the frozen
one (unique names/subtypes; every relation endpoint resolves; each verb has one family). The
`KnowledgeValidator` then enforces, in userspace, the FVCS invariants before any append:
`records` EXACTLY_ONE, `marks` EXACTLY_ONE, `parent` ANY, and the inherited Claim/Decision laws for
`CompatibilityVerdict`/`Commit`/`Tag`. FVCS itself adds two cross-object invariants
(FV-1: a Commit's snapshot must be an ArtifactPackage; FV-2: a merge commit has ≥2 distinct parents),
checked by the FVCS engines.

## Evolution

New tag roles, new commit metadata keys, and additional lineage verbs remain additive (payload keys and
registry entries). No FVCS growth requires a kernel or knowledge-system change; if one ever did, the rule
is to **stop and file an amendment proposal**, never to extend a frozen foundation silently.
