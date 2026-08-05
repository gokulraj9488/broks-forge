# KN-0002. Roles are tags, not subtypes

- Status: Accepted
- Phase: 2

## Context

Many objects vary by *usage* without varying in *identity*: a Dataset used for evaluation vs training vs
retrieval; an Evaluation run offline vs online; a Prompt acting as system vs user. Naively, each variant
becomes its own subtype (`dataset-eval`, `dataset-train`, …), multiplying the catalog.

## Decision

Usage variation is modeled as a **role** — a payload tag (`role: retrieval-corpus`) — not a new object
type. A new subtype is justified only when the object has different **identity, lifecycle, relationships,
or invariants**. Datasets share all of those regardless of role, so they are one type with a role tag.

## Consequences

- The catalog stays minimal (ONTOLOGY §11); no combinatorial subtype explosion.
- Roles are queryable payload fields; the ontology records the legal role vocabulary per type.
- When a "role" starts needing distinct invariants or edges, it graduates to a subtype — a deliberate,
  reviewable promotion, not an accident.
