# Forge Kernel — Phase 1.5 Dogfooding Report

> **Superseded in part by the Kernel Amendment Review** ([KERNEL-AMENDMENT-REVIEW.md](KERNEL-AMENDMENT-REVIEW.md)).
> The two proposals below were formally adjudicated: **KAP-1 ACCEPTED and implemented** (the kernel now
> enforces the Claim & Decision Laws at append time), **KAP-2 REJECTED** (docs corrected instead). A
> third item the review uncovered (KAP-3) was **DEFERRED**. The narrative and evidence here are retained
> as the historical dogfooding record; test names referenced below were updated during the review
> (`ClaimLawGapTest` → `ClaimLawEnforcedTest`).

**Exercise:** build one complete internal application (the *Forge Engineering Explorer*) on the
released Forge Kernel v1.0.0, using **only the public API**, treating the kernel as an external
open-source dependency; then review the kernel's fitness for long-term use and, where warranted,
raise Kernel Amendment Proposals instead of changing the kernel.

**Application:** [`backend/forge-explorer/`](../../../backend/forge-explorer/README.md) — 13 main
classes (~1,600 LOC), 6 test classes, **22 tests, all green**, built as a standalone Maven project
with no parent and no access to any kernel internal. Captured run:
[`backend/forge-explorer/docs/DEMO-OUTPUT.txt`](../../../backend/forge-explorer/docs/DEMO-OUTPUT.txt).

**Verdict:** the kernel **passes**. Every major capability was buildable through the public API, no
internal was required, and the developer experience is good enough for long-term use with a thin
userspace facade. Dogfooding surfaced **two genuine gaps between the constitution and the shipped
implementation** — the most important being that **two of the ten laws (the Claim Law and the Decision
Law) are not enforced by the kernel at all**. Both are raised below as Kernel Amendment Proposals, with
reproducing test evidence, and **neither the kernel nor the constitution was changed**.

---

## 1. Success criteria

| Criterion | Result |
|---|---|
| Application built entirely with public APIs | ✅ verified: only `api`, `api.canonical`, `core.{command,engine,event,log,op,reproduce,validate}` imported |
| No kernel internals required | ✅ no `core.memory` / `core.store` / `core.codec` import anywhere |
| Every major kernel capability exercised | ✅ six ops + four kinds + five families + closure/verifyChain/validation (coverage table in the app README) |
| Developer experience acceptable for long-term use | ✅ with a ~1 class/seam userspace facade; see §4–§5 |
| No unjustified kernel changes proposed | ✅ two proposals, both evidence-backed; the kernel was not modified |
| Any proposed amendment supported by concrete evidence | ✅ each proposal cites a passing test that reproduces the gap |

---

## 2. Performance observations

Measured **through the facade** (not the raw kernel) on the in-memory backend, cold JVM,
single-threaded (`ExplorerPerfObservationTest`, N=3,000):

| Operation | Observation | Note |
|---|---|---|
| append (create + 1 ref) | ~6,400 ops/sec | cold JVM, allocation-bound; each op builds a `CanonicalValue`+`Ref` and the engine validates the ref (a store lookup) |
| resolve (name) | ~0.44 µs/op | projection hash-map lookup |
| closure (3,000-deep chain) | ~4.1 ms | linear in closure size |
| diff (root vs 3,000-hops head) | ~258 µs | structural delta over canonical content |
| fold graph from `log()` | ~60 ms for 3,000 nodes | userspace projection rebuild (see friction F3) |

The facade adds **no measurable overhead** — it is straight delegation. The append figure is lower
than the kernel's raw RC1 benchmark (~31k ops/sec) purely because of workload (ref-bearing creates
with per-append reference validation, on a cold JVM without warmup), **not** the userspace layer. For
an interactive engineering tool these numbers are comfortably sufficient.

---

## 3. Developer-experience review (requested dimensions)

| Dimension | Assessment |
|---|---|
| **API discoverability** | Good. `ForgeKernel` is the entire operational surface — six methods plus a few read helpers. `Kernels.inMemory(...)` is an obvious entry point. Weaker spot: the *value* types are split across `kernel-api` **and** several `kernel-core` packages, one of which (`core.log`) reads like an internal (see F2). |
| **Naming consistency** | Strong. `append/resolve/traverse/diff/reproduce/subscribe` map cleanly to intent; `Kind`, `EdgeFamily`, `Address`, `Revision`, `RevisionHash`, `Name` are unambiguous. `RepointName` for CAS is precise once learned. |
| **Documentation quality** | Excellent javadoc and a strong Developer Guide. But two doc claims are **not true of the shipped code** — the Claim/Decision-law enforcement (KAP-1) and the verb-family "registry that lives in the kernel core" (KAP-2). Docs promising enforcement that does not exist is the most serious DX issue found. |
| **Boilerplate required** | Moderate and easily contained. The recurring wart is `(Address.Revision) append(...).address().orElseThrow()` after every node write; one `Handle` type removes it everywhere (F1). Building lawful claims/decisions is hand-rolled per app (F4-kinds). |
| **Error messages** | Very good. `KernelException` carries a machine-readable `Reason` (`CAS_FAILURE`, `MISSING_REFERENCE`, `KIND_MISMATCH`, …) that made branching and test assertions trivial. |
| **Developer experience** | Good. Sealed types + exhaustive `switch` made reading the log and canonical values safe and pleasant; content-addressed dedup "just worked"; CAS names modelled deploy/promote/rollback naturally. |
| **Extension points** | Excellent. The `Reproducer` SPI and `SubscriptionProgram` are clean, small, and executor-agnostic; both were implemented in userspace with no friction (`ChecklistReproducer`, `AutoObserverProgram`). |
| **Learning curve** | Low-to-moderate. The four-kinds / five-families / six-ops model is small and internally consistent; the one genuine learning cost is discovering what makes a *lawful* claim/decision — which, because the kernel doesn't enforce it, you learn from prose, not from a compiler/append error (KAP-1). |
| **Performance** | More than adequate for an engineering tool (§2). |
| **Maintainability** | High. The kernel's minimalism means the app's coupling surface is tiny and stable; the whole integration is one facade class. |

---

## 4. Kernel usability report (summary)

**What worked well (kept the app small and correct):**

1. **The six-operation surface is genuinely sufficient.** Nothing in a realistic workflow —
   versioning, deploy/rollback, evidence-based claims, decisions, reproduction, autonomy — needed a
   seventh operation or a kernel internal.
2. **Content addressing and dedup are invisible and correct.** Reproducing the same suite twice
   produced identical observation hashes (dedup) but two distinct facts — demonstrated live
   (`equal=true` in the demo).
3. **Sealed hierarchies make consumption safe.** `Payload`, `CanonicalValue`, and `Address` are
   exhaustively matchable; the renderer and graph fold are total functions with no default-case rot.
4. **CAS names are the right primitive.** Deploy, promote, and rollback are all `RepointName` with an
   `expected` — one concept, three workflows, and the lost-race case is a typed `CAS_FAILURE`.
5. **The extension SPIs are clean.** Reproducer and subscription were the easiest parts to build.

**Where the kernel made the app do the kernel's job** — see friction points and amendments below.

---

## 5. Friction points

Ranked. Each is tagged **[amendment]** (raised in §7), **[userspace]** (correctly solved in the app,
no kernel change warranted), or **[doc]**.

| # | Friction | Severity | Disposition |
|---|---|---|---|
| **F0** | **Claim Law & Decision Law are not enforced** — a naked-number claim and an unjustified decision are accepted by `append`. | **High** | **[amendment]** KAP-1 |
| **F1** | Every node write returns `Optional<Address>` that must be unwrapped **and** downcast to `Address.Revision`. | Low | **[userspace]** absorbed by `Handle` |
| **F2** | The "public API" spans `kernel-api` **and** large parts of `kernel-core`; edge/log types live in `com.broksforge.kernel.core.log`, a package that reads like an internal. | Medium | **[doc]** / optional future API-module consolidation |
| **F3** | No read-side **enumeration**: you cannot list nodes or names; `resolve`/`traverse`/`closure` all need an address you already hold. An explorer must re-fold `log(org)` into projections the kernel already maintains internally (`GraphIndex`/`NameStore`). | Medium | **[userspace]** `GraphModel`; candidate future read-only projection port (not a constitutional matter) |
| **F4** | Verb→family pairing is unchecked; the same verb name can be filed under any family. | Medium | **[amendment]** KAP-2 (may resolve as **[doc]**) |
| **F5** | Building a *lawful* claim/decision has no kernel-side helper or schema; each app hand-rolls the payload shape and evidence verbs. | Low | **[userspace]** `kinds/Claims`, `kinds/Decisions` (and a consequence of F0) |

**A friction I withdrew after checking:** I initially flagged that name time-travel needs a
`LogPosition` the write methods don't return. It is not a kernel gap — `AppendResult.entry().position()`
exposes it; my facade simply chose to return a `Handle` and drop the entry. Lesson recorded for the
app, not the kernel.

---

## 6. Adversarial review of the application (and fixes applied)

An independent pass tried to break the *app* (kernel issues are deferred to §7, never patched around):

- **Graph edges duplicated per revision.** The continuant-level views listed `uses`/`derived_from`
  once per revision. **Fixed** in `GraphRenderer` by de-duplicating to distinct continuant-level
  display edges (genuinely distinct endpoints, e.g. six check-results → suite, remain — correctly).
- **Non-ASCII output garbled under the Windows console code page.** **Fixed** by having the demo
  `main` emit UTF-8 explicitly, so redirected/piped output is faithful everywhere.
- **`GraphModel` ref-target fallback** could mislabel a reference whose owning node was not yet folded.
  For graphs built through valid appends this cannot occur (the kernel rejects `MISSING_REFERENCE`, so
  a ref target always pre-exists); documented in code, left as a defensive branch.

**Known app limitations (honest):** same-continuant derivation renders as a self-loop at the
continuant level (faithful, if visually odd); the demo is CLI/library only (no interactive UI); the
`indent()` helper is O(n²) in output size (fine for demo scale).

No attempt to break the app required a kernel internal or a kernel change.

---

## 7. Kernel Amendment Proposals

> These are proposals, not changes. The kernel and the constitution are untouched. Each is scheduled
> work for a future kernel amendment cycle, supported by a passing test that reproduces the gap.

### KAP-1 — Enforce the Claim Law and the Decision Law in the kernel (Laws 5 & 6) — **High**

**Problem.** Two of the ten constitutional laws are not enforced by the shipped kernel:
- **Law 5 (Claim Law):** "A claim cannot exist without evidence references, a named method, and a
  calibrated confidence… the end of naked numbers" (MANIFESTO §V.5, ADR-V2-0003). This is the physics
  behind one of Forge's three declared category inventions ("the Claim — no naked numbers").
- **Law 6 (Decision Law):** "Every decision cites the claims it rests on — or explicitly declares
  itself a judgment call" (MANIFESTO §V.6, ADR-V2-0004).

The kernel accepts a `CLAIM` revision that is a bare number with no evidence, method, or confidence,
and a `DECISION` that cites nothing and declares no judgment call.

**Evidence.** `backend/forge-explorer/.../ClaimLawGapTest.java` — three passing tests:
`kernelAcceptsNakedClaim` (a `Revision.leaf(Kind.CLAIM,"kpi",Num(42))` is appended and durably stored),
`kernelAcceptsUnjustifiedDecision`, and `userspaceHelpersEnforceTheLaws` (the app's own
`kinds/Claims`/`kinds/Decisions` reject exactly what the kernel accepts). Corroborating source facts:
- `KernelRuntime.prepare()` performs **no** kind validation for `CreateNode`/`AddRevision` (only ref
  existence, node existence, and kind-match-on-versioning).
- `Revision`'s javadoc explicitly disclaims it: *"The four kinds' laws … are enforced by the append
  engine's kind validators, not here."* — but no such validators exist.
- There is **no `node` package** in `kernel-core` and **no `ClaimLawTest`/`DecisionLawTest`**, though
  `KERNEL_IMPLEMENTATION_PLAN.md` specifies all three (§7 "value is unconstructable", §71 `node/`
  package, §806–807 the two named tests, §788 "claim law as constructor invariant").

**Root cause.** The plan's `node` kind-validator component was never implemented in the KERNEL RUNTIME
milestone. Responsibility fell through the seam between the `Revision` value type (which deliberately
does not enforce it) and the append engine (which has no validator). It is a missing implementation,
**not** a constitutional decision to omit it — and it is not listed among the Developer Guide's known
limitations.

**Constitutional impact.** None to the text — this is the implementation failing to meet the
constitution, which is the stronger kind of finding. Laws 5 & 6, ADR-0003/0004, and the "no naked
numbers" category claim all stand and require the kernel to conform. There is a real **design tension**
to resolve deliberately: enforcing the Claim Law requires the kernel to know a *minimal* claim schema
(that a claim carries ≥1 evidence-family reference, a non-empty `method`, and an in-range `confidence`).
The constitution already resolves it in the kernel's favour — Law 5 *is* the kernel's job — so this is
kind-level structure, not subtype/domain knowledge, and preserves kernel purity (the kernel still knows
nothing of AI or of specific subtypes).

**ADR impact.** No new ADR needed; ADR-V2-0003 and ADR-V2-0004 already mandate the behavior. A short
clarifying ADR *may* be warranted to fix the enforcement locus (append-engine kind validator) and the
minimal claim/decision payload contract as part of the kernel's public API.

**Alternatives considered.**
1. Enforce in the `Revision` constructor. *Rejected* — `Revision` is intentionally kind-agnostic
   content; per-kind schema does not belong there, and it cannot see evidence that lives in refs vs.
   payload uniformly.
2. Enforce in the append engine's kind validators (the plan's original design). **Recommended.**
3. Leave enforcement in userspace (as this app does). *Rejected as the permanent answer* — it makes
   "no naked numbers" a convention each app must re-implement and can silently skip, directly
   contradicting the constitution's "enforced by code, not convention" and the category claim.

**Recommendation.** In a future kernel amendment cycle, implement the four kind validators in the
append path (invoked from `prepare()` for `CreateNode`/`AddRevision`): reject a `CLAIM` lacking a
non-empty `method`, an in-range `confidence`, and ≥1 evidence-family reference; reject a `DECISION`
that neither cites ≥1 claim nor sets a `judgment_call` marker. Add `ClaimLawTest`/`DecisionLawTest`.
Until then, applications **must** enforce the laws in userspace exactly as `kinds/Claims` and
`kinds/Decisions` do here.

### KAP-2 — Make the verb→family registry real, or correct the docs ("closed families, open verbs") — **Medium**

**Problem.** MANIFESTO Article III declares "closed families, open verbs," and `Verb`'s javadoc states
that *"which family a given verb name canonically belongs to is a registry concern that lives in the
kernel core."* No such registry is exposed or enforced: `Verb` is a free pairing of an arbitrary name
with any of the five families, so the same verb name (`caused`) can be filed under two different
families in the same graph, and the kernel stores both.

**Evidence.** `backend/forge-explorer/.../VerbFamilyGapTest.java` — `sameVerbNameDifferentFamilies`
constructs `new Verb("caused", CAUSALITY)` and `new Verb("caused", COMPOSITION)` and shows the kernel
appends an edge using the mis-filed verb without objection; `userspaceCatalogIsConsistent` shows the
app's own `Verbs` catalog restores one-family-per-name discipline.

**Root cause.** `Verb` is a pure value type; the "registry in kernel core" the javadoc references does
not exist as a public or enforced artifact.

**Constitutional impact.** Low. The families themselves remain closed (exactly five), which is the load
-bearing invariant traversal relies on. Whether Article III actually requires *global verb-name
uniqueness* is genuinely debatable — it can be read as "each verb belongs to exactly one family *in a
given assertion*," which the current `Verb` already guarantees. So this may be a documentation-vs-code
mismatch rather than a law violation.

**ADR impact.** None required. If a registry is adopted, a small ADR would record it.

**Alternatives / recommendation.** In order of increasing cost: (a) **[doc]** correct the `Verb`
javadoc to stop asserting a kernel-core registry exists — cheapest, and possibly sufficient; (b) ship
an *optional* canonical verb registry as a public convenience (names→family) that apps may consult, as
this app's `Verbs` does, without making verbs less open; (c) hard-enforce a global name→family mapping
in the kernel — *not recommended*, as it erodes the "open verbs" half of the invariant. **Recommended:
(a), optionally (b).** This is the lighter of the two proposals and may resolve entirely as a doc fix.

---

## 8. Final engineering assessment

The Forge Kernel is a **solid, minimal, well-documented substrate that a real engineering application
can be built on using only its public API.** The core design choices vindicated themselves under a
realistic workload: the six operations were sufficient, content addressing and CAS names were exactly
the right primitives, sealed types made consumption safe, and the Reproducer/Subscription SPIs made
autonomy and reproduction easy to add in userspace. The integration surface reduced to a single facade
class — a strong signal of a clean dependency.

Dogfooding did its job: it found what internal review had not. **Two of the ten laws — the Claim Law
and the Decision Law — ship unenforced**, which means the platform's flagship "no naked numbers"
guarantee currently rests on caller discipline rather than kernel physics. That is a real defect
against the constitution, captured here with reproducing evidence and raised as KAP-1 rather than
patched around; a second, softer doc/registry gap is raised as KAP-2. Both are additive kernel
amendments for a future cycle; neither blocks building on the kernel today, because a well-behaved
application can (and this one does) enforce the laws in userspace in the meantime.

No unjustified change was proposed, no kernel internal was needed, and neither the kernel nor the
constitution was modified.

---

**Forge Kernel Phase 1.5 Complete.**
The kernel has successfully passed its first real-world dogfooding exercise and is ready to serve as
the stable foundation for Phase 2 — with KAP-1 (enforce Laws 5 & 6) recommended as the first kernel
amendment to schedule before Phase 2 relies on the Claim as a load-bearing guarantee.

*Phase 2 is not begun.*
