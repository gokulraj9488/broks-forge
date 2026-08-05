"use client";

import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { RegistryExplorer } from "@/components/registry/registry-explorer";
import { AskBrok } from "@/components/brok/ask-brok";

export default function RegistryPage() {
  return (
    <div>
      <PageHeader
        title="Registry"
        description="One catalog of every engineering artifact and the knowledge derived from it — search, filter and open providers, agents, prompts, datasets, evaluations and their observations, decisions and evidence across your organization."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId }) => (
          <div className="space-y-3">
            <div className="flex justify-end">
              {/* The registry lists what exists; Brok says which of it needs you. */}
              <AskBrok
                organizationId={organizationId}
                projectId={projectId}
                question="What is the biggest engineering risk right now?"
              />
            </div>
            <RegistryExplorer organizationId={organizationId} />
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
