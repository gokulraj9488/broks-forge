# Relationship Catalog

Every canonical relationship type: a **verb**, its **family** (closed, kernel-enforced), the legal
**from → to** endpoint types, **cardinality**, and whether it is an **intrinsic** ref (part of a
revision's content, so pinned by hash and immutable) or an **extrinsic** edge (asserted separately,
retractable). The kernel enforces the family and endpoint existence; the Knowledge System enforces the
type-level endpoint and cardinality constraints in userspace (the [Validator](ARCHITECTURE.md)).

Legend: `A+` = one or more; `A?` = optional; `A!` = exactly one. "Artifact*" = any artifact type.

## Composition — "is built from" (intrinsic; acyclic by construction)

| Verb | From → To | Card. | Notes |
|---|---|---|---|
| `uses` | Agent → Model | `!` | CI-4; the agent's model |
| `uses` | Agent → Prompt | `+` | CI-4 |
| `uses` | Agent → Tool / KnowledgeBase / Guardrail | `*` | optional components |
| `uses` | Model → Provider | `!` | CI-7 |
| `uses` | Evaluation/Experiment/Benchmark → Dataset | `+` | the data under test |
| `uses` | Guardrail/Evaluation → Model / Tool | `*` | judge/checker |
| `contains` | Workflow → Agent / Tool / Workflow | `+` | ordered steps; CI-5 |
| `contains` | Session → Run | `+` | continuity scope |
| `contains` | Experiment → variant (Agent/Prompt rev) | `≥2` | the compared variants |
| `indexes` | KnowledgeBase → Dataset | `+` | corpus; CI-5 |
| `includes` | ArtifactPackage → Artifact* | `+` | the release bundle; CI-5 |
| `enforces` | Guardrail → Policy | `+` | mechanism→rule; CI-5 |
| `depends_on` | Artifact* → Artifact* | `*` | generic runtime dependency |

## Derivation — "came from" (intrinsic or extrinsic)

| Verb | From → To | Card. | Notes |
|---|---|---|---|
| `derived_from` | Artifact rev → Artifact rev (same type) | `?` | version lineage |
| `supersedes` | Claim rev → Claim rev | `?` | better reasoning |
| `forked_from` | Artifact → Artifact | `?` | branch |
| `fine_tuned_from` | Model → Model | `?` | tuned lineage |
| `executed` | Run → Agent/Workflow rev | `!` | CI-1; what ran |
| `generated_by` | MemoryEntry → Run | `!` | memory provenance |
| `produced_by` | Artifact* → Run | `?` | **extrinsic**; process provenance — the run that emitted this artifact (fine-tune, generation, compile). Phase 3 version control (KG-R2) |

## Evidence — "is justified by" (edges originate **only** from Claims)

| Verb | From → To | Card. | Notes |
|---|---|---|---|
| `cites` | Claim → Observation / Claim | `+` | Claim law evidence |
| `supports` | Claim/Observation → Claim | `*` | confirming evidence |
| `refutes` | Claim/Observation → Claim | `*` | disconfirming evidence |
| `measured_by` | Claim → Evaluation/Experiment/Benchmark | `!` | the method artifact |

## Causality — "brought about" (extrinsic; asserted, retractable)

| Verb | From → To | Card. | Notes |
|---|---|---|---|
| `caused` | Deployment/Run/Artifact rev → Incident | `*` | root-cause chains |
| `triggered` | Run → Incident | `*` | proximate trigger |
| `triggered` | Run → Run | `*` | runtime causality — multi-agent handoff, tool-triggered sub-run, autonomous chain (KG-R1) |
| `detected_by` | RootCause/Incident → Run/Observation | `*` | how it surfaced |
| `regressed` | Observation → Artifact rev | `*` | performance regression signal |

## Intent — "was chosen by" (the decision surface)

| Verb | From → To | Card. | Notes |
|---|---|---|---|
| `applied` | Deployment → Agent/Workflow/Package rev | `!` | CI-3; what is deployed |
| `targets` | Deployment → Environment | `!` | CI-3; where |
| `rests_on` | Decision → Claim | `*` | Decision law basis (else judgment-call) |
| `proposes` | Decision → Artifact rev | `?` | the AI Pull Request (proposed) |
| `approves` / `rejects` | Decision → Decision | `?` | review/approval (three-append PR) |

## Endpoint rules (cross-cutting, enforced by the Validator)

1. **Family–verb binding is fixed** (userspace catalog): a verb name resolves to exactly one family
   (this is the userspace discipline KAP-2 assigned to consumers).
2. **Evidence originates from Claims only** (DOMAIN_MODEL §187): a non-Claim asserting `cites` is invalid.
3. **Endpoint type must match** the table; a mismatch is rejected before append (ONTOLOGY §7.2).
4. **Cardinality** is checked at append for intrinsic refs (they are in the revision) and by projection
   audit for extrinsic edges (they accrue over time).
5. **Direction is semantic**: `executed` goes Run→Agent (the run points at what ran), never the reverse.

## Intrinsic vs extrinsic guidance

- **Intrinsic** (in the revision, hash-pinned, immutable): composition of an Artifact's parts, a Run's
  `executed`, a Claim's `cites`/`measured_by`, a Decision's `applied`/`targets`/`rests_on`. These
  *define what the node is* and must exist at creation → they are refs, and the target must pre-exist
  (kernel `MISSING_REFERENCE`), which is what makes composition/evidence DAGs acyclic.
- **Extrinsic** (asserted later, retractable): causality (`caused`, `triggered`), post-hoc `supports`/
  `refutes`, `regressed`. These are assertions *about* nodes discovered after the fact.
