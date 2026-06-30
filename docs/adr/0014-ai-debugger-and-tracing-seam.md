# 14. AI Debugger execution timeline and the tracing seam

- Status: Accepted
- Date: 2026-07-01

## Context

When an evaluation run fails, an engineer needs to see **where** in the execution it went wrong:
the prompt that was sent, the model call, the parsing, the final output — a step-through, not just a
red result. Phase 4 asks for an **AI Debugger** with an execution timeline across the canonical
stages (prompt, memory, retriever, tools, model, parser, output).

The hard truth is that the platform does **not yet capture per-stage spans**. Today an
`EvaluationRun` records the input, output, latency, tokens, cost and HTTP status of the *whole*
invocation against the agent endpoint — not a breakdown by stage. Memory access, retrieval and tool
calls happen inside the agent and are invisible to us until distributed tracing is wired (a Phase
5/6 concern; the foundations are laid in ./0010-observability-and-opentelemetry-readiness.md).

The decision is how to deliver a genuinely useful debugger **now**, honestly, without faking data we
do not have — while making the eventual real-tracing upgrade a drop-in.

## Alternatives considered

- **Wait for distributed tracing.** Ship the debugger only once spans exist. Correct data, but
  defers all debugging value and ignores the rich per-run data we already persist.
- **Fabricate per-stage durations.** Synthesise plausible numbers for memory/retriever/tools.
  Dishonest and misleading — an engineering tool that invents data is worse than no tool.
- **Free-form text dump.** Just show the raw run record. Honest but unstructured, and gives the UI
  nothing stable to render or to grow into a real timeline.

## Decision

Reconstruct the timeline from persisted data over a **stable, shared stage vocabulary**, and mark
uninstrumented stages honestly:

1. **A canonical `ExecutionStage` enum** (PROMPT, MEMORY, RETRIEVER, TOOLS, MODEL, PARSER, OUTPUT) and
   a `StageStatus` enum that includes a first-class **`NOT_INSTRUMENTED`** state. The timeline shape is
   fixed and forward-compatible.
2. **The debugger reconstructs what it can observe.** From the persisted run it populates PROMPT
   (input), MODEL (the timed endpoint invocation — latency, tokens, cost, HTTP status), PARSER (driven
   by the JSON-valid metric and output presence) and OUTPUT (pass/fail and failing metrics). The
   stages the platform cannot yet see — MEMORY, RETRIEVER, TOOLS — are reported as `NOT_INSTRUMENTED`,
   never faked.
3. **Honest notes.** The response states that only the model call is timed end to end, that
   memory/retriever/tools are placeholders until live tracing lands, and whether a tracing exporter is
   active. The timeline explains failures (the run error, or the failing metrics) rather than just
   showing red.
4. **A `TraceRecorder` seam, default no-op.** A dependency-free `TraceRecorder` interface (with a
   `NoOpTraceRecorder` default) is the single point where live per-stage span recording — and, later,
   an OpenTelemetry exporter — will attach. The correlation id from ./0010 is the natural trace seam.
   No exporter is wired in Phase 4 (a deliberate scope boundary).

## Consequences

**Positive**
- Engineers get a **useful, honest debugger today**, built entirely on data we already persist — no
  new tables, no new infrastructure.
- The timeline shape is **stable**: when live tracing lands, the same contract simply fills in the
  `NOT_INSTRUMENTED` stages with real spans — no API or UI rewrite.
- `NOT_INSTRUMENTED` as a first-class state keeps the tool **trustworthy**: it never implies success
  or invents durations.

**Negative / trade-offs**
- Durations are **best-effort**: only the model invocation is timed, so the timeline shows one real
  duration and structural ordering for the rest. Clearly labelled as such.
- The rendered prompt is not persisted per run, so PROMPT reflects the item input plus whether a
  template was applied, not the fully expanded prompt. Acceptable until tracing captures it.
- A new published evaluation read (`getRun`) was added so the debugger can load a single run IDOR-
  safely — a small, correct widening of the evaluation API.

## Future impact

- **Drop-in real tracing**: driving `TraceRecorder` from instrumented agent execution lights up the
  placeholder stages with true spans and per-stage latency, with no contract change.
- The same timeline becomes the natural surface for the **RAG and memory inspectors** (Phase 5): the
  RETRIEVER and MEMORY stages already exist, waiting for their data.
- Combined with the root-cause engine (./0012-root-cause-analysis-engine.md), a failed run can show
  *both* the stage where it broke and the diagnosed cause.
