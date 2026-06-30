"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { DatasetsPanel } from "@/components/datasets/datasets-panel";

export default function DatasetsPage() {
  return (
    <div>
      <PageHeader
        title="Datasets"
        description="Curate evaluation inputs and expected outputs, versioned and ready to test against."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <DatasetsPanel
            organizationId={organizationId}
            projectId={projectId}
            canManage={isMember}
          />
        )}
      </WorkspaceSelector>
    </div>
  );
}
