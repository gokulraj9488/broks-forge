"use client";

import { Crown, Trophy } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { HBarChart, type BarDatum } from "@/components/common/mini-charts";
import { useBenchmarkLeaderboard } from "@/lib/hooks/use-benchmarks";
import { formatCost, formatLatency, formatNumber, formatPercent, formatScore } from "@/lib/format";
import type { LeaderboardRanking } from "@/lib/api/benchmarks";

function formatMetric(metricKey: string, value: number | null): string {
  if (value == null) return "—";
  switch (metricKey) {
    case "passRate":
      return formatPercent(value);
    case "avgScore":
      return formatScore(value);
    case "avgLatencyMs":
      return formatLatency(value);
    case "totalCost":
      return formatCost(value);
    case "totalTokens":
      return formatNumber(value);
    default:
      return String(value);
  }
}

export function Leaderboard({
  organizationId,
  projectId,
  benchmarkId,
}: {
  organizationId: string;
  projectId: string;
  benchmarkId: string;
}) {
  const { data, isLoading, isError } = useBenchmarkLeaderboard(
    organizationId,
    projectId,
    benchmarkId,
  );

  if (isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (isError || !data || data.rankings.length === 0) {
    return (
      <EmptyState
        icon={Trophy}
        title="No leaderboard yet"
        description="Add at least two completed evaluations to rank them."
      />
    );
  }

  const ranked = [...data.rankings].sort((a, b) => a.rank - b.rank);
  const scored = ranked.filter((r) => r.score != null);

  const chartData: BarDatum[] = scored.map((r) => ({
    label: r.label,
    value: data.higherIsBetter ? (r.score as number) : 1 / ((r.score as number) || 1),
    display: formatMetric(data.metricKey, r.score),
    highlight: r.rank === 1,
  }));

  return (
    <div className="space-y-5">
      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium">Ranking by {data.metricKey}</p>
            <Badge variant="muted">{data.higherIsBetter ? "Higher is better" : "Lower is better"}</Badge>
          </div>
          {chartData.length > 0 ? (
            <HBarChart data={chartData} />
          ) : (
            <p className="text-sm text-muted-foreground">No scores available to chart.</p>
          )}
        </CardContent>
      </Card>

      <div className="space-y-2">
        {ranked.map((entry) => (
          <RankRow key={entry.evaluationJobId} entry={entry} metricKey={data.metricKey} />
        ))}
      </div>
    </div>
  );
}

function RankRow({ entry, metricKey }: { entry: LeaderboardRanking; metricKey: string }) {
  const leader = entry.rank === 1;
  return (
    <Card className={leader ? "border-primary/40" : undefined}>
      <CardContent className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <div
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-sm font-semibold ${
              leader ? "bg-primary/15 text-primary" : "bg-muted text-muted-foreground"
            }`}
          >
            {leader ? <Crown className="h-4 w-4" /> : entry.rank}
          </div>
          <div className="min-w-0">
            <p className="truncate font-medium">{entry.label}</p>
            <p className="text-xs text-muted-foreground">
              {entry.summary
                ? `${formatPercent(entry.summary.passRate)} pass · ${formatLatency(entry.summary.avgLatencyMs)} · ${formatCost(entry.summary.totalCost)}`
                : "No summary"}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <JobStatusBadge status={entry.jobStatus} />
          <span className="font-mono text-sm font-semibold">{formatMetric(metricKey, entry.score)}</span>
        </div>
      </CardContent>
    </Card>
  );
}
