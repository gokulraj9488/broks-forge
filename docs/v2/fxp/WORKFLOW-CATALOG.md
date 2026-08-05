# Forge Experience Platform — Workflow Catalog

**Deliverable 2.** Every experience corresponds to a real engineering task. Each workflow below is
specified as a sequence of steps, each mapped to the exact platform capability that performs it.
The three **reference workflows** (W1–W3) are implemented as executable, tested end-to-end
demonstrations (`backend/forge-fxp/src/test/.../workflow/`).

Notation: `Studio`, `Explorer`, `Review`, `Copilot` are the experiences; `KG` = KnowledgeGraph
(write), `FVCS` = Repository, `FKGE` = KnowledgeGraphEngine, `K` = kernel.

---

## Reference workflow W1 — Change → Version → Evaluate → Claim → Decide → Promote → Explain

The core daily loop, end to end.

| # | Engineer's act | FXP call | Platform capability |
|---|----------------|----------|---------------------|
| 1 | edit a prompt | `Studio.revise(prompt, "v2")` | `KG.addRevision` → new revision (new hash) |
| 2 | cut a version | `Studio.commit(main, [prompt', model, agent], "improve tone")` | `FVCS.snapshot` + `FVCS.commit` |
| 3 | run an evaluation | `Studio.recordObservation(RUN, …executed→agent)` | `KG.define` (Observation) |
| 4 | record the belief | `Studio.authorClaim(EVALUATION_VERDICT, conf 0.92, cites→run)` | `KG.define` (Claim, Law 5) |
| 5 | approve | `Review.approve(deployment)` / `Studio.recordDecision(DEPLOYMENT, rests_on→verdict)` | `KG.define` (Decision, Law 6) |
| 6 | promote | `Studio.tag("release/1.4", commit, RELEASE)` | `FVCS.tag` |
| 7 | explain | `Explorer.explain(deployment)` | `FKGE.whyApproved` → proof tree |

**Acceptance:** step 7 returns a *complete* explanation whose leaves are the run (Observation) and
the claims — reproducibly, at a cited `LogPosition`.

## Reference workflow W2 — Incident → Provenance → Causal chain → Responsible evaluation → Historical decisions → Reproducible explanation

Root-cause under pressure.

| # | Act | FXP call | Capability |
|---|-----|----------|-----------|
| 1 | record the incident | `Studio.recordObservation(INCIDENT, severity high)` + `Studio.link(deployment, CAUSED, incident)` | `KG.define` + `KG.relate` |
| 2 | trace the cause | `Explorer.rootCause(incident)` | `FKGE.rootCause` (causality IN, log-position-sound) |
| 3 | walk provenance of the cause | `Explorer.provenanceOf(deployment)` | `FKGE.provenanceOf` (certified) |
| 4 | find the responsible evaluation | filter provenance to `EVALUATION_VERDICT` | `FKGE` + semantic filter |
| 5 | review the historical decisions | `Review.history(main)` / `Explorer.asOf(pos)` | `FVCS.history` / `FKGE.asOf` |
| 6 | produce a reproducible explanation | `Copilot.ask(incident, ROOT_CAUSE)` | grounded `GroundedAnswer` + proof |

**Acceptance:** the causal chain names the deployment; its provenance reaches the weak evaluation;
the answer is reproducible (`asOf`) and carries its proof.

## Reference workflow W3 — "Why is this model in production?" (the executive question)

One question, a deterministic dossier.

`Copilot.ask(model, WHY_IN_PRODUCTION)` composes, deterministically:

- **History** — `FVCS.history` of the line that deployed it.
- **Evidence** — `FKGE.evidenceFor` the claims behind the deployment.
- **Evaluations** — provenance filtered to evaluation verdicts / benchmark scores.
- **Decisions** — the `Deployment`/`Promotion`/`Approval` chain (`FKGE.explain`).
- **Policies** — `Policy`/`Guardrail` artifacts the decisions rest on / enforce.
- **Provenance** — `FKGE.provenanceOf(model)` (certified).
- **Confidence** — `FKGE.confidenceOf(deployment)` (min over supporting claims).

**Acceptance:** the dossier is produced without any human-written narrative of record — every line
is a platform fact, cited, reproducible. The LLM may phrase the cover letter; the dossier is proof.

---

## Studio workflows

| Task | Call | Capability |
|------|------|-----------|
| create an artifact | `Studio.create(type, payload, links)` | `KG.define` |
| record an observation | `Studio.recordObservation(type, payload, links)` | `KG.define` (single-revision) |
| author a claim | `Studio.authorClaim(type, statement, method, confidence, evidence)` | `KG.define` (Law 5 enforced) |
| record a decision | `Studio.recordDecision(type, restsOn / judgmentCall)` | `KG.define` (Law 6 enforced) |
| browse knowledge | `Studio.browse(type?)` | `KnowledgeView.allObjects/objects` |
| navigate versions | `Studio.history(branch)` / `Studio.checkout(commit)` | `FVCS.history/checkout` |
| explain reasoning | `Studio.explain(node)` | `FKGE.explain` |

## Explorer workflows
Provenance, dependency, impact, lineage, root cause, confidence, evidence, neighborhood, similarity,
critical path — each a direct `FKGE` call, each returning a proof carrying `asOf`. Explorer writes
nothing.

## Review workflows

| Task | Call | Capability |
|------|------|-----------|
| review a commit | `Review.reviewCommit(commit)` | `FVCS.diff(parent, commit)` + records |
| semantic diff | `Review.semanticDiff(a, b)` | `FVCS.diff` + `FKGE.impactOf` of changes |
| review an evaluation | `Review.reviewClaim(verdict)` | `FKGE.evidenceFor` + `confidenceOf` |
| review a decision | `Review.reviewDecision(decision)` | `FKGE.explain` |
| approve / reject | `Review.approve(decision)` / `Review.reject(decision, reason)` | `KG.define`(Approval) + `KG.relate`(APPROVES/REJECTS) |
| policy / deployment approval | same, over `Policy` / `Deployment` subjects | AI-PR triad reuse |

## Copilot workflows
`Copilot.ask(subject, intent)` → grounds through FKGE → refuses if no proof → narrates a non-empty
proof → returns `GroundedAnswer(prose, proof, asOf)`. Intents: `WHY`, `PROVENANCE`, `IMPACT`,
`DEPENDENCIES`, `ROOT_CAUSE`, `CONFIDENCE`, `EVIDENCE`, `WHY_IN_PRODUCTION`.

## CLI workflows
`forge explain|history|diff|impact|provenance|root-cause|confidence|evidence|search|validate|reproduce`
— each a one-line invocation of the conceptual API, emitting deterministic, greppable text plus the
`asOf` position for reproducibility.
