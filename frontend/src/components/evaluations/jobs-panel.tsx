"use client";

import { useState } from "react";
import Link from "next/link";
import { FlaskConical, Search } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { MeterBar } from "@/components/common/stat-card";
import { CreateJobDialog } from "@/components/evaluations/create-job-dialog";
import { useEvaluationJobs } from "@/lib/hooks/use-evaluation-jobs";
import { formatDateTime } from "@/lib/utils";
import { formatNumber } from "@/lib/format";
import { JOB_STATUS_OPTIONS, type EvaluationJobStatus } from "@/lib/api/evaluation-jobs";

const PAGE_SIZE = 12;

export function JobsPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<EvaluationJobStatus | "">("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useEvaluationJobs(organizationId, projectId, {
    q: q.trim() || undefined,
    status: status || undefined,
    page,
    size: PAGE_SIZE,
  });

  const reset = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const jobs = data?.content ?? [];
  const filtered = !!q || !!status;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => reset(setQ)(e.target.value)}
            placeholder="Search evaluations…"
            className="pl-9"
          />
        </div>
        {canManage && <CreateJobDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      <div className="flex flex-wrap gap-2">
        <Select
          value={status}
          onChange={(e) => reset(setStatus)(e.target.value as EvaluationJobStatus | "")}
          className="h-8 w-auto text-xs"
        >
          <option value="">All statuses</option>
          {JOB_STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={FlaskConical} title="Couldn't load evaluations" description="Please try again." />
      ) : jobs.length === 0 ? (
        <EmptyState
          icon={FlaskConical}
          title="No evaluations found"
          description={filtered ? "No evaluations match your filters." : "Run your first evaluation to score an agent against a dataset."}
          action={
            canManage && !filtered ? (
              <CreateJobDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="space-y-3">
            {jobs.map((job) => {
              const progress = job.totalItems > 0 ? job.completedItems / job.totalItems : 0;
              const active = job.status === "RUNNING" || job.status === "PENDING";
              return (
                <Link
                  key={job.id}
                  href={`/organizations/${organizationId}/projects/${projectId}/evaluations/${job.id}`}
                  className="group block"
                >
                  <Card className="transition-colors group-hover:border-primary/40">
                    <CardContent className="p-5">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="truncate font-medium leading-tight">{job.name}</h3>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {formatNumber(job.completedItems)}/{formatNumber(job.totalItems)} items
                            {job.failedItems > 0 && (
                              <span className="text-destructive"> · {formatNumber(job.failedItems)} failed</span>
                            )}
                            {" · "}
                            {job.completedAt ? `done ${formatDateTime(job.completedAt)}` : `created ${formatDateTime(job.createdAt)}`}
                          </p>
                        </div>
                        <JobStatusBadge status={job.status} />
                      </div>
                      {active && (
                        <div className="mt-3">
                          <MeterBar value={progress} />
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </Link>
              );
            })}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? jobs.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
