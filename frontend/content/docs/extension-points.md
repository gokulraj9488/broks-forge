# Extension Points

The seams designed to be extended, and what each one costs.

## Add a model provider

**Where:** `modules/provider` and `modules/model`.

Providers are configuration plus an adapter that knows how to call the vendor's API and read its
response. Adding one means implementing the adapter and registering it — credential encryption,
health checks and failure classification are inherited.

**You get for free:** encrypted credential storage, provider attribution on failures, cost and token
accounting, and appearance in the Forge Graph and provider-failure analysis.

## Add an evaluation metric

**Where:** `modules/evaluation`, `EvaluationMetricType` and its evaluator.

Add the enum constant with its category (`QUALITY`, `PERFORMANCE`, `COST`) and implement the
evaluator. If the metric calls a model — like `LLM_JUDGE` — it participates in **metric execution
failure** tracking, which keeps "the judge was rate limited" distinct from "the answer scored badly".

**Also update:** the failure classifier's `knowledgeKeyFor` mapping, so a failing metric produces a
useful root-cause finding rather than falling through to the generic bucket.

## Add a Brok intent

**Where:** `modules/brok`.

Three steps:

1. **`BrokIntent`** — add the enum constant with its weighted phrases. Weights are explicit so a
   distinctive engineering phrase always beats an incidental one appearing in the same sentence.
   Declaration order breaks ties, so specific intents go above general ones.
2. **`BrokService`** — add the handler and its `switch` case. Read from `BrokRecord`; compose with
   `BrokAnswerBuilder`.
3. **`BrokIntent.answerable()`** — add the question if it should be offered when Brok refuses.

**Rules the handler must obey**, all enforced by the integration test:

- Every statement declares an epistemic status and its basis.
- Every reference points at a real record (the id prefix is checked).
- Every recommendation ends in a known action kind.
- When the record cannot answer, say so — never improvise.

## Add an Engineering Brief

**Where:** `BrokBriefService`.

Add the kind to `KINDS`, add the composer, and add its title. A brief must follow the constitutional
narrative: **what happened → why → evidence → impact → recommendation → next action.**

## Add an investigation cause

**Where:** `InvestigationService`.

Causes belong to one of four layers: `immediate`, `contributing`, `historical`, `related-change`.
Add the derivation, and give the cause an epistemic status, a confidence, its evidence ids and an
action that tests or resolves it.

**Be honest about the layer.** A correlation is `related-change` with status `inferred`, not
`immediate` with status `derived`. The layers carry meaning, and mislabelling one is worse than
omitting it.

## Add an action kind

**Where:** `BrokActions` (backend) and `brok-actions.ts` (frontend).

An action must name a surface that **already exists**. The API carries the kind; the client resolves
the route. Adding an action that points at a page you intend to build later is exactly the failure
this split prevents.

## Add an artifact type

The largest extension, touching several layers:

1. Module with entity, repository, service, controller, Flyway migration.
2. Register it in the platform's derivation so it appears in the Registry and Forge Graph.
3. Add it to `substrate.ts` on the frontend for its structural identity and icon.
4. Add it to `artifact-links.ts` so references resolve to its workspace.
5. If versioned, wire it into the AI Git revision timeline.

## Add a documentation page

**Where:** `frontend/src/lib/docs.ts` and `frontend/content/docs/`.

Add the entry to the right section in `DOC_SECTIONS` and write the markdown file. The sidebar, the
index, the sitemap, per-page metadata and prev/next navigation all derive from that registry — there
is no second list to update.

## What is deliberately not extensible

**The epistemic vocabulary.** Verdict states, epistemic statuses and the confidence ladder are
fixed. If a surface needs a sixth verdict state, the design is wrong — the constraint is what makes
the language mean the same thing everywhere.

**Answer composition.** Content enters an answer through the record snapshot or not at all. There is
no plugin point for injecting text, and that is the guarantee, not an oversight.

**The layer rule.** A reasoning application may not own a table. If an extension needs one, it
belongs in the module that owns the underlying record.

See also: [Module Structure](/docs/module-structure) ·
[Engineering Principles](/docs/engineering-principles) · [Contributing](/docs/contributing)
