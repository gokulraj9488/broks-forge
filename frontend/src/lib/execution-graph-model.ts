import type {
  EvaluationJobResponse,
  EvaluationRunResponse,
  EvaluationRunResultResponse,
  MetricExecutionStatus,
} from "@/lib/api/evaluation-jobs";
import { METRIC_TYPE_LABELS, type MetricType } from "@/lib/api/evaluation-profiles";
import { formatCost, formatLatency, formatNumber, formatScore, humanize } from "@/lib/format";

/**
 * The runtime execution path of a single evaluation run, derived entirely from data the evaluation APIs
 * already return (the pinned job config + the run's telemetry + its metric results). No new backend, no new
 * storage — this is a visualization of what actually happened.
 *
 * The same model powers both the Execution Graph (the happy path with metadata) and the Failure Graph (the
 * same path, with the broken links highlighted): a failure is just an execution whose nodes are in an error
 * state, so one model serves both.
 */
export type ExecStatus = "ok" | "fail" | "warn" | "neutral" | "skip";

export type ExecNodeKind =
  | "input"
  | "prompt"
  | "variables"
  | "provider"
  | "model"
  | "llm"
  | "retry"
  | "judge"
  | "metric"
  | "result";

export interface ExecMeta {
  label: string;
  value: string;
}

export interface ExecNode {
  id: string;
  kind: ExecNodeKind;
  title: string;
  subtitle?: string;
  status: ExecStatus;
  /** Vertical tier for deterministic layout (0 at the top). */
  tier: number;
  meta: ExecMeta[];
  /** Longer text (input/output/error) shown in the detail panel. */
  body?: string;
}

export interface ExecEdge {
  id: string;
  source: string;
  target: string;
  status: ExecStatus;
  label?: string;
}

export interface ExecutionGraph {
  nodes: ExecNode[];
  edges: ExecEdge[];
  failed: boolean;
  /** A short, human explanation of the outcome, e.g. "Rate limited on the LLM Judge metric". */
  headline: string;
}

const EXEC_STATUS_REASON: Record<MetricExecutionStatus, string> = {
  COMPLETED: "Completed",
  AUTHENTICATION_ERROR: "Authentication error",
  PROVIDER_UNAVAILABLE: "Provider unavailable",
  RATE_LIMITED: "Rate limited",
  MODEL_NOT_FOUND: "Model not found",
  TIMEOUT: "Timed out",
  INFRASTRUCTURE_ERROR: "Infrastructure error",
};

function metricName(r: EvaluationRunResultResponse): string {
  return r.metricLabel || METRIC_TYPE_LABELS[r.metricType as MetricType] || String(r.metricType);
}

function truncate(value: string | null | undefined, max = 140): string | undefined {
  if (!value) return undefined;
  return value.length > max ? value.slice(0, max) + "…" : value;
}

/**
 * Builds the execution graph for one run. {@code results} may be empty (not yet loaded / no metrics) — the
 * graph still renders the provider→model→response path.
 */
export function buildExecutionGraph(
  job: EvaluationJobResponse,
  run: EvaluationRunResponse,
  results: EvaluationRunResultResponse[],
): ExecutionGraph {
  const nodes: ExecNode[] = [];
  const edges: ExecEdge[] = [];

  const runFailed = run.status === "FAILED";
  const callOk = run.httpStatus == null || run.httpStatus < 400;
  const llmStatus: ExecStatus = runFailed || !callOk ? "fail" : "ok";

  // 1 — Dataset input (the rendered prompt sent to the target)
  nodes.push({
    id: "input",
    kind: "input",
    title: "Dataset input",
    subtitle: `Item #${run.sequence}`,
    status: "neutral",
    tier: 0,
    meta: [],
    body: truncate(run.input, 600),
  });

  // 2 — Prompt template (only when the job pinned one)
  let head = "input";
  if (job.promptId) {
    nodes.push({
      id: "prompt",
      kind: "prompt",
      title: "Prompt template",
      subtitle: job.promptVersionId ? "pinned version" : undefined,
      status: "neutral",
      tier: 1,
      meta: [{ label: "Prompt", value: job.promptId.slice(0, 8) }],
    });
    edges.push({ id: "input-prompt", source: "input", target: "prompt", status: "neutral" });
    head = "prompt";
  }

  // 3 — Provider
  const providerId = "provider";
  nodes.push({
    id: providerId,
    kind: "provider",
    title: "LLM provider",
    subtitle: job.provider ?? "Agent default",
    status: llmStatus === "fail" && !callOk ? "fail" : "neutral",
    tier: 2,
    meta: job.provider ? [{ label: "Provider", value: humanize(job.provider) }] : [],
  });
  edges.push({ id: `${head}-provider`, source: head, target: providerId, status: "neutral" });

  // 4 — Model
  nodes.push({
    id: "model",
    kind: "model",
    title: "Model",
    subtitle: job.model ?? "Agent default",
    status: "neutral",
    tier: 3,
    meta: job.model ? [{ label: "Model", value: job.model }] : [],
  });
  edges.push({ id: "provider-model", source: providerId, target: "model", status: "neutral" });

  // 5 — Retry chain (only when the row was retried)
  let llmSource = "model";
  if (run.attempt > 1) {
    nodes.push({
      id: "retry",
      kind: "retry",
      title: "Retries",
      subtitle: `${run.attempt - 1} retry${run.attempt - 1 === 1 ? "" : "ies"} before this attempt`,
      status: "warn",
      tier: 4,
      meta: [{ label: "Attempt", value: String(run.attempt) }],
    });
    edges.push({ id: "model-retry", source: "model", target: "retry", status: "warn", label: "retried" });
    llmSource = "retry";
  }

  // 6 — LLM response (the provider call telemetry)
  const llmMeta: ExecMeta[] = [];
  if (run.latencyMs != null) llmMeta.push({ label: "Latency", value: formatLatency(run.latencyMs) });
  if (run.totalTokens != null) llmMeta.push({ label: "Tokens", value: formatNumber(run.totalTokens) });
  if (run.promptTokens != null || run.completionTokens != null) {
    llmMeta.push({
      label: "Prompt / completion",
      value: `${run.promptTokens ?? "—"} / ${run.completionTokens ?? "—"}`,
    });
  }
  if (run.cost != null) llmMeta.push({ label: "Cost", value: formatCost(run.cost) });
  if (run.httpStatus != null) llmMeta.push({ label: "HTTP", value: String(run.httpStatus) });
  nodes.push({
    id: "llm",
    kind: "llm",
    title: "LLM response",
    subtitle: runFailed ? "failed" : !callOk ? `HTTP ${run.httpStatus}` : "responded",
    status: llmStatus,
    tier: 5,
    meta: llmMeta,
    body: truncate(run.error ?? run.output, 600),
  });
  edges.push({
    id: `${llmSource}-llm`,
    source: llmSource,
    target: "llm",
    status: llmStatus === "fail" ? "fail" : "ok",
    label: runFailed ? "error" : undefined,
  });

  // 7 — Judge / metric evaluation
  const metricFailures: string[] = [];
  if (results.length > 0) {
    nodes.push({
      id: "judge",
      kind: "judge",
      title: "Evaluation metrics",
      subtitle: `${results.length} metric${results.length === 1 ? "" : "s"}`,
      status: "neutral",
      tier: 6,
      meta: [],
    });
    edges.push({
      id: "llm-judge",
      source: "llm",
      target: "judge",
      status: llmStatus === "fail" ? "fail" : "ok",
    });

    results.forEach((r, i) => {
      const id = `metric-${i}`;
      const completed = r.executionStatus === "COMPLETED";
      const status: ExecStatus = !completed ? "warn" : r.passed ? "ok" : "fail";
      if (status !== "ok") metricFailures.push(metricName(r));
      const meta: ExecMeta[] = [];
      if (completed) {
        if (r.score != null) meta.push({ label: "Score", value: formatScore(r.score) });
        if (r.threshold != null) meta.push({ label: "Threshold", value: formatScore(r.threshold) });
      } else {
        meta.push({ label: "Reason", value: EXEC_STATUS_REASON[r.executionStatus] });
      }
      nodes.push({
        id,
        kind: "metric",
        title: metricName(r),
        subtitle: !completed ? EXEC_STATUS_REASON[r.executionStatus] : r.passed ? "passed" : "failed",
        status,
        tier: 7,
        meta,
        body: truncate(r.detail, 300),
      });
      edges.push({ id: `judge-${id}`, source: "judge", target: id, status });
    });
  }

  // 8 — Overall run result
  const resultStatus: ExecStatus = runFailed ? "fail" : run.passed === false ? "fail" : run.passed ? "ok" : "neutral";
  const resultMeta: ExecMeta[] = [];
  if (run.score != null) resultMeta.push({ label: "Run score", value: formatScore(run.score) });
  nodes.push({
    id: "result",
    kind: "result",
    title: "Evaluation result",
    subtitle: runFailed ? "run failed" : run.passed === false ? "failed" : run.passed ? "passed" : run.status.toLowerCase(),
    status: resultStatus,
    tier: 8,
    meta: resultMeta,
  });
  const resultSources = results.length > 0 ? results.map((_, i) => `metric-${i}`) : ["llm"];
  resultSources.forEach((src) => {
    edges.push({ id: `${src}-result`, source: src, target: "result", status: resultStatus === "fail" ? "fail" : "ok" });
  });

  const failed = resultStatus === "fail" || llmStatus === "fail" || metricFailures.length > 0;
  let headline: string;
  if (runFailed || !callOk) {
    headline = run.error
      ? `Execution failed: ${truncate(run.error, 90)}`
      : `Provider call failed${run.httpStatus != null ? ` (HTTP ${run.httpStatus})` : ""}`;
  } else if (metricFailures.length > 0) {
    headline = `Passed the call but failed evaluation: ${metricFailures.slice(0, 3).join(", ")}`;
  } else if (run.passed) {
    headline = "Executed and passed every metric";
  } else {
    headline = `Run ${run.status.toLowerCase()}`;
  }

  return { nodes, edges, failed, headline };
}
