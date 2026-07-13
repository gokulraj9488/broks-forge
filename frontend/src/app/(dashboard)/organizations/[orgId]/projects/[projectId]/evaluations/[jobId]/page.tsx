"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, FlaskConical } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { MeterBar, StatCard } from "@/components/common/stat-card";
import { JobSummary } from "@/components/evaluations/job-summary";
import { JobActions } from "@/components/evaluations/job-actions";
import { RunsPanel } from "@/components/evaluations/runs-panel";
import { RootCausePanel } from "@/components/rootcause/root-cause-panel";
import { useEvaluationJob } from "@/lib/hooks/use-evaluation-jobs";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { formatDateTime } from "@/lib/utils";
import { formatEta, formatNumber } from "@/lib/format";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

type Tab = "overview" | "runs" | "root-cause";

const TABS = [
  { key: "overview" as const, label: "Overview" },
  { key: "runs" as const, label: "Runs" },
  { key: "root-cause" as const, label: "Root cause" },
];

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm">{children}</div>
    </div>
  );
}

function Config({ job }: { job: EvaluationJobResponse }) {
  const link = (segment: string, id: string | null) =>
    id ? (
      <Link
        href={`/organizations/${job.organizationId}/projects/${job.projectId}/${segment}/${id}`}
        className="font-mono text-xs text-primary hover:underline"
      >
        {id.slice(0, 8)}
      </Link>
    ) : (
      <span className="text-muted-foreground">—</span>
    );

  return (
    <Card>
      <CardContent className="grid gap-6 p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Detail label="Agent">{link("agents", job.agentId)}</Detail>
        <Detail label="Dataset">{link("datasets", job.datasetId)}</Detail>
        <Detail label="Prompt">{link("prompts", job.promptId)}</Detail>
        <Detail label="Provider">{job.provider ?? "Agent default"}</Detail>
        <Detail label="Model">{job.model ?? "Agent default"}</Detail>
        <Detail label="Profile">
          {job.profileId ? (
            <span className="inline-flex items-center gap-1.5">
              <Link
                href={`/organizations/${job.organizationId}/projects/${job.projectId}/evaluations/profiles/${job.profileId}`}
                className="font-mono text-xs text-primary hover:underline"
              >
                {job.profileId.slice(0, 8)}
              </Link>
              {job.profileVersionNumber != null && (
                <span className="text-xs text-muted-foreground">(v{job.profileVersionNumber})</span>
              )}
            </span>
          ) : (
            "None"
          )}
        </Detail>
        <Detail label="Created">{formatDateTime(job.createdAt)}</Detail>
        <Detail label="Completed">{formatDateTime(job.completedAt)}</Detail>
        <Detail label="Items">
          {formatNumber(job.completedItems)} / {formatNumber(job.totalItems)}
        </Detail>
      </CardContent>
    </Card>
  );
}

export default function EvaluationJobDetailPage() {
  const params = useParams<{ orgId: string; projectId: string; jobId: string }>();
  const { orgId, projectId, jobId } = params;
  const { data: organization } = useOrganization(orgId);
  const { data: job, isLoading, isError } = useEvaluationJob(orgId, projectId, jobId);
  const [tab, setTab] = useState<Tab>("overview");

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canDelete = role === "OWNER" || role === "ADMIN";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <div className="grid gap-4 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !job) {
    return (
      <EmptyState
        icon={FlaskConical}
        title="Evaluation not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  const active = job.status === "RUNNING" || job.status === "PENDING";
  const processed = job.completedItems + job.failedItems;
  const progress = job.totalItems > 0 ? processed / job.totalItems : 0;
  const remaining = Math.max(0, job.totalItems - processed);
  // Derived client-side from real, already-polled fields — never a separate/fabricated value.
  const etaMs = (() => {
    if (!job.startedAt || processed <= 0 || remaining <= 0) return null;
    const elapsedMs = Date.now() - new Date(job.startedAt).getTime();
    if (elapsedMs <= 0) return null;
    return (remaining * elapsedMs) / processed;
  })();

  return (
    <div className="space-y-6">
      <Link
        href="/evaluations"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to evaluations
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <FlaskConical className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{job.name}</h1>
            <div className="mt-1">
              <JobStatusBadge status={job.status} />
            </div>
          </div>
        </div>
        <JobActions
          job={job}
          organizationId={orgId}
          projectId={projectId}
          canManage={canManage}
          canDelete={canDelete}
        />
      </div>

      {job.errorMessage && (
        <p className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {job.errorMessage}
        </p>
      )}

      {active && (
        <Card>
          <CardContent className="space-y-4 p-5">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                Progress · {formatNumber(processed)}/{formatNumber(job.totalItems)} items
              </span>
              <span className="font-medium">{Math.round(progress * 100)}%</span>
            </div>
            <MeterBar value={progress} />
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <StatCard label="Completed" value={formatNumber(job.completedItems)} />
              <StatCard label="Failed" value={formatNumber(job.failedItems)} />
              <StatCard label="Remaining" value={formatNumber(remaining)} />
              <StatCard label="ETA" value={etaMs != null ? formatEta(etaMs) : "—"} />
            </div>
          </CardContent>
        </Card>
      )}

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "overview" && (
          <div className="space-y-6">
            <JobSummary job={job} />
            <Config job={job} />
          </div>
        )}
        {tab === "runs" && (
          <RunsPanel
            organizationId={orgId}
            projectId={projectId}
            jobId={jobId}
            jobActive={active}
          />
        )}
        {tab === "root-cause" && (
          <RootCausePanel organizationId={orgId} projectId={projectId} jobId={jobId} />
        )}
      </div>
    </div>
  );
}
