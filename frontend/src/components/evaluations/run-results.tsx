"use client";

import { Spinner } from "@/components/ui/spinner";
import { PassBadge } from "@/components/common/eval-badges";
import { useEvaluationRunResults } from "@/lib/hooks/use-evaluation-jobs";
import { METRIC_TYPE_LABELS } from "@/lib/api/evaluation-profiles";
import { formatScore } from "@/lib/format";

/** Per-metric scoring breakdown for a single run, lazily loaded on expand. */
export function RunResults({
  organizationId,
  projectId,
  jobId,
  runId,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  runId: string;
}) {
  const { data, isLoading, isError } = useEvaluationRunResults(
    organizationId,
    projectId,
    jobId,
    runId,
  );

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-2 text-xs text-muted-foreground">
        <Spinner className="h-3.5 w-3.5" />
        Loading metric results…
      </div>
    );
  }

  if (isError) {
    return <p className="py-2 text-xs text-destructive">Couldn&apos;t load metric results.</p>;
  }

  if (!data || data.length === 0) {
    return <p className="py-2 text-xs text-muted-foreground">No metric results for this run.</p>;
  }

  return (
    <div className="space-y-2">
      {data.map((r, i) => (
        <div
          key={`${r.metricType}-${i}`}
          className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-background px-3 py-2 text-xs"
        >
          <div className="flex items-center gap-2">
            <PassBadge passed={r.passed} />
            <span className="font-medium">
              {r.metricLabel || METRIC_TYPE_LABELS[r.metricType] || r.metricType}
            </span>
          </div>
          <div className="flex items-center gap-3 text-muted-foreground">
            {r.score != null && <span>score {formatScore(r.score)}</span>}
            {r.threshold != null && <span>threshold {r.threshold}</span>}
            {r.detail && <span className="max-w-[18rem] truncate">{r.detail}</span>}
          </div>
        </div>
      ))}
    </div>
  );
}
