# Forge Engineering Explorer

The **Phase 1.5 kernel dogfooding application**. It exists solely to prove that the released Forge
Kernel (v1.0.0) is sufficient to build real engineering software using **only its public API** — the
kernel is consumed here exactly as any external open-source project would consume it.

> This application is **not** part of the Broks Forge product. It is a validation harness. It does not
> modify, extend, or depend on any kernel internal. If it had needed one, the kernel would have failed
> its first dogfooding exercise.

Full findings — usability review, friction points, and two Kernel Amendment Proposals — are in
[docs/v2/kernel/PHASE-1.5-DOGFOODING-REPORT.md](../../docs/v2/kernel/PHASE-1.5-DOGFOODING-REPORT.md).

---

## What it does

A small library + demo that drives a realistic AI-engineering workflow through **every major kernel
capability**:

- create/version **Artifacts**, record **Observations**, form **Claims** from evidence, make
  **Decisions** — the four kernel kinds;
- **append** immutable revisions (all six command shapes), **resolve** names (with time travel),
  **traverse** the graph, compute **closures**, **diff** revisions, **reproduce** through the SPI,
  and **subscribe** to the log — the six operations;
- exercise all **five edge families** (composition, derivation, evidence, causality, intent), both
  intrinsic (revision refs) and extrinsic (asserted edges);
- **visualize** the engineering graph as ASCII and Graphviz DOT.

## How it consumes the kernel

`pom.xml` has **no parent** and is **not** a module of the kernel reactor. It declares ordinary Maven
dependencies on the published artifacts and resolves them from the local repository:

```xml
<dependency><groupId>com.broksforge.kernel</groupId><artifactId>kernel-core</artifactId><version>1.0.0</version></dependency>
<dependency><groupId>com.broksforge.kernel</groupId><artifactId>kernel-api</artifactId><version>1.0.0</version></dependency>
```

Packages imported by the application (verified — nothing else):

```
com.broksforge.kernel.api            com.broksforge.kernel.core.command
com.broksforge.kernel.api.canonical  com.broksforge.kernel.core.engine
                                     com.broksforge.kernel.core.event
                                     com.broksforge.kernel.core.log
                                     com.broksforge.kernel.core.op
                                     com.broksforge.kernel.core.reproduce
                                     com.broksforge.kernel.core.validate
```

No `com.broksforge.kernel.core.memory`, `...core.store`, or `...core.codec` — the backend internals
are never touched.

## Package structure

```
com.broksforge.explorer
├── ForgeExplorer         ergonomic facade over ForgeKernel (the six ops, org+actor bound)
├── Handle                typed (Address.Revision + Revision) result — removes the cast/unwrap dance
├── Verbs                 userspace verb catalog: each verb name pinned to one edge family
├── kinds/
│   ├── Claims            builds CLAIM revisions that satisfy the Claim Law (evidence+method+confidence)
│   └── Decisions         builds DECISION revisions that satisfy the Decision Law (cited claims | judgment call)
├── render/
│   ├── Values            read-side helpers + pretty-printing over CanonicalValue
│   └── Payloads          renders committed LogEntry facts into audit one-liners
├── graph/
│   ├── GraphModel        folds log(org) into node/edge/name projections (read-side enumeration)
│   └── GraphRenderer     ASCII report + Graphviz DOT of the engineering graph
├── reproduce/
│   └── ChecklistReproducer   userspace Reproducer (SPI): re-runs a check-suite deterministically
├── watch/
│   ├── AuditTrailProgram     passive SubscriptionProgram (reaction = subscription)
│   └── AutoObserverProgram   reactive SubscriptionProgram (Law 9: subscription outputs are appends)
└── demo/
    └── EngineeringExplorerDemo   the narrated end-to-end walkthrough (main)
```

13 main classes (~1,600 LOC), 6 test classes, **22 tests, all green**.

## Architecture

```
        ┌─────────────────────────────────────────────────────────┐
        │              Forge Engineering Explorer (userspace)       │
        │                                                           │
        │  demo ─┐    kinds/Claims,Decisions   graph/Model,Renderer │
        │        │            │                        │            │
        │        └──────►  ForgeExplorer (facade)  ◄────┘           │
        │  reproduce/Checklist ─┐   │   ┌─ watch/AuditTrail,Auto    │
        └───────────────────────┼───┼───┼───────────────────────────┘
                                 │   │   │   public API only
        ┌────────────────────────▼───▼───▼───────────────────────────┐
        │  ForgeKernel  (append · resolve · traverse · diff ·          │
        │               reproduce · subscribe · closure · verifyChain)│
        │  Kernels.inMemory(...)                                       │
        └─────────────────────────────────────────────────────────────┘
```

The facade adds **no capability** the kernel lacks; it is the day-one boilerplate an application would
write, gathered in one place.

## Build & run

```bash
# Prereq: the kernel is installed to the local repo (mvn -o install in ../kernel).
cd backend/forge-explorer
mvn -o test                 # compile + 22 tests (prints the full narrated demo)

# Run the demo directly (UTF-8 output; also saved at docs/DEMO-OUTPUT.txt):
mvn -o -DskipTests package
java -cp "target/classes;%USERPROFILE%/.m2/repository/com/broksforge/kernel/kernel-core/1.0.0/kernel-core-1.0.0.jar;%USERPROFILE%/.m2/repository/com/broksforge/kernel/kernel-api/1.0.0/kernel-api-1.0.0.jar" \
  com.broksforge.explorer.demo.EngineeringExplorerDemo
```

A captured run is checked in at [docs/DEMO-OUTPUT.txt](docs/DEMO-OUTPUT.txt).

## API usage examples

Create and version an artifact, deploy a name, then read it back — the whole loop:

```java
ForgeKernel kernel = Kernels.inMemory(new ChecklistReproducer());
ForgeExplorer forge = ForgeExplorer.open(kernel, org, ActorId.of("engineer:alice"));

Handle promptV1 = forge.createArtifact("prompt", obj("text", "Answer: {{ticket}}"));
Handle agentV1  = forge.createArtifact("agent",  obj("name", "support-agent"),
                        Ref.of(Verbs.USES, promptV1.hash()));          // composition

Name current = Name.of("agents/support/current");
forge.deploy(current, agentV1);                                        // name → agent.v1
forge.resolve(current);                                               // → agent.v1
```

Form a claim from evidence, decide on it, then reproduce a suite:

```java
Handle latency = forge.recordObservation("metric", obj("name","p95_ms","value", num(812)));
Handle claim   = forge.create(Claims.claim("regression-verdict",
                        "p95 regressed", "welch-t-test:v2", new BigDecimal("0.87"),
                        List.of(latency.hash())));                     // evidence + method + confidence
Handle rollback = forge.create(Decisions.restingOn("rollback",
                        "roll back to v1", List.of(claim.hash())));    // cites the claim

ReproduceResult r = forge.reproduce(suite.address());                 // SPI runs, records observations
```

Snapshot, diff, and audit:

```java
Map<RevisionHash,Revision> snapshot = forge.closure(agentV1.hash()); // system snapshot; root hash = cert
Delta delta = forge.diff(promptV1.hash(), promptV2.hash());          // structural change list
boolean intact = forge.verifyChain();                                // tamper evidence
IntegrityReport report = new IntegrityScanner().scan(kernel, org);   // validation layer
```

## Capability coverage

| Kernel capability | Where exercised |
|---|---|
| append · CreateNode / AddRevision | `ForgeExplorer.create/createArtifact/addRevision`; every test |
| append · AssertEdge / RetractEdge | `ForgeExplorer.assertEdge/retractEdge`; `CapabilityMatrixTest.appendEdges` |
| append · RepointName (CAS) | `deploy/repoint`; `appendRepointCas` |
| append · Tick | `tick`; `appendTick` |
| resolve · resolveAt (time travel) | `resolveAndTimeTravel` |
| traverse (OUT/IN/BOTH) | `traverseDirections` |
| closure (system snapshot) | `closureSnapshot` |
| diff | `diffChange` |
| reproduce (SPI) | `ChecklistReproducer`; `reproduceViaSpi`, `reproduceUnsupported` |
| subscribe (passive + reactive) | `AuditTrailProgram`, `AutoObserverProgram`; `subscribeNotified` |
| verifyChain / log / revision | `auditClean`, all reads |
| four kinds | `allFourKinds` |
| five edge families | `graphModelCoversEveryKind` |
| validation layer | `IntegrityScanner` in `auditClean` |
```
