"use client";

import { useState } from "react";
import Link from "next/link";
import { Database, Search } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/common/badges";
import { CreateDatasetDialog } from "@/components/datasets/create-dataset-dialog";
import { useDatasets } from "@/lib/hooks/use-datasets";
import { formatDate } from "@/lib/utils";
import { formatNumber } from "@/lib/format";
import type { DatasetStatus, DatasetVisibility } from "@/lib/api/datasets";

const PAGE_SIZE = 12;

export function DatasetsPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<DatasetStatus | "">("");
  const [visibility, setVisibility] = useState<DatasetVisibility | "">("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useDatasets(organizationId, projectId, {
    q: q.trim() || undefined,
    status: status || undefined,
    visibility: visibility || undefined,
    page,
    size: PAGE_SIZE,
  });

  const reset = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const datasets = data?.content ?? [];
  const filtered = !!q || !!status || !!visibility;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => reset(setQ)(e.target.value)}
            placeholder="Search datasets…"
            className="pl-9"
          />
        </div>
        {canManage && <CreateDatasetDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      <div className="flex flex-wrap gap-2">
        <Select
          value={visibility}
          onChange={(e) => reset(setVisibility)(e.target.value as DatasetVisibility | "")}
          className="h-8 w-auto text-xs"
        >
          <option value="">All visibility</option>
          <option value="PRIVATE">Private</option>
          <option value="ORGANIZATION">Organization</option>
          <option value="PUBLIC">Public</option>
        </Select>
        <Select
          value={status}
          onChange={(e) => reset(setStatus)(e.target.value as DatasetStatus | "")}
          className="h-8 w-auto text-xs"
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="ARCHIVED">Archived</option>
        </Select>
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Database} title="Couldn't load datasets" description="Please try again." />
      ) : datasets.length === 0 ? (
        <EmptyState
          icon={Database}
          title="No datasets found"
          description={filtered ? "No datasets match your filters." : "Create your first dataset to evaluate against."}
          action={
            canManage && !filtered ? (
              <CreateDatasetDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {datasets.map((dataset) => (
              <Link
                key={dataset.id}
                href={`/organizations/${organizationId}/projects/${projectId}/datasets/${dataset.id}`}
                className="group"
              >
                <Card className="h-full transition-colors group-hover:border-primary/40">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                          <Database className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate font-medium leading-tight">{dataset.name}</h3>
                          <p className="truncate text-xs text-muted-foreground">/{dataset.slug}</p>
                        </div>
                      </div>
                      {dataset.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
                    </div>
                    <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant="outline">
                        v{dataset.latestVersionNumber ?? 0}
                      </Badge>
                      <Badge variant="muted">{formatNumber(dataset.currentItemCount ?? 0)} items</Badge>
                      <span className="text-muted-foreground">
                        Updated {formatDate(dataset.updatedAt)}
                      </span>
                      {dataset.tags.slice(0, 3).map((tag) => (
                        <span key={tag} className="rounded bg-muted px-1.5 py-0.5">
                          #{tag}
                        </span>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? datasets.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
