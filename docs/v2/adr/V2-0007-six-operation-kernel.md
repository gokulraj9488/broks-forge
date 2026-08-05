# V2-0007. The six-operation kernel

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 vision proposed that every primitive support the same universal operations, offering
a candidate list of twelve: version, compare, explain, replay, inspect, reference, comment,
share, branch, merge, validate, archive — with an instruction to question its sufficiency.

Two problems surfaced. First, the twelve are not primitive: most are compositions of
something smaller (branch is a name append; comment is an annotation append; validate is a
traversal). Second and more damning, the list is *insufficient* — it describes an archive,
not an operating system. It has no naming operation (so nothing can be deployed) and no
reaction operation (so nothing can be autonomous).

An operating system's power comes from a small, closed, composable operation set — Unix
proved that a handful of syscalls outlives thousands of applications. The question is the
minimal set for engineering facts.

## Alternatives considered

- **The twelve-verb list as given.** Redundant (ten of twelve are compositions) and
  incomplete (no resolve, no subscribe). Rejected as kernel; kept as the derived vocabulary.
- **CRUD.** Update and Delete violate Law 1 outright; Create/Read is too coarse to express
  naming, reproduction, or reaction.
- **A rich, open-ended verb API** (one operation per use case, grown forever). The path
  every point tool takes; it optimizes each release and forfeits composability, and the
  operation set becomes the feature list — exactly what the kernel/userspace split forbids.
- **Five (drop `reproduce`, treating replay as userspace).** Tempting, but reproducibility
  is a *law* (Law 7); a law the kernel cannot itself exercise is unenforceable. Reproduce
  stays kernel.

## Decision

The kernel exposes exactly **six operations**, closed under composition:

1. **`append`** — the only write (nodes, edges, retractions, annotations, names).
2. **`resolve`** — name or content address → revision; deterministic at any log position.
3. **`traverse`** — the read: patterns over nodes and edges; `closure` is its distinguished
   fixpoint form.
4. **`diff`** — structural delta between any two same-kind nodes (closures → architecture
   diff; prompts → text diff; decision contexts → "what did we weigh then?").
5. **`reproduce`** — re-execute under the pinned closure (artifacts), re-derive (claims),
   reconstruct context and optionally re-decide (decisions). Observations are explicitly
   excluded: reality is not replayable.
6. **`subscribe`** — a standing traversal whose new matches invoke programs whose outputs
   are appends (ADR-V2-0008).

The twelve requested verbs reduce completely: version→append; compare→diff; explain→traverse
(evidence/causality); replay→reproduce; inspect/reference/share→free via stable addresses;
comment→append; branch→append(name); merge→diff+append (two derivation parents);
validate→traverse/reproduce; archive→append (supersession + name repointing).

Closure rule: **no seventh operation may enter the kernel while a composition of the six
expresses it** (Manifesto, Article X).

## Consequences

**Positive**
- Every future capability is a composition, so every future capability automatically works
  on every kind — new features cannot fragment the interaction model.
- The Palette (the command shell) has a complete input language from day one: six verbs
  over names.
- Testing and formal reasoning surface: six operations with specified semantics is a
  tractable object of proof; four hundred endpoints is not.

**Negative / trade-offs**
- Ergonomics must be built as derived vocabulary on top — engineers say "roll back," not
  "append a name retargeting"; the kernel's austerity is a foundation, not a UX.
- `traverse` carries enormous weight (search, why, blast radius, closure); its pattern
  language is the hardest single specification in the domain model and must be versioned
  with extreme care.
