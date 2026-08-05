# Forge Knowledge System — Architecture Review

An independent Architecture Review Board's attempt to **reject** the ontology and the foundation, the
findings, the refinements applied, and the final verdict. The board's mandate: search for duplicate
concepts, missing concepts, circular dependencies, leaky abstractions, over-specialization,
under-modeling, future incompatibilities, and violations of kernel principles — and keep refining until
no meaningful architectural improvement remains.

## Method

The board re-derived the epistemic classification independently, attacked each overlap resolution,
tried to name a missing fundamental object, traced the package and object dependency graphs for cycles,
and audited every kernel interaction for principle violations. Findings are graded
**Blocking / Refined / Accepted-with-rationale / No-issue**.

## Findings

### F1 — `executed` / `applied` endpoints were under-modeled — **Refined (fixed)**
The board found `executed` (Run→artifact) and `applied` (Deployment→artifact) were declared against
`toKind: ARTIFACT`, which would legalize "a Run executed a Prompt" or "deploy a Tool." **Fixed:**
`RelationType` gained a bounded-union target (`toAny(...)`); `executed` now targets `{Agent, Workflow}`
and `applied` targets `{Agent, Workflow, ArtifactPackage}`, with `EXACTLY_ONE` correctly meaning "exactly
one runnable/deployable." A regression test (`executedIsBoundedUnion`) confirms a Prompt is rejected.

### F2 — Benchmark vs Evaluation (over-specialization?) — **Accepted with rationale**
The closest call. A Benchmark is nearly an Evaluation, and BenchmarkScore nearly an EvaluationVerdict.
The board considered merging Benchmark into `Evaluation(role=benchmark)`. **Retained** because a
Benchmark has a genuinely distinct purpose — a *published, frozen, cross-system-comparable* standard
with a pinned dataset+metric — and a distinct query surface ("all benchmark scores for model X across
providers"). The cost of keeping it is one Artifact type + one Claim type of data (KN-0001), and the
clarity gain is real. Recorded as the ontology's weakest distinction, to revisit if usage shows the role
model suffices.

### F3 — Dataset / KnowledgeBase / MemoryStore (duplicate data concepts?) — **No-issue**
The board pressed the three "data" artifacts. They survive because they differ in the *epistemic status
of their contents and their lifecycle*: a Dataset is a pinned immutable input; a KnowledgeBase composes
Datasets **plus** a runtime retrieval capability (a composite, not a copy); a MemoryStore is a config
whose contents are **Observations** written at runtime. Merging any two would conflate design-time
immutability with runtime evolution. Justified in ONTOLOGY §4.

### F4 — Missing fundamental objects? — **No-issue**
The board tried to name a missing primitive: Trace/Span (→ Runs compose, DOMAIN_MODEL §3.2),
Credential/Secret (→ referenced by handle; a secret is not knowledge), Schedule/Trigger (→ autonomy is
subscription, kernel Article VI; a userspace program, not an object), Metric (→ a field of Evaluation /
a Run measurement / a Claim), Budget (→ a Policy), Actor/User (→ reified provenance, §2.5). All are
covered or correctly excluded. No fundamental object is missing.

### F5 — Circular dependencies — **No-issue**
Package graph is acyclic (`io → graph → validate → ontology → kernel`; `spi → ontology`), verified by
construction (the `PayloadCheck` hook was placed in `ontology`, not `spi`, precisely to avoid a
`spi ↔ ontology` cycle). Object-graph composition/derivation/evidence cycles are impossible: those edges
are intrinsic refs pinned by hash, and a target must pre-exist to be referenced (kernel
`MISSING_REFERENCE`). Extrinsic causality can form data cycles, which is acceptable (it is asserted
belief, retractable), not a definitional cycle.

### F6 — Leaky abstraction: `KnowledgeObject` exposes kernel types — **Accepted with rationale**
`KnowledgeObject` surfaces `Address.Revision` and `Revision`. The board judged this **intentional
interop**, not a leak: the Knowledge System is explicitly a *layer over* the kernel, and callers need
kernel addresses to use kernel operations (`closure`, `traverse`, `resolve`) the layer deliberately does
not re-wrap. The semantic type (`ObjectType`) is always present alongside, so the abstraction adds
meaning without hiding the substrate.

### F7 — Duplicate decision subtypes (deployment/promotion/rollback/approval/retirement) — **Accepted**
The board questioned five DECISION subtypes. They share machinery but are distinct engineering acts with
distinct query value; they cost only data and all inherit the kernel Decision law. No merge warranted.

### F8 — Kernel-principle violations — **No-issue (audited)**
Audit: the module imports only public kernel packages (`api`, `api.canonical`, `core.command`,
`core.engine`, `core.log`) — no `memory`/`store`/`codec`/`node` internals; the kernel, constitution,
ADRs, and domain model are byte-for-byte unchanged; the semantic layer is only ever *stricter* than the
kernel (it validates before append and never weakens a law). The one place the ontology adds discipline
the kernel lacks — Observation/Decision immutability — is done in userspace (KN-0004), which is exactly
where the Kernel Governance placed KAP-3. No Kernel Amendment Proposal is required.

### F9 — Future incompatibilities — **No-issue**
New object types and verbs are additive data (KN-0001); schema evolution is versioned and append-only
(content is hashed, so a schema change yields new revisions, never edits); the reserved payload keys
(`statement`/`method`/`confidence`/`judgment-call`) are the kernel's frozen contract. The ontology is
append-mostly, mirroring the kernel.

## Verdict

After F1 was refined and F2–F9 were adjudicated, **no meaningful architectural weakness remains**. The
ontology is internally consistent (proved by `OntologyConsistencyTest`), every object has a distinct
purpose, no duplicate concept survives scrutiny, the foundation uses only public kernel APIs, and no
kernel change is required.

## Success-criteria check

| Criterion | Result |
|---|---|
| Ontology internally consistent | ✅ builds; 7 consistency tests green |
| Every knowledge object has a clear purpose | ✅ Object Catalog; overlaps resolved (§4) |
| No duplicate concepts | ✅ F2/F3/F7 adjudicated; Cost merged out |
| Implementation uses only public kernel APIs | ✅ audited (F8) |
| No kernel changes required | ✅ kernel untouched |
| ARB finds no meaningful architectural weakness | ✅ F1 refined; rest resolved |

**Verification:** `forge-knowledge` — **22 tests green** (`mvn -o clean test`, offline against kernel
1.0.0). Kernel re-verified untouched.
