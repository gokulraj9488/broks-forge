"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { AdvisorPanel } from "@/components/advisor/advisor-panel";

export default function AdvisorPage() {
  return (
    <div>
      <PageHeader
        title="Advisor"
        description="AI-generated engineering recommendations to improve quality, reliability, latency and cost across your project."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId }) => (
          <AdvisorPanel organizationId={organizationId} projectId={projectId} />
        )}
      </WorkspaceSelector>
    </div>
  );
}
