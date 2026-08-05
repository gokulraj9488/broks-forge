"use client";

import { useState } from "react";
import { PageHeader } from "@/components/layout/page-header";
import { TabsBar } from "@/components/ui/tabs-bar";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { AnalyticsPanel } from "@/components/analytics/analytics-panel";
import { ReportsPanel } from "@/components/reports/reports-panel";
import { SurfaceSummary } from "@/components/common/surface-summary";

type Tab = "analytics" | "reports";

const TABS = [
  { key: "analytics" as const, label: "Analytics" },
  { key: "reports" as const, label: "Reports" },
];

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("analytics");

  return (
    <div>
      <PageHeader
        title="Analytics"
        description="What the evidence adds up to — quality, latency and spend, always reported together."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId }) => (
          <div className="space-y-5">
            <SurfaceSummary kind="analytics" organizationId={organizationId} projectId={projectId} />
            <TabsBar tabs={TABS} value={tab} onChange={setTab} />
            {tab === "analytics" ? (
              <AnalyticsPanel organizationId={organizationId} projectId={projectId} />
            ) : (
              <ReportsPanel organizationId={organizationId} projectId={projectId} />
            )}
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
