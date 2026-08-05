"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { DatasetsPanel } from "@/components/datasets/datasets-panel";
import { SurfaceSummary } from "@/components/common/surface-summary";

export default function DatasetsPage() {
  return (
    <div>
      <PageHeader
        title="Datasets"
        description="The ground truth your evaluations measure against — versioned, so every result stays reproducible."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <div className="space-y-5">
            <SurfaceSummary kind="datasets" organizationId={organizationId} projectId={projectId} />
            <DatasetsPanel
              organizationId={organizationId}
              projectId={projectId}
              canManage={isMember}
            />
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
