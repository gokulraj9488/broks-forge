"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Ban, Download, Play, RotateCcw, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import {
  useDeleteEvaluationJob,
  useEvaluationJobAction,
} from "@/lib/hooks/use-evaluation-jobs";
import { useExportReport } from "@/lib/hooks/use-reports";
import { downloadReport } from "@/lib/api/reports";
import { getApiErrorMessage } from "@/lib/api/client";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

export function JobActions({
  job,
  organizationId,
  projectId,
  canManage,
  canDelete,
}: {
  job: EvaluationJobResponse;
  organizationId: string;
  projectId: string;
  canManage: boolean;
  canDelete: boolean;
}) {
  const router = useRouter();
  const action = useEvaluationJobAction(organizationId, projectId, job.id);
  const remove = useDeleteEvaluationJob(organizationId, projectId);
  const exportReport = useExportReport(organizationId, projectId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const active = job.status === "RUNNING" || job.status === "PENDING";
  const canRun = job.status === "PENDING";
  // A failed/cancelled job, or a completed job with some failed items, resumes only the
  // outstanding rows (see EvaluationService#resume) — /run only accepts a PENDING job.
  const canResume =
    job.status === "FAILED" ||
    job.status === "CANCELLED" ||
    (job.status === "COMPLETED" && job.failedItems > 0);

  const run = () =>
    action.mutate("run", {
      onSuccess: () => toast.success("Evaluation started"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });

  const resume = () =>
    action.mutate("resume", {
      onSuccess: () => toast.success("Evaluation resumed"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });

  const cancel = () =>
    action.mutate("cancel", {
      onSuccess: () => toast.success("Evaluation cancelled"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });

  const handleExport = () =>
    exportReport.mutate(
      { type: "EVALUATION_JOB", format: "CSV", targetId: job.id, name: job.name },
      {
        onSuccess: (body) => {
          downloadReport(body, job.name, "CSV");
          toast.success("Report exported");
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  const handleDelete = () =>
    remove.mutate(job.id, {
      onSuccess: () => {
        toast.success("Evaluation deleted");
        router.replace("/evaluations");
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setConfirmDelete(false);
      },
    });

  return (
    <div className="flex flex-wrap items-center gap-2">
      {canManage && canRun && (
        <Button size="sm" onClick={run} loading={action.isPending}>
          <Play className="h-4 w-4" />
          Run
        </Button>
      )}
      {canManage && canResume && (
        <Button size="sm" onClick={resume} loading={action.isPending}>
          <RotateCcw className="h-4 w-4" />
          Resume
        </Button>
      )}
      {canManage && active && (
        <Button size="sm" variant="outline" onClick={cancel} loading={action.isPending}>
          <Ban className="h-4 w-4" />
          Cancel
        </Button>
      )}
      <Button size="sm" variant="outline" onClick={handleExport} loading={exportReport.isPending}>
        <Download className="h-4 w-4" />
        Export CSV
      </Button>
      {canDelete && (
        <Button
          size="sm"
          variant="ghost"
          className="text-muted-foreground hover:text-destructive"
          onClick={() => setConfirmDelete(true)}
        >
          <Trash2 className="h-4 w-4" />
          Delete
        </Button>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete evaluation?"
        description={`"${job.name}" and all of its runs will be permanently removed.`}
        confirmLabel="Delete evaluation"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
