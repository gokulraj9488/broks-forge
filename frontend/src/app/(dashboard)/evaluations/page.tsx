"use client";

import { useState } from "react";
import { PageHeader } from "@/components/layout/page-header";
import { TabsBar } from "@/components/ui/tabs-bar";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { JobsPanel } from "@/components/evaluations/jobs-panel";
import { ProfilesPanel } from "@/components/evaluations/profiles-panel";

type Tab = "jobs" | "profiles";

const TABS = [
  { key: "jobs" as const, label: "Evaluations" },
  { key: "profiles" as const, label: "Scoring profiles" },
];

export default function EvaluationsPage() {
  const [tab, setTab] = useState<Tab>("jobs");

  return (
    <div>
      <PageHeader
        title="Evaluations"
        description="Score agents against datasets, track pass rates, latency, tokens and cost."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <div className="space-y-5">
            <TabsBar tabs={TABS} value={tab} onChange={setTab} />
            {tab === "jobs" ? (
              <JobsPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
            ) : (
              <ProfilesPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
            )}
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
