"use client";

import { useState } from "react";
import { PageHeader } from "@/components/layout/page-header";
import { TabsBar } from "@/components/ui/tabs-bar";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { BenchmarksPanel } from "@/components/benchmarks/benchmarks-panel";
import { RegressionPanel } from "@/components/benchmarks/regression-panel";
import { BenchmarkGalleryPanel } from "@/components/benchmarks/benchmark-gallery-panel";
import { SurfaceSummary } from "@/components/common/surface-summary";

type Tab = "gallery" | "benchmarks" | "regression";

const TABS = [
  { key: "gallery" as const, label: "Gallery" },
  { key: "benchmarks" as const, label: "Benchmarks" },
  { key: "regression" as const, label: "Regression checks" },
];

export default function BenchmarksPage() {
  const [tab, setTab] = useState<Tab>("gallery");

  return (
    <div>
      <PageHeader
        title="Benchmarks"
        description="The standards your artifacts are measured against — so quality is compared to a known bar, not to a feeling."
      />
      <WorkspaceSelector>
        {({ organizationId, projectId, isMember }) => (
          <div className="space-y-5">
            <SurfaceSummary kind="benchmarks" organizationId={organizationId} projectId={projectId} />
            <TabsBar tabs={TABS} value={tab} onChange={setTab} />
            {tab === "gallery" ? (
              <BenchmarkGalleryPanel organizationId={organizationId} projectId={projectId} canManage={isMember} />
            ) : tab === "benchmarks" ? (
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
