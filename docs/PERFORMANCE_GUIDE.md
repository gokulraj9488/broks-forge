# Performance Guide — Brok's Forge

- Audience: backend & frontend engineers, reviewers, on-call.
- Stack: Java 21 / Spring Boot 3.4.1, PostgreSQL + Flyway, HikariCP, Redis, Next.js 15.
- Status: living document. Last reviewed 2026-07-01 (Phase 5). Sections are explicitly labelled **Implemented** vs **Recommended / Future** — do not assume a *Future* item is in place.

---

## 1. Performance principles

1. **Push work to the database.** Filter, paginate, and aggregate in SQL; never load a table into the JVM to count or sum it.
2. **Bound everything.** Every list endpoint is paginated; every import is size/shape-capped. There are no unbounded result sets.
3. **Never hold a transaction across a network call.** Outbound agent calls are slow and untrusted; a DB connection held during one starves the pool.
4. **Measure before optimizing.** Add an index/cache because a query/profile shows a hot path, not on a hunch.
5. **Design for the data shape.** Evaluation produces *millions* of rows; its schema and access patterns are built for that from day one.
6. **Be honest about sync vs async.** The current evaluation executor is synchronous; this guide says where that hurts and how to fix it.

---

## 2. Database performance

### 2.1 Indexing strategy — *Implemented*

- Every foreign-key / ownership column used in the **`(id, projectId, organizationId)`** lookup pattern is indexed, so the tenant-scoped fetch is an index seek, not a scan.
- Composite indexes match the **leading columns of actual queries** (e.g. `(organization_id, project_id, created_at)` for tenant-scoped, time-ordered lists).
- Indexes are created and versioned via **Flyway** migrations alongside the tables they serve — schema and indexes ship together.

```sql
-- Tenant-scoped lookup powering the IDOR-safe access guards
CREATE INDEX idx_dataset_owner ON dataset (organization_id, project_id, id);

-- Tenant-scoped, time-ordered listing (keyset/offset pagination friendly)
CREATE INDEX idx_eval_run_job_created ON evaluation_run (job_id, created_at);
```

### 2.2 Avoiding N+1 — *Implemented*

The platform avoids the classic ORM N+1 with two deliberate patterns:

- **Batch tag loading:** rather than lazily fetching tags per aggregate (1 + N queries), tags for a page of aggregates are loaded in **one** `IN (:ids)` query and stitched in memory.
- **Id-reference second query:** for collections, fetch the page of parent ids first, then fetch children with a single `... WHERE parent_id IN (:ids)` — two queries total, regardless of page size.

```java
// Two queries for a whole page, not 1 + N.
List<UUID> agentIds = page.getContent().stream().map(AgentView::id).toList();
Map<UUID, List<TagView>> tagsByAgent = tagRepository
    .findByAgentIdIn(agentIds)                       // single batched query
    .stream().collect(groupingBy(TagView::agentId));
```

### 2.3 Pagination — *Implemented*

- **Every** list/search/analytics endpoint is paginated (`Pageable`), with a **maximum page size** enforced server-side so a client cannot request "everything."
- Sort columns are validated against an allowlist (no arbitrary `ORDER BY` from user input).
- For very large, deep result sets (evaluation runs/results), **keyset (seek) pagination** is preferred over large `OFFSET`s as a scaling step — *Recommended* where deep paging appears.

### 2.4 Projections — *Implemented*

- Read endpoints select **DTO/interface projections** containing only the needed columns, not full entities. This avoids hydrating large or sensitive fields (and keeps secrets out of read paths entirely).

```java
public interface DatasetSummaryView {
    UUID getId();
    String getName();
    long getRowCount();     // no payload columns hydrated
    Instant getCreatedAt();
}
```

### 2.5 Connection pool (HikariCP) — *Implemented*

- `spring.datasource.hikari.maximum-pool-size=10`. This is **intentionally modest**: PostgreSQL throughput peaks at a small multiple of CPU cores, and oversized pools cause contention, not speed.
- Because the pool is small, **rule #3 is load-bearing**: holding a connection across a slow outbound call quickly exhausts all 10 and stalls the app. See §4.
- Pool size should scale **with** replica count and DB capacity — 10 per replica is a starting point, sized against `max_connections`.

### 2.6 Read-only transactions — *Implemented*

- Query paths are annotated `@Transactional(readOnly = true)`, letting Hibernate skip dirty-checking/flush and signalling intent to the driver. Write paths use a normal read-write transaction kept **as short as possible**.

---

## 3. Evaluation at scale — `EvaluationJob → Run → Result`

The evaluation domain is the platform's heaviest data producer. The model is designed to scale to **millions** of rows.

```
EvaluationJob (1) ──< EvaluationRun (N, one per dataset row × profile)
                              └──< EvaluationResult (M, one per metric)
              plus: precomputed EvaluationSummary (aggregates per job)
```

### 3.1 What is implemented

- **Batched inserts:** runs and results are persisted in **batches** (JDBC batch / `saveAll` with batching configured), not row-by-row, to amortize round-trips when a job fans out over a large dataset.
- **Pagination of runs/results:** these collections are **never** returned whole; clients page through them. Detail endpoints are tuple-scoped and paginated.
- **Summary precomputation:** a per-job **`EvaluationSummary`** (counts, pass/fail, mean/percentile metric values) is computed and stored so dashboards/reports read **one summary row** instead of aggregating millions of results on every view.

### 3.2 The async/queue-ready seam — *Implemented seam, Future workers*

The executor is structured so that **starting** a job and **executing** runs are separated behind an interface (an executor "seam"). Today execution is **synchronous** in-process; the seam exists so it can be swapped for **async workers / a queue** without touching callers.

```java
public interface EvaluationExecutor {
    void execute(EvaluationJob job); // today: synchronous; future: enqueue to workers
}
```

### 3.3 Future scaling work — *Recommended / Future*

- **Async workers + queue:** move run execution off the request thread to a worker pool / message queue; the request only **enqueues** the job. (Also fixes the resource-holding issue in §4.)
- **Partitioning:** partition `evaluation_run` / `evaluation_result` (e.g. by `job_id` range or time) so old data prunes cheaply and queries hit fewer pages.
- **Retention:** age out or archive completed-job detail rows; keep the precomputed summary indefinitely.

---

## 4. Outbound call efficiency — *Implemented basics, with a known issue*

Evaluation/benchmark/health execution makes outbound HTTP calls to agent endpoints (always through `OutboundUrlGuard`; see `SECURITY_GUIDE.md` §6).

### 4.1 Implemented

- **Timeouts** on both connect and read for every outbound call — a slow/hung agent cannot block a worker indefinitely.

### 4.2 Known issue — synchronous executor holds resources across the network call

> **Problem:** in the current synchronous executor, the outbound agent call can occur **while a database transaction / connection is held**. With `maximum-pool-size=10`, a handful of slow agents can hold all connections and stall unrelated requests. This is called out honestly here and in `SECURITY_GUIDE.md` §15.

**How to fix:**

1. **Separate the transaction from the network call.** Commit/read what you need, **release the DB connection**, perform the outbound call, then open a *new short* transaction to persist the result.

```java
// DO: no DB transaction open across the network boundary
EvaluationRun run = txTemplate.execute(s -> loadAndMarkRunning(runId)); // tx 1 (short)
AgentResponse resp = agentCaller.callAgent(run.endpoint(), run.authHeaders()); // NO tx held
txTemplate.executeWithoutResult(s -> persistResult(runId, resp));     // tx 2 (short)
```

2. **Move execution to async workers** (§3.3) so request threads and DB connections are never tied up by upstream latency.
3. **Bound concurrency** of outbound calls (a fixed worker pool / semaphore) so a large job cannot open unbounded simultaneous connections.

---

## 5. Caching strategy (Redis) — *Future*

Redis is part of the stack; an application-level **read cache is not yet implemented** for Phase 3 read models. When introduced:

- Cache **expensive, read-mostly** results: analytics aggregates, dashboard tiles, evaluation summaries.
- Key by the **full tenant scope** (`org:project:resource:params`) so cache entries can **never** cross tenants.
- Set explicit **TTLs** and **invalidate** on the corresponding write (or accept bounded staleness for analytics).
- Cache **projections/DTOs**, never entities or anything secret-bearing.
- A Redis token-bucket is also the natural home for the **rate limiting** that is currently unimplemented (`RATE_LIMITED` reserved).

---

## 6. Analytics aggregation — *Implemented DB-side, materialized views Future*

- **DB-side aggregation:** analytics run as SQL `GROUP BY` / aggregate queries; the JVM never sums rows it pulled into memory.
- **Time-bucketing:** time-series analytics bucket in SQL (e.g. `date_trunc('day', created_at)`) and rely on the tenant-scoped, time-ordered indexes from §2.1.
- **Every analytics/search query is tenant-scoped** (`organization_id`, usually `project_id`) — a correctness *and* performance requirement (the scope predicate is also what the index serves).
- **Materialized views** (or a rollup table refreshed on a schedule) for the heaviest dashboards — *Recommended / Future*; precomputed `EvaluationSummary` (§3.1) is the first step in that direction.

---

## 7. Report generation — *Recommended (streaming)*

Reports (including HTML export) can cover large jobs.

- **Stream** report output (paginate/iterate the source data and write incrementally) rather than building the whole document in memory — prevents large-report OOM and reduces time-to-first-byte.
- Read source data through the **precomputed summary** plus paged details, not a full results scan.
- Apply the **export-safety encoding** (HTML escaping / CSV formula defusing — `SECURITY_GUIDE.md` §9) inside the streaming writer so safety and performance are not in tension.
- For very large/expensive reports, generate **asynchronously** and notify on completion — *Recommended / Future*, and a natural fit for the same worker model as §3.3.

---

## 8. Frontend performance (Next.js 15) — *Implemented patterns*

- **Server-side pagination** end to end: the UI requests bounded pages and renders them; it never fetches "all rows."
- **Query caching & deduplication** (e.g. TanStack Query / the framework's data cache): cache by query key, dedupe in-flight requests, and revalidate in the background instead of refetching on every navigation.
- **Code splitting & lazy loading:** route-level code splitting and `dynamic()` imports for heavy, rarely-used views (large charts, report viewers) keep the initial bundle small.
- **Render large tables virtually** (windowing) and **memoize** derived data to avoid re-render storms.
- **Streaming/Suspense** for slow data so the shell paints immediately.
- Keep payloads lean by consuming the **projection** DTOs (§2.4) rather than fat entities.

---

## 9. Load & performance testing — *Recommended*

- **Tooling:** k6 or Gatling against representative endpoints — paginated lists, evaluation job start, analytics aggregates, report export.
- **Scenarios that matter most:**
  - Large evaluation job (fan-out over a big dataset) — watch DB connection saturation and batch-insert throughput.
  - Concurrent outbound calls to slow agents — verify the pool is **not** exhausted (validates the §4 fix).
  - Deep pagination / large analytics windows — verify indexes are used (check `EXPLAIN (ANALYZE)`).
- **What to measure:** p95/p99 latency, throughput, DB connection-pool wait time, GC pauses, and error rate under load.
- **Regression budget:** wire a perf smoke test into CI (nightly) and alert on p95 regressions on the hot endpoints.

---

## 10. Capacity & scaling roadmap

| Stage | Action | Status |
|-------|--------|--------|
| 1 | **Horizontal stateless replicas** behind a load balancer — the app holds no in-process session state (stateless JWT/API-key auth), so replicas scale linearly | *Recommended — architecture already supports it* |
| 2 | **Right-size Hikari per replica** against PostgreSQL `max_connections`; add a read replica for analytics if read load dominates | *Recommended* |
| 3 | **Extract evaluation workers** — move run execution to a dedicated async worker tier / queue so heavy jobs scale independently of the API and stop holding API resources (§3.3, §4) | *Future* |
| 4 | **Partition & retention** for `evaluation_run` / `evaluation_result`; **materialized views** for analytics; **Redis caching** for read models | *Future* |
| 5 | **Egress proxy** for outbound agent traffic (also closes the DNS-rebinding gap in `SECURITY_GUIDE.md`) and centralized outbound rate limiting | *Future* |

### Honest summary of state

- **Implemented now:** ownership indexes, N+1 avoidance (batch tags / id-reference queries), mandatory pagination, projections, Hikari pool of 10, read-only transactions, batched eval inserts, paginated runs/results, precomputed summaries, the async-ready executor seam, outbound timeouts, the frontend pagination/caching/code-splitting patterns, the Phase 4 on-read advisor model (§11), and — new in Phase 5 — a **Prometheus metrics endpoint** so the hot paths in this guide are now directly measurable (§12).
- **Known weak spot:** the **synchronous** executor can hold DB resources across outbound calls (§4) — the highest-value fix.
- **Not yet implemented (recommended):** async eval workers, Redis read cache, materialized views, partitioning/retention, streaming/async report generation, formal load testing, and rate limiting.

---

## 11. Phase 4 (advisor) performance — *Implemented (computed on read)*

The Phase 4 features — the **AI Engineering Advisor**, **root-cause analysis** and the **AI debugger
timeline** — add **no new hot write path and no new outbound surface**. They follow the same on-read
model as benchmark leaderboards and regression findings (§3, §6): recommendations, findings and
timelines are **computed each time they are requested** from already-persisted data, then discarded.
That keeps them correct (they can never drift) and bounds their cost.

### 11.1 Reads are over bounded windows — *Implemented*

- The advisor and root-cause services load data **only through the published services** of `evaluation`,
  `prompt`, `agent` and `regression` — every one of those reads is **tenant-scoped and paginated**, so
  the analyzers operate over a **bounded window** of recent jobs/runs, never a full-table scan.
- Failed-run sampling for root cause is **capped** (`failure-sample-size`, default 50) and metric
  failure counts come from a **DB-side JPQL aggregate** (`MetricFailureTally`), not by pulling rows into
  the JVM — consistent with principle #1 ("push work to the database").
- The sub-advisors and the `RootCauseEngine` are **pure (no I/O)**: they take already-loaded inputs and
  return value records, so the only cost is the bounded load that precedes them plus in-memory ranking.
  There is no N+1 risk because there is no lazy navigation — data arrives as DTOs.
- Heuristic thresholds (`broksforge.advisor.*`: `prompt-max-chars`, `min-samples-for-comparison`,
  `latency-spike-ms`, …) also act as **cost guards** — comparisons are only attempted once there are
  enough comparable jobs, so sparse projects do no extra work.

### 11.2 The knowledge graph is a tiny seeded reference table — *Implemented*

- `knowledge_nodes` / `knowledge_edges` are **platform-global reference data** seeded by Flyway with
  **20 nodes and 20 edges** — kilobytes, not a growth table. Lookups are by `node_key` (unique index)
  or by the small `node_type` / `category` indexes, so reads are constant-time in practice and trivially
  cacheable later. The graph is **not** tenant-scoped and carries no tenant content.

### 11.3 The `recordObservation` write-on-GET is bounded and best-effort — *Implemented*

- The advisor/root-cause read paths perform **one** kind of write: incrementing a pattern's
  `occurrence_count` via an **atomic single-row UPDATE** (`incrementOccurrence(nodeKey)`) for each
  recommendation that carries a `knowledgeKey`. The number of such writes per request is **bounded by the
  (small) number of recommendations** produced, not by the data volume scanned.
- It is **best-effort**: unknown keys are ignored and failures never propagate, so the counter bump can
  **never** fail or slow down the user-facing read. It is the deliberate "learning seam," intentionally
  cheap. If this counter ever becomes hot under very high read volume, the natural step is to make it
  **fire-and-forget / batched** (or Redis-backed) — *Recommended / Future*, not needed at current scale.

> Net: Phase 4 is read-shaped and bounded by construction. It inherits the pagination, projection and
> tenant-scoping guarantees of the modules it reads, and adds only a tiny reference table plus a bounded,
> best-effort counter write — nothing that changes the scaling story of §3–§6.

---

## 12. Phase 5 observability — metrics make the hot paths measurable — *Implemented*

Phase 5 closes the loop on this guide's principle #4 ("measure before optimizing"): the hot paths
described above are now **directly observable in production**. A Prometheus-format metrics endpoint is
exposed, so the optimisation decisions this document recommends can be driven by **real numbers** instead
of inference.

### 12.1 What is now exposed — *Implemented*

- **`GET /actuator/prometheus`** publishes Micrometer metrics in Prometheus text format. The endpoint is
  **ADMIN-guarded** by `SecurityConfig` (scrapers authenticate, or run on a private management network),
  so exposing metrics does not widen the attack surface.
- **Kubernetes-style health probes** (`/actuator/health/liveness`, `/actuator/health/readiness`, with
  readiness including the database) make load-balancer draining and restart decisions accurate under DB
  stress — relevant to the §4 connection-saturation failure mode.
- **Structured JSON logs (ECS)** in the `docker` profile carry the **correlation/request id**, so a slow
  request seen in a metric histogram can be traced to its exact log lines.

### 12.2 What to watch — the signals that map to this guide

| Metric | Watch for | Tells you about |
|--------|-----------|-----------------|
| **`http_server_requests`** (timer; tags `uri`, `method`, `status`, `outcome`) | rising **p95 / p99** on paginated lists, evaluation start, analytics, report export | the endpoint latencies this guide tunes (§2, §6, §7, §8) |
| **`hikaricp_connections_active`** / `hikaricp_connections_pending` / `hikaricp_connections_acquire` (timer) | `active` pinned at the pool max (**10**) and `pending` > 0 / rising acquire time | the §4 known weak spot — the synchronous executor **holding connections across outbound calls**; this is the metric that catches it in the act |
| **`jvm_gc_pause`** (timer) | long or frequent pauses, especially during large report generation or big eval fan-out | memory pressure from building documents/result sets in memory (§3, §7) — the case for streaming |
| `jvm_memory_used` / `jvm_threads_live` | sustained growth / thread exhaustion | leaks or unbounded concurrency on outbound calls (§4.2 point 3) |
| `executor_*` (when async workers land) | queue depth, task latency | the future async eval tier (§3.3) once it exists |

### 12.3 How to use it

- **Set SLOs on `http_server_requests` p95/p99** for the hot endpoints and alert on regressions — this is
  the production counterpart of the CI perf-smoke budget in §9.
- **Alert on `hikaricp_connections_pending > 0` sustained** and on acquire-time spikes: it is the early
  warning for the §4 issue and the trigger to prioritise the async-worker / split-transaction fix.
- **Correlate with logs** via the correlation id when a histogram shows a tail, so you debug the *actual*
  slow request, not a synthetic repro.

> Phase 5 adds **measurement, not new load**: scraping is cheap and the endpoint is admin-scoped. It does
> not change any "Implemented vs Future" label above — the async workers, Redis cache, materialized views,
> partitioning and rate limiting remain **Future**. What changed is that you can now **see** exactly which
> of those to do first.
