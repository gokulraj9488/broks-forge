"use client";

import { useState } from "react";
import { KeyRound, ShieldCheck, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { SetCredentialDialog } from "@/components/agents/set-credential-dialog";
import { useAgentCredentials, useDeleteCredential } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/utils";
import type { AgentCredentialResponse } from "@/lib/api/agents";

export function CredentialsPanel({
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
  const { data: credentials, isLoading } = useAgentCredentials(organizationId, projectId, agentId);
  const deleteCredential = useDeleteCredential(organizationId, projectId, agentId);
  const [pendingDelete, setPendingDelete] = useState<AgentCredentialResponse | null>(null);

  if (!canManage) {
    return (
      <EmptyState
        icon={ShieldCheck}
        title="Restricted"
        description="Only organization admins can view and manage agent credentials."
      />
    );
  }

  const handleDelete = () => {
    if (!pendingDelete) return;
    deleteCredential.mutate(pendingDelete.id, {
      onSuccess: () => {
        toast.success("Credential deleted");
        setPendingDelete(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setPendingDelete(null);
      },
    });
  };

  if (isLoading) {
    return <Skeleton className="h-32 w-full" />;
  }

  const list = credentials ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Credentials</h2>
          <p className="text-sm text-muted-foreground">
            How the platform authenticates to this agent. Secrets are encrypted at rest.
          </p>
        </div>
        <SetCredentialDialog organizationId={organizationId} projectId={projectId} agentId={agentId} />
      </div>

      {list.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title="No credentials set"
          description="Set a credential so the platform can authenticate to the agent."
          action={
            <SetCredentialDialog organizationId={organizationId} projectId={projectId} agentId={agentId} />
          }
        />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {list.map((credential) => (
              <div key={credential.id} className="flex items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">{credential.authType.replace(/_/g, " ")}</span>
                    {credential.active ? (
                      <Badge variant="success">Active</Badge>
                    ) : (
                      <Badge variant="muted">Inactive</Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {credential.headerName ? `${credential.headerName} · ` : ""}
                    {credential.username ? `${credential.username} · ` : ""}
                    {credential.secretHint ? `secret ${credential.secretHint} · ` : ""}
                    key v{credential.keyVersion} · added {formatDate(credential.createdAt)}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-destructive"
                  onClick={() => setPendingDelete(credential)}
                  aria-label="Delete credential"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete credential?"
        description="The platform will no longer be able to authenticate to this agent with it."
        confirmLabel="Delete"
        destructive
        loading={deleteCredential.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
