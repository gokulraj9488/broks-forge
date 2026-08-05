# Forge Knowledge Governance — Final Phase 2 Gate

**Body:** Forge Knowledge Architecture Review Board.
**Mandate:** decide whether the semantic model is mature enough to become a permanent dependency for
every remaining phase — reject it if meaningful weaknesses remain; otherwise freeze it.

**Inputs (frozen upstream):** the kernel and its constitution/ADRs/domain model. **Under review:** the
[Ontology](ONTOLOGY.md), [Object Catalog](OBJECT-CATALOG.md), [Relationship Catalog](RELATIONSHIP-CATALOG.md),
semantic validator, public APIs, import/export, [ADRs](adr/README.md), and evolution strategy.

**Outcome:** two low-severity ontology-completeness refinements accepted and applied (KG-R1, KG-R2);
all other stress tests passed; **the Knowledge System is frozen.** Verification: `forge-knowledge`
**23 tests green** (`mvn -o clean test`, offline against kernel 1.0.0); kernel untouched.

---

## 1. Knowledge Governance Report (summary)

The Board re-derived the epistemic classification independently, interrogated every object and
relationship, ran ten scenario stress tests, and traced Phases 3–5 for forced semantic changes. The
model held. Two gaps surfaced — both *missing additive relations within existing families*, not
structural flaws — and were fixed minimally:

- **KG-R1 (Ontology defect, low):** no runtime causality between runs (`triggered` was Run→Incident
  only), so multi-agent handoffs and autonomous chains had no causal arrow. **Fixed:** added
  `triggered` Run→Run (causality, extrinsic).
- **KG-R2 (Ontology defect, low):** no artifact→producing-process link, which Phase 3 version control
  requires for reproducibility ("which run produced this fine-tuned model?"). **Fixed:** added
  `produced_by` Artifact→Run (derivation, extrinsic).

Both are additive registry data (KN-0001); no kind, family, object-type structure, invariant, API
signature, or kernel behavior changed. The review then converged — no further meaningful improvement
remained.

## 2. Final Ontology Review

**Per-object verdicts** (Is it fundamental / derived / redundant / composable / does it have identity /
does it violate minimality?). All objects retained have a distinct identity and lifecycle and are not
expressible as another object plus a role or relationship.

| Object | Kind | Verdict | Distinct identity because… |
|---|---|---|---|
| Prompt | A | Fundamental | atomic unit of intent; own version lineage |
| Model | A | Fundamental | pinned model version; provider-bound |
| Provider | A | Fundamental | external vendor reference; models bind to it |
| Tool | A | Fundamental | callable capability with side-effect class |
| Agent | A | Fundamental (composite) | the runnable composition; own closure |
| Workflow | A | Fundamental (composite) | multi-step orchestration; empty-step case ≠ Agent |
| Dataset | A | Fundamental | pinned data collection; role-tagged |
| KnowledgeBase | A | Fundamental (composite) | Dataset(s) + retrieval capability — composition, not a Dataset copy |
| MemoryStore | A | Fundamental | config whose *contents are Observations*; distinct lifecycle |
| Evaluation | A | Fundamental | measurement *definition* (def/result split) |
| Experiment | A | Fundamental | controlled *comparison* of variants (≥2) |
| Benchmark | A | Retained (specialization) | published/frozen/comparable standard; weakest distinction, justified |
| Environment | A | Fundamental | deployment target context |
| Policy | A | Fundamental | declarative rule (the *what*) |
| Guardrail | A | Fundamental (composite) | runtime mechanism enforcing Policies (the *how*) |
| ArtifactPackage | A | Fundamental | named releasable bundle; a curated closure |
| Run | O | Fundamental | the atomic unit of reality |
| Session | O | Fundamental | continuity scope over Runs |
| Incident | O | Fundamental | failure event; own lifecycle |
| HumanFeedback | O | Fundamental | primary human signal (reality, not verdict) |
| MemoryEntry | O | Fundamental | one written memory unit (reality) |
| EvaluationVerdict | C | Fundamental (result) | belief from an Evaluation; Claim law |
| ExperimentConclusion | C | Fundamental (result) | causal conclusion; ≥2 variants |
| BenchmarkScore | C | Fundamental (result) | comparable score |
| Capability | C | Fundamental | evidenced belief about what a subject can do |
| RootCause | C | Fundamental | diagnosed cause (justified belief) |
| CostRollup | C | Fundamental | the merged-out "Cost", as aggregation Claim |
| Deployment (+ promotion/rollback/approval/retirement) | D | Fundamental | acts of will; name repoint mechanism |
| **Cost** | — | **Rejected as object** | no identity; Run field + CostRollup Claim |
| **Status / Actor** | — | **Rejected as object** | status is a query (Law 10); actor is reified provenance |

**Per-relationship review** (necessary / directional / correctly-familied / mergeable?). Every relation
is directional and semantically necessary; each belongs to exactly one family (verb→family pinned in the
userspace catalog). Merge analysis: `derived_from`/`supersedes`/`fine_tuned_from`/`forked_from` were
tested for merger and **kept separate** (different lineage semantics the family alone cannot express —
they read differently in a version-control UI and carry different expectations). `cites`/`supports`/
`refutes` kept separate (assertion vs. confirmation vs. disconfirmation). `trained_on` was **rejected as
redundant** with the already-legal `depends_on` (Artifact→Artifact) and `fine_tuned_from`. No two
relations survive as duplicates.

## 3. Object Stability Report

- **Stable.** The object catalog is closed at the kind level (4) and fixed at the structural level (the
  28 registered types above). No object is redundant, none lacks identity, and none violates minimality
  (§2). Growth is additive registry data (new subtype = new `ObjectType`), never a redesign.
- **Reserved subtypes are permanent:** each object's kernel subtype token (`agent`, `run`,
  `evaluation-verdict`, `deployment`, …) participates in addresses and content hashes and must round-trip
  forever — treated as append-only.
- **Roles are stable and non-structural** (KN-0002): `role` vocabularies (dataset roles, eval modes) may
  grow without touching identity.

## 4. Relationship Stability Report

- **Stable, additive-open.** Five families are closed (kernel Article III). The verb catalog is the
  permanent userspace vocabulary; verbs are pinned one-family-each and are add-only. KG-R1/KG-R2 grew the
  catalog by two relations without disturbing any existing one.
- **Directionality is permanent** (e.g. `executed` Run→Agent, `produced_by` Artifact→Run) — reversing a
  direction would be a new relation, never a repurpose.
- **Intrinsic/extrinsic split is stable:** identity-defining edges are intrinsic refs (hash-pinned,
  acyclic by construction); asserted-later edges are extrinsic and retractable.

## 5. Semantic Validator Audit

**Enforced above the kernel (before append):** payload field presence + canonical type; legal `role`
vocabulary; relationship endpoint types (incl. bounded unions, e.g. `executed`→{Agent,Workflow});
relationship cardinality min/max for intrinsic links; CI-2 (a Claim carries ≥1 evidence edge); CI-6
(Observations/Decisions are single-revision — the userspace KAP-3 discipline); intrinsic-vs-extrinsic
placement (an intrinsic verb cannot be asserted post-hoc, and vice-versa); custom per-type
`PayloadCheck` hooks.

**Delegated to the kernel (the floor beneath):** the Claim law and Decision law (kind-level), append-only,
content-addressing, reference existence (`MISSING_REFERENCE`), kind match on versioning, CAS on names,
hash-chain/tamper evidence, bitemporal provenance. The validator is only ever *stricter*, never looser.

**Coverage vs. cross-object invariants:** CI-1 (Run executes exactly one runnable — via `executed`
bounded union + EXACTLY_ONE), CI-3 (Deployment applies one + targets one), CI-4 (Agent uses one Model,
≥1 Prompt), CI-5 (composite minimums), CI-6, CI-7 (Model→Provider), CI-8 (traceability — kernel) are all
enforced or kernel-inherited. **One documented limitation (accepted, not a defect):** extrinsic-edge
cardinality is validated at *projection-audit* time, not at assert-time, because extrinsic edges accrue
over the object's life; intrinsic cardinality is enforced at append. This is inherent to
asserted-later edges and is recorded in the architecture, not a hole.

**Verdict: complete** for the ontology as specified.

## 6. ADR Traceability Report

| ADR | Decision | Realized by | Tested by |
|---|---|---|---|
| KN-0001 | Ontology is open data | `ObjectType`/`RelationType`/`Ontology`/`Ontologies`; SPI `OntologyModule` | `OntologyConsistencyTest` |
| KN-0002 | Roles are tags, not subtypes | `PayloadSchema.roles`; validator role check | `ValidationTest.wrongFieldType`/role path |
| KN-0003 | Definition/result split; Cost non-object | Eval/Exp/Bench Artifact + Claim pairs; no Cost type; `CostRollup` Claim | `OntologyConsistencyTest.definitionResultSplit`, `costIsNotAnObject` |
| KN-0004 | Semantic validation in userspace (+ KAP-3 discipline) | `KnowledgeValidator`; `KnowledgeGraph` validates before append | `ValidationTest.*`, `observationsAreImmutable` |

All four ADRs are internally consistent, each is implemented, and each has test coverage. KG-R1/KG-R2
required **no ADR change** (they are additive relations governed by KN-0001 + the evolution strategy).

## 7. API Stability Report

| Surface | Status | Compatibility |
|---|---|---|
| `KnowledgeGraph` (define/addRevision/relate/deploy/resolve/view) | **Frozen** | signatures stable |
| `KnowledgeObject`, `Link`, `KnowledgeView` | **Frozen** | stable |
| `ObjectType`, `RelationType`, `Cardinality`, `FieldType`, `PayloadSchema/Field`, `Ontology(.Builder)` | **Frozen** | `RelationType` gained `toTypes` (bounded union) in ARB; additive |
| `ObjectTypes.*`, `Verbs.*` constants | **Additive-open** | KG-R1/KG-R2 added `Verbs.PRODUCED_BY` + 2 relations; no removals/renames |
| `validate` (`ValidationResult`/`Issue`/`KnowledgeException`/`LinkSpec`) | **Frozen** | stable |
| `io` (`OntologyExport`, `GraphExport`) | **Frozen** | stable |
| `spi` (`OntologyModule`, `ontology.PayloadCheck`) | **Frozen** | stable |

Policy going forward: **additive-only** (new types/verbs/relations), never rename or repurpose (mirrors
kernel append-only). No public signature changed during this governance pass.

## 8. Import/Export Compatibility Report

- **Deterministic:** `OntologyExport`/`GraphExport` render to the kernel's canonical (RFC-8785-profile)
  bytes; two exports of the same state are byte-identical (`ImportExportTest`), so ontology and subgraph
  documents are content-addressable and diffable — a stable interchange format.
- **Forward-compatible:** the projection surfaces unknown `(kind, subtype)` as `untypedSubtypes` rather
  than failing, so a newer ontology's export imports into an older reader without breakage.
- **Versioned:** payload `schema_version` + append-only content addressing mean a schema change yields
  new revisions, never edits; old documents remain valid under their original schema.
- **Import path:** replay through the validated `KnowledgeGraph.define` (re-validated, re-signed — Law 9).
  Format is stable; a bulk importer is a thin additive loop (Future Evolution).

## 9. Future Phase Compatibility Report

**Requirement:** future phases must be buildable with **no ontology redesign** (additive registry data
is permitted; structural change is not).

**Ten scenario stress tests** — all representable with the current kinds/families:

| Scenario | Modeled with | Redesign? |
|---|---|---|
| Single-agent | Agent(uses Model/Prompt/Tool) → Run → Verdict → Deployment | No |
| Multi-agent | Workflow contains Agents / Agent depends_on Agent; Session contains Runs; **Run triggered Run** (KG-R1) | No |
| RAG | Agent uses KnowledgeBase indexes Dataset(corpus); retrieval in Run payload | No |
| Evaluation pipeline | Evaluation uses Dataset → Runs → EvaluationVerdict cites Runs | No |
| Autonomous workflow | kernel subscription programs append Runs/Claims/Decisions; **Run triggered Run** chains | No |
| Human-in-the-loop | HumanFeedback (obs); Approval decision approves proposed Deployment | No |
| Fine-tuning | Model fine_tuned_from Model; depends_on Dataset(training); **Model produced_by Run** (KG-R2) | No |
| Production deployment | Deployment applies/targets/rests_on; Incident caused_by; Rollback decision | No |
| AI governance | Policy + Guardrail enforces Policy; Environment enforces Policy; judgment-call decisions; total provenance | No |
| Unknown future architectures | epistemic typing (design/reality/belief/will) is architecture-agnostic; new object types are data | No |

**Phase 3 — AI Engineering Version Control:** the kernel is already append-only, content-addressed, and
bitemporal; knowledge objects already version via revisions; lineage exists (`derived_from`,
`supersedes`, `forked_from`, `fine_tuned_from`, **`produced_by`**); branches/tags/deploys are kernel
names; PRs are the proposed→approve/reject Decision triad. Version control is a *view + workflow* over
the existing ontology. **No ontology change required.**

**Phase 4 — Forge Graph:** the graph already exists (kernel `traverse`/`closure` + `KnowledgeView`
projection, typed by the ontology). Forge Graph is query/visualization/navigation over these primitives.
**No ontology change required.**

**Phase 5 — Applications:** consume `KnowledgeGraph` + `KnowledgeView` + the ontology; they add UX and
domain workflows, not new semantics. **No ontology change required.**

**Conclusion:** no future phase forces a semantic redesign. Growth is additive relations/subtypes/roles,
already governed by the evolution strategy.

## 10. Final Architecture Decision — FREEZE

Freeze checklist:

| Check | Result |
|---|---|
| Object catalog stable | ✅ §3 |
| Relationship catalog stable | ✅ §4 (additive-open) |
| Semantic validator complete | ✅ §5 (one documented, inherent limitation) |
| Public APIs stable | ✅ §7 |
| Import/export format stable | ✅ §8 |
| Extension SPI stable | ✅ §7 |
| ADRs internally consistent | ✅ §6 |
| Ontology minimal | ✅ §2 (Cost/Status/Actor rejected; no duplicates) |
| Ontology complete | ✅ §9 (all 10 scenarios + Phases 3–5) |
| No duplicate semantic concepts | ✅ §2 (merge analysis; `trained_on` rejected) |
| No unjustified abstraction | ✅ §2 (Benchmark retained with rationale; decision subtypes justified) |

Every knowledge object and every relationship has a permanent semantic meaning; future phases require no
ontology redesign; no meaningful architectural weakness remains. The ontology can serve as the semantic
foundation for the remainder of Broks Forge.

---

**Forge Knowledge Governance Complete.**
**The Forge Knowledge System is frozen.**
**Phase 3 is authorized to begin.**
