"use client";

import { useState } from "react";
import { KeyRound, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { CreateApiKeyDialog } from "@/components/api-keys/create-api-key-dialog";
import { useApiKeys, useRevokeApiKey } from "@/lib/hooks/use-api-keys";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate, formatDateTime } from "@/lib/utils";
import type { ApiKeyResponse } from "@/lib/api/types";

function keyStatus(key: ApiKeyResponse): { label: string; variant: "success" | "muted" | "destructive" } {
  if (key.revoked) return { label: "Revoked", variant: "destructive" };
  if (key.expiresAt && new Date(key.expiresAt).getTime() < Date.now()) {
    return { label: "Expired", variant: "muted" };
  }
  return { label: "Active", variant: "success" };
}

export function ApiKeysPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const { data, isLoading } = useApiKeys(organizationId, projectId, { size: 100 });
  const revoke = useRevokeApiKey(organizationId, projectId);
  const [pendingRevoke, setPendingRevoke] = useState<ApiKeyResponse | null>(null);

  const keys = data?.content ?? [];

  const handleRevoke = () => {
    if (!pendingRevoke) return;
    revoke.mutate(pendingRevoke.id, {
      onSuccess: () => {
        toast.success("API key revoked");
        setPendingRevoke(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setPendingRevoke(null);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">API keys</h2>
          <p className="text-sm text-muted-foreground">
            Programmatic access scoped to this project.
          </p>
        </div>
        {canManage && (
          <CreateApiKeyDialog organizationId={organizationId} projectId={projectId} />
        )}
      </div>

      {keys.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title="No API keys"
          description={
            canManage
              ? "Create a key to access this project programmatically."
              : "An organization admin can create API keys for this project."
          }
          action={
            canManage ? (
              <CreateApiKeyDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {keys.map((key) => {
              const status = keyStatus(key);
              return (
                <div key={key.id} className="flex items-center justify-between gap-3 p-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-sm font-medium">{key.name}</p>
                      <Badge variant={status.variant}>{status.label}</Badge>
                    </div>
                    <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground">
                      {key.keyPrefix}••••••••
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Created {formatDate(key.createdAt)}
                      {key.expiresAt ? ` · expires ${formatDate(key.expiresAt)}` : " · never expires"}
                      {key.lastUsedAt ? ` · last used ${formatDateTime(key.lastUsedAt)}` : ""}
                    </p>
                  </div>
                  {canManage && !key.revoked && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 shrink-0 text-muted-foreground hover:text-destructive"
                      onClick={() => setPendingRevoke(key)}
                      aria-label="Revoke API key"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              );
            })}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={!!pendingRevoke}
        onOpenChange={(open) => !open && setPendingRevoke(null)}
        title="Revoke API key?"
        description={`"${pendingRevoke?.name}" will stop working immediately. This cannot be undone.`}
        confirmLabel="Revoke"
        destructive
        loading={revoke.isPending}
        onConfirm={handleRevoke}
      />
    </div>
  );
}
