# Brok's Forge V2 — Conceptual Foundation

The founding document suite of V2, produced **before any implementation planning**, in
mandatory order. Implementation that contradicts these documents is wrong by definition;
changing them is a supersession, never an edit.

| # | Document | Role |
|---|---|---|
| 0 | [../FORGE_KERNEL.md](../FORGE_KERNEL.md) | The research derivation — how the concepts were discovered, what was rejected, competitive conceptual analysis, known failure modes |
| 1 | [MANIFESTO.md](MANIFESTO.md) | **The constitution** — philosophy, mental model, kinds, laws, operations, amendment rules, the category declaration |
| 2 | [adr/](adr/README.md) | **One ADR per kernel primitive** — why each exists, what alternatives were rejected |
| 3 | [DOMAIN_MODEL.md](DOMAIN_MODEL.md) | **The canonical domain model** — identity, entities, relationships, events, lifecycles, invariants, operations, ownership, versioning |
| 4 | [ADVERSARIAL_REVIEW.md](ADVERSARIAL_REVIEW.md) | **The falsification attempt** — a hostile committee's attacks on kinds, operations, and invariants; three claims falsified and amended, verdicts and dissents on record |
| 5 | [KERNEL_IMPLEMENTATION_PLAN.md](KERNEL_IMPLEMENTATION_PLAN.md) | **Phase 1 plan of record** — the gate before kernel code: package architecture, storage abstraction, transaction/identity/revision/event models, the six operations as sequence diagrams, testing (incl. TCK), risk, roadmap, and constitutional traceability |
| 6 | [kernel/MILESTONE-1-REPORT.md](kernel/MILESTONE-1-REPORT.md) | **Implementation, Milestone 1 (`kernel-api`)** — value objects, identities, canonical serializer; 45 tests green, 88% line coverage, constitutional validation, risks |
| 7 | [kernel/MILESTONE-1-REVIEW.md](kernel/MILESTONE-1-REVIEW.md) | **Independent review, Milestone 1** — 23-point adversarial review; 5 issues found and fixed, converged; break-attempts; the quality gate that lets every future component depend on `kernel-api` |
| 8 | [kernel/KERNEL-RUNTIME-REPORT.md](kernel/KERNEL-RUNTIME-REPORT.md) | **KERNEL RUNTIME milestone** — the fully-functioning in-memory kernel: all six operations, hash-chained log, in-memory backend, reproduce SPI, event bus; 75 tests green; architecture/sequence diagrams, thread-safety & perf analysis, adversarial review, compliance |
| 9 | [kernel/PHASE-1-RC1-REPORT.md](kernel/PHASE-1-RC1-REPORT.md) | **Phase 1 COMPLETE** — persistence (PostgreSQL/JDBC + dependency-free migrator), TCK **verified against PostgreSQL 16.14** (104 tests), canonical parser + log codec, validation layer, executed benchmarks; full report, final committee review, security/recovery, release-criteria all met |
| 10 | [kernel/DEVELOPER-GUIDE.md](kernel/DEVELOPER-GUIDE.md) | **Developer & extension guide** — getting started, API reference, extension points (backends/reproducers/subscriptions), contribution norms, migration, known limitations |
| — | [../V2_MASTER_PLAN.md](../V2_MASTER_PLAN.md) | Product framing and delivery phasing (V2.0–V2.3); its §3 primitives are superseded by this suite |

**Code:** the kernel lives at [`../../backend/kernel/`](../../backend/kernel/README.md) as a
standalone Maven reactor, independent of the Spring application.

## The kernel on one screen

- **One substrate:** the Forge Graph — append-only, content-addressed, bitemporal,
  provenance-total; its log is its event bus; names are the only mutable state.
- **Four kinds** (Epistemic Typing): Artifact (intent), Observation (reality),
  Claim (belief), Decision (will) — each under its own law of revision.
- **Five edge families:** composition, derivation, evidence, causality, intent.
- **Six operations:** append, resolve, traverse, diff, reproduce, subscribe.
- **Ten laws**, enforced as physics — binding the platform itself hardest of all.
- **Everything else is userspace.**

## Category declaration

Forge introduces three concepts that did not previously exist in AI engineering, each
adoptable by future tools independently of Forge: **Epistemic Typing**, **the Claim**
(no naked numbers), and **the Trail** (the investigation as a first-class object).

## Status

Conceptual foundation complete and **adversarially reviewed**
([ADVERSARIAL_REVIEW.md](ADVERSARIAL_REVIEW.md)): the four kinds and the substrate survived
every attack; three prose overclaims were falsified and amended (configuration vs.
behavioral identity; cryptographic erasure of regulated content; kernel clock ticks); two
gaps block V2.0 until specified (read visibility; compare-and-swap name repointing — both
now sketched in the domain model). Per the founding mandate, implementation planning may
begin only from these documents — starting with V2.0 "The Substrate" as phased in
[../V2_MASTER_PLAN.md](../V2_MASTER_PLAN.md) §8.
