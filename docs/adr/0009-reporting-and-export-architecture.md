# 9. Reporting and export architecture

- Status: Accepted
- Date: 2026-07-01

## Context

Users need to **export** the outputs of the platform — evaluation results (see
./0005-evaluation-job-as-top-level-aggregate.md), benchmark comparisons and regression findings —
in **multiple formats** for sharing, archival and downstream tooling. JSON and CSV are needed now;
HTML is wanted for readable shareable reports; **PDF is wanted eventually**. The architecture must
make adding a format cheap, must not bloat storage with rendered artifacts, and must not introduce
output-encoding security holes (HTML and CSV are both classic injection sinks).

## Alternatives considered

- **Store rendered blobs.** Generate each report once and persist the bytes. Fast re-download, but
  reports go stale the moment the underlying data changes, storage grows unbounded, and every new
  format multiplies stored artifacts.
- **Client-side-only export.** Push raw data to the browser and render there. Zero server cost,
  but duplicates rendering/encoding logic per client, cannot produce server-only formats like PDF,
  and makes consistent, safe encoding hard to guarantee.
- **Couple export to one format.** Hardcode a single exporter. Simplest now, but every additional
  format becomes a structural change rather than a plug-in.

## Decision

Adopt a **format-agnostic `ReportRenderer` abstraction**:

1. **`ReportRenderer` interface** with concrete **JSON, CSV and HTML** renderers, selected by a
   **format enum**. Adding a format is implementing one interface and adding an enum value — the
   same plug-in shape used for model invokers in ./0006-provider-agnostic-model-invocation.md.
2. **On-demand generation, streamed** to the client. Reports are rendered fresh from current data
   at request time and streamed rather than buffered or stored, so they are **always up to date**
   and carry negligible storage cost.
3. **A lightweight `Report` audit record** is written per export to power a **"recent reports"**
   view. It captures *that* a report was produced (who, what, when, which format) — **not** the
   rendered bytes.
4. **Output is safely encoded.** HTML output is escaped to prevent **XSS**; CSV output is
   sanitised to prevent **CSV/formula injection** (leading `=`, `+`, `-`, `@`). Encoding is the
   renderer's responsibility, applied uniformly.
5. **PDF is a future renderer behind the same interface** — no architectural change required to
   add it.

## Consequences

**Positive**
- Reports are **always fresh** because they re-render from live data.
- **Small storage footprint**: only audit metadata persists, never rendered blobs.
- **Easy new formats**: a new renderer + enum value, with no change to callers.
- Centralised, consistent **output encoding** closes the XSS and CSV-injection holes in one place.

**Negative / trade-offs**
- **Re-render cost** on every export (no caching of rendered output). Accepted because evaluation
  reports are read far less often than they are computed, and rendering is cheap relative to the
  evaluation itself; a cache can be added later if needed.
- The `Report` audit record can show a "recent report" whose bytes no longer exist as stored
  artifacts — by design, since re-opening re-renders.

## Future impact

- **PDF rendering** as an additional `ReportRenderer`.
- **Scheduled reports** and **email delivery** built on the same on-demand renderers.
