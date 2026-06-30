"use client";

import { useState } from "react";
import { Gauge, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { CreateProfileDialog } from "@/components/evaluations/create-profile-dialog";
import {
  useDeleteEvaluationProfile,
  useEvaluationProfiles,
} from "@/lib/hooks/use-evaluation-profiles";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/utils";
import { formatPercent } from "@/lib/format";

export function ProfilesPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const { data, isLoading, isError } = useEvaluationProfiles(organizationId, projectId, {
    size: 100,
  });
  const remove = useDeleteEvaluationProfile(organizationId, projectId);
  const [toDelete, setToDelete] = useState<{ id: string; name: string } | null>(null);
  const profiles = data?.content ?? [];

  const handleDelete = () => {
    if (!toDelete) return;
    remove.mutate(toDelete.id, {
      onSuccess: () => {
        toast.success("Profile deleted");
        setToDelete(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setToDelete(null);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-28 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Scoring profiles</h2>
          <p className="text-sm text-muted-foreground">Reusable metric sets used to grade evaluations.</p>
        </div>
        {canManage && <CreateProfileDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      {isError ? (
        <EmptyState icon={Gauge} title="Couldn't load profiles" description="Please try again." />
      ) : profiles.length === 0 ? (
        <EmptyState
          icon={Gauge}
          title="No profiles yet"
          description="Create a scoring profile to grade evaluation runs against your metrics."
          action={
            canManage ? <CreateProfileDialog organizationId={organizationId} projectId={projectId} /> : undefined
          }
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {profiles.map((profile) => (
            <Card key={profile.id}>
              <CardContent className="p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                      <Gauge className="h-5 w-5 text-primary" />
                    </div>
                    <div className="min-w-0">
                      <h3 className="truncate font-medium leading-tight">{profile.name}</h3>
                      <p className="truncate text-xs text-muted-foreground">/{profile.slug}</p>
                    </div>
                  </div>
                  {canManage && (
                    <Button
                      size="icon"
                      variant="ghost"
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setToDelete({ id: profile.id, name: profile.name })}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <Badge variant="outline">{profile.metricCount} metrics</Badge>
                  {profile.passThreshold != null && (
                    <Badge variant="muted">pass ≥ {formatPercent(profile.passThreshold)}</Badge>
                  )}
                  <span>Updated {formatDate(profile.updatedAt)}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(o) => !o && setToDelete(null)}
        title="Delete profile?"
        description={toDelete ? `"${toDelete.name}" will be permanently removed.` : undefined}
        confirmLabel="Delete profile"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
