"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { MetricStatusBadge, EXECUTION_STATUS_HINTS } from "@/components/common/eval-badges";
import { useEvaluationRunResults } from "@/lib/hooks/use-evaluation-jobs";
import { METRIC_TYPE_LABELS } from "@/lib/api/evaluation-profiles";
import { formatScore } from "@/lib/format";
import type { EvaluationRunResultResponse, MetricExecutionStatus } from "@/lib/api/evaluation-jobs";

interface StructuredDetail {
  score?: number;
  reasoning?: string;
  criteria?: Record<string, number>;
  embeddingModel?: string;
  distance?: number;
}

/** Parses the judge/similarity metrics' compact JSON detail envelope; returns null for anything
 * else (a plain-text detail, or no detail at all) so those metrics keep rendering as before. */
function parseStructuredDetail(detail: string | null | undefined): StructuredDetail | null {
  if (!detail) return null;
  try {
    const parsed = JSON.parse(detail);
    if (parsed && typeof parsed === "object" && typeof parsed.score === "number") {
      return parsed as StructuredDetail;
    }
    return null;
  } catch {
    return null;
  }
}

const JUDGMENT_ONLY_TYPES = new Set(["HALLUCINATION_DETECTION", "CITATION_VERIFICATION"]);

/** Per-metric scoring breakdown for a single run, lazily loaded on expand. */
export function RunResults({
  organizationId,
  projectId,
  jobId,
  runId,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  runId: string;
}) {
  const { data, isLoading, isError } = useEvaluationRunResults(
    organizationId,
    projectId,
    jobId,
    runId,
  );

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-2 text-xs text-muted-foreground">
        <Spinner className="h-3.5 w-3.5" />
        Loading metric results…
      </div>
    );
  }

  if (isError) {
    return <p className="py-2 text-xs text-destructive">Couldn&apos;t load metric results.</p>;
  }

  if (!data || data.length === 0) {
    return <p className="py-2 text-xs text-muted-foreground">No metric results for this run.</p>;
  }

  return (
    <div className="space-y-2">
      {data.map((r, i) => (
        <MetricResultRow key={`${r.metricType}-${i}`} result={r} />
      ))}
    </div>
  );
}

function MetricResultRow({ result: r }: { result: EvaluationRunResultResponse }) {
  const [open, setOpen] = useState(false);
  const didNotExecute = r.executionStatus !== "COMPLETED";
  const structured = didNotExecute ? null : parseStructuredDetail(r.detail);
  const criteriaEntries = structured?.criteria ? Object.entries(structured.criteria) : [];
  const hasExpandableContent = !!r.detail;
  const label = r.metricLabel || METRIC_TYPE_LABELS[r.metricType] || r.metricType;
  const isJudgment = JUDGMENT_ONLY_TYPES.has(r.metricType);
  const isJudgeScore = r.metricType === "LLM_JUDGE";

  return (
    <div className="rounded-md border border-border bg-background text-xs">
      <button
        type="button"
        onClick={() => hasExpandableContent && setOpen((o) => !o)}
        className="flex w-full flex-wrap items-center justify-between gap-2 px-3 py-2 text-left"
        disabled={!hasExpandableContent}
      >
        <div className="flex items-center gap-2">
          {hasExpandableContent ? (
            open ? (
              <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            )
          ) : (
            <span className="w-3.5" />
          )}
          <MetricStatusBadge passed={r.passed} executionStatus={r.executionStatus} />
          <span className="font-medium">{label}</span>
        </div>
        <div className="flex items-center gap-3 text-muted-foreground">
          {!didNotExecute && !isJudgment && (
            structured?.score != null ? (
              <span className="font-mono">
                {isJudgeScore ? `${(structured.score * 10).toFixed(1)}/10` : structured.score.toFixed(2)}
              </span>
            ) : (
              r.score != null && <span>score {formatScore(r.score)}</span>
            )
          )}
          {!didNotExecute && !isJudgment && r.threshold != null && <span>threshold {r.threshold}</span>}
        </div>
      </button>

      {open && hasExpandableContent && (
        <div className="space-y-2 border-t border-border px-3 py-2.5">
          {didNotExecute ? (
            <ExecutionErrorDetail status={r.executionStatus} detail={r.detail} />
          ) : structured ? (
            <>
              {r.metricType === "SEMANTIC_SIMILARITY" && (
                <div className="grid gap-1.5 sm:grid-cols-2">
                  {r.threshold != null && (
                    <DetailRow label="Threshold" value={r.threshold} />
                  )}
                  {structured.embeddingModel && (
                    <DetailRow label="Embedding model" value={structured.embeddingModel} />
                  )}
                  {structured.distance != null && (
                    <DetailRow label="Vector distance" value={structured.distance.toFixed(4)} />
                  )}
                </div>
              )}
              {isJudgeScore && (
                <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                  Overall {(Number(structured.score) * 10).toFixed(1)}/10
                </p>
              )}
              {criteriaEntries.length > 0 && (
                <div className="grid gap-1.5 sm:grid-cols-2">
                  {criteriaEntries.map(([name, value]) => (
                    <div key={name} className="flex items-center justify-between rounded bg-muted/40 px-2 py-1">
                      <span>{name}</span>
                      <span className="font-mono">{value}/10</span>
                    </div>
                  ))}
                </div>
              )}
              {structured.reasoning && (
                <div>
                  <p className="mb-0.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                    Reasoning
                  </p>
                  <p className="whitespace-pre-wrap text-foreground">{structured.reasoning}</p>
                </div>
              )}
            </>
          ) : (
            <p className="whitespace-pre-wrap text-foreground">{r.detail}</p>
          )}
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between rounded bg-muted/40 px-2 py-1">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-mono">{value}</span>
    </div>
  );
}

/** Explains *why* a metric never ran — the raw provider message plus plain-language possibilities
 * for that failure category, so a non-engineer reading the Runs page isn't left with just "HTTP 401". */
function ExecutionErrorDetail({
  status,
  detail,
}: {
  status: MetricExecutionStatus;
  detail: string | null;
}) {
  const hints = status !== "COMPLETED" ? EXECUTION_STATUS_HINTS[status] : undefined;
  return (
    <div className="space-y-2">
      {detail && <p className="whitespace-pre-wrap text-foreground">{detail}</p>}
      {hints && (
        <div>
          <p className="mb-0.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Common causes
          </p>
          <ul className="list-inside list-disc space-y-0.5 text-muted-foreground">
            {hints.map((hint) => (
              <li key={hint}>{hint}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
