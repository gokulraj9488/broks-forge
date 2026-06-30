"use client";

import { useState } from "react";
import { Download, FileDown } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pagination } from "@/components/ui/pagination";
import { useExportReport, useReports } from "@/lib/hooks/use-reports";
import { downloadReport } from "@/lib/api/reports";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import { humanize } from "@/lib/format";

const PAGE_SIZE = 15;

export function ReportsPanel({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useReports(organizationId, projectId, {
    page,
    size: PAGE_SIZE,
  });
  const exportReport = useExportReport(organizationId, projectId);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const reports = data?.content ?? [];

  const reDownload = (report: (typeof reports)[number]) => {
    setDownloadingId(report.id);
    exportReport.mutate(
      { type: report.type, format: report.format, targetId: report.targetId, name: report.name },
      {
        onSuccess: (body) => {
          downloadReport(body, report.name, report.format);
          toast.success("Report downloaded");
          setDownloadingId(null);
        },
        onError: (error) => {
          toast.error(getApiErrorMessage(error));
          setDownloadingId(null);
        },
      },
    );
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base font-semibold">Reports</h2>
        <p className="text-sm text-muted-foreground">
          Exports generated from evaluations, benchmarks and analytics.
        </p>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={FileDown} title="Couldn't load reports" description="Please try again." />
      ) : reports.length === 0 ? (
        <EmptyState
          icon={FileDown}
          title="No reports yet"
          description="Export a report from an evaluation to see it listed here."
        />
      ) : (
        <>
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {reports.map((report) => (
                <div key={report.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                  <div className="min-w-0">
                    <p className="truncate font-medium leading-tight">{report.name}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant="outline">{humanize(report.type)}</Badge>
                      <Badge variant="muted">{report.format}</Badge>
                      <span>{formatDateTime(report.createdAt)}</span>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => reDownload(report)}
                    loading={exportReport.isPending && downloadingId === report.id}
                  >
                    <Download className="h-4 w-4" />
                    Download
                  </Button>
                </div>
              ))}
            </CardContent>
          </Card>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? reports.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
