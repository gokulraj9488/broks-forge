# 13. Engineering Knowledge Graph

- Status: Accepted
- Date: 2026-07-01

## Context

The advisor and root-cause engines repeatedly reason about the same recurring patterns: empty
output, timeouts, prompt bloat, cost spikes, oversized retrieval chunks, model overkill. Today that
knowledge lives only as code in the analyzers. Phase 4 asks for the **first version of an
Engineering Knowledge Graph** — a place to store common failures, regressions, recommendations and
optimisations — that is **architecture-ready for future AI learning**, without building a learning
system yet.

We need a representation that: is queryable and navigable (patterns relate to each other — a failure
is *mitigated by* a recommendation, one failure *causes* another); is shared by every project
(this is platform knowledge, not tenant data); can be linked to from advisor/root-cause findings by
a stable identifier; and carries a seam for learning from real usage.

## Alternatives considered

- **Hard-code the knowledge in the analyzers only.** Zero infrastructure, but the knowledge is not
  queryable, not navigable, not visible in the UI, and has nowhere to accumulate observations — it
  cannot be a foundation for learning.
- **A dedicated graph database (Neo4j, etc.).** Natural fit for a graph, but a whole new datastore to
  run and secure for ~20 seed nodes — disproportionate, and it breaks the single-datastore simplicity
  of the modular monolith (./0001-modular-monolith.md).
- **A document/JSON blob of patterns.** Simple to seed, but not relationally queryable and awkward to
  link to by id or to increment counters atomically.

## Decision

Model the graph **relationally in the existing PostgreSQL**, as a small `knowledge` module:

1. **Two tables, nodes and edges.** `knowledge_nodes` (a `node_key`-addressed pattern: type,
   category, summary, detection hint, remediation, expected improvement, default severity/confidence,
   tags, and an `occurrence_count`) and `knowledge_edges` (a typed, directed relation between two
   nodes with a weight). A graph is naturally a node table + an edge table; Postgres handles this
   well at this scale.
2. **Platform-global reference data, not tenant-scoped.** The graph encodes how the platform reasons
   about quality and is identical for every project, so it carries no organization/project columns and
   is readable by any authenticated user.
3. **Stable `node_key` identifiers.** Advisor and root-cause findings link to knowledge by key
   (e.g. `PROMPT_BLOAT`, `TIMEOUT`, `SWITCH_CHEAPER_MODEL`), decoupling code from row ids and keeping
   seeds and edges readable.
4. **Seeded via Flyway** (V24 nodes, V25 edges) with deterministic UUIDs so edges can reference nodes,
   establishing ~20 canonical patterns and ~20 relations as the starting graph.
5. **`occurrence_count` is the learning seam.** A published `recordObservation(nodeKey)` increments a
   pattern's counter (atomically) whenever the advisor or root-cause engine surfaces it — so the
   platform begins accumulating *which* patterns actually occur, the raw material future learning needs.

## Consequences

**Positive**
- Knowledge is **queryable, navigable and visible** (a real API and UI), not buried in code.
- **No new infrastructure** — it reuses the existing database and the standard entity/repository/
  migration conventions, and validates cleanly under `ddl-auto=validate`.
- Findings gain **canonical, linkable remediation**; the graph is the shared spine for the advisor,
  the root-cause engine and the UI.
- The occurrence counter lays a **concrete, low-cost foundation for learning** without committing to
  any ML now.

**Negative / trade-offs**
- A relational edge table is less ergonomic than a native graph DB for deep traversals. Acceptable:
  the graph is small and queries are shallow (neighbours, by type/category). If traversal needs grow,
  the abstraction (`KnowledgeGraphService`) can move to a graph store without changing callers.
- The seed is **curated by hand**; it is only as good as the patterns we encode. This is by design for
  a "first version" — the occurrence counter exists precisely so future data can refine it.
- Incrementing a counter on a read path is a write on a GET. Bounded and best-effort (failures are
  swallowed), and it is the deliberate learning mechanism rather than an accident.

## Future impact

- **Learning**: occurrence counts (and, later, outcome feedback) can rank or re-weight patterns and
  edges; weights already exist on edges for this.
- **Growth**: new patterns are new seed rows in a new migration — append-only, no schema change.
- **Extraction**: if the graph outgrows relational queries, `KnowledgeGraphService` is the single seam
  to repoint at a graph database, per ./0001-modular-monolith.md.
