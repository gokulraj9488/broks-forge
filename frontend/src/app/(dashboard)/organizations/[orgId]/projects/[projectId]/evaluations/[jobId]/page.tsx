"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, FlaskConical, Maximize2, Search } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { MeterBar, StatCard } from "@/components/common/stat-card";
import { JobSummary } from "@/components/evaluations/job-summary";
import { JobActions } from "@/components/evaluations/job-actions";
import { RunsPanel } from "@/components/evaluations/runs-panel";
import { InvestigationWorkspace } from "@/components/investigation/investigation-workspace";
import { ArtifactEvolution } from "@/components/platform/artifact-evolution";
import { ArtifactIntelligence } from "@/components/platform/artifact-intelligence";
import { AskBrok } from "@/components/brok/ask-brok";
import { EvaluationPipeline } from "@/components/platform/evaluation-pipeline";
import { ExecutionGraph } from "@/components/platform/execution-graph";
import { useEvaluationJob } from "@/lib/hooks/use-evaluation-jobs";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { formatDateTime } from "@/lib/utils";
import { formatEta, formatNumber } from "@/lib/format";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

type Tab = "overview" | "runs" | "execution" | "root-cause" | "evolution" | "intelligence";

const TABS = [
  { key: "overview" as const, label: "Overview" },
  { key: "runs" as const, label: "Runs" },
  { key: "execution" as const, label: "Execution graph" },
  { key: "root-cause" as const, label: "Root cause" },
  { key: "evolution" as const, label: "Evolution" },
  { key: "intelligence" as const, label: "Intelligence" },
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
      <CardContent className="grid grid-cols-1 gap-6 p-6 sm:grid-cols-2 lg:grid-cols-3">
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

  // Deep-link support: arriving from Knowledge/Graph with ?tab=intelligence opens that workspace tab.
  useEffect(() => {
    const t = new URLSearchParams(window.location.search).get("tab");
    if (t && TABS.some((x) => x.key === t)) setTab(t as Tab);
  }, []);

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canDelete = role === "OWNER" || role === "ADMIN";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
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
        {/* Two or three actions side by side are wider than a 320px screen, so let them wrap. */}
        <div className="flex flex-wrap items-center gap-2">
          {/*
           * A failure earns the primary affordance: the fastest thing an engineer can do with a red
           * evaluation is open the investigation that is already assembled for it.
           */}
          {(job.status === "FAILED" || job.failedItems > 0) && (
            <Link
              href={`/organizations/${orgId}/projects/${projectId}/evaluations/${jobId}/investigate`}
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90"
            >
              <Search className="h-3.5 w-3.5" />
              Investigate
            </Link>
          )}
          {/*
           * One way into Brok per workspace, visible from every tab, and it already knows the state it is
           * being asked from: a red evaluation opens the question the engineer actually has.
           */}
          <AskBrok
            organizationId={orgId}
            projectId={projectId}
            focus={`evaluation:${jobId}`}
            question={
              job.status === "FAILED" || job.failedItems > 0
                ? "Why is this graph red?"
                : "Explain this evaluation."
            }
          />
          <JobActions
            job={job}
            organizationId={orgId}
            projectId={projectId}
            canManage={canManage}
            canDelete={canDelete}
          />
        </div>
      </div>

      {job.errorMessage && (
        <p className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {job.errorMessage}
        </p>
      )}

      {/* A failed run keeps its pipeline visible, stopped exactly where the chain broke. */}
      {job.status === "FAILED" && (
        <Card>
          <CardContent className="p-5">
            <EvaluationPipeline job={job} />
          </CardContent>
        </Card>
      )}

      {active && (
        <Card>
          <CardContent className="space-y-4 p-5">
            {/* The run as the pipeline it actually is — stages complete as the counters move. */}
            <EvaluationPipeline job={job} />
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
        {/* The graph shows where the chain broke; Brok — one click up, in the header — says why. */}
        {tab === "execution" && (
          <ExecutionGraph organizationId={orgId} projectId={projectId} job={job} />
        )}
        {/*
         * The root-cause tab is now the Root Cause Explorer itself (P13) — the same diagnosis this tab
         * always showed, with the chronology, the deeper causal layers and the evidence chains around it.
         * The full-width route is one click away for the actual investigation.
         */}
        {tab === "root-cause" && (
          <div className="space-y-3">
            <div className="flex justify-end">
              <Link
                href={`/organizations/${orgId}/projects/${projectId}/evaluations/${jobId}/investigate`}
                className="inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
              >
                <Maximize2 className="h-3.5 w-3.5" />
                Open the full investigation
              </Link>
            </div>
            <InvestigationWorkspace
              organizationId={orgId}
              projectId={projectId}
              evaluationId={jobId}
            />
          </div>
        )}
        {tab === "evolution" && <ArtifactEvolution organizationId={orgId} projectId={projectId} type="evaluation" entityId={jobId} />}
        {tab === "intelligence" && (
          <ArtifactIntelligence organizationId={orgId} projectId={projectId} type="evaluation" entityId={jobId} />
        )}
      </div>
    </div>
  );
}
