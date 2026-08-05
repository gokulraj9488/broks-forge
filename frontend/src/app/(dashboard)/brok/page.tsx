"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { Skeleton } from "@/components/ui/skeleton";
import { BrokWorkspace } from "@/components/brok/brok-workspace";

/**
 * Brok — a permanent engineering workspace, not a floating assistant.
 *
 * Deep links carry the engineering context an engineer was already in: {@code ?org=&project=} land the
 * workspace on the right project, {@code ?focus=} puts an artifact or knowledge object in focus, and
 * {@code ?q=} asks a question on arrival. That is what makes "ask Brok about this" from an evaluation,
 * an artifact or the graph feel like continuing the same thought rather than starting a new one.
 */
export default function BrokPage() {
  return (
    <div>
      <PageHeader
        title="Brok"
        description="Your engineering partner — answers read from the engineering record, each one saying how it is known and where it came from."
      />
      <Suspense fallback={<Skeleton className="h-96 w-full rounded-xl" />}>
        <BrokRoute />
      </Suspense>
    </div>
  );
}

function BrokRoute() {
  const params = useSearchParams();
  const focus = params.get("focus");
  const question = params.get("q");
  const org = params.get("org") ?? undefined;
  const project = params.get("project") ?? undefined;

  return (
    <WorkspaceSelector initialOrganizationId={org} initialProjectId={project}>
      {({ organizationId, projectId }) => (
        <BrokWorkspace
          key={`${organizationId}:${projectId}`}
          organizationId={organizationId}
          projectId={projectId}
          initialFocus={focus}
          initialQuestion={question}
        />
      )}
    </WorkspaceSelector>
  );
}
