"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { DashboardPanel } from "@/components/dashboard/dashboard-panel";
import { SearchBox } from "@/components/search/search-box";

export default function InsightsPage() {
  return (
    <div>
      <PageHeader
        title="Project insights"
        description="A live overview of agents, datasets, prompts and evaluation activity in this project."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId }) => (
          <div className="space-y-6">
            <SearchBox organizationId={organizationId} projectId={projectId} />
            <DashboardPanel organizationId={organizationId} projectId={projectId} />
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
