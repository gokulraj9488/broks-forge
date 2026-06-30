"use client";

import { Layers } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { UploadVersionDialog } from "@/components/datasets/upload-version-dialog";
import { useDatasetVersions } from "@/lib/hooks/use-datasets";
import { formatDateTime } from "@/lib/utils";
import { formatNumber } from "@/lib/format";

export function DatasetVersionsPanel({
  organizationId,
  projectId,
  datasetId,
  currentVersionId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
  currentVersionId: string | null;
  canManage: boolean;
}) {
  const { data, isLoading, isError } = useDatasetVersions(organizationId, projectId, datasetId, {
    size: 50,
  });
  const versions = data?.content ?? [];

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Versions</h2>
          <p className="text-sm text-muted-foreground">Immutable snapshots of this dataset.</p>
        </div>
        {canManage && (
          <UploadVersionDialog organizationId={organizationId} projectId={projectId} datasetId={datasetId} />
        )}
      </div>

      {isError ? (
        <EmptyState icon={Layers} title="Couldn't load versions" description="Please try again." />
      ) : versions.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="No versions yet"
          description="Upload a version to populate this dataset with items."
          action={
            canManage ? (
              <UploadVersionDialog organizationId={organizationId} projectId={projectId} datasetId={datasetId} />
            ) : undefined
          }
        />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {versions.map((version) => (
              <div key={version.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm font-medium">v{version.versionNumber}</span>
                    {version.id === currentVersionId && <Badge variant="success">Current</Badge>}
                    <Badge variant="outline">{version.sourceFormat}</Badge>
                    <Badge variant="muted">{formatNumber(version.itemCount)} items</Badge>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {version.columns.length} columns · created {formatDateTime(version.createdAt)}
                  </p>
                  {version.description && (
                    <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{version.description}</p>
                  )}
                </div>
                <code className="shrink-0 rounded bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">
                  {version.checksum.slice(0, 12)}
                </code>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
