"use client";

import { useEffect, useState } from "react";
import { Rows3 } from "lucide-react";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
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
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="w-12">#</TableHead>
                <TableHead>Input</TableHead>
                <TableHead>Expected output</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id} className="align-top">
                  <TableCell className="font-mono text-xs text-muted-foreground">{item.sequence}</TableCell>
                  <TableCell className="max-w-[28rem]">
                    <p className="line-clamp-3 whitespace-pre-wrap break-words">{item.input}</p>
                  </TableCell>
                  <TableCell className="max-w-[28rem]">
                    {item.expectedOutput ? (
                      <p className="line-clamp-3 whitespace-pre-wrap break-words text-muted-foreground">
                        {item.expectedOutput}
                      </p>
                    ) : (
                      <span className="text-xs text-muted-foreground">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
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
