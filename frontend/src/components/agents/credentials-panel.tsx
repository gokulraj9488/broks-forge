"use client";

import { useState } from "react";
import { KeyRound, Pencil, PlugZap, ShieldCheck, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip } from "@/components/ui/tooltip";
import { SetCredentialDialog } from "@/components/agents/set-credential-dialog";
import { useAgentCredentials, useDeleteCredential, useTestCredential } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import type { AgentAuthType, AgentCredentialResponse } from "@/lib/api/agents";

function ConnectionStatusBadge({ credential }: { credential: AgentCredentialResponse }) {
  if (credential.lastTestSuccess == null) return <Badge variant="muted">Untested</Badge>;
  return credential.lastTestSuccess ? (
    <Badge variant="success">Connected</Badge>
  ) : (
    <Badge variant="destructive">Connection failed</Badge>
  );
}

export function CredentialsPanel({
  organizationId,
  projectId,
  agentId,
  canManage,
  createOpen,
  onCreateOpenChange,
  onCredentialSaved,
  initialAuthType,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
  canManage: boolean;
  /** Controlled open state for the create dialog (used by the onboarding flow). */
  createOpen?: boolean;
  onCreateOpenChange?: (open: boolean) => void;
  onCredentialSaved?: (credential: AgentCredentialResponse) => void;
  /** Pre-selects the auth type when creating (mirrors the agent's declared auth type). */
  initialAuthType?: AgentAuthType;
}) {
  const { data: credentials, isLoading } = useAgentCredentials(organizationId, projectId, agentId);
  const deleteCredential = useDeleteCredential(organizationId, projectId, agentId);
  const testCredential = useTestCredential(organizationId, projectId, agentId);
  const [pendingDelete, setPendingDelete] = useState<AgentCredentialResponse | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [internalCreateOpen, setInternalCreateOpen] = useState(false);

  const createDialogOpen = createOpen ?? internalCreateOpen;
  const setCreateDialogOpen = onCreateOpenChange ?? setInternalCreateOpen;

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

  const handleTest = (credential: AgentCredentialResponse) => {
    setTestingId(credential.id);
    testCredential.mutate(credential.id, {
      onSuccess: (result) =>
        result.success ? toast.success(result.message) : toast.error(result.message),
      onError: (error) => toast.error(getApiErrorMessage(error)),
      onSettled: () => setTestingId(null),
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
        <Button size="sm" onClick={() => setCreateDialogOpen(true)}>
          <KeyRound className="h-4 w-4" />
          Set credential
        </Button>
      </div>

      {list.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title="Setup required"
          description="This agent cannot be used until credentials are configured. Set a credential so the platform can authenticate to it — e.g. a Groq, OpenAI or Anthropic API key."
          action={
            <Button size="sm" onClick={() => setCreateDialogOpen(true)}>
              <KeyRound className="h-4 w-4" />
              Set credential
            </Button>
          }
        />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {list.map((credential) => (
              <div key={credential.id} className="flex items-start justify-between gap-3 p-4">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">
                      {credential.label || credential.authType.replace(/_/g, " ")}
                    </span>
                    <Badge variant="outline">{credential.authType.replace(/_/g, " ")}</Badge>
                    {credential.active ? (
                      <Badge variant="success">Active</Badge>
                    ) : (
                      <Badge variant="muted">Inactive</Badge>
                    )}
                    <ConnectionStatusBadge credential={credential} />
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {credential.headerName ? `${credential.headerName} · ` : ""}
                    {credential.headerPrefix ? `prefix “${credential.headerPrefix}” · ` : ""}
                    {credential.username ? `${credential.username} · ` : ""}
                    {credential.secretHint ? `secret ${credential.secretHint} · ` : ""}
                    key v{credential.keyVersion} · updated {formatDateTime(credential.updatedAt)}
                  </p>
                  {credential.lastTestedAt && credential.lastTestMessage && (
                    <p
                      className={
                        credential.lastTestSuccess
                          ? "text-xs text-muted-foreground"
                          : "text-xs text-destructive"
                      }
                    >
                      Last test: {credential.lastTestMessage} ({formatDateTime(credential.lastTestedAt)})
                    </p>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Tooltip content="Test connection">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => handleTest(credential)}
                      loading={testingId === credential.id}
                      aria-label="Test connection"
                    >
                      <PlugZap className="h-4 w-4" />
                    </Button>
                  </Tooltip>
                  <SetCredentialDialog
                    organizationId={organizationId}
                    projectId={projectId}
                    agentId={agentId}
                    credential={credential}
                    trigger={
                      <Button variant="ghost" size="icon-sm" aria-label="Edit credential">
                        <Pencil className="h-4 w-4" />
                      </Button>
                    }
                  />
                  <Tooltip content="Delete credential">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setPendingDelete(credential)}
                      aria-label="Delete credential"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </Tooltip>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {/* Single controlled "create" dialog — opened by the buttons above or by onboarding. */}
      <SetCredentialDialog
        organizationId={organizationId}
        projectId={projectId}
        agentId={agentId}
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSaved={onCredentialSaved}
        initialAuthType={initialAuthType}
      />

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
