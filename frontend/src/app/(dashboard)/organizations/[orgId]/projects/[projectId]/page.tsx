"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, FolderKanban } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { StatusBadge } from "@/components/common/badges";
import { AgentsPanel } from "@/components/agents/agents-panel";
import { ApiKeysPanel } from "@/components/api-keys/api-keys-panel";
import { DatasetsPanel } from "@/components/datasets/datasets-panel";
import { PromptsPanel } from "@/components/prompts/prompts-panel";
import { JobsPanel } from "@/components/evaluations/jobs-panel";
import { useProject } from "@/lib/hooks/use-projects";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { formatDate } from "@/lib/utils";

type Tab = "agents" | "datasets" | "prompts" | "evaluations" | "api-keys";

const TABS = [
  { key: "agents" as const, label: "Agents" },
  { key: "datasets" as const, label: "Datasets" },
  { key: "prompts" as const, label: "Prompts" },
  { key: "evaluations" as const, label: "Evaluations" },
  { key: "api-keys" as const, label: "API Keys" },
];

export default function ProjectDetailPage() {
  const params = useParams<{ orgId: string; projectId: string }>();
  const { orgId, projectId } = params;
  const { data: organization } = useOrganization(orgId);
  const { data: project, isLoading, isError } = useProject(orgId, projectId);
  const [tab, setTab] = useState<Tab>("agents");

  const canManage =
    organization?.currentUserRole === "OWNER" || organization?.currentUserRole === "ADMIN";
  const isMember = !!organization?.currentUserRole;

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError || !project) {
    return (
      <EmptyState
        icon={FolderKanban}
        title="Project not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/organizations/${orgId}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        {organization?.name ?? "Organization"}
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <FolderKanban className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{project.name}</h1>
            <p className="text-sm text-muted-foreground">
              /{project.slug} · created {formatDate(project.createdAt)}
            </p>
          </div>
        </div>
        <StatusBadge status={project.status} />
      </div>

      {project.description && (
        <p className="max-w-2xl text-sm text-muted-foreground">{project.description}</p>
      )}

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "agents" && (
          <AgentsPanel organizationId={orgId} projectId={projectId} canManage={isMember} />
        )}
        {tab === "datasets" && (
          <DatasetsPanel organizationId={orgId} projectId={projectId} canManage={isMember} />
        )}
        {tab === "prompts" && (
          <PromptsPanel organizationId={orgId} projectId={projectId} canManage={isMember} />
        )}
        {tab === "evaluations" && (
          <JobsPanel organizationId={orgId} projectId={projectId} canManage={isMember} />
        )}
        {tab === "api-keys" && (
          <ApiKeysPanel organizationId={orgId} projectId={projectId} canManage={!!canManage} />
        )}
      </div>
    </div>
  );
}
