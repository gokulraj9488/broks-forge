"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { PromptsPanel } from "@/components/prompts/prompts-panel";
import { SurfaceSummary } from "@/components/common/surface-summary";

export default function PromptsPage() {
  return (
    <div>
      <PageHeader
        title="Prompts"
        description="Versioned intent — every change recorded with its rationale, so the reasoning behind your instructions is never lost."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <div className="space-y-5">
            <SurfaceSummary kind="prompts" organizationId={organizationId} projectId={projectId} />
            <PromptsPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
