"use client";

import { useState } from "react";
import { Activity, CircleDollarSign, Gauge, Hash, Target } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { StatCard } from "@/components/common/stat-card";
import { Sparkline, type SparkPoint } from "@/components/common/mini-charts";
import { useAnalytics } from "@/lib/hooks/use-analytics";
import { formatCompact, formatCost, formatLatency, formatNumber, formatPercent } from "@/lib/format";
import { formatDate } from "@/lib/utils";

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
        <StatCard label="Runs" value={formatNumber(data.runCount)} icon={Hash} />
        <StatCard label="Pass rate" value={formatPercent(data.passRate)} icon={Target} />
        <StatCard label="Avg latency" value={formatLatency(data.avgLatencyMs)} icon={Gauge} />
        <StatCard label="Total tokens" value={formatCompact(data.totalTokens)} icon={Hash} />
        <StatCard label="Total cost" value={formatCost(data.totalCost)} icon={CircleDollarSign} />
      </div>

      {trend.length === 0 ? (
        <EmptyState icon={Activity} title="No activity in this window" description="Run evaluations to populate trends." />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <TrendCard title="Runs" hint={rangeLabel} data={runSeries} formatValue={formatNumber} />
          <TrendCard title="Avg latency" hint={rangeLabel} data={latencySeries} formatValue={formatLatency} />
          <TrendCard title="Tokens" hint={rangeLabel} data={tokenSeries} formatValue={formatCompact} />
          <TrendCard title="Cost" hint={rangeLabel} data={costSeries} formatValue={formatCost} tone="success" />
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
  tone = "primary",
}: {
  title: string;
  hint: string;
  data: SparkPoint[];
  formatValue: (v: number) => string;
  tone?: "primary" | "success";
}) {
  const latest = data.length > 0 ? data[data.length - 1].value : 0;
  return (
    <Card>
      <CardContent className="space-y-3 p-5">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-sm text-muted-foreground">{title}</p>
            <p className="text-xl font-semibold tracking-tight">{formatValue(latest)}</p>
          </div>
          <span className="text-xs text-muted-foreground">{hint}</span>
        </div>
        <Sparkline data={data} tone={tone} />
      </CardContent>
    </Card>
  );
}
