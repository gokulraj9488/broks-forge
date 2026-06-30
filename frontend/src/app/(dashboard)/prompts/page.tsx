"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { PromptsPanel } from "@/components/prompts/prompts-panel";

export default function PromptsPage() {
  return (
    <div>
      <PageHeader
        title="Prompts"
        description="Version-control prompt templates, track variables and roll back with confidence."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <PromptsPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
        )}
      </WorkspaceSelector>
    </div>
  );
}
