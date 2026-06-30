# 10. Observability and OpenTelemetry readiness

- Status: Accepted
- Date: 2026-07-01

## Context

Brok's Forge is a multi-module platform (see ./0001-modular-monolith.md) whose requests fan out
across agent registry, evaluation, model invocation and reporting. We need **traceability now** —
the ability to follow a single request through the logs and correlate it end to end — and we need
to be ready for **distributed tracing later**, once modules are extracted into independent
services. At the same time, wiring a full tracing stack (exporters, collectors, backends) before
there is operational need would add infrastructure and overhead we cannot yet justify. The goal is
**readiness without premature commitment**.

## Alternatives considered

- **Full OpenTelemetry now.** Stand up the SDK, exporters and a collector/backend immediately.
  Maximum capability, but real operational overhead (a backend to run, sample and pay for) for a
  single-process monolith that does not yet need distributed spans.
- **No correlation ids.** Rely on timestamps and ad-hoc grepping. Cheapest, but makes it nearly
  impossible to follow one request across modules, and leaves nothing for a future tracing layer
  to build on.
- **Logging only, unstructured.** Keep plain log lines without identifiers or structure. Readable
  by humans, but not correlatable or queryable, and not a foundation for OTel.

## Decision

Build **observability foundations that are OpenTelemetry-ready but stop short of exporters**:

1. **Correlation-id + request-id propagation.** A **servlet filter** establishes a correlation id
   (stable across a logical operation) and a request id (per HTTP request), stored in the **SLF4J
   MDC** so they appear on every log line, and **echoed back in response headers** so clients and
   operators can reference them.
2. **Structured logging** that includes **actor and resource ids** (who did what to which
   resource), making logs queryable rather than just readable.
3. **Code organised for a drop-in tracing layer.** Cross-cutting instrumentation lives at the
   edges (filters, service entry points) so an **OpenTelemetry exporter** plus **Micrometer
   `Observation`** can be added **without touching business logic**. The correlation id is the
   natural seam to map onto a future trace id.
4. **Exporters are deliberately not implemented yet.** No OTLP exporter, collector or tracing
   backend is wired today.

## Consequences

**Positive**
- Every request is **correlatable end to end today**, through logs and response headers, with zero
  tracing infrastructure to operate.
- Structured, id-tagged logs are queryable and double as an audit aid.
- The future tracing upgrade is **additive** and isolated to the edges, not a refactor of business
  logic.

**Negative / trade-offs**
- We have **ids without spans** today: no latency breakdown, no flame graphs, no automatic
  cross-service propagation until OTel is wired. This is the deliberately accepted cost of
  **zero premature operational overhead**.
- Correlation-id propagation across asynchronous boundaries (e.g. a future evaluation queue) must
  be handled explicitly when those boundaries are introduced.

## Future impact

- **Drop-in OpenTelemetry tracing and metrics** (OTLP exporter + Micrometer Observation) without
  business-logic changes.
- **Distributed tracing** across services extracted from the monolith per ./0001-modular-monolith.md,
  with today's correlation id mapping cleanly onto trace context.
