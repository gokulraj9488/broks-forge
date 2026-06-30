"use client";

import { Activity, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { HealthBadge } from "@/components/common/badges";
import { useAgentHealth, useRunHealthCheck } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";

export function HealthPanel({
  organizationId,
  projectId,
  agentId,
  disabled,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
  disabled?: boolean;
}) {
  const { data, isLoading } = useAgentHealth(organizationId, projectId, agentId);
  const runCheck = useRunHealthCheck(organizationId, projectId, agentId);

  const run = () =>
    runCheck.mutate(undefined, {
      onSuccess: (result) =>
        result.success
          ? toast.success(`Healthy · ${result.latencyMs ?? "?"}ms`)
          : toast.error(result.failureReason ?? "Agent is unhealthy"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  const availability = data?.availabilityPercent;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Health</h2>
          <p className="text-sm text-muted-foreground">
            Probe the agent endpoint and review availability.
          </p>
        </div>
        <Button size="sm" onClick={run} loading={runCheck.isPending} disabled={disabled}>
          <RefreshCw className="h-4 w-4" />
          Run check
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="space-y-1 p-5">
            <p className="text-sm text-muted-foreground">Current status</p>
            <div className="pt-1">
              <HealthBadge status={data?.currentStatus ?? "UNKNOWN"} />
            </div>
            <p className="pt-1 text-xs text-muted-foreground">
              Last checked {formatDateTime(data?.lastCheckedAt)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="space-y-1 p-5">
            <p className="text-sm text-muted-foreground">Availability ({data?.windowDays ?? 30}d)</p>
            <p className="text-3xl font-semibold tracking-tight">
              {availability != null ? `${availability}%` : "—"}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="space-y-1 p-5">
            <p className="text-sm text-muted-foreground">Checks ({data?.windowDays ?? 30}d)</p>
            <p className="text-3xl font-semibold tracking-tight">{data?.totalChecks ?? 0}</p>
            <p className="text-xs text-muted-foreground">{data?.successfulChecks ?? 0} successful</p>
          </CardContent>
        </Card>
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold">Recent checks</h3>
        {!data || data.recent.length === 0 ? (
          <EmptyState icon={Activity} title="No checks yet" description="Run a health check to begin." />
        ) : (
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {data.recent.map((check) => (
                <div key={check.id} className="flex items-center justify-between gap-3 p-3 text-sm">
                  <div className="flex items-center gap-3">
                    <HealthBadge status={check.status} />
                    <span className="text-muted-foreground">{formatDateTime(check.checkedAt)}</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    {check.httpStatus != null && <Badge variant="outline">HTTP {check.httpStatus}</Badge>}
                    {check.latencyMs != null && <span>{check.latencyMs}ms</span>}
                    {!check.success && check.failureReason && (
                      <span className="max-w-[16rem] truncate text-destructive">{check.failureReason}</span>
                    )}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
