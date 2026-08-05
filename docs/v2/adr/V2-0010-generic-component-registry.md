# V2-0010. One generic component registry with typed views

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 vision names at least twelve component types an AI system is composed of: prompt,
model configuration, retriever, embedding, memory policy, planner, tool, knowledge source,
guardrail, evaluation profile, dataset, policy — with more certain to appear (rerankers,
routers, cache policies, cost policies) as the field moves. Each needs versioning, diffing,
composition into closures, and lifecycle.

Built conventionally, that is twelve modules with twelve versioning behaviors, twelve
lifecycles, and twelve chances to diverge — and every new component type in the field is a
platform release. V1 already discovered the antidote in miniature: its text-backed-enum
convention makes new enum values a code-only change with no schema migration. The question
is whether component types deserve the same treatment at kernel scale.

## Alternatives considered

- **One bespoke module per component type.** Maximal fit per type; catastrophic divergence
  over time. Twelve implementations of "version this" will disagree within a year, and the
  kernel's promise — every operation works on every kind — dies by a thousand special cases.
- **A fixed component taxonomy in the kernel** (component types as kernel-level kinds).
  Freezes today's AI architecture into the constitution. The field will invent component
  types the kernel authors never imagined; each would demand an amendment. Kernel kinds
  must be closed by *principle* (laws of revision — ADR-V2-0002), not by *inventory*.
- **Schemaless components** (arbitrary blobs as artifacts). Versioning works, but typed
  behavior dies: nothing can know that a retriever has recall characteristics or that a
  prompt has template variables, so diff, validation, and lenses degrade to text.

## Decision

All components are **Artifacts** in the one substrate, and component types are an **open
subtype registry** — data, not schema:

- A **subtype registration** declares: the type's name, its content shape, which edge
  verbs it participates in (what it may compose), and its lens hints. Registering a new
  component type is an append, not a platform release.
- **Every kernel operation works on every subtype automatically** — version, diff, closure
  membership, reproduce, subscribe — because they are defined on the Artifact kind, not on
  subtypes. A subtype adds meaning, never mechanics.
- **Typed views, not typed silos**: "the prompt registry" is a lens (a filtered traversal)
  over artifacts of subtype prompt, not a separate store. One substrate, N faces.
- The same pattern governs Observation subtypes (trace, eval result, incident, cost,
  reasoning step, trail…), Claim subtypes (score, regression, root cause, suggestion…),
  and Decision subtypes (deploy, approval, rollback, retirement, ADR…): **kinds are closed,
  subtypes are open** — the single most important extensibility rule in the architecture.

## Consequences

**Positive**
- Forge is future-proof against its own field: when the industry invents a new component
  class, supporting it is a registration, and it inherits a decade of machinery instantly.
- Uniformity by construction: there is exactly one versioning behavior, one diff, one
  lifecycle in the entire platform — not because teams were disciplined, but because there
  is nothing else to build.
- The V1 lesson (text-backed enums, generalized) is preserved as doctrine rather than
  rediscovered.

**Negative / trade-offs**
- Generic substrates resist deep type-specific optimization; where a subtype needs rich
  specific behavior (e.g., prompt template analysis), that behavior lives in userspace
  lenses and analyzers, which must not be tempted back into the kernel.
- Registry governance is a real question (who may register subtypes, how shapes evolve);
  the domain model must define subtype evolution as supersession like everything else, so
  even the type system has honest history.
