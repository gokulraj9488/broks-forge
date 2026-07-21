"use client";

import { useState } from "react";
import { Activity, CircleDollarSign, Gauge, Hash, Target } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { StatCard } from "@/components/common/stat-card";
import {
  BarTimeline,
  CumulativeLineChart,
  DonutChart,
  Sparkline,
  TrendLineChart,
  type SparkPoint,
} from "@/components/common/mini-charts";
import { useAnalytics } from "@/lib/hooks/use-analytics";
import { formatCompact, formatCost, formatLatency, formatNumber, formatPercent } from "@/lib/format";
import { formatDate } from "@/lib/utils";

/** Simple last-vs-previous delta for the compact stat tiles — not a full regression check, just
 * enough to answer "is this better or worse than a moment ago" at a glance. */
function dayOverDay(series: SparkPoint[]): { pct: number; direction: "up" | "down" } | null {
  if (series.length < 2) return null;
  const prev = series[series.length - 2].value;
  const latest = series[series.length - 1].value;
  if (prev === 0) return null;
  const pct = ((latest - prev) / prev) * 100;
  return { pct: Math.abs(pct), direction: pct >= 0 ? "up" : "down" };
}

const WINDOW_OPTIONS = [7, 14, 30, 90];

export function AnalyticsPanel({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [windowDays, setWindowDays] = useState(30);
  const { data, isLoading, isError } = useAnalytics(organizationId, projectId, windowDays);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return <EmptyState icon={Activity} title="Couldn't load analytics" description="Please try again." />;
  }

  const trend = data.trend ?? [];
  const runSeries: SparkPoint[] = trend.map((t) => ({ label: t.date, value: t.runCount }));
  const latencySeries: SparkPoint[] = trend.map((t) => ({ label: t.date, value: t.avgLatencyMs }));
  const tokenSeries: SparkPoint[] = trend.map((t) => ({ label: t.date, value: t.totalTokens }));
  const costSeries: SparkPoint[] = trend.map((t) => ({ label: t.date, value: t.totalCost }));

  const rangeLabel =
    trend.length > 0 ? `${formatDate(trend[0].date)} – ${formatDate(trend[trend.length - 1].date)}` : "";

  const runDelta = dayOverDay(runSeries);
  const latencyDelta = dayOverDay(latencySeries);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-end gap-2">
        <span className="text-sm text-muted-foreground">Window</span>
        <Select value={String(windowDays)} onValueChange={(v) => setWindowDays(Number(v))}>
          <SelectTrigger className="h-8 w-auto min-w-[8rem] text-xs" aria-label="Analytics window">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {WINDOW_OPTIONS.map((w) => (
              <SelectItem key={w} value={String(w)}>
                Last {w} days
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard label="Evaluations" value={formatNumber(data.jobCount)} icon={Activity} hint={`${windowDays}d window`} />
        <StatCard
          label="Runs"
          value={formatNumber(data.runCount)}
          icon={Hash}
          delta={runDelta ? { value: `${runDelta.pct.toFixed(0)}%`, good: runDelta.direction === "up", direction: runDelta.direction } : undefined}
          hint="vs. previous day"
        />
        <Card>
          <CardContent className="flex items-center gap-4 p-5">
            <DonutChart value={data.passRate} tone={data.passRate >= 0.8 ? "success" : "warning"} size={72} strokeWidth={8} />
            <div>
              <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                <Target className="h-3.5 w-3.5" /> Pass rate
              </p>
              <p className="mt-1 text-xs text-muted-foreground">{formatNumber(data.runCount)} runs evaluated</p>
            </div>
          </CardContent>
        </Card>
        <StatCard
          label="Avg latency"
          value={formatLatency(data.avgLatencyMs)}
          icon={Gauge}
          delta={
            latencyDelta
              ? { value: `${latencyDelta.pct.toFixed(0)}%`, good: latencyDelta.direction === "down", direction: latencyDelta.direction }
              : undefined
          }
          hint="vs. previous day"
        />
        <StatCard label="Total tokens" value={formatCompact(data.totalTokens)} icon={Hash} hint={`${windowDays}d total`} />
        <StatCard label="Total cost" value={formatCost(data.totalCost)} icon={CircleDollarSign} hint={`${windowDays}d total`} />
      </div>

      {trend.length === 0 ? (
        <EmptyState icon={Activity} title="No activity in this window" description="Run evaluations to populate trends." />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <TrendCard title="Runs" hint={rangeLabel} data={runSeries} formatValue={formatNumber}>
            <BarTimeline data={runSeries} />
          </TrendCard>
          <TrendCard title="Avg latency" hint={rangeLabel} data={latencySeries} formatValue={formatLatency}>
            <TrendLineChart data={latencySeries} />
          </TrendCard>
          <TrendCard title="Tokens" hint={rangeLabel} data={tokenSeries} formatValue={formatCompact}>
            <Sparkline data={tokenSeries} />
          </TrendCard>
          <TrendCard title="Cost (cumulative)" hint={rangeLabel} data={costSeries} formatValue={(v) => formatCost(v)} cumulative>
            <CumulativeLineChart data={costSeries} tone="success" />
          </TrendCard>
        </div>
      )}
    </div>
  );
}

function TrendCard({
  title,
  hint,
  data,
  formatValue,
  cumulative = false,
  children,
}: {
  title: string;
  hint: string;
  data: SparkPoint[];
  formatValue: (v: number) => string;
  cumulative?: boolean;
  children: React.ReactNode;
}) {
  // The cumulative cost card's headline number is the running total, not the last day's spend —
  // the chart below it is a running total too, so the two should agree.
  const headline = cumulative
    ? data.reduce((sum, d) => sum + d.value, 0)
    : data.length > 0
      ? data[data.length - 1].value
      : 0;
  return (
    <Card>
      <CardContent className="space-y-3 p-5">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-sm text-muted-foreground">{title}</p>
            <p className="text-xl font-semibold tracking-tight">{formatValue(headline)}</p>
          </div>
          <span className="text-xs text-muted-foreground">{hint}</span>
        </div>
        {children}
      </CardContent>
    </Card>
  );
}
