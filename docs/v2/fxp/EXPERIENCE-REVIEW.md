# Forge Experience Platform — Experience Review Report

**Deliverable 11.** An independent panel — AI Engineers, Platform Engineers, UX Designers, SREs,
Security Engineers, Compliance Engineers, Developer Relations, Technical Writers — attempts to reject
the experiences. Findings are graded **Refined (fixed)**, **Accepted with rationale**, or **No-issue**.

## Pre-review refinement (found by the test suite)

**R0 — the engine went stale after authoring.** The first implementation cached a single
`KnowledgeGraphEngine` at `ForgeClient.open()`. Because FXP opens the client *before* authoring, every
read then reasoned over an empty projection ("unknown node"). **Fixed** before review: the engine is a
pure projection of the log, so each experience operation now obtains a **fresh engine reflecting the
current log** (`ForgeClient` supplies `() -> KnowledgeGraphEngine.open(repo)`; each operation folds one
consistent snapshot). Determinism is preserved — every answer still carries the `asOf` it was computed
at. All 19 tests, including the three reference workflows, pass on the corrected design.

## Per-role attack

### AI Engineers — workflow friction, missing explanations
- Every one of the seven daily questions maps to a single call; the three reference workflows run end
  to end. ✓
- **F1 (id-centric API).** Operations take `NodeId`s; engineers think in names. **Accepted with
  mitigation:** `search(text)` resolves names → ids, and a name→id resolver is a pure read projection a
  UX/CLI layer adds without new platform logic. No engineering answer is computed client-side.
- Missing explanations: impossible by construction — every knowledge result is a proof; the Copilot
  refuses rather than omit. ✓

### Platform Engineers — hidden coupling, redundant functionality
- **No hidden coupling:** FXP imports only public packages of the four layers; zero
  `core.memory/store/codec/node/op`, `store.postgres`, `.impl`, `.internal` (audited). The platform
  module graph has no dependency on FXP or its integrations. ✓
- **No redundant/duplicated logic:** Explorer is a pass-through to FKGE; Studio wraps the write APIs;
  Review composes FVCS diff + FKGE impact; `search` is a substring filter over the read view (a
  projection, not re-implemented reasoning). No traversal/diff/merge/reasoning is re-implemented. ✓

### UX Designers — usability, accessibility
- Four experiences by *mode of work* (write/understand/judge/ask), not by screen; each result ships a
  "show proof" payload (`asOf` + proof). ✓
- **Accessibility:** proofs are text-first (an ordered list of typed steps); any graph visual is a
  rendering of that list, never the source — so every answer is fully available to assistive tech. ✓

### SREs — scalability, operations
- **F2 (per-operation full-log fold).** Each read re-folds the org log (O(n)). **Accepted with
  mitigation:** (a) it is correct and deterministic; (b) answers are content-addressed and `asOf`-
  stamped, so a `(query, asOf)` cache is *always safe* and never changes a result (UX-Architecture §8);
  (c) the scale ceiling — indexed reads / incremental projection instead of a full fold — is a **known
  platform amendment** (the FKGE node-enumeration gap), correctly routed to the amendment process, not
  worked around in the app. No correctness impact; a documented performance boundary.
- Stateless services + single stateful kernel tier → trivial horizontal scaling and safe blue/green
  (Deployment Guide). ✓

### Security Engineers — security issues
- **Attribution:** every write carries the authenticated principal as the kernel `ActorId`; no
  anonymous append. ✓
- **Copilot exfiltration guard:** the language model receives only a `GroundingContext` of facts, never
  the graph — structurally incapable of leaking beyond the proof, and never invoked without one. ✓
- **F3 (single-org trust boundary).** The library is bound to one org and trusts the injected actor;
  authentication and authorization live at the gateway (UX-Architecture §4). **Accepted (documented
  boundary):** org isolation is a *kernel invariant* (one log per org), so tenant separation is enforced
  by the platform, not re-implemented; the library is a per-tenant handle by design.

### Compliance Engineers
- Every answer is reproducible (`asOf`), evidence-backed (Laws 5/6), attributed, and chain-verifiable
  (`validate`). The "why in production" dossier (W3) is a deterministic compliance artifact with no
  human-written record of narrative. No gap. ✓

### Developer Relations — API consistency
- One conceptual API mirrored across CLI/SDK/REST; a conformance suite (the reference workflows)
  pins identical proofs at equal `asOf` across bindings. ✓

### Technical Writers — documentation gaps
- Experience Model, Workflow Catalog, UX Architecture, API/CLI/SDK specs, Integrations, Deployment,
  Operations, and Developer guides are present. ✓

## Success-criteria audit

| Criterion | Result |
|---|---|
| Every workflow supported | ✅ Workflow Catalog; W1–W3 implemented & green |
| Every engineering answer evidence-backed | ✅ proofs on every read; Copilot refuses ungrounded |
| Every explanation reproducible | ✅ `asOf` on every result; deterministic re-fold |
| Every application consumes only public platform APIs | ✅ import audit: public packages only, zero internals |
| No frozen layer modified | ✅ git shows only new untracked dirs; four layers byte-for-byte unchanged |
| Integrations remain adapters | ✅ one-way; platform has no dependency on `integrate` |
| No meaningful architectural weakness | ✅ R0 fixed; F1–F3 documented boundaries |

## Verdict
The experiences add no engineering logic: they route, shape, and present proofs the platform computes
and can independently reproduce. The one real defect (R0 — stale projection) was caught by the tests
and fixed before review; the remaining findings are deliberate, documented boundaries (a UX-level name
resolver, a caching/amendment-bounded scale ceiling, and a gateway-owned auth boundary). **No
meaningful architectural weakness remains.**

**Verification:** `forge-fxp` — **19 tests green** (`mvn -o test`, offline), including the three
reference workflows. Public APIs only; the kernel, knowledge system, FVCS, and FKGE re-verified
unchanged.
