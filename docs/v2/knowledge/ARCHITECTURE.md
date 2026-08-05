# Forge Knowledge System — Architecture

The semantic layer's design. It follows the [Ontology](ONTOLOGY.md) and the [ADRs](adr/README.md), and
consumes **only the public Forge Kernel API** (kernel-core / kernel-api 1.0.0), exactly as any external
project would. No kernel internal, no kernel change.

## 1. Module boundary

One module, `forge-knowledge`, a standalone Maven project (no parent; not part of the kernel reactor).
It depends on `kernel-core` and `kernel-api` and nothing else (no Spring, no ORM, no AI). It is the
reusable library that turns kernel primitives into AI-engineering knowledge; applications (Phase 3+)
depend on it, never the reverse.

```
forge-knowledge  ──depends──▶  kernel-core ──▶ kernel-api        (public API only)
      ▲
      └── (Phase 3+ applications depend on forge-knowledge)
```

## 2. Package structure

```
com.broksforge.knowledge
├── ontology/          the ontology AS DATA (KN-0001) — the vocabulary
│   ├── Cardinality          EXACTLY_ONE | ZERO_OR_ONE | ONE_OR_MORE | AT_LEAST_TWO | ANY
│   ├── FieldType            STRING | NUMBER | BOOL | OBJECT | ARRAY | ANY
│   ├── PayloadField         (key, FieldType, required)
│   ├── PayloadSchema        required/optional fields + legal role vocabulary
│   ├── ObjectType           name + kernel Kind + subtype + PayloadSchema  (a knowledge object type)
│   ├── RelationType         verb + EdgeFamily + fromType + toType + Cardinality + intrinsic
│   ├── Ontology             the registry: object types, relation types, self-consistency check
│   ├── Ontologies           factory: the canonical Forge ontology (all catalog types registered)
│   ├── ObjectTypes          constants for well-known types (ObjectTypes.AGENT, .RUN, …)
│   └── Verbs                verb→family catalog (userspace discipline, KAP-2)
├── validate/          semantic enforcement ABOVE the kernel (KN-0004)
│   ├── ValidationIssue      (severity, code, message, path)
│   ├── ValidationResult     ok() | issues; throwIfInvalid()
│   └── KnowledgeValidator   payload-schema + relationship + cardinality + CI-* checks
├── graph/             the typed façade over ForgeKernel
│   ├── KnowledgeObject      typed handle (ObjectType + Address.Revision + Revision)
│   ├── Link                 a relationship to assert at creation (RelationType + target hash)
│   ├── KnowledgeGraph       define/relate/resolve/deploy — validates, then appends via the kernel
│   └── KnowledgeView        projection: folds kernel.log into typed objects + relationships; queries
├── io/                import/export (ontology + subgraphs) as canonical values
│   ├── OntologyExport       ontology → CanonicalValue (deterministic, JCS bytes)
│   └── GraphExport          a KnowledgeView subgraph → CanonicalValue
└── spi/               extension points (KN-0001)
    ├── OntologyModule       contribute ObjectTypes/RelationTypes to an Ontology.Builder
    └── PayloadCheck         custom payload validation hook for an ObjectType
```

## 3. Dependency graph (acyclic)

```
io ──▶ graph ──▶ validate ──▶ ontology ──▶ (kernel-core, kernel-api)
                    ▲             ▲
spi ────────────────┘─────────────┘        (spi is depended on by ontology's builder)
```

No cycles. `ontology` knows the kernel value types; `validate` knows `ontology`; `graph` orchestrates
`validate` + the kernel; `io` serializes; `spi` is interfaces only.

## 4. Public API (the surface applications use)

- **Vocabulary:** `Ontologies.forge()` → the canonical `Ontology`; `ObjectTypes.*`, `Verbs.*`.
- **Writing:** `KnowledgeGraph.open(kernel, org, actor, ontology)` then
  `define(ObjectType, payload, Link...) → KnowledgeObject` (validates, then `kernel.append`);
  `relate(from, RelationType, to)` (extrinsic edges); `deploy(name, target, env, restingOn…)`.
- **Reading:** `KnowledgeView.of(kernel, org, ontology)` → `objects(ObjectType)`, `object(NodeId)`,
  `relationships()`, `resolve(Name)`, plus kernel `traverse`/`closure` for graph walks.
- **Validation:** `KnowledgeValidator.validate(ObjectType, payload, links)` → `ValidationResult`.
- **Errors:** `ValidationResult.throwIfInvalid()` throws `KnowledgeException` before any append.
- **IO:** `OntologyExport.toCanonical(ontology)`, `GraphExport.toCanonical(view)`.

## 5. Storage strategy (public kernel APIs only)

There is **no separate store**. A knowledge object *is* a kernel node:

- `define(type, payload, links)` → build a `Revision(type.kind(), type.subtype(), payload, intrinsicRefs)`
  → `kernel.append(CreateNode)`. Intrinsic `Link`s become kernel `Ref`s (composition/derivation/
  evidence/intent) inside the revision; extrinsic links become `AssertEdge` appends after.
- Versioning → `kernel.append(AddRevision)` (Artifacts/Claims only; the validator blocks it for
  Observations/Decisions, CI-6).
- Deployment → a `decision/deployment` node **plus** `kernel.append(RepointName)` on `deploy/<env>/…`.
- Durability/tenancy/provenance/audit → entirely the kernel's (in-memory or PostgreSQL backend,
  unchanged). The Knowledge System adds no persistence code.

## 6. Projection strategy

`KnowledgeView.of(kernel, org, ontology)` replays `kernel.log(org)` once and folds each `NodePut` into a
typed `KnowledgeObject` by looking up `(kind, subtype)` in the ontology; unknown subtypes are surfaced
as `untyped` (forward-compat). Edges (intrinsic refs + extrinsic asserted−retracted) become typed
`Relationship`s. This is the same "fold the log" pattern the Phase 1.5 explorer validated, now
ontology-typed. Projections are derivations, rebuildable at any time (kernel Law 10 / ADR-V2-0001).

## 7. Query strategy

Foundation-level, typed, no query language:
- `view.objects(type)`, `view.object(nodeId)`, `view.byName(path)`, `view.relationships(family)`.
- Graph walks reuse the kernel: `kernel.traverse(Query)` and `kernel.closure(hash)` (e.g. an Agent's
  reproducibility closure). A richer query language is explicitly deferred (Future Evolution).

## 8. Import / export strategy

- **Ontology export:** `OntologyExport.toCanonical` renders the ontology (object types, schemas,
  relation types) to a `CanonicalValue`; `CanonicalSerializer.toBytes` gives deterministic JCS bytes —
  a portable, diffable, hashable ontology document for tooling and cross-system exchange.
- **Subgraph export:** `GraphExport.toCanonical` renders a `KnowledgeView`'s objects + relationships to
  a `CanonicalValue`. Export is a pure read; the deterministic encoding means two exports of the same
  state are byte-identical.
- **Import:** replay of exported objects through `KnowledgeGraph.define` (so imports are re-validated and
  re-signed as ordinary appends — Law 9). The foundation ships export + the validated `define` path;
  a bulk importer is a thin loop over them (Future Evolution).

## 9. Extension SPI

- `OntologyModule.contribute(Ontology.Builder)` — a third party registers new `ObjectType`s and
  `RelationType`s (KN-0001); `Ontologies.forge()` is itself one module; others compose on top.
- `PayloadCheck` — an `ObjectType` may carry a custom payload validator beyond the declarative schema
  (e.g. cross-field rules), invoked by `KnowledgeValidator`.
- New kinds/families are **not** an extension point — that is a kernel amendment.

## 10. Repository layout

```
backend/forge-knowledge/
├── pom.xml                         standalone; depends on kernel-core + kernel-api 1.0.0
├── README.md
└── src/
    ├── main/java/com/broksforge/knowledge/{ontology,validate,graph,io,spi}/…
    └── test/java/com/broksforge/knowledge/…
```

## 11. What the foundation implements (scope)

Per the mission ("implement only what is necessary to establish the foundation; no AI Git, no Forge
Graph, no apps, no UI"):

- The **framework**: ontology model, the canonical `Ontology` with **all** catalog types registered as
  data, the validator, the typed façade, the projection, and IO.
- **Exemplar end-to-end coverage** of every kernel kind and the definition/result pattern (Prompt,
  Model, Provider, Agent → Run → EvaluationVerdict/Capability → Deployment), proving the ontology is
  buildable and enforceable.
- Rich per-object helper APIs beyond `define(type, payload, links)` are **out of scope** for the
  foundation (Future Evolution) — the generic, ontology-driven `define`/`relate` path already builds
  any registered type.
