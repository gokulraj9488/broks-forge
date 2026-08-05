# KN-0004. Semantic invariants are enforced in userspace, above the kernel

- Status: Accepted
- Phase: 2

## Context

The kernel enforces exactly its ten laws — including the Claim and Decision laws (kind-level), append-
only, content addressing, reference existence, and CAS names. It deliberately does **not** know the
richer, domain-specific invariants of AI engineering: "an Agent uses exactly one Model", "a Deployment
targets an Environment", "evidence edges originate only from Claims", "a Run is never revised". The
Phase 1.5 dogfooding and Kernel Governance settled that such type-level rules are a consumer concern
(and KAP-3 — observation immutability — was deliberately deferred out of the kernel).

## Decision

The Knowledge System enforces all **ontology-level** invariants in userspace, *before* calling
`kernel.append`, via a `KnowledgeValidator` driven by the ontology data (object schemas, relationship
endpoint/cardinality rules, cross-object invariants CI-1..CI-8). The kernel remains the final authority
on its ten laws; the semantic layer adds the chemistry on top and never weakens or duplicates a kernel
law — it composes with them.

Specifically, the Knowledge System supplies **in userspace** the discipline the kernel left as KAP-3:
Observation-kind objects (Run, Session, Incident, Human Feedback, Memory Entry) are single-revision —
the validator refuses to build an `AddRevision` for them. No kernel change is required or requested.

## Consequences

- **No kernel change** (success criterion): all Phase-2 rules live above the public API.
- Validation is **fail-fast and explainable**: a `ValidationResult` lists typed issues before any
  irreversible append, so the append-only log never accumulates ontology-invalid facts.
- The kernel's guarantees still hold underneath: even if a caller bypasses the validator and appends
  directly, the kernel's ten laws (incl. Claim/Decision) are never violated — the semantic layer can
  only be *stricter*, never looser.
- If a genuinely un-enforceable-in-userspace invariant were found, the correct response is a Kernel
  Amendment Proposal (governance), not a silent workaround. None was found.
