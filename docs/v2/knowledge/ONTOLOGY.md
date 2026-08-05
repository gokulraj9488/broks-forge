# The Forge Knowledge Ontology

The canonical ontology of AI Engineering, expressed as a **semantic layer over the frozen Forge
Kernel**. Nothing here changes the kernel. Every knowledge object is a kernel node of one of the four
kinds, with a reserved subtype, a payload schema, and a constrained set of edges; every knowledge
relationship is a kernel edge in one of the five families. The Knowledge System is therefore *ontology
as data + validation in userspace* — the kernel stays the physics, this is the chemistry.

> Companion documents: the [Object Catalog](OBJECT-CATALOG.md) (every object, fully specified), the
> [Relationship Catalog](RELATIONSHIP-CATALOG.md), the [Architecture](ARCHITECTURE.md), and the
> [ADRs](adr/README.md). Governing kernel documents are frozen: [MANIFESTO](../MANIFESTO.md),
> [DOMAIN_MODEL](../DOMAIN_MODEL.md).

---

## 1. The founding move: epistemic classification

The mission listed ~23 candidate objects. The first ontological act is **not** to give each a box, but
to ask of each: *what is its relationship to truth?* The kernel already answers with four kinds
(Epistemic Typing, MANIFESTO Article II):

- **Artifact** — intent; what we designed. Revisable.
- **Observation** — reality; what happened. Not revised.
- **Claim** — belief; what we think it means. Superseded by reasoning; needs evidence+method+confidence.
- **Decision** — will; what we chose. Never unmade.

Classifying the candidates this way is what collapses 23 loose "objects" into a small, closed,
internally consistent ontology. Two structural results fall out immediately and drive everything else:

1. **The definition/result split.** Several "objects" are secretly *two* objects with different
   epistemic status: a designed **definition** (Artifact) and a derived **result** (Claim). An
   *Evaluation* is a spec you author (Artifact) whose *verdict* is a belief you derive (Claim). An
   *Experiment* (Artifact) yields a *conclusion* (Claim). Conflating them is the single most common
   modeling error in AI platforms; the kernel's kinds make the split mandatory.
2. **Attribute-not-object.** Some candidates are not first-class nodes at all but *facets* of another
   object. **Cost** is the clearest: a measured cost is a field on a Run (Observation); an aggregate
   cost is a Claim (`method: aggregation`). It has no independent identity, lifecycle, or ownership, so
   it is **not** a knowledge object.

## 2. The epistemic map (canonical assignment)

| Kind | Knowledge objects (reserved subtype) |
|---|---|
| **Artifact** (design) | Prompt `prompt` · Model `model` · Agent `agent` · Tool `tool` · Workflow `workflow` · Dataset `dataset` · Knowledge Base `knowledge-base` · Memory Store `memory-store` · Evaluation `evaluation` · Experiment `experiment` · Benchmark `benchmark` · Environment `environment` · Policy `policy` · Guardrail `guardrail` · Provider `provider` · Artifact Package `artifact-package` |
| **Observation** (reality) | Run `run` · Session `session` · Incident `incident` · Human Feedback `human-feedback` · Memory Entry `memory-entry` |
| **Claim** (belief) | Evaluation Verdict `evaluation-verdict` · Experiment Conclusion `experiment-conclusion` · Benchmark Score `benchmark-score` · Capability `capability` · Root Cause `root-cause` · Cost Rollup `cost-rollup` |
| **Decision** (will) | Deployment `deployment` (and the general decision subtypes: promotion, rollback, approval, retirement — DOMAIN_MODEL §3.4) |

Every candidate from the mission is placed. Overlaps were resolved by merge or composition (§4). This
table is the ontology's spine: it is **closed at the kind level** (no fifth kind — that would be a
kernel amendment) and **open at the subtype level** (new object types are registry data, ADR-KN-0001).

## 3. Object hierarchy

Knowledge objects are not a class hierarchy — the kernel has no inheritance. The "hierarchy" is a
two-level classification: **kind → object type**, plus orthogonal **roles** an object may play.

```
Knowledge Object
├── Artifact ── Prompt, Model, Agent, Tool, Workflow, Dataset, KnowledgeBase,
│               MemoryStore, Evaluation, Experiment, Benchmark, Environment,
│               Policy, Guardrail, Provider, ArtifactPackage
├── Observation ── Run, Session, Incident, HumanFeedback, MemoryEntry
├── Claim ── EvaluationVerdict, ExperimentConclusion, BenchmarkScore,
│            Capability, RootCause, CostRollup
└── Decision ── Deployment (+ promotion, rollback, approval, retirement)
```

Roles (a tag on an object, not a subtype): a Dataset may play the role `evaluation-set`,
`training-set`, or `retrieval-corpus`; an Evaluation may be `offline` or `online`. Roles vary usage,
not identity — modeling them as subtypes would multiply the catalog without adding meaning (ADR-KN-0002).

## 4. Overlap resolutions (why the catalog is minimal)

| Tension | Resolution | Rationale |
|---|---|---|
| **Cost** vs Run/Claim | **Merged out.** Measured cost = field on Run; aggregate cost = `cost-rollup` Claim. | No independent identity or lifecycle; "every derived number is a claim" (Law 5). |
| **Dataset** vs **Knowledge Base** | **Kept, related by composition.** KB *composes* one or more Datasets (role `retrieval-corpus`) plus a retrieval/index config. | A Dataset is a pinned collection; a KB is that collection **plus** a runtime retrieval capability. Different purpose, so KB is a composite, not a duplicate. |
| **Dataset** vs **Memory** | **Kept, different kind.** Dataset is a pinned Artifact; Memory Store is an Artifact config whose *entries are Observations* written during runs. | Datasets are immutable-once-pinned inputs; memory is evolving reality. Different epistemic status of the *contents*. |
| **Evaluation** vs **Benchmark** | **Kept, Benchmark specializes Evaluation.** A Benchmark is a *published, frozen, comparable* Evaluation with a fixed Dataset and metric. | Standardization + cross-system comparison is a distinct purpose; but it reuses the Evaluation machinery, so it is a specialization by role/composition, not a new mechanism. |
| **Evaluation** vs **Experiment** | **Kept, distinct.** Evaluation measures *one* subject against criteria; Experiment *compares variants* to test a hypothesis and concludes causally. | "How good is X?" ≠ "Is A better than B, and why?" Different structure (variants), different result kind of reasoning. |
| **Policy** vs **Guardrail** | **Kept, related by composition.** A Guardrail *enforces* one or more Policies at runtime. | Policy = the rule (what); Guardrail = the runtime mechanism (how/where). One policy, many guardrails; one guardrail, many policies. |
| **Capability** as Artifact vs Claim | **Claim.** A capability is an *evidenced belief* ("this agent can do X, confidence Y, per benchmark Z"). | A bare capability tag would be a naked assertion; Law 5 makes it a Claim with evidence. |
| **Human Feedback** as Evaluation vs Observation | **Observation.** The fact that a human gave feedback is primary reality; a quality verdict derived from it is a separate Claim. | Keeps primary signal (reality) distinct from interpretation (belief). |
| **Deployment** as Artifact vs Decision | **Decision.** Deploying is an act of will; mechanically it is a name repointing (ADR-V2-0006). | ADR-V2-0004: "Deployment is a decision." |

Result: **21 first-class knowledge objects** (16 Artifact, 5 Observation-ish… see catalog), **6 Claim
result-types**, **1+ Decision family** — with Cost merged out and KB/Guardrail modeled as composites.
Every remaining object has a distinct purpose, identity, and lifecycle.

## 5. Relationship taxonomy

Knowledge relationships are kernel edges; each belongs to exactly one of the five closed families, and
the **family carries the semantics** (MANIFESTO Article III). Verbs are open registry data. The
canonical verbs:

| Family | Meaning | Canonical verbs (open set) |
|---|---|---|
| **Composition** | is built from | `uses`, `contains`, `depends_on`, `includes`, `indexes`, `enforces` |
| **Derivation** | came from | `derived_from`, `supersedes`, `forked_from`, `fine_tuned_from`, `generated_by`, `executed` |
| **Evidence** | is justified by | `cites`, `supports`, `refutes`, `measured_by` |
| **Causality** | brought about | `caused`, `triggered`, `detected_by`, `regressed` |
| **Intent** | was chosen by | `rests_on`, `targets`, `applied`, `proposes`, `approves`, `rejects` |

The full binding of each verb to its legal (from-type → to-type) endpoints is the
[Relationship Catalog](RELATIONSHIP-CATALOG.md). The kernel enforces the family is closed and the
endpoint exists; the Knowledge System enforces the *type-level* endpoint constraints in userspace.

## 6. Allowed graph patterns (the canonical shapes)

1. **The engineering loop** (MANIFESTO §2.3), typed:
   `Prompt/Model/Tool → (composition) → Agent → (executed) → Run → (evidence) → EvaluationVerdict →
   (rests_on) → Deployment → (targets) → Environment`.
2. **Composition DAG:** `Agent uses Prompt, uses Model, uses Tool*, uses KnowledgeBase*`;
   `Workflow contains (Agent|Tool|Workflow)+`; `ArtifactPackage includes Artifact+`;
   `KnowledgeBase indexes Dataset+`; `Guardrail enforces Policy+`.
3. **Derivation chain:** `Prompt.v2 derived_from Prompt.v1`; `Model fine_tuned_from Model`;
   `Run generated MemoryEntry`.
4. **Evidence fan-in (Claim law):** every Claim (`EvaluationVerdict`, `Capability`, `RootCause`,
   `BenchmarkScore`, `CostRollup`, `ExperimentConclusion`) `cites`/`measured_by` ≥1 Observation or prior
   Claim.
5. **Causality chain (incidents):** `Deployment caused Incident`; `Run triggered Incident`;
   `RootCause detected_by Run`.
6. **Intent/decision:** `Deployment rests_on EvaluationVerdict+ (or judgment-call)`,
   `applied Agent.rev`, `targets Environment`.
7. **Session/Run containment:** `Session contains Run+`.

## 7. Invalid graph patterns (rejected by the semantic layer)

The kernel already makes some illegal (append-only, ref-must-exist ⇒ no composition cycles; Claim/
Decision laws). The Knowledge System additionally rejects, in userspace, before append:

1. **Kind mislabeling** — a Run modeled as Artifact, an Evaluation verdict modeled as Artifact, a
   Deployment modeled as anything but Decision.
2. **Endpoint type violation** — e.g. `Agent uses Run` (composition target must be an Artifact),
   `Deployment rests_on Prompt` (basis must be a Claim), `EvaluationVerdict cites Policy` (evidence must
   be an Observation or Claim).
3. **Evidence from a non-claim** — evidence-family edges originate only from Claims (DOMAIN_MODEL §187).
4. **Revising reality** — adding a revision to a `Run`, `Incident`, `Session`, `HumanFeedback`, or
   `MemoryEntry` continuant (observations are never revised). *This is where the Knowledge System
   supplies, in userspace, the discipline the kernel deferred as KAP-3 — no kernel change needed.*
5. **Missing mandatory composition** — an `Agent` without exactly one Model or without ≥1 Prompt; a
   `Deployment` without a target Environment and an applied Agent/Workflow; a `KnowledgeBase` that
   indexes no Dataset; a `Guardrail` that enforces no Policy.
6. **Cross-family verb misuse** — using a composition verb for a derivation edge, etc. (the userspace
   verb catalog pins each verb to one family; the kernel does not, per KAP-2).

## 8. Cross-object invariants

- **CI-1 (Loop integrity):** a `Run` MUST reference exactly one executed Agent **or** Workflow revision
  (`executed`, derivation).
- **CI-2 (Claim law inheritance):** every Claim object MUST satisfy the kernel Claim law *and* cite
  evidence whose types are legal for its verb (Relationship Catalog).
- **CI-3 (Deployment completeness):** a `Deployment` MUST `target` exactly one Environment and `apply`
  exactly one Agent/Workflow revision; it SHOULD `rest_on` ≥1 Claim or be a judgment call.
- **CI-4 (Agent completeness):** an `Agent` MUST `use` exactly one Model and ≥1 Prompt.
- **CI-5 (Composite integrity):** `KnowledgeBase indexes ≥1 Dataset`; `Guardrail enforces ≥1 Policy`;
  `ArtifactPackage includes ≥1 Artifact`; `Workflow contains ≥1 step`.
- **CI-6 (Reality immutability):** Observation-kind objects are single-revision continuants.
- **CI-7 (Provider binding):** a `Model` MUST declare its `provider` (composition to a Provider).
- **CI-8 (Traceability):** every object is reachable, via kernel provenance, to the actor and log
  position that created it (inherited — Law 2, Law 8; free).

## 9. Naming conventions

- **Object subtypes:** lower-kebab, matching the kernel `Revision` subtype grammar
  `[a-z][a-z0-9._-]{0,63}` (e.g. `evaluation-verdict`).
- **Relationship verbs:** lower_snake, matching the kernel `Verb` grammar `[a-z][a-z0-9_]{0,63}`
  (e.g. `fine_tuned_from`). Each verb is registered to exactly one family in the userspace catalog.
- **Names (kernel Name paths):** hierarchical, `namespace/segment/...` — conventions:
  `env/<name>` (environments), `agents/<domain>/<name>`, `models/<provider>/<name>`,
  `deploy/<env>/<target>` for deployment pointers. This gives the flat kernel namespace a semantic tree.
- **Payload keys:** lower_snake; reserved keys inherited from the kernel (`statement`, `method`,
  `confidence`, `judgment-call`) keep their exact spelling.

## 10. Evolution strategy

- **New object type** = add an `ObjectType` to the ontology registry (data). No code change to the
  framework, no kernel change. It picks a kind, a subtype, a payload schema, and its legal edges.
- **New relationship** = add a `RelationType` (verb + family + endpoint constraints). Verbs are open.
- **New kind or family** = forbidden here; would be a kernel constitutional amendment.
- **Schema migration** = payload schemas are versioned by an optional `schema_version` key; because
  content is hashed, a schema change produces new revisions, never edits (kernel Law 1). Old objects
  remain valid under the schema they were written with; the ontology records the min/current version.
- **Deprecation** = an object type or verb may be marked `deprecated` in the registry (still resolvable,
  no new instances encouraged); nothing is ever deleted (Law 1).
- **Compatibility rule:** the ontology is **append-mostly** — types and verbs are added, never
  repurposed; a repurpose is a new type. This mirrors the kernel's own append-only discipline.

## 11. Minimality & completeness claim

- **Complete:** every object a modern AI platform needs (the mission's 23, plus Memory Entry and the
  Claim result-types the definition/result split revealed) has a home, and the seven canonical patterns
  cover the engineering loop, composition, derivation, evidence, causality, and deployment.
- **Minimal:** Cost merged out; KB/Guardrail/Benchmark expressed by composition/specialization rather
  than new mechanisms; result-types share one Claim machinery; roles avoid subtype explosion. No object
  exists that another object plus a role or a relationship could express.

This ontology is the input to the [Architecture](ARCHITECTURE.md); implementation may begin only after
it is shown internally consistent (see [Architecture Review](ARCHITECTURE-REVIEW.md)).
