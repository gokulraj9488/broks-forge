# Forge Experience Platform — Developer Documentation

**Deliverable 14.** How to build on and extend FXP. The one rule: **applications orchestrate; the
platform reasons.** If you find yourself computing an engineering answer the platform can't
independently prove, stop — you are either misusing the API or discovering a platform amendment.

## Getting started

```java
Repository repo = Repository.open(kernel, org, actor);   // or PostgresKernels.open(...)
ForgeClient client = ForgeClient.open(repo, actor);

// author (Studio)
KnowledgeObject prompt = client.studio().create(ObjectTypes.PROMPT, obj("text", "be concise"));
// understand (Explorer) — every result carries asOf
Provenance p = client.explorer().provenance(prompt.node());
// judge (Review)
client.review().approve(deployment, "evidence sufficient");
// ask (Copilot) — grounded, refuses if unprovable
GroundedAnswer a = client.copilot(new TemplateLanguageModel()).ask(model.node(), Intent.WHY_IN_PRODUCTION);
```

## The mental model
- **Everything you write is a kernel fact** — attributed, timestamped, content-addressed. Artifacts and
  observations are inputs; claims and decisions are governed by Laws 5 & 6 (evidence / cited-claims-or-
  judgment-call) and rejected at append if unlawful.
- **Everything you read is a proof** carrying the `asOf` it was computed at. Store the `asOf` and you can
  reproduce the exact answer forever.
- **The engine is a projection.** FKGE folds the log on demand; there is no separate store to keep in
  sync and no cache that can change an answer.

## Extending FXP (additive, via SPIs — never modify a frozen layer)

| To add… | Implement | Composed at |
|---------|-----------|-------------|
| a new engineering question | FKGE `LensModule` | engine open |
| new vocabulary (object/relation types) | knowledge `OntologyModule` | ontology build |
| a Copilot model (OpenAI/Anthropic/Ollama) | FXP `LanguageModel` | `client.copilot(model)` |
| an integration | FXP `*Adapter` (`SourceControlAdapter`, `ModelProviderAdapter`, …) | edge |
| a reproducer | kernel `Reproducer` | kernel open |

If a capability cannot be expressed through these SPIs or the public read/write APIs, it requires a
frozen-layer change — **file an amendment proposal; do not extend or bypass the platform.**

## Writing a Copilot adapter
```java
LanguageModel anthropic = ctx -> callClaude(systemPrompt(ctx.intent()), ctx.facts());
GroundedAnswer a = client.copilot(anthropic).ask(subject, intent);
```
Your adapter receives only a `GroundingContext` of platform facts — never the graph. The Copilot has
already computed the proof and will have refused if there were none, so your model cannot invent
engineering truth; it can only phrase what is proven. Always surface `answer.proof()` alongside the
narrative so users can verify it.

## Writing an integration adapter
Implement the relevant `*Adapter`; read the external system, and record facts through `StudioService`
only. Never give the platform a dependency on your external system — the arrow points one way. See
`GitSourceControlAdapter` and `LocalModelProviderAdapter` for the pattern.

## Testing your extension
Follow the reference workflow tests: build a scenario through Studio, exercise your extension, and
assert on the returned proofs (including `asOf`). Use `TemplateLanguageModel` for deterministic Copilot
tests, and a throwing `LanguageModel` to prove your code refuses ungrounded questions without invoking
the model.

## Conventions
- Never compute provenance/impact/confidence/diff yourself — call the platform.
- Always propagate and display `asOf`.
- Surface platform error codes verbatim (`CLAIM_LAW`, `CAS_FAILURE`, …); don't invent error meaning.
- Keep experiences thin: an experience method should be one platform call plus shaping.
