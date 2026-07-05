"use client";

import { Bug } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Badge } from "@/components/ui/badge";
import { RunStatusBadge } from "@/components/common/eval-badges";
import { useExecutionTimeline } from "@/lib/hooks/use-debugger";
import {
  formatCost,
  formatLatency,
  formatNumber,
  humanize,
} from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  TimelineStageResponse,
  TimelineStageStatus,
} from "@/lib/api/debugger";

type Tone = {
  dot: string;
  label: string;
  dashed: boolean;
};

const STAGE_TONE: Record<TimelineStageStatus, Tone> = {
  OK: { dot: "bg-success border-success", label: "text-success", dashed: false },
  WARN: { dot: "bg-warning border-warning", label: "text-warning", dashed: false },
  ERROR: { dot: "bg-destructive border-destructive", label: "text-destructive", dashed: false },
  SKIPPED: { dot: "bg-muted border-border", label: "text-muted-foreground", dashed: true },
  NOT_INSTRUMENTED: {
    dot: "bg-transparent border-border",
    label: "text-muted-foreground",
    dashed: true,
  },
};

/** Vertical execution timeline for a single evaluation run. */
export function ExecutionTimeline({
  organizationId,
  projectId,
  jobId,
  runId,
  enabled = true,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  runId: string;
  enabled?: boolean;
}) {
  const { data, isLoading, isError } = useExecutionTimeline(
    organizationId,
    projectId,
    jobId,
    runId,
    enabled,
  );

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState icon={Bug} title="Couldn't load timeline" description="Please try again." />
    );
  }

  return (
    <div className="space-y-4">
      {/* Header metrics */}
      <div className="flex flex-wrap items-center gap-3">
        <RunStatusBadge status={data.runStatus} />
        {!data.tracingActive && (
          <Badge variant="muted" className="gap-1">
            Tracing inactive
          </Badge>
        )}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <Metric label="Latency" value={formatLatency(data.totalLatencyMs)} />
          <Metric
            label="Tokens"
            value={data.totalTokens != null ? formatNumber(data.totalTokens) : "—"}
          />
          <Metric label="Cost" value={formatCost(data.cost)} />
          {data.model && <Metric label="Model" value={data.model} />}
          {data.provider && <Metric label="Provider" value={humanize(data.provider)} />}
        </div>
      </div>

      {/* Failure banner */}
      {data.failureExplanation && (
        <p className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {data.failureExplanation}
        </p>
      )}

      {/* Timeline */}
      {data.stages.length === 0 ? (
        <EmptyState
          icon={Bug}
          title="No stages recorded"
          description="This run produced no instrumented stages."
        />
      ) : (
        <ol className="relative space-y-0">
          {data.stages.map((stage, i) => (
            <StageRow key={`${stage.stage}-${i}`} stage={stage} last={i === data.stages.length - 1} />
          ))}
        </ol>
      )}

      {/* Notes */}
      {data.notes.length > 0 && (
        <ul className="space-y-1 pt-1 text-xs text-muted-foreground">
          {data.notes.map((note, i) => (
            <li key={i}>{note}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className="text-muted-foreground/70">{label}</span>
      <span className="font-mono text-foreground">{value}</span>
    </span>
  );
}

function StageRow({ stage, last }: { stage: TimelineStageResponse; last: boolean }) {
  const tone = STAGE_TONE[stage.status];
  return (
    <li className="relative flex gap-4 pb-5 last:pb-0">
      {/* Connector line */}
      {!last && (
        <span
          className={cn(
            "absolute left-[7px] top-4 h-full w-px",
            tone.dashed ? "border-l border-dashed border-border" : "bg-border",
          )}
          aria-hidden
        />
      )}
      {/* Dot */}
      <span
        className={cn(
          "relative z-10 mt-1 h-4 w-4 shrink-0 rounded-full border-2",
          tone.dot,
        )}
        aria-hidden
      />
      {/* Content */}
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">{stage.label}</span>
            <span className={cn("text-xs font-medium", tone.label)}>{humanize(stage.status)}</span>
          </div>
          {stage.durationMs != null && (
            <span className="font-mono text-xs text-muted-foreground">
              {formatLatency(stage.durationMs)}
            </span>
          )}
        </div>
        {stage.detail && <p className="text-sm text-muted-foreground">{stage.detail}</p>}
        {stage.explanation && (
          <p className="rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
            {stage.explanation}
          </p>
        )}
      </div>
    </li>
  );
}
