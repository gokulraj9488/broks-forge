"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDatasetUploads } from "@/lib/hooks/use-datasets";
import { formatDateTime } from "@/lib/utils";
import type { DatasetUploadResponse } from "@/lib/api/datasets";

const STATUS_BADGE: Record<DatasetUploadResponse["status"], "success" | "destructive" | "outline" | "muted"> = {
  PENDING: "muted",
  PARSING: "outline",
  COMPLETED: "success",
  DUPLICATE: "outline",
  FAILED: "destructive",
};

/**
 * Recent file-upload attempts for a dataset — including FAILED and DUPLICATE ones, which never
 * produce a version and so would otherwise be invisible once the upload dialog is closed. Renders
 * nothing when there is no upload history yet (e.g. a dataset built entirely via paste mode).
 */
export function DatasetUploadHistory({
  organizationId,
  projectId,
  datasetId,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
}) {
  const { data } = useDatasetUploads(organizationId, projectId, datasetId, { size: 5 });
  const uploads = data?.content ?? [];

  if (uploads.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">Recent uploads</CardTitle>
      </CardHeader>
      <CardContent className="divide-y divide-border p-0">
        {uploads.map((upload) => (
          <div key={upload.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="truncate text-sm font-medium">{upload.filename}</span>
                <Badge variant={STATUS_BADGE[upload.status]}>{upload.status}</Badge>
              </div>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {upload.status === "FAILED"
                  ? upload.errorMessage
                  : `${upload.rowCount ?? 0} rows · ${upload.columnCount ?? 0} columns`}
                {" · "}
                {formatDateTime(upload.createdAt)}
              </p>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
