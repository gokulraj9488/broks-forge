"use client";

import { BarChart3 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { StatCard, MeterBar } from "@/components/common/stat-card";
import { useDatasetStats } from "@/lib/hooks/use-datasets";
import { formatNumber, formatPercent } from "@/lib/format";

export function DatasetStatsPanel({
  organizationId,
  projectId,
  datasetId,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
}) {
  const { data, isLoading, isError } = useDatasetStats(organizationId, projectId, datasetId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState
        icon={BarChart3}
        title="No statistics yet"
        description="Upload a dataset version to compute statistics."
      />
    );
  }

  const coverage = data.itemCount > 0 ? data.itemsWithExpectedOutput / data.itemCount : 0;

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Items" value={formatNumber(data.itemCount)} hint={`Version v${data.versionNumber}`} />
        <StatCard
          label="With expected output"
          value={formatNumber(data.itemsWithExpectedOutput)}
          hint={formatPercent(coverage)}
        />
        <StatCard label="Columns" value={formatNumber(data.columns.length)} />
      </div>

      <Card>
        <CardContent className="space-y-4 p-5">
          <div>
            <div className="mb-1.5 flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Expected-output coverage</span>
              <span className="font-medium">{formatPercent(data.expectedOutputCoverage)}</span>
            </div>
            <MeterBar value={data.expectedOutputCoverage} tone="success" />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Avg input length</p>
              <p className="text-lg font-semibold">{formatNumber(Math.round(data.avgInputLength))} chars</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Avg expected output length</p>
              <p className="text-lg font-semibold">
                {formatNumber(Math.round(data.avgExpectedOutputLength))} chars
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-2 p-5">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Columns</p>
          <div className="flex flex-wrap gap-2">
            {data.columns.map((col) => (
              <Badge key={col} variant="outline" className="font-mono">
                {col}
              </Badge>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
