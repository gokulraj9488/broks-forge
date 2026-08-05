# V2-0005. The Closure — System Snapshot as a derived operation

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The original master plan's first primitive was the System Snapshot: a content-addressed,
immutable manifest pinning every component of an AI system — prompt, model, retriever,
memory policy, tools, datasets, evaluation profiles, guardrails — such that AI Git,
Architecture Diff, Agent DNA, reproducible deployment, and replay are all views over it.
The vision instructed that this primitive be questioned for sufficiency.

Questioning it revealed the opposite problem: not insufficiency but **redundancy**. Once the
substrate is a content-addressed graph of immutable revisions (ADR-V2-0001), a snapshot's
entire content is references to things the graph already holds. Storing snapshots as a
separate primitive would duplicate identity machinery and create a second place where
composition is asserted.

## Alternatives considered

- **Snapshot as a stored, first-class primitive (the original proposal).** Duplicates the
  graph's identity and composition machinery; invites drift between "the manifest" and the
  composition edges; adds a kind with no distinct law of revision (it revises exactly like
  any artifact).
- **Per-module pinning (V1's pattern generalized ad hoc).** V1 pins dataset/prompt/profile
  versions on evaluation jobs. Extending this pattern module-by-module scatters
  reproducibility across N conventions with no universal guarantee or diff.
- **Environment/config records outside the graph** (the classic deploy-manifest file).
  Reproducible in the small, but severed from history, causality, and evidence — a
  lockfile nobody can traverse from an incident.

## Decision

**Snapshot = `closure(artifact revision)`** — the transitive fixpoint of composition edges,
computed by the kernel's `traverse` operation. It is the single most important *derived*
object in Forge and requires no machinery of its own:

- Because references are Merkle (content-addressed), a closure has a hash, and that hash is
  a **configuration-identity certificate**: same hash, same configuration, exactly.
  (Amended by the adversarial review, finding B2: the certificate is *not* behavioral —
  components declare pinnability classes (pinned / attested / unpinnable), and behavioral
  agreement under an identical closure is measured, never assumed, which makes provider
  drift a detectable first-class phenomenon. See ../ADVERSARIAL_REVIEW.md.)
- **Agent DNA** is `closure(agent revision)`.
- **Architecture Diff** is `diff(closure A, closure B)`.
- **Reproducible deployment** is a name resolving to a closure (ADR-V2-0006).
- **Replay** works because every observation records the closure hash it occurred under
  (Law 7) — the one rule that makes every later "why" traversal possible.
- The **closedness requirement** is a law, not a suggestion: anything executable must have a
  fully pinned closure (no floating references), the way a Docker image cannot contain an
  unpinned layer.

## Consequences

**Positive**
- One mechanism yields five headline capabilities; nothing to keep in sync because there is
  only one assertion of composition — the edges themselves.
- Closure hashes make "did anything change?" and "are these two deployments identical?"
  O(1) questions.
- The pattern is proven ancestry: Git trees, Docker images, Nix derivations — applied for
  the first time to entire AI systems.

**Negative / trade-offs**
- Closure computation cost grows with composition depth; Merkle structure keeps it shallow
  (children's hashes suffice), but the discipline of always-pinned references must be
  enforced at authoring time, which constrains casual "just point at latest" workflows —
  deliberately. "Latest" is a name, and names resolve at a log position; nothing executable
  may embed one unresolved.
- Very dynamic compositions (tools chosen at runtime) must record what was *actually* used
  as observations, since intent-closure and observed-usage can legitimately differ; the
  model captures this as intent vs. reality (ADR-V2-0002), not as a snapshot defect.
