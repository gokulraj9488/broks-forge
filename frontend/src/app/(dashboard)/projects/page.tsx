"use client";

import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import { Building2, FolderKanban } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/common/badges";
import { PageHeader } from "@/components/layout/page-header";
import { useOrganizations } from "@/lib/hooks/use-organizations";
import { projectsApi } from "@/lib/api/projects";
import type { OrganizationResponse, ProjectResponse } from "@/lib/api/types";

export default function ProjectsPage() {
  const { data: orgsData, isLoading: orgsLoading } = useOrganizations({ size: 100 });
  const organizations = orgsData?.content ?? [];

  const projectQueries = useQueries({
    queries: organizations.map((org) => ({
      queryKey: ["organizations", org.id, "projects", "list", { size: 100 }],
      queryFn: () => projectsApi.list(org.id, { size: 100 }),
      enabled: organizations.length > 0,
    })),
  });

  const loading = orgsLoading || projectQueries.some((q) => q.isLoading);

  const rows: { org: OrganizationResponse; project: ProjectResponse }[] = organizations.flatMap(
    (org, index) => {
      const projects = projectQueries[index]?.data?.content ?? [];
      return projects.map((project) => ({ org, project }));
    },
  );

  return (
    <div>
      <PageHeader
        title="Projects"
        description="Every project across the organizations you belong to."
      />

      {loading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
      ) : organizations.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No organizations yet"
          description="Create an organization first, then add projects to it."
          action={
            <Button asChild>
              <Link href="/organizations">Go to organizations</Link>
            </Button>
          }
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          description="Open an organization to create your first project."
          action={
            <Button asChild>
              <Link href="/organizations">Go to organizations</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {rows.map(({ org, project }) => (
            <Link
              key={project.id}
              href={`/organizations/${org.id}/projects/${project.id}`}
              className="group"
            >
              <Card className="h-full transition-colors group-hover:border-primary/40">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
                        <FolderKanban className="h-4 w-4 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-medium leading-tight">{project.name}</h3>
                        <p className="truncate text-xs text-muted-foreground">
                          {org.name} · /{project.slug}
                        </p>
                      </div>
                    </div>
                    <StatusBadge status={project.status} />
                  </div>
                  {project.description && (
                    <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">
                      {project.description}
                    </p>
                  )}
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
