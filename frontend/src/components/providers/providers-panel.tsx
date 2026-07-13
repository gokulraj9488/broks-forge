"use client";

import { useState } from "react";
import { Plug, Search, MoreHorizontal, Copy, Power, PowerOff, Trash2, Pencil, Zap, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { HealthBadge } from "@/components/common/badges";
import { ProviderFormDialog } from "@/components/providers/provider-form-dialog";
import {
  useDeleteProvider,
  useDuplicateProvider,
  useProviders,
  useRefreshProviderModels,
  useTestProviderConnection,
  useToggleProviderEnabled,
} from "@/lib/hooks/use-providers";
import { PROVIDER_TYPE_OPTIONS, type ProviderResponse } from "@/lib/api/providers";
import { AUTH_TYPE_OPTIONS } from "@/lib/api/agents";
import { getApiErrorMessage } from "@/lib/api/client";

const PAGE_SIZE = 12;

function formatRelativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.round(diffMs / 60_000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}

const TYPE_LABELS = Object.fromEntries(PROVIDER_TYPE_OPTIONS.map((o) => [o.value, o.label]));
const AUTH_LABELS = Object.fromEntries(AUTH_TYPE_OPTIONS.map((o) => [o.value, o.label]));

export function ProvidersPanel({
  organizationId,
  projectId,
  canManage,
  canDelete = canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
  /** Deleting a provider requires ADMIN server-side; defaults to canManage for other callers. */
  canDelete?: boolean;
}) {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<ProviderResponse | null>(null);
  const [editTarget, setEditTarget] = useState<ProviderResponse | null>(null);

  const { data, isLoading, isError } = useProviders(organizationId, projectId, { page, size: PAGE_SIZE });
  const duplicate = useDuplicateProvider(organizationId, projectId);
  const toggleEnabled = useToggleProviderEnabled(organizationId, projectId);
  const remove = useDeleteProvider(organizationId, projectId);
  const testConnection = useTestProviderConnection(organizationId, projectId);
  const refreshModels = useRefreshProviderModels(organizationId, projectId);

  const providers = data?.content ?? [];
  const filtered = q.trim()
    ? providers.filter((p) => p.name.toLowerCase().includes(q.trim().toLowerCase()))
    : providers;

  const handleDuplicate = (provider: ProviderResponse) => {
    duplicate.mutate(provider.id, {
      onSuccess: () => toast.success(`Duplicated "${provider.name}"`),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  const handleToggle = (provider: ProviderResponse) => {
    toggleEnabled.mutate(
      { providerId: provider.id, enabled: !provider.enabled },
      {
        onSuccess: () => toast.success(provider.enabled ? "Provider disabled" : "Provider enabled"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const handleTestConnection = (provider: ProviderResponse) => {
    testConnection.mutate(provider.id, {
      onSuccess: (result) => {
        const label = result.probeUrl ? `${result.probeStrategy ?? "probe"} · ${result.probeUrl}` : result.message;
        if (result.success) {
          toast.success(`Connected · ${result.latencyMs}ms`, { description: label });
        } else {
          toast.error(result.message, { description: result.probeUrl ?? undefined });
        }
      },
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  const handleRefreshModels = (provider: ProviderResponse) => {
    refreshModels.mutate(provider.id, {
      onSuccess: (result) => {
        if (!result.supported) {
          toast.error(result.message ?? "Model discovery isn't supported for this provider.");
        } else if (result.models.length === 0) {
          toast.warning(result.message ?? "No models returned by the provider.");
        } else {
          toast.success(`Found ${result.models.length} model${result.models.length === 1 ? "" : "s"}`);
        }
      },
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    remove.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(`Deleted "${deleteTarget.name}"`);
        setDeleteTarget(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setDeleteTarget(null);
      },
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search providers…"
            className="pl-9"
          />
        </div>
        {canManage && <ProviderFormDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-36 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Plug} title="Couldn't load providers" description="Please try again." />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={Plug}
          title="No providers found"
          description={
            q
              ? "No providers match your search."
              : "Register a provider (OpenAI, Groq, OpenRouter, Google AI Studio, Anthropic, Ollama, ...) so agents can reference it instead of duplicating configuration."
          }
          action={
            canManage && !q ? (
              <ProviderFormDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {filtered.map((provider) => (
              <Card key={provider.id} className={!provider.enabled ? "opacity-60" : undefined}>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                        <Plug className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-medium leading-tight">{provider.name}</h3>
                        <p className="truncate text-xs text-muted-foreground">{provider.baseUrl}</p>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      {!provider.enabled && <Badge variant="muted">Disabled</Badge>}
                      <HealthBadge status={provider.healthStatus} />
                      {canManage && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-7 w-7">
                              <MoreHorizontal className="h-4 w-4" />
                              <span className="sr-only">Actions</span>
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => setEditTarget(provider)}>
                              <Pencil className="h-4 w-4" />
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleTestConnection(provider)}
                              disabled={testConnection.isPending}
                            >
                              <Zap className="h-4 w-4" />
                              Test connection
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleRefreshModels(provider)}
                              disabled={refreshModels.isPending}
                            >
                              <RefreshCw className="h-4 w-4" />
                              Refresh models
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleDuplicate(provider)}
                              disabled={duplicate.isPending}
                            >
                              <Copy className="h-4 w-4" />
                              Duplicate
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleToggle(provider)} disabled={toggleEnabled.isPending}>
                              {provider.enabled ? (
                                <>
                                  <PowerOff className="h-4 w-4" />
                                  Disable
                                </>
                              ) : (
                                <>
                                  <Power className="h-4 w-4" />
                                  Enable
                                </>
                              )}
                            </DropdownMenuItem>
                            {canDelete && (
                              <>
                                <DropdownMenuSeparator />
                                <DropdownMenuItem
                                  className="text-destructive focus:text-destructive"
                                  onClick={() => setDeleteTarget(provider)}
                                >
                                  <Trash2 className="h-4 w-4" />
                                  Delete
                                </DropdownMenuItem>
                              </>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <Badge variant="outline">{TYPE_LABELS[provider.type] ?? provider.type}</Badge>
                    <Badge variant="muted">{AUTH_LABELS[provider.authType] ?? provider.authType}</Badge>
                    {provider.defaultModel && <Badge variant="muted">{provider.defaultModel}</Badge>}
                  </div>

                  <div className="mt-4 grid grid-cols-3 gap-2 text-xs text-muted-foreground">
                    <div>
                      <p className="text-[11px] uppercase tracking-wide">Models</p>
                      <p className="font-mono text-foreground">{provider.modelCount || "—"}</p>
                    </div>
                    <div>
                      <p className="text-[11px] uppercase tracking-wide">Agents</p>
                      <p className="font-mono text-foreground">{provider.linkedAgentCount}</p>
                    </div>
                    <div>
                      <p className="text-[11px] uppercase tracking-wide">Last used</p>
                      <p className="font-mono text-foreground">
                        {provider.lastUsedAt ? formatRelativeTime(provider.lastUsedAt) : "Never"}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? filtered.length}
            onPageChange={setPage}
          />
        </>
      )}

      {editTarget && (
        <ProviderFormDialog
          organizationId={organizationId}
          projectId={projectId}
          provider={editTarget}
          open={!!editTarget}
          onOpenChange={(open) => !open && setEditTarget(null)}
        />
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Delete "${deleteTarget?.name}"?`}
        description="This can't be undone. Fails if any agent is still linked to this provider."
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
