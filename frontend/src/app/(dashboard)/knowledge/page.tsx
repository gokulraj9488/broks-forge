"use client";

import { PageHeader } from "@/components/layout/page-header";
import { KnowledgePanel } from "@/components/knowledge/knowledge-panel";

export default function KnowledgePage() {
  return (
    <div>
      <PageHeader
        title="Engineering knowledge"
        description="A curated graph of failure modes, regressions, recommendations and optimizations that powers the advisor and root-cause analysis."
      />
      <KnowledgePanel />
    </div>
  );
}
