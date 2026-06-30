"use client";

import { useState } from "react";
import { AlertTriangle, CheckCircle2, ChevronDown, ChevronUp, GitCompare, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { CreateRegressionDialog } from "@/components/benchmarks/create-regression-dialog";
import {
  useDeleteRegressionCheck,
  useRegressionCheck,
  useRegressionChecks,
} from "@/lib/hooks/use-regression";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils";
import { formatDelta } from "@/lib/format";

export function RegressionPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const { data, isLoading, isError } = useRegressionChecks(organizationId, projectId, { size: 50 });
  const remove = useDeleteRegressionCheck(organizationId, projectId);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<{ id: string; name: string } | null>(null);
  const checks = data?.content ?? [];

  const handleDelete = () => {
    if (!toDelete) return;
    remove.mutate(toDelete.id, {
      onSuccess: () => {
        toast.success("Check deleted");
        setToDelete(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setToDelete(null);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold">Regression checks</h2>
          <p className="text-sm text-muted-foreground">Catch metric regressions between two evaluations.</p>
        </div>
        {canManage && <CreateRegressionDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      {isError ? (
        <EmptyState icon={GitCompare} title="Couldn't load checks" description="Please try again." />
      ) : checks.length === 0 ? (
        <EmptyState
          icon={GitCompare}
          title="No regression checks yet"
          description="Create a check to compare a candidate evaluation against a baseline."
          action={
            canManage ? <CreateRegressionDialog organizationId={organizationId} projectId={projectId} /> : undefined
          }
        />
      ) : (
        <div className="space-y-3">
          {checks.map((check) => {
            const open = expanded === check.id;
            return (
              <Card key={check.id} className={check.regressed ? "border-destructive/40" : undefined}>
                <CardContent className="p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      {check.regressed ? (
                        <AlertTriangle className="h-5 w-5 text-destructive" />
                      ) : (
                        <CheckCircle2 className="h-5 w-5 text-success" />
                      )}
                      <div>
                        <p className="font-medium leading-tight">{check.name}</p>
                        <p className="text-xs text-muted-foreground">{formatDateTime(check.createdAt)}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={check.regressed ? "destructive" : "success"}>
                        {check.regressed ? "Regressed" : "Passed"}
                      </Badge>
                      <Button variant="ghost" size="sm" onClick={() => setExpanded(open ? null : check.id)}>
                        {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                        Details
                      </Button>
                      {canManage && (
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-muted-foreground hover:text-destructive"
                          onClick={() => setToDelete({ id: check.id, name: check.name })}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                  {open && (
                    <RegressionFindings
                      organizationId={organizationId}
                      projectId={projectId}
                      checkId={check.id}
                    />
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(o) => !o && setToDelete(null)}
        title="Delete regression check?"
        description={toDelete ? `"${toDelete.name}" will be permanently removed.` : undefined}
        confirmLabel="Delete check"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}

function RegressionFindings({
  organizationId,
  projectId,
  checkId,
}: {
  organizationId: string;
  projectId: string;
  checkId: string;
}) {
  const { data, isLoading, isError } = useRegressionCheck(organizationId, projectId, checkId);

  if (isLoading) return <Skeleton className="mt-3 h-24 w-full" />;
  if (isError || !data) return <p className="mt-3 text-xs text-destructive">Couldn&apos;t load findings.</p>;

  const findings = Object.entries(data.findings);

  return (
    <div className="mt-4 space-y-2">
      <p className="text-xs text-muted-foreground">Tolerance ± {data.tolerancePct}%</p>
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
              <th className="px-3 py-2 font-medium">Metric</th>
              <th className="px-3 py-2 text-right font-medium">Baseline</th>
              <th className="px-3 py-2 text-right font-medium">Candidate</th>
              <th className="px-3 py-2 text-right font-medium">Δ</th>
              <th className="px-3 py-2 text-right font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {findings.map(([key, f]) => (
              <tr key={key}>
                <td className="px-3 py-2">{f.label}</td>
                <td className="px-3 py-2 text-right font-mono text-xs">{f.baseline.toFixed(3)}</td>
                <td className="px-3 py-2 text-right font-mono text-xs">{f.candidate.toFixed(3)}</td>
                <td
                  className={`px-3 py-2 text-right font-mono text-xs ${
                    f.regressed ? "text-destructive" : "text-success"
                  }`}
                >
                  {formatDelta(f.deltaPct)}
                </td>
                <td className="px-3 py-2 text-right">
                  <Badge variant={f.regressed ? "destructive" : "success"}>
                    {f.regressed ? "Regressed" : "OK"}
                  </Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
