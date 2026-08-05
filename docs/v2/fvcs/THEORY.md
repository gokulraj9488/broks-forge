# Forge Version Control — Theory (derived from first principles)

Git versions **files**. FVCS versions **engineering truth** — prompts, agents, models, datasets,
evaluations, policies, whole AI systems, *and the relationships between them* — with provenance and
reproducibility preserved. This document derives every version-control concept from the frozen Forge
Kernel and Forge Knowledge System. **We do not assume Git semantics**; where a derived concept happens
to match Git, we note the convergence — it is a result, not an input.

> Built on frozen foundations, public APIs only. Companion docs: [Versioning Ontology](VERSIONING-ONTOLOGY.md),
> [Conflict Model](CONFLICT-MODEL.md), [Architecture](ARCHITECTURE.md), [Review](ARCHITECTURE-REVIEW.md).

---

## 0. What we already have (the primitives we derive from)

- **Content addressing** — a `Revision` is an immutable, hash-identified state of a continuant; equal
  content deduplicates (kernel Law 3).
- **The continuant** — a `NodeId` is the stable identity a chain of revisions shares ("which thing?"),
  distinct from `RevisionHash` ("which state?").
- **The append log** — a per-org, hash-chained, bitemporal, provenance-total sequence of immutable
  facts; the sole source of truth (Law 1, 2, 8). Every fact carries actor, valid time, record time,
  position, and a chain hash.
- **Names** — the *only* mutable state; a name is repointed by compare-and-swap (ADR-V2-0006).
- **Closure** — `closure(rev)` is the composition-closed set of revisions rooted at `rev`; its root hash
  is the **reproducibility certificate** (Law 7).
- **Deterministic time travel** — `resolveAt(name, position)` yields the exact revision a name pointed at
  as of any past log position.
- **The knowledge ontology** — objects are kernel nodes (Artifact/Observation/Claim/Decision);
  **ArtifactPackage** is "a named bundle pinning a set of artifact revisions by hash — a curated
  closure"; **Deployment/Promotion/Rollback/Approval** are Decisions; lineage verbs (`derived_from`,
  `supersedes`, `produced_by`) already exist.

Everything below is a *composition* of these. FVCS adds engines and a thin, additive vocabulary — no
kernel change, no modification to the knowledge system.

## 1. What is a version?

A version is an **immutable, content-addressed state**. The kernel already has two granularities, and
FVCS adds a third by composition:

| Scope | Version = | Identity |
|---|---|---|
| One object | a `Revision` | `RevisionHash` |
| An object + its parts | a **closure** | closure root hash (reproducibility certificate) |
| A whole system | a **Snapshot** = an `ArtifactPackage` pinning many objects' revisions | the package `RevisionHash` |

A "version" is therefore never invented — it is a hash. The system-level version (Snapshot) is the new
composition: a manifest that pins *the exact revision of every object in scope*, the way a lockfile pins
a dependency set. Because it is content-addressed, two identical system states are the same version.

## 2. What constitutes identity?

FVCS invents **no** identity. It reuses the kernel's:
- **Which thing** → `NodeId` (the versioned continuant).
- **Which state** → `RevisionHash` (a version of one object).
- **Which system-state** → the Snapshot's `RevisionHash` (a tree identity).
- **Which fact/act** → the `LogEntry` chain hash (a commit *as an event*, with author+time).
- **Which line of development** → a `Name` (a branch/tag).

Identity is thus content-derived (states) or opaque-and-assigned (continuants, names) — never
FVCS-managed. This is the answer to "identity inconsistencies": there is one identity model, the
kernel's.

## 3. What is a commit?

**A commit is an act of will to checkpoint a Snapshot on a line of development.** By epistemic typing, an
act of will is a **Decision** — the same principle that forbids conflating belief with fact forbids
conflating a *commit* (will) with a *snapshot* (designed content, an Artifact). So:

> **Commit = a `Decision` that `records` a Snapshot (ArtifactPackage) and names its `parent` commit(s),
> carrying a message; creating it advances a branch `Name` to it by compare-and-swap.**

Author, time, position, and the tamper-evident chain come free from the kernel `LogEntry` (Laws 2, 8, 1).
The commit's identity as content is its Decision `RevisionHash`; as an event, the `LogEntry` hash.
Convergence with Git (branch→commit→tree→blobs): FVCS derives the same four-layer shape — branch =
Name, commit = Decision, tree = ArtifactPackage, blob = object Revision — from the primitives, not from
Git. The difference: author/time live on the *fact*, not in the content hash (kernel value/fact
separation, DOMAIN_MODEL §6), so two identical-content commits dedup as content but remain two facts.

## 4. What is a branch?

**A branch is a `Name`.** Its head is `resolve(branch)` → the latest commit. Advancing it is a
compare-and-swap repoint (expected = current head). This is the kernel's only mutable state, which means
a branch is the *only* mutable thing in FVCS — everything else is immutable and append-only. Concurrent
advances of the same branch produce a kernel `CAS_FAILURE`, which is exactly a non-fast-forward
rejection ("the branch moved; reconcile first"). Branch namespace convention: `branch/<line>` (e.g.
`branch/main`, `branch/exp/cheaper-model`).

## 5. What is a tag?

**A tag is a `Name` that is never moved, blessed by a `Decision`.** The pointer is a Name
(`tag/<name>`); the act of tagging (who/when/why) is a `Tag` Decision that `marks` a commit. A tag with
`role: release` is a **Release** (a blessed, immutable snapshot for external consumption, resting on
approvals); a tag with `role: baseline` is a **Baseline** (a named reference point for comparison).
Release and Baseline are therefore *not* separate objects — they are tag roles (KN-0002).

## 6. What is a workspace?

The kernel has no uncommitted mutable working tree: every object revision, once appended, is immutable
and already in the log. So a **workspace is a projection**, not storage: it is a branch plus the
*difference* between the latest revisions of continuants in scope and the revisions pinned by the
branch's head Snapshot. "Staging" is choosing which current object revisions to include in the next
Snapshot. There is no dirty mutable state to lose — the answer to "hidden mutable state" is: there is
none but the branch Name.

## 7. What is a change set?

**A change set is the diff between two Snapshots**: per continuant, `added` / `removed` / `changed`
(old→new `RevisionHash`), plus relationship changes. Computed by set-diffing the two packages' pinned
members and running kernel `diff` on each changed object. It is a pure function of two content hashes —
deterministic and reproducible.

## 8. What is a merge?

**A merge is a `Commit` with two (or more) `parent` commits that reconciles divergent Snapshots.** The
reconciliation is three-way against the **merge base** = the lowest common ancestor (LCA) of the two
heads in the commit DAG. For each continuant, the merge combines the two sides relative to the base
(§Conflict Model). A merge that reconciles cleanly is an automatic Commit; a merge with unresolved
structural conflicts halts and returns a conflict report — resolution is itself a `Decision` (choosing a
side, or authoring a new revision). A merge is an act of will over divergence — correctly a Decision.

## 9. What is a conflict?

Divergence that the merge cannot resolve unambiguously. Three kinds (full taxonomy in the
[Conflict Model](CONFLICT-MODEL.md)): **structural** (same continuant changed to different revisions on
both sides), **semantic** (structurally clean but the combination is invalid or claims contradict),
**operational** (valid as data but violates a runtime/compatibility/policy constraint at deploy). This
three-level split is the core theoretical contribution of the conflict model.

## 10. What is history?

**History is deterministic and two-layered.** The **commit history** is the DAG of `Commit` Decisions
linked by `parent` edges, with branch Names as movable heads — projected purely from the log. Beneath it,
the **fact history** is the kernel's own hash-chained log of every append. History is deterministic
because it is content-addressed and hash-chained (Laws 1, 3) and time-indexed (Law 8); `resolveAt`
reconstructs any past head exactly. History is never rewritten (append-only) — the answer to "history
corruption": it is cryptographically impossible to alter undetectably, and there is no rebase-that-
mutates; a "rewrite" is a new branch of new commits, with the old ones permanently present.

## 11. What is reproducibility?

**Reproducibility is checkout + closure + reproduce.** A commit records a Snapshot whose closure root
hash certifies configuration identity (Law 7). To reproduce a version: resolve the commit → its Snapshot
→ its closure (pinned by hash) → `reproduce`, which yields *new observations under the same
configuration*, and agreement with the original is measured, never assumed. Reproducibility is thus a
first-class kernel operation, not an FVCS reconstruction.

## 12. What is rollback?

**A rollback is a `Commit` (a Rollback Decision) that advances a branch back to a prior Snapshot.** It is
never a deletion (append-only): "a rollback follows a deploy; both remain true forever" (DOMAIN_MODEL
§3.4). The prior state is re-pointed-to, not restored-by-erasure; the failed state remains in history,
attributed.

## 13. What is promotion?

**A promotion is a `Commit`/`Deployment` Decision that advances a higher-environment line to a Snapshot
already validated on a lower one.** Promotion is deployment across Environments (dev→staging→prod): the
same immutable Snapshot, re-pointed by a higher-tier branch/deployment Name, resting on the evaluation
verdicts that validated it. No content changes on promotion — only which line points at it.

## 14. What is a release?

**A release is a `Tag` (role `release`) marking an immutable Snapshot, blessed by approval Decisions.**
It is a frozen, externally-consumable version with a stable Name (`tag/release/<version>`), a pinned
closure (reproducibility), and a chain of `approves` Decisions (the review). Releases never move.

## 15. Canonical vocabulary (one line each)

| Term | Definition (derived) | Realized as |
|---|---|---|
| **Version** | an immutable content-addressed state | `RevisionHash` / closure hash |
| **Snapshot** | a manifest pinning object revisions | `ArtifactPackage` revision |
| **Commit** | an act of will checkpointing a snapshot on a branch | `Decision` (`commit`) + branch CAS |
| **Branch** | a line of development | kernel `Name` (`branch/…`) |
| **Head** | a branch's current commit | `resolve(branch)` |
| **Tag** | an immovable named pointer | `Name` (`tag/…`) + `Tag` Decision |
| **Release** | a blessed immutable snapshot | `Tag` role `release` + approvals |
| **Baseline** | a named reference snapshot | `Tag` role `baseline` |
| **Merge** | reconciliation of divergence | `Commit` with ≥2 `parent`s |
| **Merge base** | LCA of two heads | commit-DAG traversal |
| **Change set** | diff of two snapshots | engine output (`ChangeSet`) |
| **Conflict** | unresolvable divergence | structural / semantic / operational |
| **History** | the commit DAG over the fact log | projection (`HistoryEngine`) |
| **Rollback** | re-point a branch to a prior snapshot | `Commit`/`Rollback` Decision |
| **Promotion** | advance a higher line to a validated snapshot | `Promotion`/`Deployment` Decision |
| **Lineage** | the derivation graph of an object/system | `derived_from`/`supersedes`/`produced_by`/`parent` |
| **Compatibility** | whether B may replace A | `CompatibilityVerdict` Claim |
| **Workspace** | branch + staged (uncommitted) object revisions | projection |

## 16. Core concepts — justified or rejected

| Concept | Verdict | Rationale |
|---|---|---|
| Snapshot | **Keep** = ArtifactPackage (reuse) | the system-version unit; already in the ontology |
| Commit | **Keep** = Decision (`commit`) | an act of will; epistemic typing forbids merging with the snapshot Artifact |
| Branch | **Keep** = Name (no object) | a line of development is mutable-pointer state, which is exactly a Name |
| Merge | **Reject as object** | a merge is a Commit with ≥2 parents — a property, not a new type |
| Tag | **Keep** = Decision (`tag`) + Name | the act of immovable naming is a will |
| Release | **Reject as object** | a Tag with role `release` (KN-0002) |
| Baseline | **Reject as object** | a Tag with role `baseline` |
| Workspace | **Reject as object** | a projection (branch + staged revisions); no storage |
| Change Set | **Reject as object** | a computed diff (engine output), not a stored node |
| Review | **Reject as object** | the AI-PR triad `proposes`/`approves`/`rejects` (existing Decisions) |
| Rollback | **Keep** = existing Rollback Decision (reuse) | already an ontology decision subtype |
| Promotion | **Keep** = existing Promotion/Deployment (reuse) | already ontology decisions |
| Compatibility | **Keep** = Claim (`compatibility-verdict`) | an evidenced belief "B replaces A"; no naked assertion |
| Lineage | **Reject as object** | the derivation/parent edge graph (existing + `parent`) |

Net new vocabulary FVCS must register (additively, via the public `OntologyModule` SPI — never modifying
the frozen system): three types (`Commit`, `Tag` Decisions; `CompatibilityVerdict` Claim) and three
relations (`parent`, `records`, `marks`). Everything else is reuse or engine logic. See the
[Versioning Ontology](VERSIONING-ONTOLOGY.md).
