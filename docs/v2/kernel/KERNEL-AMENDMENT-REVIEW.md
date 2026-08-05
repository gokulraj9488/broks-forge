# Forge Kernel — Kernel Amendment Review (Final Phase 1 Gate)

**Body:** Forge Kernel Architecture Review Board.
**Mandate:** governance, not redesign — formally adjudicate every Kernel Amendment Proposal (KAP) from
Phase 1.5 dogfooding, implement what is accepted with the minimum change, and certify the kernel for
freeze before Phase 2.

**Inputs:** [PHASE-1.5-DOGFOODING-REPORT.md](PHASE-1.5-DOGFOODING-REPORT.md) (KAP-1, KAP-2); the
constitution ([MANIFESTO](../MANIFESTO.md)), the [ADRs](../adr/README.md), and the
[domain model](../DOMAIN_MODEL.md).

## Decisions at a glance

| KAP | Title | Decision | Disposition |
|---|---|---|---|
| **KAP-1** | Enforce the Claim Law & Decision Law in the kernel (Laws 5 & 6) | **ACCEPT** | Implemented, tested, verified |
| **KAP-2** | Make the verb→family registry real ("closed families, open verbs") | **REJECT** | Design affirmed; `Verb` docs corrected |
| **KAP-3** | *(review-uncovered)* Enforce "never revised" for Observation/Decision | **DEFER** | Needs a clarifying ADR first |

Final verification after implementation: **kernel 114 tests green** (58 api + 38 core incl. new
`ClaimLawTest`/`DecisionLawTest` + 9 in-memory TCK + **9 real-PostgreSQL TCK**); **Explorer 24 tests
green** against the amended kernel. No public method signature changed; `KernelException.Reason` gained
two additive constants.

---

## KAP-1 — Enforce the Claim Law and the Decision Law (Laws 5 & 6)

### 1. Proposal (restated)
Two of the ten constitutional laws are stated as substrate-enforced physics but are not enforced by the
shipped kernel: a `CLAIM` revision with no evidence/method/confidence (a "naked number") and a
`DECISION` that cites nothing and declares no judgment call are both accepted by `append`. The proposal
is to enforce both laws at append time, as the implementation plan originally specified.

### 2. Constitutional articles involved
- **Article V preamble:** "Laws are enforced by the substrate: violating appends are unrepresentable,
  not flagged." (This is the decisive clause — it makes enforcement the kernel's job, not a layer's.)
- **Law 5 (Claim Law):** "A claim cannot exist without evidence references, a named method, and a
  calibrated confidence… no unexplained number can exist anywhere in Forge."
- **Law 6 (Decision Law):** "A decision cites the claims it rests on — or explicitly declares itself a
  judgment call."
- **Law 4 (Epistemic partition):** every node is "under that kind's law of revision."
- **Article II** (the four kinds) and **§2.4** (Epistemic Typing as the category invention).

### 3. Affected ADRs
- **ADR-V2-0003 (Claim Law):** Decision section — "The Envelope becomes a **structural law on the Claim
  kind**: a Claim node is *unappendable* without evidence, method, confidence… an unexplained number is
  *unrepresentable*, the way Git cannot store a commit without a tree."
- **ADR-V2-0004 (Decision model):** a decision records "the claims it cites as its basis (or an explicit
  self-declaration as a judgment call — Law 6)."
- **DOMAIN_MODEL §3.3** ("Structure (all four mandatory — Law 5, unappendable without)") and **§3.4**
  ("the **basis** — cited claims via **intent edges**, *or* an explicit `judgment-call`").

### 4. Violation, or enforcement-left-to-a-higher-layer?
**A violation of the intended architecture.** The constitution and both ADRs use the words
"unappendable" and "unrepresentable" — enforcement is explicitly assigned to the substrate, not to
callers. The implementation plan concretely specified a `node` kind-validator (§71), a claim/decision
constructor invariant (§277, §788), and named tests `ClaimLawTest`/`DecisionLawTest` (§806–807). None
shipped: `KernelRuntime.prepare()` had no kind validation, there was no `node` package, and neither test
existed. This is not a deliberate deferral to userspace; it is a missing implementation of a mandated
kernel behavior, and it was not recorded among the Developer Guide's known limitations.

### 5. Alternatives evaluated
1. **Enforce in the `Revision` constructor.** Rejected — `Revision` is deliberately kind-agnostic
   structural content; per-kind schema and cross-checking payload against refs do not belong there.
2. **Enforce in the append engine's kind validators** (the plan's design). **Chosen** — the append path
   is exactly where "unappendable-if-violated" belongs; replay bypasses it, preserving history.
3. **Leave enforcement in userspace** (Phase 1.5's stopgap). Rejected as permanent — it reduces "no
   naked numbers" to a convention each app can skip, contradicting Article V and gutting the category
   invention.

**Design tension resolved.** Enforcing Law 5 requires the kernel to know a *minimal* claim schema
(evidence-family ref + `statement`/`method`/`confidence` payload keys). This is kind-level structure the
constitution already assigns to the kernel; it introduces no subtype or domain (AI) knowledge, so kernel
purity (ADR-V2-0001) is preserved. The reserved keys are the price the constitution already agreed to
pay ("the way Git cannot store a commit without a tree").

### 6. Backwards compatibility
- **Stored logs:** unaffected. Enforcement runs only in `prepare()` (append path). Log **replay** folds
  payloads directly (`graph.apply`/`names.apply`, revision re-put) and never calls `prepare()`, so any
  pre-amendment log — including one containing a naked claim — still reopens and rebuilds identically.
  *Verified:* the real-PostgreSQL TCK (reopen/replay/recovery) passes 9/9.
- **API surface:** no method signature changed. `KernelException.Reason` gained `CLAIM_LAW` and
  `DECISION_LAW` (additive; `Reason` is never switched exhaustively in the codebase).
- **Behavioral tightening:** appends that were previously accepted (lawless claims/decisions) are now
  rejected. This is the intended effect of the amendment. No kernel test relied on the old behavior; one
  Phase 1.5 harness test (`ClaimLawGapTest`) asserted it and was updated to assert enforcement
  (`ClaimLawEnforcedTest`).

### 7. Implementation complexity
Low and contained. One new pure-function validator; two call sites; two enum constants; two doc touches.

### 8. Long-term maintenance impact
Positive. The flagship "no naked numbers" guarantee is now structural, so every present and future
surface inherits it for free — the ADR-V2-0003 promise. The reserved-key contract is small, documented,
and stable. Risk: the canonical claim/decision payload keys are now part of the kernel contract and
must be treated as append-only (never renamed) — recorded in the API-stability report below.

### 9. Decision: **ACCEPT**
Justification: an explicit, ADR-backed constitutional requirement was unimplemented; the fix is small,
backwards-compatible for stored data, and must precede Phase 2's reliance on the Claim as a load-bearing
guarantee.

### Implementation (as built)
- **New:** `kernel-core/.../node/KindLaws.java` — pure validator. Reserved keys: `statement`, `method`,
  `confidence` (claim); `judgment-call` (decision).
  - **Claim:** payload must be an object with non-blank `statement`, non-blank `method`, numeric
    `confidence` ∈ [0,1], **and** ≥1 intrinsic reference in the **evidence** family.
  - **Decision:** ≥1 intrinsic reference in the **intent** family (the cited basis, per DOMAIN_MODEL
    §3.4), **or** payload `"judgment-call": true`.
  - **Artifact / Observation:** no construction-time invariant (their laws of revision are behavioral).
- **Changed:** `KernelRuntime.prepare()` calls `KindLaws.enforce(revision)` for `CreateNode` and
  `AddRevision` (after kind-match, before ref-existence). `KernelException.Reason` += `CLAIM_LAW`,
  `DECISION_LAW`.
- **Tests:** `kernel-core` `ClaimLawTest` (6) + `DecisionLawTest` (4) — the plan's named tests, now real.
- **Docs:** `Revision` javadoc now names the validator; Developer Guide §2 lists the new reasons, the
  reserved keys, and the replay-exemption.
- **Independent re-review of the affected area (append path):** the change is a guard at the front of
  `prepare()`; it does not alter sealing, positioning, hashing, projection folding, or subscription
  publication; `LawEnforcementTest`, `AppendResolveTest`, `ProjectionRebuildTest`, `ConcurrencyTest`,
  and both TCKs remain green — confirming no regression to the six operations or persistence.

---

## KAP-2 — Verb→family registry ("closed families, open verbs")

### 1. Proposal (restated)
`Verb`'s javadoc claimed a name→family "registry that lives in the kernel core," but none exists or is
enforced: the same verb name can be paired with any family. The proposal was to make the registry real
(expose and/or enforce it).

### 2–3. Articles / ADRs
- **Article III (§3.2, §3.3):** "Relationship verbs are an open set, but every verb belongs to one of
  five closed families." **ADR-V2-0002** (epistemic typing / closed families). No ADR asserts global
  verb-name uniqueness.

### 4. Violation, or higher-layer concern?
**Neither a violation nor a higher-layer omission — a documentation overstatement.** Article III's
load-bearing invariant is that **families are closed** (exactly five) and that **every verb belongs to
exactly one family**. Both hold: `EdgeFamily` is a closed enum, and every `Verb` instance carries
exactly one family, so the invariant is satisfied *per assertion*. Article III does not require that a
verb *name* map to one family *globally*. The only defect is the `Verb` javadoc describing a kernel-core
registry that was never built.

### 5. Alternatives evaluated
1. **Correct the `Verb` javadoc** (doc-only). **Chosen.**
2. **Ship an optional userspace verb catalog** as a convenience. Noted as the recommended pattern; the
   Explorer already demonstrates it (`Verbs`). Not a kernel change.
3. **Hard-enforce a global name→family map in the kernel.** **Rejected** — it would (a) introduce
   mutable state outside the append log, violating the substrate model (Article I; Law 3 "names are the
   only mutable state"); (b) narrow the "open verbs" half of Article III; and (c) enforce an invariant
   the constitution does not actually require.

### 6–8. Compatibility / complexity / maintenance
Doc-only change; zero code/API/behavior impact; removes a false promise that would otherwise mislead
future implementers.

### 9. Decision: **REJECT** (enforcement) + **documentation corrected**
Justification: the current design is correct and the more constitutionally faithful of the options;
kernel-enforced verb identity would damage two constitutional properties to fix a userspace data-hygiene
concern. The `Verb` javadoc was corrected to state that the kernel keeps no global registry, that each
verb belongs to exactly one family per assertion, and that name↔family consistency is a userspace
convention (with a pointer to this review).

---

## KAP-3 — *(review-uncovered)* "Never revised" for Observation and Decision

### Origin
While fixing the enforcement locus for Law 4 ("each kind under its law of revision"), the Board noted
the kernel allows `AddRevision` on **any** kind, yet §2.1 labels Observation "Never revised; only
annotated or re-measured" and Decision "Never unmade; only followed by new decisions," and DOMAIN_MODEL
§3.4 says a decision is "never superseded."

### Assessment
- **Decision:** textual support for immutability is fairly strong (§3.4 "never superseded"; the proposal
  protocol uses *separate* decision nodes, not revisions).
- **Observation:** genuinely ambiguous — "or **re-measured**" can be read as a new revision of the same
  observation continuant, which would make `AddRevision` legitimate.
No ADR addresses `AddRevision`-by-kind, and no current workflow (kernel or Explorer) relies on revising
observations or decisions.

### Decision: **DEFER**
- **Why deferred:** the observation case is constitutionally ambiguous; enforcing now risks locking a
  contested reading into the permanent contract. Governance should legislate from an explicit ADR, not
  infer a permanent restriction from a table cell.
- **Risks of deferral:** Law 4's "under that kind's law of revision" remains partially unenforced for
  the never-revised kinds; an application *could* revise an observation/decision continuant. Risk is
  **low** — the epistemic partition (one node = one kind) is enforced, and the claim/decision
  *construction* laws are now enforced (KAP-1).
- **Recommended revisit:** before any Phase 2 feature depends on observation/decision immutability,
  author an ADR resolving "re-measurement = new revision vs. new node" and decision supersession, then
  enforce (a one-line guard in `prepare()` mirroring KAP-1).
- **Impact on future phases:** none blocking. Phase 2's use of the Claim is already protected by KAP-1.

---

## Final constitutional compliance report

| Check | Result |
|---|---|
| Constitution internally consistent | ✅ No article changed. Article V's "enforced by the substrate" is now *true* of Laws 5 & 6 (previously aspirational in code). |
| ADRs internally consistent | ✅ No ADR changed. ADR-V2-0003/0004 are now implemented as written; ADR-V2-0001 (kernel purity) preserved — the kernel gained kind-level structure, no domain/AI knowledge. |
| Law-by-law enforcement | ✅ Laws 1,2,3,4,8,9,10 as before; **Laws 5 & 6 now enforced at append**; Law 7 unchanged. |
| No new architectural contradictions | ✅ KAP-2 removed a doc contradiction (phantom registry); KAP-1 removed a code-vs-constitution contradiction. |
| Category invention intact | ✅ "The Claim — no naked numbers" is now structural, as ADR-V2-0003 requires. |

## Final API stability report

| Surface | Change | Compatibility |
|---|---|---|
| `ForgeKernel` (six operations + helpers) | none | ✅ signatures unchanged |
| `AppendCommand`, `Address`, `Revision`, `CanonicalValue`, identities | none | ✅ unchanged |
| `KernelException.Reason` | **+`CLAIM_LAW`, +`DECISION_LAW`** | ✅ additive; never switched exhaustively |
| New public type | `com.broksforge.kernel.core.node.KindLaws` (+reserved-key constants) | ✅ additive |
| Append **semantics** | lawless CLAIM/DECISION now rejected | ⚠️ intentional tightening (the amendment); stored logs unaffected — replay bypasses validation |
| Reserved payload keys | `statement`, `method`, `confidence`, `judgment-call` now part of the contract | ⚠️ treat as append-only (never rename) — new constraint to maintain |
| Content-hash stability / canonical encoding | none | ✅ golden vectors unchanged; KAP-1 adds no hashed bytes |
| Storage contract (TCK) | none | ✅ both backends pass the unchanged TCK (in-memory 9/9, PostgreSQL 9/9) |

Doc-only corrections: `Verb` javadoc (KAP-2), `Revision` javadoc, Developer Guide §2.

## Verification re-run (post-implementation)

- `mvn -o clean install` (kernel): **BUILD SUCCESS** — 58 api + 38 core (incl. `ClaimLawTest`,
  `DecisionLawTest`) + 9 in-memory TCK.
- Real PostgreSQL 16.14 TCK (`forge_kernel_test`): **9/9** — reopen/replay/recovery intact.
- `mvn -o clean test` (Explorer, external consumer of the reinstalled kernel): **24/24**.

---

**Forge Kernel Governance Complete.**
**The kernel is frozen.**
**Phase 2 is authorized to begin.**
