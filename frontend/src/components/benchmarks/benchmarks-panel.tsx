"use client";

import Link from "next/link";
import { Trophy } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { CreateBenchmarkDialog } from "@/components/benchmarks/create-benchmark-dialog";
import { useBenchmarks } from "@/lib/hooks/use-benchmarks";
import { formatDate } from "@/lib/utils";
import { BENCHMARK_TYPE_LABELS } from "@/lib/api/benchmarks";
import { useState } from "react";

const PAGE_SIZE = 12;

export function BenchmarksPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useBenchmarks(organizationId, projectId, {
    page,
    size: PAGE_SIZE,
  });
  const benchmarks = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Benchmarks</h2>
          <p className="text-sm text-muted-foreground">Head-to-head leaderboards across evaluations.</p>
        </div>
        {canManage && <CreateBenchmarkDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Trophy} title="Couldn't load benchmarks" description="Please try again." />
      ) : benchmarks.length === 0 ? (
        <EmptyState
          icon={Trophy}
          title="No benchmarks yet"
          description="Create a benchmark to rank evaluations against each other."
          action={
            canManage ? <CreateBenchmarkDialog organizationId={organizationId} projectId={projectId} /> : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {benchmarks.map((benchmark) => (
              <Link
                key={benchmark.id}
                href={`/organizations/${organizationId}/projects/${projectId}/benchmarks/${benchmark.id}`}
                className="group"
              >
                <Card className="h-full transition-colors group-hover:border-primary/40">
                  <CardContent className="p-5">
                    <div className="flex items-start gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                        <Trophy className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-medium leading-tight">{benchmark.name}</h3>
                        <p className="truncate text-xs text-muted-foreground">
                          {BENCHMARK_TYPE_LABELS[benchmark.type] ?? benchmark.type}
                        </p>
                      </div>
                    </div>
                    <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant="outline">{benchmark.entryCount} entries</Badge>
                      {benchmark.metricKey && <Badge variant="muted">{benchmark.metricKey}</Badge>}
                      <span>Created {formatDate(benchmark.createdAt)}</span>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? benchmarks.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
