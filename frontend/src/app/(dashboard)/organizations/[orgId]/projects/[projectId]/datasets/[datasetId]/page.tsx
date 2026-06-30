"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Database } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { StatusBadge } from "@/components/common/badges";
import { DatasetVersionsPanel } from "@/components/datasets/dataset-versions-panel";
import { DatasetItemsPanel } from "@/components/datasets/dataset-items-panel";
import { DatasetStatsPanel } from "@/components/datasets/dataset-stats-panel";
import { DatasetSettingsPanel } from "@/components/datasets/dataset-settings-panel";
import { useDataset } from "@/lib/hooks/use-datasets";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { formatDateTime } from "@/lib/utils";
import { formatNumber } from "@/lib/format";
import type { DatasetResponse } from "@/lib/api/datasets";

type Tab = "overview" | "versions" | "items" | "stats" | "settings";

const TABS = [
  { key: "overview" as const, label: "Overview" },
  { key: "versions" as const, label: "Versions" },
  { key: "items" as const, label: "Items" },
  { key: "stats" as const, label: "Stats" },
  { key: "settings" as const, label: "Settings" },
];

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm">{children}</div>
    </div>
  );
}

function Overview({ dataset }: { dataset: DatasetResponse }) {
  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="grid gap-6 p-6 sm:grid-cols-2">
          <Detail label="Visibility">{dataset.visibility}</Detail>
          <Detail label="Status">{dataset.status}</Detail>
          <Detail label="Latest version">v{dataset.latestVersionNumber ?? 0}</Detail>
          <Detail label="Current items">{formatNumber(dataset.currentItemCount ?? 0)}</Detail>
          <Detail label="Created">{formatDateTime(dataset.createdAt)}</Detail>
          <Detail label="Updated">{formatDateTime(dataset.updatedAt)}</Detail>
        </CardContent>
      </Card>
      {dataset.tags.length > 0 && (
        <Card>
          <CardContent className="space-y-2 p-6">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Tags</p>
            <div className="flex flex-wrap gap-2">
              {dataset.tags.map((tag) => (
                <span key={tag} className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  #{tag}
                </span>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

export default function DatasetDetailPage() {
  const params = useParams<{ orgId: string; projectId: string; datasetId: string }>();
  const { orgId, projectId, datasetId } = params;
  const { data: organization } = useOrganization(orgId);
  const { data: dataset, isLoading, isError } = useDataset(orgId, projectId, datasetId);
  const [tab, setTab] = useState<Tab>("overview");

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canDelete = role === "OWNER" || role === "ADMIN";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !dataset) {
    return (
      <EmptyState
        icon={Database}
        title="Dataset not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/datasets"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to datasets
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <Database className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{dataset.name}</h1>
            <p className="text-sm text-muted-foreground">/{dataset.slug}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {dataset.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
          <Badge variant="muted">{formatNumber(dataset.currentItemCount ?? 0)} items</Badge>
        </div>
      </div>

      {dataset.description && (
        <p className="max-w-2xl text-sm text-muted-foreground">{dataset.description}</p>
      )}

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "overview" && <Overview dataset={dataset} />}
        {tab === "versions" && (
          <DatasetVersionsPanel
            organizationId={orgId}
            projectId={projectId}
            datasetId={datasetId}
            currentVersionId={dataset.currentVersionId}
            canManage={canManage && dataset.status !== "ARCHIVED"}
          />
        )}
        {tab === "items" && (
          <DatasetItemsPanel
            organizationId={orgId}
            projectId={projectId}
            datasetId={datasetId}
            currentVersionId={dataset.currentVersionId}
          />
        )}
        {tab === "stats" && (
          <DatasetStatsPanel organizationId={orgId} projectId={projectId} datasetId={datasetId} />
        )}
        {tab === "settings" && (
          <DatasetSettingsPanel
            dataset={dataset}
            organizationId={orgId}
            projectId={projectId}
            canManage={canManage}
            canDelete={canDelete}
          />
        )}
      </div>
    </div>
  );
}
