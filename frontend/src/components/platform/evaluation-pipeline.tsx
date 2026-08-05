"use client";

import {
  BookOpen,
  Cpu,
  Database,
  FileText,
  Flag,
  Scale,
  Server,
} from "lucide-react";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";
import { formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * The engineering pipeline an evaluation actually is — Dataset → Prompt → Provider → Inference →
 * Judgment → Knowledge → Verdict — animated while the run is live.
 *
 * Every stage state is read from real job fields (item counters, pinned configuration, status); nothing
 * here is decorative guesswork. While the job runs, completed connectors carry a visible flow and the
 * active stage pulses. When a run fails, the pipeline stops exactly where the chain broke: the failed
 * stage turns red and everything downstream of it is shown as never reached. Partial failures (items that
 * broke while the run continued) mark the inference stage amber with the real count.
 */

type StageState = "done" | "active" | "failed" | "unreached" | "pending";

interface Stage {
  key: string;
  label: string;
  detail: string | null;
  icon: typeof Database;
  state: StageState;
}

function stagesOf(job: EvaluationJobResponse): Stage[] {
  const processed = job.completedItems + job.failedItems;
  const running = job.status === "RUNNING";
  const completed = job.status === "COMPLETED";
  const failed = job.status === "FAILED";
  const started = running || completed || failed || processed > 0;
  // A hard failure before any item produced a result broke reaching the provider; one that recorded
  // item results broke during inference. Both readings come from the counters, not from a guess.
  const brokeBeforeItems = failed && processed === 0;

  const stages: Stage[] = [];

  stages.push({
    key: "dataset",
    label: "Dataset",
    detail: job.totalItems > 0 ? `${formatNumber(job.totalItems)} items` : "resolving",
    icon: Database,
    state: job.totalItems > 0 || started ? "done" : "active",
  });

  if (job.promptId) {
    stages.push({
      key: "prompt",
      label: "Prompt",
      detail: "pinned revision",
      icon: FileText,
      state: "done",
    });
  }

  stages.push({
    key: "provider",
    label: "Provider",
    detail: job.model ?? (job.provider ? job.provider.toLowerCase() : "via agent"),
    icon: Server,
    state: brokeBeforeItems ? "failed" : started ? "done" : "pending",
  });

  stages.push({
    key: "inference",
    label: "Inference",
    detail:
      job.failedItems > 0
        ? `${formatNumber(processed)}/${formatNumber(job.totalItems)} · ${formatNumber(job.failedItems)} failed`
        : `${formatNumber(processed)}/${formatNumber(job.totalItems)}`,
    icon: Cpu,
    state: brokeBeforeItems
      ? "unreached"
      : failed
        ? "failed"
        : completed
          ? job.failedItems > 0
            ? "failed"
            : "done"
          : running
            ? "active"
            : "pending",
  });

  stages.push({
    key: "judgment",
    label: "Judgment",
    detail: job.completedItems > 0 ? `${formatNumber(job.completedItems)} measured` : null,
    icon: Scale,
    state: failed
      ? "unreached"
      : completed
        ? "done"
        : running && job.completedItems > 0
          ? "active"
          : "pending",
  });

  stages.push({
    key: "knowledge",
    label: "Knowledge",
    detail: completed ? "derived" : null,
    icon: BookOpen,
    state: failed ? "unreached" : completed ? "done" : "pending",
  });

  stages.push({
    key: "verdict",
    label: "Verdict",
    detail: failed
      ? "failed"
      : completed
        ? job.failedItems > 0
          ? "attention"
          : "healthy"
        : null,
    icon: Flag,
    state: failed ? "unreached" : completed ? (job.failedItems > 0 ? "failed" : "done") : "pending",
  });

  // A partial failure is amber, not red — the run continued past it, and the pipeline should say so.
  if (completed && job.failedItems > 0) {
    for (const stage of stages) {
      if (stage.state === "failed") {
        stage.state = "active";
      }
    }
  }

  return stages;
}

const STAGE_STYLE: Record<StageState, { ring: string; icon: string; label: string }> = {
  done: { ring: "border-emerald-500/50 bg-emerald-500/10", icon: "text-emerald-400", label: "text-foreground" },
  active: { ring: "border-amber-500/50 bg-amber-500/10", icon: "text-amber-400", label: "text-foreground" },
  failed: { ring: "border-red-500/60 bg-red-500/10", icon: "text-red-400", label: "text-foreground" },
  unreached: { ring: "border-border bg-muted/20", icon: "text-muted-foreground/40", label: "text-muted-foreground/50" },
  pending: { ring: "border-border bg-background", icon: "text-muted-foreground", label: "text-muted-foreground" },
};

export function EvaluationPipeline({ job }: { job: EvaluationJobResponse }) {
  const stages = stagesOf(job);
  const running = job.status === "RUNNING" || job.status === "PENDING";
  const firstFailure = stages.findIndex((s) => s.state === "failed");

  return (
    <div className="overflow-x-auto">
      <style>{`
        @keyframes bf-flow { from { background-position: 0 0; } to { background-position: 16px 0; } }
        .bf-flow { animation: bf-flow 0.6s linear infinite; }
        @media (prefers-reduced-motion: reduce) { .bf-flow { animation: none; } }
      `}</style>
      <ol className="flex min-w-max items-start gap-0 py-1" aria-label="Evaluation pipeline">
        {stages.map((stage, i) => {
          const style = STAGE_STYLE[stage.state];
          const Icon = stage.icon;
          // The connector into this stage flows while the run is live and the chain is intact this far.
          const reached = stage.state === "done" || stage.state === "active" || stage.state === "failed";
          const flowing = running && reached && (firstFailure < 0 || i <= firstFailure);
          return (
            <li key={stage.key} className="flex items-start">
              {i > 0 && (
                <span
                  aria-hidden
                  className={cn(
                    "mt-4 h-0.5 w-6 shrink-0 sm:w-9",
                    reached
                      ? flowing
                        ? "bf-flow bg-[linear-gradient(90deg,theme(colors.emerald.500/60)_50%,transparent_50%)] bg-[length:16px_2px]"
                        : "bg-emerald-500/50"
                      : "bg-border",
                    stage.state === "failed" && "bg-red-500/50",
                  )}
                />
              )}
              <div className="flex w-[4.6rem] flex-col items-center gap-1 text-center sm:w-[5.4rem]">
                <span
                  className={cn(
                    "flex h-8 w-8 items-center justify-center rounded-full border transition-colors",
                    style.ring,
                    stage.state === "active" && "animate-pulse",
                  )}
                >
                  <Icon className={cn("h-3.5 w-3.5", style.icon)} />
                </span>
                <span className={cn("text-[10px] font-medium leading-tight", style.label)}>
                  {stage.label}
                </span>
                {stage.detail && (
                  <span className="max-w-full truncate text-[9px] leading-tight text-muted-foreground/80">
                    {stage.detail}
                  </span>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
