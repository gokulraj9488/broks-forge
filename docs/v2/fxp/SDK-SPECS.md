# Forge Experience Platform — SDK Specifications

**Deliverable 6.** All SDKs expose the **same conceptual API** — the Java `ForgeClient` is the
reference; Python and TypeScript mirror it exactly. "Same conceptual API" is enforced by construction:
each binding is a projection of the one API, and every method returns a proof object carrying `asOf`.

## Shared conceptual surface

```
client.studio.create(type, payload, links) -> Object
client.studio.revise(object, payload, links) -> Object
client.studio.authorClaim(type, statement, method, confidence, evidence) -> Object
client.studio.recordDecision(type, statement, judgmentCall, links) -> Object
client.studio.commit(branch, snapshot, message) -> Commit
client.studio.tag(name, commit, role, message) -> Tag

client.explorer.provenance(id) -> Provenance{ ancestors, certificate, asOf }
client.explorer.impact(id) -> Impact{ dependents, criticalPath, byKind, asOf }
client.explorer.dependencies(id) -> DependencySet{ nodes, criticalPath, asOf }
client.explorer.rootCause(id) -> CausalTrace{ causes, anomalies, sound, asOf }
client.explorer.confidence(id) -> Confidence{ value, weakestLink, asOf }
client.explorer.evidence(id) -> [Node]
client.explorer.explain(id) -> Explanation{ steps, leaves, complete, gaps, asOf }

client.review.reviewCommit(from, to) -> CommitReview
client.review.approve(id, statement) / reject(id, reason) -> Approval

client.copilot.ask(id, intent) -> GroundedAnswer{ grounded, narrative, proof, asOf }

client.reproduce(id) -> ReproduceResult
client.validate() -> PlatformHealth
client.search(text) -> [Object]
```

## Java (reference — implemented)
The `forge-fxp` module *is* the Java SDK: `ForgeClient.open(repository)` →
`client.studio()/explorer()/review()/copilot(model)`. Every result is an immutable record carrying
`asOf`. This is the definition the other SDKs conform to.

```java
ForgeClient client = ForgeClient.open(repository);
Provenance p = client.explorer().provenance(modelId);
GroundedAnswer a = client.copilot(new TemplateLanguageModel()).ask(deploymentId, Intent.WHY_IN_PRODUCTION);
```

## Python (specified — REST-backed)
Idiomatic snake_case over the REST binding; identical semantics and identical `as_of` fields.

```python
client = ForgeClient(base_url, token, org)
p = client.explorer.provenance(model_id)      # -> Provenance(ancestors=[...], certificate=..., as_of=27)
a = client.copilot.ask(deployment_id, Intent.WHY_IN_PRODUCTION)   # -> GroundedAnswer(grounded=True, proof=..., as_of=27)
assert a.grounded or a.proof.empty            # refusal carries an empty proof, never invented text
```

## TypeScript (specified — REST-backed)
camelCase, Promise-based, over the same REST binding; identical semantics and `asOf` fields.

```ts
const client = new ForgeClient({ baseUrl, token, org });
const p = await client.explorer.provenance(modelId);   // Provenance { ancestors, certificate, asOf }
const a = await client.copilot.ask(deploymentId, Intent.WhyInProduction);
if (!a.grounded) console.log(a.narrative);             // an honest refusal, not a hallucination
```

## Conformance
An SDK is conformant iff, for every method, it (a) returns the same proof fields including `asOf`, (b)
surfaces platform error codes verbatim, and (c) never adds a client-side computed engineering answer.
A shared conformance suite runs the three reference workflows (W1–W3) against each binding and asserts
identical proofs at equal `asOf`. The Java binding's workflow tests
([`workflow/ReferenceWorkflowsTest`](../../../backend/forge-fxp/src/test/java/com/broksforge/fxp/workflow/ReferenceWorkflowsTest.java))
are the executable reference for that suite.

> Scope note: the Java SDK is implemented and tested here. The Python and TypeScript SDKs and the REST
> server are specified against this contract; they are thin transport bindings with no logic of their
> own, generated from and verified against the conformance suite at build-out.
