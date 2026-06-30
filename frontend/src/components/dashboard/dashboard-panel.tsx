"use client";

import Link from "next/link";
import {
  AlertTriangle,
  Bot,
  Database,
  FileText,
  FlaskConical,
  Loader2,
  Trophy,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { StatCard, MeterBar } from "@/components/common/stat-card";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { Sparkline, type SparkPoint } from "@/components/common/mini-charts";
import { useDashboard } from "@/lib/hooks/use-dashboard";
import { formatCost, formatCompact, formatLatency, formatNumber, formatPercent } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";

export function DashboardPanel({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const { data, isLoading, isError } = useDashboard(organizationId, projectId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return <EmptyState icon={FlaskConical} title="Couldn't load dashboard" description="Please try again." />;
  }

  const c = data.counts;
  const a = data.analytics;
  const trend: SparkPoint[] = (a?.trend ?? []).map((t) => ({ label: t.date, value: t.runCount }));
  const base = `/organizations/${organizationId}/projects/${projectId}`;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <CountTile label="Agents" value={c.agents} icon={Bot} href="/agents" />
        <CountTile label="Datasets" value={c.datasets} icon={Database} href="/datasets" />
        <CountTile label="Prompts" value={c.prompts} icon={FileText} href="/prompts" />
        <CountTile label="Evaluations" value={c.evaluationJobs} icon={FlaskConical} href="/evaluations" />
        <CountTile label="Running" value={c.runningJobs} icon={Loader2} href="/evaluations" spin={c.runningJobs > 0} />
        <CountTile label="Benchmarks" value={c.benchmarks} icon={Trophy} href="/benchmarks" />
      </div>

      {a && (
        <div className="grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardContent className="space-y-3 p-5">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">Run activity ({a.windowDays}d)</p>
                <Link href="/analytics" className="text-xs text-primary hover:underline">
                  View analytics
                </Link>
              </div>
              <Sparkline data={trend} height={72} />
            </CardContent>
          </Card>
          <div className="grid gap-4">
            <StatCard label="Pass rate" value={formatPercent(a.passRate)} hint={`${formatNumber(a.runCount)} runs`} />
            <StatCard label="Total cost" value={formatCost(a.totalCost)} hint={formatCompact(a.totalTokens) + " tokens"} />
          </div>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold">Recent evaluations</h2>
            <Link href="/evaluations" className="text-xs text-primary hover:underline">
              View all
            </Link>
          </div>
          {data.recentJobs.length === 0 ? (
            <EmptyState icon={FlaskConical} title="No evaluations yet" />
          ) : (
            <Card>
              <CardContent className="divide-y divide-border p-0">
                {data.recentJobs.slice(0, 6).map((job) => (
                  <Link
                    key={job.id}
                    href={`${base}/evaluations/${job.id}`}
                    className="flex items-center justify-between gap-3 p-3 text-sm hover:bg-muted/40"
                  >
                    <span className="min-w-0 truncate">{job.name}</span>
                    <JobStatusBadge status={job.status} />
                  </Link>
                ))}
              </CardContent>
            </Card>
          )}
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-semibold">Top agents</h2>
          {data.topAgents.length === 0 ? (
            <EmptyState icon={Bot} title="No ranked agents yet" />
          ) : (
            <Card>
              <CardContent className="space-y-3 p-4">
                {data.topAgents.slice(0, 5).map((agent, i) => (
                  <div key={agent.agentId} className="space-y-1">
                    <div className="flex items-center justify-between text-sm">
                      <Link
                        href={`${base}/agents/${agent.agentId}`}
                        className="font-mono text-xs text-primary hover:underline"
                      >
                        #{i + 1} · {agent.agentId.slice(0, 8)}
                      </Link>
                      <span className="font-medium">{formatPercent(agent.avgPassRate)}</span>
                    </div>
                    <MeterBar value={agent.avgPassRate} tone={agent.avgPassRate >= 0.8 ? "success" : "primary"} />
                    <p className="text-xs text-muted-foreground">{agent.evaluatedJobs} evaluations</p>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </section>
      </div>

      {data.regressionAlerts.length > 0 && (
        <section className="space-y-3">
          <h2 className="flex items-center gap-2 text-base font-semibold">
            <AlertTriangle className="h-4 w-4 text-destructive" />
            Regression alerts
          </h2>
          <Card className="border-destructive/40">
            <CardContent className="divide-y divide-border p-0">
              {data.regressionAlerts.map((alert) => (
                <div key={alert.id} className="flex items-center justify-between gap-3 p-3 text-sm">
                  <span className="min-w-0 truncate">{alert.name}</span>
                  <div className="flex items-center gap-2">
                    <Badge variant="destructive">Regressed</Badge>
                    <span className="text-xs text-muted-foreground">{formatDateTime(alert.createdAt)}</span>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </section>
      )}

      {data.recentReports.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-base font-semibold">Recent reports</h2>
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {data.recentReports.slice(0, 5).map((report) => (
                <div key={report.id} className="flex items-center justify-between gap-3 p-3 text-sm">
                  <span className="min-w-0 truncate">{report.name}</span>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <Badge variant="muted">{report.format}</Badge>
                    {formatDateTime(report.createdAt)}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </section>
      )}

      <div className="flex items-center justify-end gap-1.5 text-xs text-muted-foreground">
        <FlaskConical className="h-3.5 w-3.5" />
        Avg latency {formatLatency(a?.avgLatencyMs)} over {a?.windowDays ?? 30} days
      </div>
    </div>
  );
}

function CountTile({
  label,
  value,
  icon: Icon,
  href,
  spin,
}: {
  label: string;
  value: number;
  icon: typeof Bot;
  href: string;
  spin?: boolean;
}) {
  return (
    <Link href={href} className="group">
      <Card className="transition-colors group-hover:border-primary/40">
        <CardContent className="p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-foreground">{label}</p>
            <Icon className={`h-4 w-4 text-muted-foreground ${spin ? "animate-spin" : ""}`} />
          </div>
          <p className="mt-1.5 text-2xl font-semibold tracking-tight">{formatNumber(value)}</p>
        </CardContent>
      </Card>
    </Link>
  );
}
