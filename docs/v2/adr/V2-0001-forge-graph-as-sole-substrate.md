# V2-0001. The Forge Graph as the sole substrate

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 vision proposed a "Forge System Graph" — a living engineering graph in which every
engineering object is a node and every relationship a first-class citizen — as a research
hypothesis, with an explicit instruction to challenge it aggressively and discard it if
flawed. Separately, the original V2 master plan proposed two primitives side by side: a
System Snapshot store and an Evidence Ledger.

The hypothesis was challenged on three fronts:

1. **The degeneracy test.** A *mutable* graph of engineering objects is precisely a knowledge
   graph — the category the vision rejects. If nodes can be edited and edges deleted, the
   graph can lie about history, and every downstream promise (memory, replay, auditability,
   "nothing forgotten") collapses. The graph shape alone is therefore not the primitive.
2. **The two-truths test.** Keeping an Evidence Ledger *beside* a graph creates two sources
   of truth for "what happened." Any divergence between them is unresolvable by construction.
3. **The sufficiency test.** Is a graph even needed, or would an event log with projections,
   or a relational registry, carry the vision?

## Alternatives considered

- **A mutable knowledge/property graph (the naive reading of the hypothesis).** Rejected by
  the degeneracy test: without a write discipline it cannot be trusted about the past, and
  trust about the past is the entire product.
- **An event store with derived projections (pure event sourcing).** The log is honest, but
  relationships become second-class — recomputed projections rather than addressable,
  citable facts. "Every relationship contains history" and "edges can be evidence" are not
  expressible without reifying the graph anyway.
- **Snapshot store + Evidence Ledger as two peer primitives (the original master-plan
  model).** Rejected by the two-truths test; also duplicates identity machinery (both need
  versioning, addressing, provenance).
- **A conventional relational domain model.** V1's approach, correct for V1's scope. It
  cannot make history, causality, or evidence structural — they remain conventions in
  service code, exactly what an operating system must not rely on.

## Decision

There is exactly one structure per organization: **the Forge Graph** — an append-only,
content-addressed, bitemporal, provenance-total, typed graph.

- The **write discipline is the primitive**, not the graph shape: append-only (supersession,
  never deletion), content-addressed (identity = hash, Merkle references), bitemporal
  (valid time + record time), provenance-total (every append actor-signed).
- The graph's **append log is the event stream and the Evidence Ledger**. History and record
  are one structure viewed by time and by shape — the two-truths problem is dissolved, not
  solved.
- Relationships are first-class: addressable, immutable, provenance-stamped, retractable by
  later append, citable as evidence.

The System Graph hypothesis is thereby **confirmed, promoted, and corrected**: it is not one
primitive among several — it is the substrate everything else is defined on, and it is only
sound under the write discipline above.

## Consequences

**Positive**
- One identity model, one history, one audit trail for the whole platform; no
  synchronization problem can exist between "the data" and "the record."
- Trust becomes structural: the graph cannot lie about the past because there is no
  operation with which to lie.
- Deduplication, O(1) equality, structural diff, and reproducibility certificates fall out
  of content addressing for free.

**Negative / trade-offs**
- Monotonic growth is a fact of life; storage tiering and relevance ranking become
  first-order engineering problems (accepted openly — see FORGE_KERNEL.md §15).
- Append-only correction is less familiar to engineers than UPDATE; the burden falls on
  tooling to make supersession effortless.
- A single substrate is a single point of conceptual failure: if the write discipline is
  ever weakened "just this once," the entire trust argument voids. Hence Law 9 (no
  privileged writer) is constitutional, not advisory.
