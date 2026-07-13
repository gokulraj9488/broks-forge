"use client";

import { CircleDollarSign, Gauge, Hash } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { StatCard, MeterBar } from "@/components/common/stat-card";
import { EXECUTION_STATUS_LABEL } from "@/components/common/eval-badges";
import { METRIC_TYPE_LABELS, type MetricType } from "@/lib/api/evaluation-profiles";
import { formatCompact, formatCost, formatLatency, formatPercent, formatScore } from "@/lib/format";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

const EXECUTION_ERROR_KEYS = [
  ["authenticationErrors", "AUTHENTICATION_ERROR"],
  ["providerErrors", "PROVIDER_UNAVAILABLE"],
  ["rateLimited", "RATE_LIMITED"],
  ["modelNotFound", "MODEL_NOT_FOUND"],
  ["timeouts", "TIMEOUT"],
  ["infrastructureErrors", "INFRASTRUCTURE_ERROR"],
] as const;

export function JobSummary({ job }: { job: EvaluationJobResponse }) {
  const s = job.summary;

  if (!s) {
    return (
      <Card>
        <CardContent className="p-6 text-sm text-muted-foreground">
          {job.status === "PENDING" || job.status === "RUNNING"
            ? "Summary metrics will appear here once the evaluation completes."
            : "No summary available for this evaluation."}
        </CardContent>
      </Card>
    );
  }

  const breakdownEntries = Object.entries(s.metricBreakdown ?? {});
  const evaluation = s.evaluation ?? { passed: s.passed, failed: 0, skipped: 0 };
  const execution = s.execution;
  const totalExecutionErrors = execution
    ? EXECUTION_ERROR_KEYS.reduce((sum, [key]) => sum + (execution[key] ?? 0), 0)
    : 0;

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Avg latency" value={formatLatency(s.avgLatencyMs)} hint="per run" icon={Gauge} />
        <StatCard label="Total tokens" value={formatCompact(s.totalTokens)} hint="prompt + completion" icon={Hash} />
        <StatCard label="Total cost" value={formatCost(s.totalCost)} hint="estimated" icon={CircleDollarSign} />
        <StatCard label="Runs" value={s.totalRuns} hint={`${s.succeeded} succeeded · ${s.failed} failed`} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Evaluation — quality verdict, independent of provider health. */}
        <Card>
          <CardContent className="space-y-3 p-5">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Evaluation</p>
            <div className="grid grid-cols-3 gap-2 text-center">
              <div>
                <p className="text-2xl font-semibold tracking-tight text-success">{evaluation.passed}</p>
                <p className="text-xs text-muted-foreground">Passed</p>
              </div>
              <div>
                <p className="text-2xl font-semibold tracking-tight text-destructive">{evaluation.failed}</p>
                <p className="text-xs text-muted-foreground">Failed</p>
              </div>
              <div>
                <p className="text-2xl font-semibold tracking-tight text-muted-foreground">{evaluation.skipped}</p>
                <p className="text-xs text-muted-foreground">Skipped</p>
              </div>
            </div>
            {evaluation.skipped > 0 && (
              <p className="text-xs text-muted-foreground">
                Skipped runs had no metric that could complete — not counted as pass or fail.
              </p>
            )}
          </CardContent>
        </Card>

        {/* Execution — metric-level provider-call health, job-wide. */}
        <Card>
          <CardContent className="space-y-3 p-5">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Execution</p>
            <div className="flex items-baseline gap-2">
              <p className="text-2xl font-semibold tracking-tight text-success">{execution?.succeeded ?? 0}</p>
              <p className="text-xs text-muted-foreground">metric calls succeeded</p>
            </div>
            {totalExecutionErrors > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {EXECUTION_ERROR_KEYS.map(([key, status]) => {
                  const count = execution?.[key] ?? 0;
                  if (!count) return null;
                  return (
                    <Badge key={key} variant="warning" className="gap-1">
                      {EXECUTION_STATUS_LABEL[status]}: {count}
                    </Badge>
                  );
                })}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">No provider execution errors.</p>
            )}
          </CardContent>
        </Card>

        {/* Average score — only over metrics that actually completed. */}
        <Card>
          <CardContent className="p-5">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Average score</p>
            <p className="mt-2 text-2xl font-semibold tracking-tight">{formatScore(s.avgScore)}</p>
            <div className="mt-3">
              <MeterBar value={s.avgScore} />
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              Based on {s.completedMetricCount} completed metric{s.completedMetricCount === 1 ? "" : "s"}
              {s.unavailableMetricCount > 0 && <> · {s.unavailableMetricCount} unavailable</>}
            </p>
          </CardContent>
        </Card>
      </div>

      {breakdownEntries.length > 0 && (
        <Card>
          <CardContent className="space-y-4 p-5">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Per-metric results</p>
            <div className="grid gap-3 sm:grid-cols-2">
              {breakdownEntries.map(([metric, entry]) => (
                <MetricBreakdownCard key={metric} metric={metric} entry={entry} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function MetricBreakdownCard({
  metric,
  entry,
}: {
  metric: string;
  entry: {
    total: number;
    completed: number;
    passed: number;
    failed: number;
    executionErrors: Record<string, number>;
  };
}) {
  const label = METRIC_TYPE_LABELS[metric as MetricType] ?? metric;
  const rate = entry.completed > 0 ? entry.passed / entry.completed : null;
  const dominantError = Object.entries(entry.executionErrors).sort((a, b) => b[1] - a[1])[0];

  return (
    <div className="space-y-1.5 rounded-lg border border-border p-3">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium">{label}</span>
        {entry.total === 0 ? (
          <Badge variant="muted">Not Executed</Badge>
        ) : rate != null ? (
          <span className="font-medium">{formatPercent(rate)}</span>
        ) : dominantError ? (
          <Badge variant="warning">
            {EXECUTION_STATUS_LABEL[dominantError[0] as keyof typeof EXECUTION_STATUS_LABEL] ?? dominantError[0]}
          </Badge>
        ) : (
          <Badge variant="muted">Not Executed</Badge>
        )}
      </div>
      {rate != null ? (
        <MeterBar value={rate} tone={rate >= 0.8 ? "success" : "primary"} />
      ) : (
        <p className="text-xs text-muted-foreground">
          {entry.completed === 0 && entry.total > 0
            ? `Failed to execute on ${entry.total} of ${entry.total} run${entry.total === 1 ? "" : "s"}`
            : "No runs yet"}
        </p>
      )}
      {entry.completed > 0 && (entry.total - entry.completed) > 0 && (
        <p className="text-xs text-muted-foreground">
          {entry.completed} completed · {entry.total - entry.completed} unavailable
        </p>
      )}
    </div>
  );
}
