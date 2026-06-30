"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Trash2, Trophy } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Leaderboard } from "@/components/benchmarks/leaderboard";
import { useBenchmark, useDeleteBenchmark, useRemoveBenchmarkEntry } from "@/lib/hooks/use-benchmarks";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { getApiErrorMessage } from "@/lib/api/client";
import { BENCHMARK_TYPE_LABELS } from "@/lib/api/benchmarks";

export default function BenchmarkDetailPage() {
  const params = useParams<{ orgId: string; projectId: string; benchmarkId: string }>();
  const { orgId, projectId, benchmarkId } = params;
  const router = useRouter();
  const { data: organization } = useOrganization(orgId);
  const { data: benchmark, isLoading, isError } = useBenchmark(orgId, projectId, benchmarkId);
  const removeBenchmark = useDeleteBenchmark(orgId, projectId);
  const removeEntry = useRemoveBenchmarkEntry(orgId, projectId, benchmarkId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !benchmark) {
    return (
      <EmptyState
        icon={Trophy}
        title="Benchmark not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  const handleDelete = () =>
    removeBenchmark.mutate(benchmark.id, {
      onSuccess: () => {
        toast.success("Benchmark deleted");
        router.replace("/benchmarks");
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setConfirmDelete(false);
      },
    });

  const handleRemoveEntry = (entryId: string) =>
    removeEntry.mutate(entryId, {
      onSuccess: () => toast.success("Entry removed"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });

  return (
    <div className="space-y-6">
      <Link
        href="/benchmarks"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to benchmarks
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <Trophy className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{benchmark.name}</h1>
            <p className="text-sm text-muted-foreground">
              {BENCHMARK_TYPE_LABELS[benchmark.type] ?? benchmark.type}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {benchmark.metricKey && <Badge variant="muted">{benchmark.metricKey}</Badge>}
          {canManage && (
            <Button
              variant="ghost"
              size="sm"
              className="text-muted-foreground hover:text-destructive"
              onClick={() => setConfirmDelete(true)}
            >
              <Trash2 className="h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      {benchmark.description && (
        <p className="max-w-2xl text-sm text-muted-foreground">{benchmark.description}</p>
      )}

      <Leaderboard organizationId={orgId} projectId={projectId} benchmarkId={benchmarkId} />

      <div className="space-y-2">
        <h2 className="text-base font-semibold">Entries</h2>
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {benchmark.entries.map((entry) => (
              <div key={entry.id} className="flex items-center justify-between gap-3 p-3 text-sm">
                <div className="flex items-center gap-2">
                  <Link
                    href={`/organizations/${orgId}/projects/${projectId}/evaluations/${entry.evaluationJobId}`}
                    className="text-primary hover:underline"
                  >
                    {entry.label ?? entry.evaluationJobId.slice(0, 8)}
                  </Link>
                </div>
                {canManage && benchmark.entries.length > 2 && (
                  <Button
                    size="icon"
                    variant="ghost"
                    className="text-muted-foreground hover:text-destructive"
                    onClick={() => handleRemoveEntry(entry.id)}
                    disabled={removeEntry.isPending}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete benchmark?"
        description={`"${benchmark.name}" will be permanently removed.`}
        confirmLabel="Delete benchmark"
        destructive
        loading={removeBenchmark.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
