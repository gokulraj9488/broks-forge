"use client";

import { useState } from "react";
import { ChevronDown, ChevronUp, GitBranch, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { CreatePromptVersionDialog } from "@/components/prompts/create-prompt-version-dialog";
import { usePromptVersionAction, usePromptVersions } from "@/lib/hooks/use-prompts";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";

export function PromptVersionsPanel({
  organizationId,
  projectId,
  promptId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  promptId: string;
  canManage: boolean;
}) {
  const { data, isLoading, isError } = usePromptVersions(organizationId, projectId, promptId, {
    size: 50,
  });
  const action = usePromptVersionAction(organizationId, projectId, promptId);
  const [expanded, setExpanded] = useState<string | null>(null);
  const versions = data?.content ?? [];

  const run = (versionId: string, type: "activate" | "rollback") =>
    action.mutate(
      { versionId, action: type },
      {
        onSuccess: () => toast.success(type === "activate" ? "Version activated" : "Rolled back"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-24 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Versions</h2>
          <p className="text-sm text-muted-foreground">Every revision of this prompt template.</p>
        </div>
        {canManage && (
          <CreatePromptVersionDialog organizationId={organizationId} projectId={projectId} promptId={promptId} />
        )}
      </div>

      {isError ? (
        <EmptyState icon={GitBranch} title="Couldn't load versions" description="Please try again." />
      ) : versions.length === 0 ? (
        <EmptyState
          icon={GitBranch}
          title="No versions yet"
          description="Add the first version of this prompt template."
          action={
            canManage ? (
              <CreatePromptVersionDialog organizationId={organizationId} projectId={projectId} promptId={promptId} />
            ) : undefined
          }
        />
      ) : (
        <div className="space-y-3">
          {versions.map((version) => {
            const open = expanded === version.id;
            return (
              <Card key={version.id}>
                <CardContent className="p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-medium">v{version.versionNumber}</span>
                      {version.active && <Badge variant="success">Active</Badge>}
                      {version.provider && <Badge variant="muted">{version.provider}</Badge>}
                      {version.model && <Badge variant="outline">{version.model}</Badge>}
                      <span className="text-xs text-muted-foreground">
                        {formatDateTime(version.createdAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      {canManage && !version.active && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => run(version.id, "activate")}
                          disabled={action.isPending}
                        >
                          Activate
                        </Button>
                      )}
                      {canManage && !version.active && (
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
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setExpanded(open ? null : version.id)}
                      >
                        {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                        {open ? "Hide" : "View"}
                      </Button>
                    </div>
                  </div>

                  {version.variables.length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-1.5">
                      {version.variables.map((v) => (
                        <code key={v} className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                          {`{{${v}}}`}
                        </code>
                      ))}
                    </div>
                  )}

                  {version.notes && (
                    <p className="mt-2 text-xs text-muted-foreground">{version.notes}</p>
                  )}

                  {open && (
                    <pre className="mt-3 max-h-80 overflow-auto rounded-lg border border-border bg-muted/40 p-3 text-xs">
                      {version.template}
                    </pre>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
