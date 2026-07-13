"use client";

import { Fragment, useState } from "react";
import { Bug, ChevronDown, ChevronRight, ListChecks } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pagination } from "@/components/ui/pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PassBadge, RunStatusBadge, EXECUTION_STATUS_LABEL } from "@/components/common/eval-badges";
import { RunResults } from "@/components/evaluations/run-results";
import { ExecutionTimeline } from "@/components/debugger/execution-timeline";
import { useEvaluationRuns, useEvaluationRunResults } from "@/lib/hooks/use-evaluation-jobs";
import { METRIC_TYPE_LABELS, type MetricType } from "@/lib/api/evaluation-profiles";
import { formatCost, formatLatency, formatNumber, formatScore } from "@/lib/format";
import type { EvaluationRunResponse } from "@/lib/api/evaluation-jobs";

const PAGE_SIZE = 20;

export function RunsPanel({
  organizationId,
  projectId,
  jobId,
  jobActive,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  jobActive: boolean;
}) {
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [debugRun, setDebugRun] = useState<string | null>(null);

  const { data, isLoading, isError, refetch, isRefetching } = useEvaluationRuns(
    organizationId,
    projectId,
    jobId,
    { page, size: PAGE_SIZE },
    jobActive,
  );
  const runs = data?.content ?? [];

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <EmptyState
        icon={ListChecks}
        title="Couldn't load runs"
        description="Something went wrong reaching the server. Check your connection and try again."
        action={
          <Button variant="outline" onClick={() => refetch()} loading={isRefetching}>
            Retry
          </Button>
        }
      />
    );
  }

  if (runs.length === 0) {
    return (
      <EmptyState
        icon={ListChecks}
        title="No runs yet"
        description={jobActive ? "Runs will appear here as the evaluation progresses." : "This evaluation has no runs."}
      />
    );
  }

  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className="w-10" />
            <TableHead className="w-12">#</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Retries</TableHead>
            <TableHead>Result</TableHead>
            <TableHead className="text-right">Score</TableHead>
            <TableHead className="text-right">Latency</TableHead>
            <TableHead className="text-right">Tokens</TableHead>
            <TableHead className="text-right">Cost</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => {
            const open = expanded === run.id;
            return (
              <Fragment key={run.id}>
                <TableRow className="cursor-pointer" onClick={() => setExpanded(open ? null : run.id)}>
                  <TableCell className="text-muted-foreground">
                    <button
                      type="button"
                      aria-expanded={open}
                      aria-label={open ? "Collapse run details" : "Expand run details"}
                      onClick={(event) => {
                        event.stopPropagation();
                        setExpanded(open ? null : run.id);
                      }}
                      className="flex h-6 w-6 items-center justify-center rounded-sm transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                    </button>
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{run.sequence}</TableCell>
                  <TableCell>
                    <RunStatusBadge status={run.status} />
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs text-muted-foreground">
                    {run.attempt > 1 ? `${run.attempt - 1}` : "—"}
                  </TableCell>
                  <TableCell>
                    <RunMetricSummary
                      organizationId={organizationId}
                      projectId={projectId}
                      jobId={jobId}
                      run={run}
                    />
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs">{formatScore(run.score)}</TableCell>
                  <TableCell className="text-right font-mono text-xs">{formatLatency(run.latencyMs)}</TableCell>
                  <TableCell className="text-right font-mono text-xs">
                    {run.totalTokens != null ? formatNumber(run.totalTokens) : "—"}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs">{formatCost(run.cost)}</TableCell>
                </TableRow>
                {open && (
                  <TableRow className="bg-muted/20 hover:bg-muted/20">
                    <TableCell colSpan={9} className="px-4 py-4">
                      <div className="space-y-4">
                        {run.error && (
                          <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                            {run.error}
                          </p>
                        )}
                        <div className="grid gap-4 lg:grid-cols-2">
                          <div>
                            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              Input
                              <span className="ml-2 font-normal normal-case text-muted-foreground/70">
                                Full rendered prompt — template + dataset input
                              </span>
                            </p>
                            <pre className="max-h-[28rem] overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-3 text-xs">
                              {run.input}
                            </pre>
                          </div>
                          <div>
                            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              Output
                              {run.httpStatus != null && (
                                <span className="ml-2 font-normal normal-case">HTTP {run.httpStatus}</span>
                              )}
                            </p>
                            <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-3 text-xs">
                              {run.output ?? "—"}
                            </pre>
                          </div>
                        </div>
                        <div>
                          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            Metric results
                          </p>
                          <RunResults
                            organizationId={organizationId}
                            projectId={projectId}
                            jobId={jobId}
                            runId={run.id}
                          />
                        </div>
                        <div className="space-y-3 border-t border-border pt-4">
                          <div className="flex items-center justify-between">
                            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              Execution timeline
                            </p>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setDebugRun(debugRun === run.id ? null : run.id)}
                            >
                              <Bug className="h-4 w-4" />
                              {debugRun === run.id ? "Hide debugger" : "Debug"}
                            </Button>
                          </div>
                          {debugRun === run.id && (
                            <ExecutionTimeline
                              organizationId={organizationId}
                              projectId={projectId}
                              jobId={jobId}
                              runId={run.id}
                            />
                          )}
                        </div>
                      </div>
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            );
          })}
        </TableBody>
      </Table>
      <Pagination
        page={data?.page ?? 0}
        totalPages={data?.totalPages ?? 1}
        totalElements={data?.totalElements ?? runs.length}
        onPageChange={setPage}
      />
    </div>
  );
}

/**
 * Collapsed-row result summary: a mini badge per configured metric so the timeline immediately
 * reveals *why* a run failed — "Authentication Error · LLM Judge" instead of a bare "Fail" that
 * reads as a quality problem when the metric never even ran.
 */
function RunMetricSummary({
  organizationId,
  projectId,
  jobId,
  run,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  run: EvaluationRunResponse;
}) {
  const { data, isLoading } = useEvaluationRunResults(organizationId, projectId, jobId, run.id);

  if (run.status !== "SUCCEEDED" || isLoading || !data || data.length === 0) {
    return <PassBadge passed={run.passed} />;
  }

  return (
    <div className="flex flex-wrap gap-1">
      {data.map((r, i) => {
        const label = r.metricLabel || METRIC_TYPE_LABELS[r.metricType as MetricType] || r.metricType;
        if (r.executionStatus !== "COMPLETED") {
          return (
            <Badge key={i} variant="warning" className="gap-1 text-[11px]">
              <span aria-hidden>⚠</span>
              {EXECUTION_STATUS_LABEL[r.executionStatus] ?? r.executionStatus}
              <span className="font-normal opacity-70">· {label}</span>
            </Badge>
          );
        }
        return (
          <Badge key={i} variant={r.passed ? "success" : "destructive"} className="gap-1 text-[11px]">
            <span aria-hidden>{r.passed ? "✔" : "✖"}</span>
            {r.passed ? "Passed" : "Failed"}
            <span className="font-normal opacity-70">· {label}</span>
          </Badge>
        );
      })}
    </div>
  );
}
