"use client";

import { useEffect, useState } from "react";
import { Rows3 } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pagination } from "@/components/ui/pagination";
import { useDatasetItems, useDatasetVersions } from "@/lib/hooks/use-datasets";

const PAGE_SIZE = 20;

export function DatasetItemsPanel({
  organizationId,
  projectId,
  datasetId,
  currentVersionId,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
  currentVersionId: string | null;
}) {
  const { data: versionsData, isLoading: versionsLoading } = useDatasetVersions(
    organizationId,
    projectId,
    datasetId,
    { size: 50 },
  );
  const versions = versionsData?.content ?? [];

  const [versionId, setVersionId] = useState<string>("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (versionId || versions.length === 0) return;
    const preferred = currentVersionId && versions.some((v) => v.id === currentVersionId)
      ? currentVersionId
      : versions[0].id;
    setVersionId(preferred);
  }, [versions, versionId, currentVersionId]);

  const { data, isLoading, isError } = useDatasetItems(
    organizationId,
    projectId,
    datasetId,
    versionId || undefined,
    { page, size: PAGE_SIZE },
  );
  const items = data?.content ?? [];

  if (versionsLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (versions.length === 0) {
    return (
      <EmptyState
        icon={Rows3}
        title="No items to show"
        description="Upload a dataset version to browse its items."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">Version</span>
        <Select
          value={versionId}
          onValueChange={(v) => {
            setVersionId(v);
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-auto min-w-[8rem] text-xs" aria-label="Dataset version">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {versions.map((v) => (
              <SelectItem key={v.id} value={v.id}>
                v{v.versionNumber} · {v.itemCount} items
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <Skeleton className="h-64 w-full" />
      ) : isError ? (
        <EmptyState icon={Rows3} title="Couldn't load items" description="Please try again." />
      ) : items.length === 0 ? (
        <EmptyState icon={Rows3} title="No items in this version" />
      ) : (
        <>
          <Card>
            <CardContent className="overflow-x-auto p-0">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="w-12 px-4 py-2.5 font-medium">#</th>
                    <th className="px-4 py-2.5 font-medium">Input</th>
                    <th className="px-4 py-2.5 font-medium">Expected output</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((item) => (
                    <tr key={item.id} className="align-top">
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{item.sequence}</td>
                      <td className="max-w-[28rem] px-4 py-3">
                        <p className="line-clamp-3 whitespace-pre-wrap break-words">{item.input}</p>
                      </td>
                      <td className="max-w-[28rem] px-4 py-3">
                        {item.expectedOutput ? (
                          <p className="line-clamp-3 whitespace-pre-wrap break-words text-muted-foreground">
                            {item.expectedOutput}
                          </p>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? items.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
