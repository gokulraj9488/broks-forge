"use client";

import { useState } from "react";
import Link from "next/link";
import { FolderKanban, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/common/badges";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { useDeleteProject, useProjects } from "@/lib/hooks/use-projects";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/utils";
import type { ProjectResponse } from "@/lib/api/types";

export function ProjectsPanel({
  organizationId,
  canManage,
}: {
  organizationId: string;
  canManage: boolean;
}) {
  const { data, isLoading } = useProjects(organizationId, { size: 100 });
  const deleteProject = useDeleteProject(organizationId);
  const [pendingDelete, setPendingDelete] = useState<ProjectResponse | null>(null);

  const projects = data?.content ?? [];

  const handleDelete = () => {
    if (!pendingDelete) return;
    deleteProject.mutate(pendingDelete.id, {
      onSuccess: () => {
        toast.success("Project deleted");
        setPendingDelete(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setPendingDelete(null);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-24 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {projects.length} project{projects.length === 1 ? "" : "s"}
        </p>
        <CreateProjectDialog organizationId={organizationId} />
      </div>

      {projects.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          description="Create a project to start building agents and issuing API keys."
          action={<CreateProjectDialog organizationId={organizationId} />}
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {projects.map((project) => (
            <Card key={project.id} className="group transition-colors hover:border-primary/40">
              <CardContent className="p-5">
                <div className="flex items-start justify-between gap-2">
                  <Link
                    href={`/organizations/${organizationId}/projects/${project.id}`}
                    className="min-w-0 flex-1"
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
                        <FolderKanban className="h-4 w-4 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-medium leading-tight">{project.name}</h3>
                        <p className="truncate text-xs text-muted-foreground">/{project.slug}</p>
                      </div>
                    </div>
                  </Link>
                  {canManage && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 text-muted-foreground hover:text-destructive"
                      onClick={() => setPendingDelete(project)}
                      aria-label="Delete project"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
                {project.description && (
                  <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">
                    {project.description}
                  </p>
                )}
                <div className="mt-4 flex items-center gap-3 text-xs text-muted-foreground">
                  <StatusBadge status={project.status} />
                  <span>Created {formatDate(project.createdAt)}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete project?"
        description={`"${pendingDelete?.name}" and its API keys will be removed. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleteProject.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
