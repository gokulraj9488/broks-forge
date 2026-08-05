# Forge Experience Platform (FXP)

The canonical Forge applications — Studio, Explorer, Review, Copilot, and the CLI — over the frozen
platform (Kernel, Knowledge System, FVCS, FKGE). A standalone Maven library that consumes **only public
platform APIs** (`forge-fkge` 2.0.0 and, transitively, FVCS, the knowledge system, and the kernel). It
holds no engineering logic, stores nothing, and modifies no frozen layer.

Design documents: [`../../docs/v2/fxp/`](../../docs/v2/fxp/README.md) — experience model, workflow
catalog, UX architecture, API/CLI/SDK specs, integrations, review, deployment/operations/developer
guides.

## What it provides

- **`ForgeClient`** — the single conceptual API every surface (CLI, SDKs, REST) mirrors:
  `studio()`, `explorer()`, `review()`, `copilot(model)`, plus `reproduce`, `validate`, `search`.
- **`studio/`** — the authoring experience (create/revise, author claims, record decisions, version,
  tag, browse, explain) — the only write path.
- **`explore/`** — the understanding experience: a thin, read-only projection of FKGE
  (provenance, dependency, impact, lineage, root cause, confidence, evidence) + the `ProductionDossier`.
- **`review/`** — the judgement experience: commit review (diff + blast radius), claim/decision review,
  and approvals recorded as first-class `Approval` decisions (`approves`/`rejects`).
- **`copilot/`** — grounded Q&A under a hard contract: the LLM narrates FKGE proofs and refuses when
  there are none. `LanguageModel` SPI + deterministic `TemplateLanguageModel` + `GroundedAnswer`/`Proof`.
- **`cli/`** — `ForgeCli`, a deterministic, greppable command surface returning text (+ `asOf`).
- **`integrate/`** — one-way edge adapters (`SourceControlAdapter`, `ModelProviderAdapter`) with Git and
  local-model reference implementations.

## Build

```bash
# Prereqs: kernel, forge-knowledge, forge-fvcs, forge-fkge installed to the local repo (mvn -o install).
cd backend/forge-fxp
mvn -o test        # compile + 19 tests (incl. 3 end-to-end reference workflows), offline
```

## Example

```java
ForgeClient client = ForgeClient.open(repository, actor);

// author → version → evaluate → claim → decide
KnowledgeObject agent = client.studio().create(ObjectTypes.AGENT, name("bot"), uses(model), uses(prompt));
CommitRef c = client.studio().commit(main, client.studio().snapshot("v2", members), "improve tone");
KnowledgeObject verdict = client.studio().authorClaim(ObjectTypes.EVALUATION_VERDICT,
        "meets bar", "offline-eval", new BigDecimal("0.92"), Link.of(Verbs.CITES, run));

// understand + judge + ask (all reproducible, all evidence-backed)
Provenance prov = client.explorer().provenance(agent.node());
DecisionReview dr = client.review().reviewDecision(deployment.node());
GroundedAnswer a = client.copilot(new TemplateLanguageModel()).ask(model.node(), Intent.WHY_IN_PRODUCTION);
```

## Boundaries
Applications orchestrate; the platform reasons. **No** engineering logic, **no** second source of truth,
**no** frozen-layer modification. Integrations are one-way adapters. A capability that would need a
frozen-layer change is a **stop-and-file-an-amendment** event.
