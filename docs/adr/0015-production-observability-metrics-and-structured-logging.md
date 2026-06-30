# 15. Production observability — Prometheus metrics and structured logging

- Status: Accepted
- Date: 2026-07-01

## Context

[ADR 0010](./0010-observability-and-opentelemetry-readiness.md) established the observability
*foundations* — correlation/request ids in the MDC, structured-ready logging, and a code layout that
keeps a future OpenTelemetry tracing exporter a drop-in — but deliberately **shipped no exporters and
no metrics backend**, because a single-process monolith did not yet need distributed spans.

For the Version 1 **production release**, an operator must be able to answer the basics on day one —
*is the service up, is it serving traffic, what is its latency/error rate, what is it doing* — and a
log aggregator must be able to parse our logs without a custom grok rule. That is metrics + machine-
readable logs, **not** distributed tracing. We want the smallest addition that makes the platform
genuinely operable, without standing up a tracing collector we do not yet need.

## Alternatives considered

- **Full OpenTelemetry (traces + metrics + logs) with an OTLP collector now.** Maximum capability,
  but it requires running and paying for a collector/backend and instrumenting spans across the
  monolith — premature for a single process, and it contradicts the "readiness without premature
  commitment" decision in ADR 0010.
- **Nothing beyond ADR 0010 (ids + logs only).** Cheapest, but ships a release with **no metrics
  endpoint** — no latency/error/throughput/JVM/DB-pool visibility — which is below the bar for a
  production service.
- **A bespoke `/metrics` endpoint hand-rolled in the app.** Reinvents Micrometer, with no ecosystem of
  dashboards or exporters. Wasteful.

## Decision

Add the **two production-table-stakes** observability capabilities and stop there:

1. **Prometheus metrics via Micrometer.** Add the `micrometer-registry-prometheus` registry (managed
   by the Spring Boot BOM) and expose **`/actuator/prometheus`** alongside `health`, `info` and
   `metrics`. This publishes JVM, HTTP-server (per-URI latency/percentiles/error rate), HikariCP
   connection-pool, Flyway and process metrics out of the box — **Grafana-ready** with standard
   dashboards. All metrics are tagged `application=broks-forge-api`.
2. **Native structured (JSON) logging in production.** Use Spring Boot 3.4's built-in structured
   logging (`logging.structured.format.console=ecs`) in the `docker`/production profile, emitting one
   Elastic Common Schema JSON object per line. The existing **MDC correlation/request ids appear as
   fields automatically**, so logs are queryable and correlate to the same ids returned in response
   headers. Local `dev` keeps the human-readable coloured pattern.
3. **Kubernetes-grade health probes.** Define explicit `liveness` (process up) and `readiness`
   (`readinessState` + `db`) health groups, so an orchestrator drains a pod that cannot reach the
   database instead of killing a healthy process.
4. **Security posture unchanged.** `/actuator/prometheus` remains **ADMIN-guarded** by the existing
   `SecurityConfig` rule (`/actuator/** → hasRole('ADMIN')`); `health`/`info` stay public for probes.
   Scrapers authenticate, or the endpoints are reached over a private management network.
5. **Tracing exporters remain deferred.** No OTLP exporter, collector or tracing backend is wired —
   that is still Phase-5-roadmap work driving the `TraceRecorder` seam from
   [ADR 0014](./0014-ai-debugger-and-tracing-seam.md). This ADR adds **metrics and logs**, not traces.

## Consequences

**Positive**
- The release is **operable**: latency/error/throughput/JVM/DB-pool metrics scrape into Prometheus and
  render in Grafana with zero custom code; logs parse directly into Loki/ELK/Datadog.
- **Correlatable end to end** — the same correlation id is in the structured log fields and the
  response headers.
- **Probes are orchestrator-correct** — readiness reflects database reachability.
- Achieved with **one runtime dependency and configuration only** — no new infrastructure to operate,
  honouring ADR 0010's "no premature overhead".

**Negative / trade-offs**
- **Metrics and logs, but still no spans.** No per-stage latency breakdown or flame graphs until the
  OTel exporter lands (the `TraceRecorder` seam is ready). Accepted, consistent with ADR 0010/0014.
- The Prometheus endpoint behind ADMIN auth means a scraper needs a credential or a private network
  path (rather than an open `/metrics`). This is the secure-by-default trade; a separate management
  port can be introduced later if open scraping is required.
- Structured JSON logs are less pleasant to read raw; mitigated by keeping the coloured pattern in
  `dev` and only switching to JSON in the container profile.

## Future impact

- **Drop-in distributed tracing**: adding an OTLP exporter + Micrometer `Observation`/Tracing
  promotes the existing metrics and correlation ids to full traces without business-logic changes,
  and feeds the AI Debugger's `NOT_INSTRUMENTED` stages (ADR 0014).
- **Alerting & SLOs**: the exposed metrics are the substrate for Prometheus alert rules (error-rate,
  p99 latency, pool saturation) and Grafana SLO dashboards.
- **Per-tenant metrics**: request metrics can later be tagged with coarse tenant/route dimensions if
  cardinality is controlled.
