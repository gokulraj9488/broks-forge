"use client";

import { Fragment, useState } from "react";
import { Bug, ChevronDown, ChevronRight, ListChecks } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pagination } from "@/components/ui/pagination";
import { PassBadge, RunStatusBadge } from "@/components/common/eval-badges";
import { RunResults } from "@/components/evaluations/run-results";
import { ExecutionTimeline } from "@/components/debugger/execution-timeline";
import { useEvaluationRuns } from "@/lib/hooks/use-evaluation-jobs";
import { formatCost, formatLatency, formatNumber, formatScore } from "@/lib/format";

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

  const { data, isLoading, isError } = useEvaluationRuns(
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
    return <EmptyState icon={ListChecks} title="Couldn't load runs" description="Please try again." />;
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
      <Card>
        <CardContent className="overflow-x-auto p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="w-10 px-3 py-2.5" />
                <th className="w-12 px-2 py-2.5 font-medium">#</th>
                <th className="px-3 py-2.5 font-medium">Status</th>
                <th className="px-3 py-2.5 font-medium">Result</th>
                <th className="px-3 py-2.5 text-right font-medium">Score</th>
                <th className="px-3 py-2.5 text-right font-medium">Latency</th>
                <th className="px-3 py-2.5 text-right font-medium">Tokens</th>
                <th className="px-3 py-2.5 text-right font-medium">Cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {runs.map((run) => {
                const open = expanded === run.id;
                return (
                  <Fragment key={run.id}>
                    <tr
                      className="cursor-pointer transition-colors hover:bg-muted/40"
                      onClick={() => setExpanded(open ? null : run.id)}
                    >
                      <td className="px-3 py-2.5 text-muted-foreground">
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
                      </td>
                      <td className="px-2 py-2.5 font-mono text-xs text-muted-foreground">{run.sequence}</td>
                      <td className="px-3 py-2.5">
                        <RunStatusBadge status={run.status} />
                      </td>
                      <td className="px-3 py-2.5">
                        <PassBadge passed={run.passed} />
                      </td>
                      <td className="px-3 py-2.5 text-right font-mono text-xs">{formatScore(run.score)}</td>
                      <td className="px-3 py-2.5 text-right font-mono text-xs">{formatLatency(run.latencyMs)}</td>
                      <td className="px-3 py-2.5 text-right font-mono text-xs">
                        {run.totalTokens != null ? formatNumber(run.totalTokens) : "—"}
                      </td>
                      <td className="px-3 py-2.5 text-right font-mono text-xs">{formatCost(run.cost)}</td>
                    </tr>
                    {open && (
                      <tr className="bg-muted/20">
                        <td colSpan={8} className="px-4 py-4">
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
                                </p>
                                <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-3 text-xs">
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
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </CardContent>
      </Card>
      <Pagination
        page={data?.page ?? 0}
        totalPages={data?.totalPages ?? 1}
        totalElements={data?.totalElements ?? runs.length}
        onPageChange={setPage}
      />
    </div>
  );
}
