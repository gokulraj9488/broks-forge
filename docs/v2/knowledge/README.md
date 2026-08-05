# Forge Knowledge System — Phase 2

The **semantic layer** that turns the frozen Forge Kernel's primitives into the canonical ontology of AI
Engineering. It is built entirely on the public kernel API (kernel-core / kernel-api 1.0.0); it requires
no kernel change and modifies nothing in the frozen constitution, ADRs, domain model, or kernel code.

Produced in the mandated order — **concepts before code**: the ontology and architecture were made
internally consistent and self-reviewed before implementation began.

## Deliverables

| # | Deliverable | Where |
|---|---|---|
| 1 | Forge Knowledge Ontology | [ONTOLOGY.md](ONTOLOGY.md) |
| 2 | Knowledge Object Catalog | [OBJECT-CATALOG.md](OBJECT-CATALOG.md) |
| 3 | Relationship Catalog | [RELATIONSHIP-CATALOG.md](RELATIONSHIP-CATALOG.md) |
| 4 | Architecture Decision Records | [adr/](adr/README.md) (KN-0001..0004) |
| 5 | Package Diagram | [ARCHITECTURE.md §2](ARCHITECTURE.md) |
| 6 | Dependency Graph | [ARCHITECTURE.md §3](ARCHITECTURE.md) |
| 7 | Public API Documentation | [ARCHITECTURE.md §4](ARCHITECTURE.md) + Javadoc in [`backend/forge-knowledge`](../../../backend/forge-knowledge/README.md) |
| 8 | Implementation | [`backend/forge-knowledge/`](../../../backend/forge-knowledge/README.md) |
| 9 | Tests | `backend/forge-knowledge/src/test` (22 tests) |
| 10 | Architecture Review Report | [ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md) |
| 11 | Future Evolution Strategy | this document, §Future Evolution |
| — | **Knowledge Governance (Phase 2 freeze gate)** | [KNOWLEDGE-GOVERNANCE.md](KNOWLEDGE-GOVERNANCE.md) |

## The system on one screen

- **The founding move:** classify every candidate object by its relationship to truth → it becomes a
  kernel **Artifact / Observation / Claim / Decision**. This collapses ~23 loose objects into a small,
  closed ontology and forces two structural results: the **definition/result split** (Evaluation is an
  Artifact; its Verdict is a Claim) and **attribute-not-object** (Cost is a Run field + a Claim, not an
  object).
- **21 first-class objects + 6 Claim result-types + a Decision family**, over the kernel's 4 kinds and 5
  edge families. Nothing new in the kernel.
- **Ontology as data** (KN-0001): object/relationship types are a registry; new types are data, not code
  or kernel changes.
- **Semantics in userspace** (KN-0004): a validator enforces payload schemas, endpoint types,
  cardinalities, and cross-object invariants *before* append — including the Observation-immutability
  discipline the kernel left as KAP-3 — while the kernel's ten laws remain the floor beneath.

## Future Evolution Strategy

**Governance (how the ontology changes)**
- New object type / verb → add a registry entry (an `ObjectType` / `RelationType`), or ship an
  `OntologyModule` (SPI). Never a framework or kernel change.
- New kind or edge family → **not here**; that is a kernel constitutional amendment (governance).
- Ontology changes are **append-mostly**: types/verbs are added, deprecated (never deleted), or promoted
  (a role graduates to a subtype), mirroring the kernel's append-only law. Payload schemas carry an
  optional `schema_version`; because content is hashed, a schema change yields new revisions, never edits.

**Foundation → product (deferred, in priority order)**
1. **Tighten endpoint unions further** and add a validation-layer audit for extrinsic cardinalities
   (intrinsic ones are checked at append; extrinsic accrue over time).
2. **Per-object helper APIs** (`Agents.define(...)`, `Runs.record(...)`) over the generic
   `KnowledgeGraph.define` path, for ergonomics — pure userspace.
3. **A query layer** (typed graph queries, "why?" traversals, lens/aggregation views) over the
   projection + kernel `traverse`/`closure`. Explicitly out of the foundation.
4. **Bulk import** (a loop over the validated `define` path) and cross-org subgraph exchange, building on
   `OntologyExport`/`GraphExport`.
5. **Calibration loop** for Claim confidence (ADR-V2-0003) via subscription programs — userspace autonomy.

**Explicitly not built in Phase 2** (per mandate): AI Git, Forge Graph, applications, UI. This phase is
the semantic layer only.

## Status

**Frozen** (Phase 2 governance gate, [KNOWLEDGE-GOVERNANCE.md](KNOWLEDGE-GOVERNANCE.md)). Ontology
internally consistent and adversarially reviewed twice: the ARB refined one under-modeling finding
([ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md)); the governance gate accepted two additive
relationship refinements (KG-R1 multi-agent runtime causality, KG-R2 process provenance) and confirmed
all ten scenarios and Phases 3–5 need no ontology redesign. Foundation implemented and green (**23
tests**), using only public kernel APIs, with the kernel untouched.
