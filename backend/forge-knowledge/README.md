# Forge Knowledge System

The **semantic layer** of Brok's Forge V2 — the canonical ontology of AI Engineering built on the frozen
Forge Kernel. A standalone Maven library that consumes **only the public kernel API**
(`kernel-core` / `kernel-api` 1.0.0); it uses no kernel internal and requires no kernel change.

Design documents: [`../../docs/v2/knowledge/`](../../docs/v2/knowledge/README.md) — ontology, object &
relationship catalogs, ADRs, architecture, and the architecture review.

## What it provides

- **The ontology as data** (`ontology/`): `Ontologies.forge()` returns the canonical, self-consistent
  ontology — every AI-engineering object type mapped to a kernel kind + subtype + payload schema, and
  every relationship type mapped to an edge family with endpoint/cardinality constraints.
- **Semantic validation** (`validate/`): `KnowledgeValidator` enforces payload schemas, relationship
  endpoints, cardinalities, and cross-object invariants **before** anything is appended.
- **A typed façade** (`graph/`): `KnowledgeGraph.define / addRevision / relate / deploy` build knowledge
  objects as kernel nodes; `KnowledgeView` folds the log back into typed objects and relationships.
- **Import/export** (`io/`): deterministic canonical documents for the ontology and for subgraphs.
- **Extension SPI** (`spi/`): `OntologyModule` to contribute new types; `ontology.PayloadCheck` for
  custom payload rules.

## Build

```bash
# Prereq: the kernel is installed to the local repo (mvn -o install in ../kernel).
cd backend/forge-knowledge
mvn -o test        # compile + 22 tests, offline
```

## Example

```java
KnowledgeGraph kg = KnowledgeGraph.open(Kernels.inMemory(), org, actor, Ontologies.forge());

KnowledgeObject provider = kg.define(ObjectTypes.PROVIDER, obj("name","anthropic"));
KnowledgeObject model    = kg.define(ObjectTypes.MODEL, obj("model_id","claude-sonnet-5"),
                                     Link.of(Verbs.USES, provider));
KnowledgeObject prompt   = kg.define(ObjectTypes.PROMPT, obj("text","Answer: {{ticket}}"));
KnowledgeObject agent    = kg.define(ObjectTypes.AGENT, obj("name","support-agent"),
                                     Link.of(Verbs.USES, model), Link.of(Verbs.USES, prompt)); // CI-4 enforced
KnowledgeObject run      = kg.define(ObjectTypes.RUN, obj("status","ok"),
                                     Link.of(Verbs.EXECUTED, agent));
KnowledgeObject verdict  = kg.define(ObjectTypes.EVALUATION_VERDICT, claimPayload,
                                     Link.of(Verbs.CITES, run));                                // Claim law enforced
kg.deploy(Name.of("deploy/prod/support-agent"), ObjectTypes.DEPLOYMENT,
          agent, environment, List.of(verdict), "ship it");

KnowledgeView view = kg.view();      // typed projection folded from the log
```

Invalid graphs are rejected before append: an Agent without a Model, a Claim without evidence, an illegal
endpoint type, a wrong payload field, or revising an Observation all throw `KnowledgeException`.

## Boundaries

Only the semantic layer. **No** AI Git, Forge Graph, applications, or UI (Phase 3+). Depends on the
kernel; applications depend on this module, never the reverse.
