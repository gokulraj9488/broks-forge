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
