"use client";

import { GitBranch, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { RegisterVersionDialog } from "@/components/agents/register-version-dialog";
import { useAgentVersions, useVersionAction } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";

export function VersionsPanel({
  organizationId,
  projectId,
  agentId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
  canManage: boolean;
}) {
  const { data, isLoading } = useAgentVersions(organizationId, projectId, agentId, { size: 50 });
  const action = useVersionAction(organizationId, projectId, agentId);
  const versions = data?.content ?? [];

  const run = (versionId: string, type: "activate" | "rollback") => {
    action.mutate(
      { versionId, action: type },
      {
        onSuccess: () => toast.success(type === "activate" ? "Version activated" : "Rolled back"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Versions</h2>
          <p className="text-sm text-muted-foreground">Deployment history for this agent.</p>
        </div>
        {canManage && (
          <RegisterVersionDialog organizationId={organizationId} projectId={projectId} agentId={agentId} />
        )}
      </div>

      {versions.length === 0 ? (
        <EmptyState
          icon={GitBranch}
          title="No versions yet"
          description="Register a version to record a deployment."
          action={
            canManage ? (
              <RegisterVersionDialog organizationId={organizationId} projectId={projectId} agentId={agentId} />
            ) : undefined
          }
        />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {versions.map((version) => (
              <div key={version.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm font-medium">{version.versionNumber}</span>
                    {version.active && <Badge variant="success">Active</Badge>}
                    <Badge variant="outline">{version.environment}</Badge>
                    <Badge variant="muted">{version.provider}</Badge>
                  </div>
                  <p className="mt-1 truncate text-xs text-muted-foreground">
                    {version.model}
                    {version.gitCommitSha ? ` · ${version.gitCommitSha.slice(0, 7)}` : ""} · deployed{" "}
                    {formatDateTime(version.deploymentTimestamp)}
                  </p>
                  {version.releaseNotes && (
                    <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{version.releaseNotes}</p>
                  )}
                </div>
                {canManage && !version.active && (
                  <div className="flex shrink-0 items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => run(version.id, "activate")}
                      disabled={action.isPending}
                    >
                      Activate
                    </Button>
                    {version.rollbackReady && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => run(version.id, "rollback")}
                        disabled={action.isPending}
                      >
                        <RotateCcw className="h-4 w-4" />
                        Rollback
                      </Button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
