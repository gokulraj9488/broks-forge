"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plug, Building2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { ProvidersPanel } from "@/components/providers/providers-panel";
import { useOrganizations } from "@/lib/hooks/use-organizations";
import { useProjects } from "@/lib/hooks/use-projects";

export default function ProvidersPage() {
  const { data: orgsData, isLoading: orgsLoading } = useOrganizations({ size: 100 });
  const organizations = orgsData?.content ?? [];

  const [orgId, setOrgId] = useState<string>("");
  useEffect(() => {
    if (!orgId && organizations.length > 0) {
      setOrgId(organizations[0].id);
    }
  }, [orgId, organizations]);

  const { data: projectsData, isLoading: projectsLoading } = useProjects(orgId || undefined, { size: 100 });
  const projects = projectsData?.content ?? [];

  const [projectId, setProjectId] = useState<string>("");
  useEffect(() => {
    if (projects.length > 0 && !projects.some((p) => p.id === projectId)) {
      setProjectId(projects[0].id);
    }
    if (projects.length === 0) {
      setProjectId("");
    }
  }, [projects, projectId]);

  const selectedOrg = organizations.find((o) => o.id === orgId);
  const isMember = !!selectedOrg?.currentUserRole;
  // Deleting a provider requires ADMIN server-side (ProviderService.delete); every other action
  // (edit/duplicate/enable-disable/test-connection/refresh-models) only requires MEMBER. A plain
  // member must not see a Delete action that will always 403.
  const canDelete = selectedOrg?.currentUserRole === "OWNER" || selectedOrg?.currentUserRole === "ADMIN";

  return (
    <div>
      <PageHeader
        title="Providers"
        description="Register the LLM providers your agents connect through — OpenAI, Groq, OpenRouter, Google AI Studio, Anthropic, Ollama and more — instead of duplicating connection details on every agent."
      />

      {orgsLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-9 w-full max-w-md" />
          <Skeleton className="h-48 w-full" />
        </div>
      ) : organizations.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No organizations yet"
          description="Create an organization and a project before registering providers."
          action={
            <Button asChild>
              <Link href="/organizations">Go to organizations</Link>
            </Button>
          }
        />
      ) : (
        <div className="space-y-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Organization</span>
              <Select
                value={orgId}
                onValueChange={(v) => {
                  setOrgId(v);
                  setProjectId("");
                }}
              >
                <SelectTrigger className="h-9 w-auto min-w-[12rem]" aria-label="Organization">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {organizations.map((org) => (
                    <SelectItem key={org.id} value={org.id}>
                      {org.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Project</span>
              <Select value={projectId || undefined} onValueChange={(v) => setProjectId(v)}>
                <SelectTrigger
                  className="h-9 w-auto min-w-[12rem]"
                  disabled={projects.length === 0}
                  aria-label="Project"
                >
                  <SelectValue placeholder="No projects" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map((project) => (
                    <SelectItem key={project.id} value={project.id}>
                      {project.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {projectsLoading ? (
            <Skeleton className="h-48 w-full" />
          ) : projects.length === 0 ? (
            <EmptyState
              icon={Plug}
              title="No projects in this organization"
              description="Create a project first, then register providers inside it."
              action={
                <Button asChild>
                  <Link href={`/organizations/${orgId}`}>Open organization</Link>
                </Button>
              }
            />
          ) : projectId ? (
            <ProvidersPanel
              organizationId={orgId}
              projectId={projectId}
              canManage={isMember}
              canDelete={canDelete}
            />
          ) : null}
        </div>
      )}
    </div>
  );
}
