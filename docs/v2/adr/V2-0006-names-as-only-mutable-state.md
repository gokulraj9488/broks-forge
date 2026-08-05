# V2-0006. Names as the only mutable state

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

An append-only, content-addressed substrate (ADR-V2-0001) poses an immediate practical
question: if nothing can change, how does anything *happen*? Production must point at a new
closure; an agent must have a "current" version; a branch must advance. Some mutable
indirection is unavoidable. The design question is how much mutability to admit, and where.

Every information system that rots does so through uncontrolled mutability: status columns
that disagree with history, "current version" flags updated in three places, environments
described by config that changed since the deploy. The blast radius of mutability is the
blast radius of lying.

## Alternatives considered

- **Mutable status/current fields on objects.** The conventional model. Every mutable field
  is a place where the record and reality can diverge, and each needs its own audit
  mechanism bolted on.
- **Environments as first-class mutable objects** (a Deployment entity with an updatable
  target). Reintroduces the same problem one level up, with the highest-stakes pointer in
  the system as the mutable one.
- **No mutability at all** (pure references by hash everywhere). Theoretically clean,
  practically inhuman: engineers and systems need stable, meaningful handles ("prod",
  "nightly-suite") whose referents advance.

## Decision

Exactly **one** mutable concept exists in Forge: the **name**. A name is a pointer from a
stable, human-meaningful identifier to a revision or closure. Everything else is immutable.

- **Repointing a name is itself an append to the log** — so even mutation has immutable
  history, and `resolve(name, at: log position)` is deterministic forever.
- **Deployment, rollback, promotion, and branching are name operations** — which is why
  they are atomic, instant, reversible, and fully audited by construction. Rollback is not
  a procedure; it is repointing at a hash that never stopped existing.
- Names carry **stewardship**: who may repoint a given name is the entire permission model
  for change (repointing `prod` *is* deploy permission).
- Precedent: Git refs, DNS records, Kubernetes labels — every durable system converges on
  "immutable objects, mutable names." Forge adopts it as constitutional law (Law 3) rather
  than as an implementation habit.

## Consequences

**Positive**
- The entire mutable state of the operating system is a small dictionary of pointers —
  auditable at a glance, impossible to desynchronize from history.
- "What is in production?" has exactly one answer with a proof: resolve the name, get a
  closure hash.
- Time travel is free: resolving any name at any past log position reconstructs any
  historical configuration.

**Negative / trade-offs**
- Everything meaningful funnels through name resolution, making the name table the hottest
  concept in the system; its semantics (atomicity of repointing, resolution determinism)
  must be specified with total precision in the domain model.
- Engineers accustomed to editing objects must learn to think in repointing; the tooling
  must make "supersede + repoint" feel like one gesture or the discipline will be resented.
