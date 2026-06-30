"use client";

import { useState } from "react";
import { PageHeader } from "@/components/layout/page-header";
import { TabsBar } from "@/components/ui/tabs-bar";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { AnalyticsPanel } from "@/components/analytics/analytics-panel";
import { ReportsPanel } from "@/components/reports/reports-panel";

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
        description="Track evaluation volume, pass rates, latency and spend across your project."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId }) => (
          <div className="space-y-5">
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
