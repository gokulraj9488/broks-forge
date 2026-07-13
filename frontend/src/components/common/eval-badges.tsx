import { Badge } from "@/components/ui/badge";
import { humanize } from "@/lib/format";
import type { EvaluationJobStatus, EvaluationRunStatus } from "@/lib/api/evaluation-jobs";

type Variant = "default" | "secondary" | "outline" | "success" | "destructive" | "muted";

const JOB_VARIANT: Record<EvaluationJobStatus, Variant> = {
  PENDING: "muted",
  RUNNING: "default",
  COMPLETED: "success",
  FAILED: "destructive",
  CANCELLED: "secondary",
};

export function JobStatusBadge({ status }: { status: EvaluationJobStatus }) {
  return (
    <Badge variant={JOB_VARIANT[status]} className="gap-1.5">
      {status === "RUNNING" && (
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" aria-hidden />
      )}
      {humanize(status)}
    </Badge>
  );
}

const RUN_VARIANT: Record<EvaluationRunStatus, Variant> = {
  PENDING: "muted",
  RUNNING: "default",
  SUCCEEDED: "success",
  FAILED: "destructive",
  SKIPPED: "secondary",
};

export function RunStatusBadge({ status }: { status: EvaluationRunStatus }) {
  return <Badge variant={RUN_VARIANT[status]}>{humanize(status)}</Badge>;
}

/** Pass / Fail pill driven by a nullable boolean. */
export function PassBadge({ passed }: { passed: boolean | null | undefined }) {
  if (passed == null) return <Badge variant="muted">—</Badge>;
  return <Badge variant={passed ? "success" : "destructive"}>{passed ? "Pass" : "Fail"}</Badge>;
}

export type MetricExecutionStatus =
  | "COMPLETED"
  | "AUTHENTICATION_ERROR"
  | "PROVIDER_UNAVAILABLE"
  | "RATE_LIMITED"
  | "MODEL_NOT_FOUND"
  | "TIMEOUT"
  | "INFRASTRUCTURE_ERROR";

export const EXECUTION_STATUS_LABEL: Record<Exclude<MetricExecutionStatus, "COMPLETED">, string> = {
  AUTHENTICATION_ERROR: "Authentication Error",
  PROVIDER_UNAVAILABLE: "Provider Error",
  RATE_LIMITED: "Rate Limited",
  MODEL_NOT_FOUND: "Model Not Found",
  TIMEOUT: "Timeout",
  INFRASTRUCTURE_ERROR: "Infrastructure Error",
};

/**
 * Plain-language explanations of what each execution status usually means, shown as a hint under
 * the badge in the metric detail panel — the raw provider error text alone ("HTTP 401: ...") isn't
 * always obvious to a non-engineer reading the Runs page.
 */
export const EXECUTION_STATUS_HINTS: Record<Exclude<MetricExecutionStatus, "COMPLETED">, string[]> = {
  AUTHENTICATION_ERROR: ["Wrong or expired API key", "Invalid credentials", "Missing permissions for this model"],
  PROVIDER_UNAVAILABLE: ["Provider is disabled or unreachable", "Endpoint shape isn't supported", "Server-side outage (5xx)"],
  RATE_LIMITED: ["Too many requests (429)", "Quota exhausted", "Retry after a short delay or lower concurrency"],
  MODEL_NOT_FOUND: ["Model id doesn't exist for this provider", "Model was removed or renamed", "Provider doesn't support this capability"],
  TIMEOUT: ["Provider took too long to respond", "Network latency or an oversized prompt"],
  INFRASTRUCTURE_ERROR: ["Unexpected/unparseable provider response", "Missing or invalid metric configuration"],
};

/**
 * A metric's status: a real Pass/Fail when it actually ran (`executionStatus === "COMPLETED"`),
 * otherwise a distinct warning badge for *why* it never ran — a transport/auth/provider failure
 * must never render as a bare "Fail", which reads as a real low score.
 */
export function MetricStatusBadge({
  passed,
  executionStatus,
}: {
  passed: boolean | null | undefined;
  executionStatus: MetricExecutionStatus | null | undefined;
}) {
  if (executionStatus && executionStatus !== "COMPLETED") {
    return (
      <Badge variant="warning" className="gap-1">
        <span aria-hidden>⚠</span>
        {EXECUTION_STATUS_LABEL[executionStatus]}
      </Badge>
    );
  }
  if (passed == null) return <Badge variant="muted">—</Badge>;
  return (
    <Badge variant={passed ? "success" : "destructive"} className="gap-1">
      <span aria-hidden>{passed ? "✔" : "✖"}</span>
      {passed ? "Passed" : "Failed"}
    </Badge>
  );
}
