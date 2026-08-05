# Forge Kernel — Developer & Extension Guide

For engineers building on, extending, or contributing to the Forge Kernel. Assumes the concepts in
the [constitution](../MANIFESTO.md) and [domain model](../DOMAIN_MODEL.md).

---

## 1. Getting started

```java
import com.broksforge.kernel.core.engine.*;
import com.broksforge.kernel.core.command.*;
import com.broksforge.kernel.api.*;
import com.broksforge.kernel.api.canonical.CanonicalValue;

ForgeKernel kernel = Kernels.inMemory();                     // or PostgresKernels.open(dataSource, List.of())
OrgId org = OrgId.of(UUID.randomUUID());
ActorId me = ActorId.of("engineer:alice");

// Create a continuant with a first revision.
Revision promptV1 = Revision.leaf(Kind.ARTIFACT, "prompt",
        CanonicalValue.objectBuilder().put("text", "Summarize: {{input}}").build());
Address.Revision v1 = (Address.Revision) kernel.append(org, new AppendCommand.CreateNode(promptV1), me)
        .address().orElseThrow();

// Version it.
Revision promptV2 = Revision.leaf(Kind.ARTIFACT, "prompt",
        CanonicalValue.objectBuilder().put("text", "Summarize concisely: {{input}}").build());
Address.Revision v2 = (Address.Revision) kernel.append(org, new AppendCommand.AddRevision(v1.node(), promptV2), me)
        .address().orElseThrow();

// Deploy by pointing a name at a revision (compare-and-swap).
Name prod = Name.of("prod");
kernel.append(org, new AppendCommand.RepointName(prod, v1, null), me);   // deploy v1
kernel.append(org, new AppendCommand.RepointName(prod, v2, v1), me);     // promote v2 (expects v1)

// Read.
kernel.resolve(org, prod);                       // -> v2
kernel.diff(v1.revision(), v2.revision());       // structural delta
kernel.closure(v2.revision());                   // system snapshot (composition closure)
kernel.verifyChain(org);                          // tamper-evident audit
```

## 2. The six operations (API reference)

`ForgeKernel` is the whole public surface. Everything else composes from it.

| Operation | Method(s) | Notes |
|---|---|---|
| **append** | `append(org, command, actor[, validTime])` | the only write; returns `AppendResult(entry, address)` |
| **resolve** | `resolve(org, name)`, `resolveAt(org, name, position)` | current / deterministic historical |
| **traverse** | `traverse(org, query)`, `closure(rootHash)` | graph BFS / composition closure |
| **diff** | `diff(leftHash, rightHash)` | structural `Delta` over canonical content |
| **reproduce** | `reproduce(org, target, actor)` | re-derive via the reproducer SPI |
| **subscribe** | `subscribe(predicate, program)` | standing program over committed entries |
| audit/read | `verifyChain(org)`, `log(org)`, `revision(hash)` | tamper check / inspection / content |

**Write commands** (`AppendCommand`, a closed set): `CreateNode`, `AddRevision`, `AssertEdge`,
`RetractEdge`, `RepointName` (CAS), `Tick`. There is deliberately no update or delete — Law 1 is
enforced by the type system.

**Rejections** throw `KernelException` with a `Reason` (`MISSING_REFERENCE`, `UNKNOWN_NODE`,
`KIND_MISMATCH`, `MISSING_TARGET`, `CAS_FAILURE`, `UNKNOWN_REVISION`, `CLAIM_LAW`, `DECISION_LAW`) —
branch on `reason()`.

**Kind laws (Law 5 & Law 6) are enforced at append.** A `CLAIM` revision is unappendable without a
non-blank `statement`, a non-blank `method`, a `confidence` in `[0,1]`, and ≥1 evidence-family
reference; a `DECISION` revision is unappendable unless it carries ≥1 intent-family reference (the
claims it rests on) or declares `"judgment-call": true`. The reserved payload keys are defined on
`com.broksforge.kernel.core.node.KindLaws`. Enforcement runs on `CreateNode`/`AddRevision` only, never
on log replay, so historical logs always fold back unchanged.

## 3. Extension points

The kernel is designed so that **no Phase-2 capability requires changing it**. You extend it in
userspace through four seams:

### 3.1 A new storage backend

Implement the four ports in `com.broksforge.kernel.core.store` — `Log`, `RevisionStore`,
`GraphIndex`, `NameStore` — and build a `KernelRuntime` over them. To be a valid backend, extend
`com.broksforge.kernel.tck.KernelContract` and pass every test. The in-memory and PostgreSQL backends
are the worked examples; a distributed backend (FoundationDB, etc.) is the same exercise. The engine
does not change (ADR-V2-0001).

Minimal pattern (log-durable, projections rebuilt at open — as the PostgreSQL adapter does):

```java
MyDurableLog log = new MyDurableLog(...);
InMemoryRevisionStore revisions = new InMemoryRevisionStore();   // reuse the reference projections
InMemoryGraphIndex graph = new InMemoryGraphIndex();
InMemoryNameStore names = new InMemoryNameStore();
for (OrgId org : log.organizations())
    for (LogEntry e : log.all(org)) { fold(e, revisions, graph, names); }
ForgeKernel kernel = new KernelRuntime(log, revisions, graph, names, reproducers, minter, clock);
```

### 3.2 A reproducer

Implement `com.broksforge.kernel.core.reproduce.Reproducer` (`supports(kind, subtype)` +
`reproduce(context)`), register it at construction, and `reproduce` will invoke it and record its
observations. The kernel stays executor-agnostic — a reproducer can run anything.

### 3.3 A subscription program

`subscribe(predicate, program)`; the program runs on every matching committed entry and appends its
outputs as an ordinary actor (Law 9). This is how autonomy (nightly passes, failure→test, suggestions)
is built — all in userspace.

### 3.4 A new component/observation/claim/decision subtype

Subtypes are open data — just use a new subtype string on a `Revision`. Kinds and edge families are
closed (a constitutional amendment); subtypes are free.

## 4. Contributing

- **Never weaken a law.** The ten laws (MANIFESTO Article V) are enforced by code; a change that makes
  an illegal state representable is rejected on principle.
- **Every law and operation has a test.** New behavior ships with unit + (where relevant) property,
  concurrency, and TCK coverage. See `LawEnforcementTest`, `ConcurrencyTest`, and the TCK.
- **Kernel purity.** `kernel-api` has zero dependencies; `kernel-core` depends only on `kernel-api`.
  No Spring, no ORM, no AI types may enter either — enforced by Maven scoping and reviewed on every
  change.
- **Append-only docs.** The constitution and ADRs change by supersession, never edit (they obey their
  own Law 1).

## 5. Migration guide

- **Schema:** the PostgreSQL adapter applies versioned SQL from `db/migration/V<n>__*.sql` via the
  dependency-free `SchemaMigrations` (idempotent, forward-only, tracked in `kernel_schema_history`).
  Add a migration by dropping a new `V<n>__*.sql` file and listing it in `SchemaMigrations`.
- **Content-hash stability:** the canonical encoding is frozen by golden vectors. Changing it is a
  hash-algorithm migration (the `RevisionHash` multihash tag exists for exactly this) — never a silent
  edit.
- **Backends:** because projections rebuild from the log, migrating storage engines is export/replay
  of the log, not a bespoke data migration.

## 6. Known limitations (Phase 1)

1. **PostgreSQL verification is env-gated** (but verified). The adapter passes the same TCK as the
   in-memory backend; the contract runs when `KERNEL_TEST_PG_URL` points at a database and skips
   otherwise. It has been executed and passes against **PostgreSQL 16.14** (see the RC1 report §11).
2. **Subscription delivery order under concurrency is best-effort.** The log is always strictly
   ordered; ordered *delivery* via a durable per-subscription cursor is a documented future step.
3. **Projections rebuild by full replay at open.** Fine at current scale (~4,200 entries/sec measured);
   a materialized-projection fast path (avoiding O(log) startup) is a future optimization.
4. **Benchmarks use a measured harness, not the JMH library.** The suite is implemented, executed, and
   published (RC1 report §11); the JMH library itself was unavailable offline. Larger-scale JMH runs
   are a valuable future addition.
5. **Connection pooling.** Supply a pooled `DataSource` in production — the sample uses a plain
   `PGSimpleDataSource`, so per-append connection setup dominates the durable-write path (see §11).
6. **Read visibility and regulated-content erasure** (adversarial-review findings C2/D1) are specified
   but not yet implemented — deliberately deferred beyond the kernel.
7. **`resolveAt` replays the log prefix** (O(prefix)); add a name-history index if it becomes hot.

None of these is a constitutional violation; each is a documented, additive follow-up.
