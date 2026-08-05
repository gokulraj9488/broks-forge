"use client";

import { useEffect, useMemo, useState } from "react";
import { Check } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

/**
 * What Brok shows while it works — never "Thinking…".
 *
 * The steps mirror the phases the reasoning engine actually performs for the question that was asked: one
 * read of the engineering record, subject resolution, the intent's own inspections (failed runs, precedents,
 * AI Git revisions, engineering memory, the graph), then composition. The engine usually finishes in a
 * second or two, so this is a truthful trace of real work shown at a human-readable pace — and the moment
 * the answer arrives, the answer replaces the trace entirely. The final step never checks itself off:
 * completion is the answer, not an animation.
 */
const OPENING = "Reading the engineering record";

function investigationPlan(question: string, hasSubject: boolean, brief: boolean): string[] {
  if (brief) {
    return [OPENING, "Ordering what happened by consequence", "Writing the brief"];
  }
  const q = question.toLowerCase();
  const steps: string[] = [OPENING];
  if (hasSubject) {
    steps.push("Resolving the subject");
  }
  if (q.includes("happened before") || q.includes("precedent") || q.includes("similar")
      || q.includes("seen this")) {
    steps.push("Searching for precedents", "Reviewing decisions and engineering memory");
  } else if (q.includes("fail") || q.includes("broke") || q.includes("red") || q.includes("wrong")) {
    steps.push("Inspecting the failed runs", "Attributing the break to a stage");
  } else if (q.includes("promote") || q.includes("rollback") || q.includes("revision")
      || q.includes("compare") || q.includes("changed between")) {
    steps.push("Reviewing AI Git revisions", "Weighing the evidence that covers them");
  } else if (q.includes("reasoning") || q.includes("memory") || q.includes("why did we change")) {
    steps.push("Consulting engineering memory");
  } else if (q.includes("affected") || q.includes("graph") || q.includes("depends")) {
    steps.push("Walking the Forge Graph");
  } else if (q.includes("knowledge") || q.includes("know") || q.includes("contradiction")) {
    steps.push("Searching engineering knowledge");
  } else if (q.includes("evidence")) {
    steps.push("Collecting the evidence on record");
  } else {
    steps.push("Inspecting evaluations and decisions");
  }
  steps.push("Composing the answer");
  return steps;
}

export function BrokInvestigation({
  question,
  subjectLabel,
  brief = false,
}: {
  question: string;
  /** The resolved focus label, when the question is being asked about something specific. */
  subjectLabel?: string | null;
  /** Briefs are written, not answered — their trace says so. */
  brief?: boolean;
}) {
  const steps = useMemo(
    () => investigationPlan(question, !!subjectLabel, brief),
    [question, subjectLabel, brief],
  );
  const [reached, setReached] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => setReached((r) => Math.min(r + 1, steps.length - 1)), 550);
    return () => clearInterval(timer);
  }, [steps.length]);

  return (
    <div
      role="status"
      aria-live="polite"
      className="space-y-1.5 rounded-xl border border-border bg-muted/20 px-3.5 py-3"
    >
      {steps.slice(0, reached + 1).map((step, i) => {
        const done = i < reached;
        return (
          <div
            key={step}
            className={cn(
              "flex items-center gap-2 text-xs duration-300 animate-in fade-in slide-in-from-bottom-1",
              done ? "text-muted-foreground" : "text-foreground",
            )}
          >
            {done ? (
              <Check className="h-3.5 w-3.5 shrink-0 text-emerald-500" />
            ) : (
              <Spinner className="h-3.5 w-3.5 shrink-0" />
            )}
            <span className="min-w-0 truncate">
              {step}
              {!brief && subjectLabel && step === "Resolving the subject" ? ` — ${subjectLabel}` : ""}
              {done ? "" : "…"}
            </span>
          </div>
        );
      })}
    </div>
  );
}
