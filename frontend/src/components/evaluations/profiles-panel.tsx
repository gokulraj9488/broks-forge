"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Gauge, Search, MoreHorizontal, Copy, Power, PowerOff, Trash2, Pencil } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { CreateProfileDialog } from "@/components/evaluations/create-profile-dialog";
import {
  useDeleteEvaluationProfile,
  useDuplicateEvaluationProfile,
  useEvaluationProfiles,
  useToggleEvaluationProfileEnabled,
} from "@/lib/hooks/use-evaluation-profiles";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/utils";
import { formatPercent } from "@/lib/format";
import type { EvaluationProfileResponse } from "@/lib/api/evaluation-profiles";

const PAGE_SIZE = 12;

export function ProfilesPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<EvaluationProfileResponse | null>(null);

  const { data, isLoading, isError, refetch, isRefetching } = useEvaluationProfiles(organizationId, projectId, {
    page,
    size: PAGE_SIZE,
    search: q.trim() || undefined,
  });
  const duplicate = useDuplicateEvaluationProfile(organizationId, projectId);
  const toggleEnabled = useToggleEvaluationProfileEnabled(organizationId, projectId);
  const remove = useDeleteEvaluationProfile(organizationId, projectId);

  const profiles = data?.content ?? [];

  const handleDuplicate = (profile: EvaluationProfileResponse) => {
    duplicate.mutate(profile.id, {
      onSuccess: (copy) => {
        toast.success(`Duplicated "${profile.name}"`);
        router.push(`/organizations/${organizationId}/projects/${projectId}/evaluations/profiles/${copy.id}`);
      },
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  const handleToggle = (profile: EvaluationProfileResponse) => {
    toggleEnabled.mutate(
      { profileId: profile.id, enabled: !profile.enabled },
      {
        onSuccess: () => toast.success(profile.enabled ? "Profile disabled" : "Profile enabled"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
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

  const editHref = (profileId: string) =>
    `/organizations/${organizationId}/projects/${projectId}/evaluations/profiles/${profileId}`;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder="Search profiles…"
            className="pl-9"
          />
        </div>
        {canManage && <CreateProfileDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState
          icon={Gauge}
          title="Couldn't load profiles"
          description="Something went wrong reaching the server. Check your connection and try again."
          action={
            <Button variant="outline" onClick={() => refetch()} loading={isRefetching}>
              Retry
            </Button>
          }
        />
      ) : profiles.length === 0 ? (
        <EmptyState
          icon={Gauge}
          title={q ? "No profiles match your search" : "No evaluation profiles yet"}
          description={
            q
              ? "Try a different name or clear the search."
              : "Start from a preset — Conversation (semantic similarity + LLM judge) for chat agents, or Exact Match for structured/deterministic outputs."
          }
          action={
            canManage && !q ? <CreateProfileDialog organizationId={organizationId} projectId={projectId} /> : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {profiles.map((profile) => (
              <Card key={profile.id} className={!profile.enabled ? "opacity-60" : undefined}>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-2">
                    <button
                      type="button"
                      className="flex min-w-0 items-center gap-3 text-left"
                      onClick={() => router.push(editHref(profile.id))}
                    >
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                        <Gauge className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-medium leading-tight">{profile.name}</h3>
                        <p className="truncate text-xs text-muted-foreground">/{profile.slug}</p>
                      </div>
                    </button>
                    <div className="flex shrink-0 items-center gap-2">
                      {!profile.enabled && <Badge variant="muted">Disabled</Badge>}
                      {canManage && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-7 w-7">
                              <MoreHorizontal className="h-4 w-4" />
                              <span className="sr-only">Actions</span>
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => router.push(editHref(profile.id))}>
                              <Pencil className="h-4 w-4" />
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDuplicate(profile)}>
                              <Copy className="h-4 w-4" />
                              Duplicate
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleToggle(profile)}>
                              {profile.enabled ? (
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
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              className="text-destructive focus:text-destructive"
                              onClick={() => setDeleteTarget(profile)}
                            >
                              <Trash2 className="h-4 w-4" />
                              Delete
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </div>
                  </div>
                  <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <Badge variant="outline">{profile.metrics.length} metrics</Badge>
                    <Badge variant="muted">v{profile.currentVersionNumber}</Badge>
                    {profile.passThreshold != null && (
                      <Badge variant="muted">pass ≥ {formatPercent(profile.passThreshold)}</Badge>
                    )}
                    <span>Updated {formatDate(profile.updatedAt)}</span>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? profiles.length}
            onPageChange={setPage}
          />
        </>
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(o) => !o && setDeleteTarget(null)}
        title={`Delete "${deleteTarget?.name}"?`}
        description="This can't be undone."
        confirmLabel="Delete profile"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
