"use client";

import { CircleDollarSign, Gauge, Hash, Target } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { StatCard, MeterBar } from "@/components/common/stat-card";
import { METRIC_TYPE_LABELS, type MetricType } from "@/lib/api/evaluation-profiles";
import { formatCompact, formatCost, formatLatency, formatPercent, formatScore } from "@/lib/format";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

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

  const metricEntries = Object.entries(s.metricPassRates ?? {});

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Pass rate" value={formatPercent(s.passRate)} hint={`${s.passed}/${s.totalRuns} passed`} icon={Target} />
        <StatCard label="Avg latency" value={formatLatency(s.avgLatencyMs)} hint="per run" icon={Gauge} />
        <StatCard label="Total tokens" value={formatCompact(s.totalTokens)} hint="prompt + completion" icon={Hash} />
        <StatCard label="Total cost" value={formatCost(s.totalCost)} hint="estimated" icon={CircleDollarSign} />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="p-5">
            <p className="text-sm text-muted-foreground">Runs</p>
            <p className="mt-2 text-2xl font-semibold tracking-tight">{s.totalRuns}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              <span className="text-success">{s.succeeded} succeeded</span> ·{" "}
              <span className="text-destructive">{s.failed} failed</span>
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <p className="text-sm text-muted-foreground">Average score</p>
            <p className="mt-2 text-2xl font-semibold tracking-tight">{formatScore(s.avgScore)}</p>
            <div className="mt-3">
              <MeterBar value={s.avgScore} />
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <p className="text-sm text-muted-foreground">Pass rate</p>
            <p className="mt-2 text-2xl font-semibold tracking-tight">{formatPercent(s.passRate)}</p>
            <div className="mt-3">
              <MeterBar
                value={s.passRate}
                tone={s.passRate >= 0.8 ? "success" : s.passRate >= 0.5 ? "primary" : "destructive"}
              />
            </div>
          </CardContent>
        </Card>
      </div>

      {metricEntries.length > 0 && (
        <Card>
          <CardContent className="space-y-4 p-5">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Per-metric pass rates</p>
            <div className="space-y-3">
              {metricEntries.map(([metric, rate]) => (
                <div key={metric} className="space-y-1">
                  <div className="flex items-center justify-between text-sm">
                    <span>{METRIC_TYPE_LABELS[metric as MetricType] ?? metric}</span>
                    <span className="font-medium">{formatPercent(rate)}</span>
                  </div>
                  <MeterBar value={rate} tone={rate >= 0.8 ? "success" : "primary"} />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
