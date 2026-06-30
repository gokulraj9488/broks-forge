"use client";

import { useState } from "react";
import { PageHeader } from "@/components/layout/page-header";
import { TabsBar } from "@/components/ui/tabs-bar";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { BenchmarksPanel } from "@/components/benchmarks/benchmarks-panel";
import { RegressionPanel } from "@/components/benchmarks/regression-panel";

type Tab = "benchmarks" | "regression";

const TABS = [
  { key: "benchmarks" as const, label: "Benchmarks" },
  { key: "regression" as const, label: "Regression checks" },
];

export default function BenchmarksPage() {
  const [tab, setTab] = useState<Tab>("benchmarks");

  return (
    <div>
      <PageHeader
        title="Benchmarks"
        description="Rank evaluations head-to-head and guard against regressions over time."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <div className="space-y-5">
            <TabsBar tabs={TABS} value={tab} onChange={setTab} />
            {tab === "benchmarks" ? (
              <BenchmarksPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
            ) : (
              <RegressionPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
            )}
          </div>
        )}
      </WorkspaceSelector>
    </div>
  );
}
