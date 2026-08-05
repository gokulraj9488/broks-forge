"use client";

import Link from "next/link";
import { ArrowUpRight, CornerDownRight, Lightbulb, ListTree, Microscope, Target } from "lucide-react";
import type { BrokAnswer, BrokRecommendation } from "@/lib/api/brok";
import { brokActionHref } from "@/lib/brok-actions";
import { BrokRefGroup } from "@/components/brok/brok-ref";
import { EpistemicMark, VerdictBanner } from "@/components/platform/verdict";
import {
  CONFIDENCE_WORD,
  type Confidence,
  type EpistemicStatus,
  type Verdict,
  type VerdictState,
} from "@/lib/verdict";
import { cn } from "@/lib/utils";

/**
 * One grounded answer, rendered as the constitutional narrative rather than as a chat message.
 *
 * The order is not a layout choice — it is the contract: what happened (the verdict), why (a reasoning chain
 * where every step shows how it is known), what it costs you (impact), what to do (recommendations, each
 * carrying its evidence, confidence and a real next action), and what to ask next.
 *
 * Nothing here is prose the model wrote. Every line is a field the backend derived from a real record, which
 * is why the same components that render the Engineering Brief render this too.
 */
export function BrokAnswerView({
  organizationId,
  answer,
  onFocus,
  onAsk,
  activeFocusId,
}: {
  organizationId: string;
  answer: BrokAnswer;
  onFocus?: (nodeId: string) => void;
  onAsk?: (question: string, focus?: string | null) => void;
  activeFocusId?: string | null;
}) {
  const verdict: Verdict = {
    state: answer.verdict.state as VerdictState,
    headline: answer.verdict.headline,
    consequence: answer.verdict.consequence,
    status: answer.verdict.status as EpistemicStatus,
    provenance: { basis: answer.verdict.basis },
  };

  const investigated = investigatedLine(answer);

  return (
    <article className="space-y-4">
      {/* 1 — What happened, on what footing, derived from what. */}
      <VerdictBanner
        verdict={verdict}
        action={
          <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
            {CONFIDENCE_WORD[answer.verdict.confidence as Confidence] ?? answer.verdict.confidence}
          </span>
        }
      />

      {/* The audit trail of the investigation — counted from what this answer actually referenced. */}
      {investigated && (
        <p className="flex items-center gap-1.5 px-0.5 text-[11px] text-muted-foreground/80">
          <Microscope className="h-3 w-3 shrink-0" />
          {investigated}
        </p>
      )}

      {/* 2 — Why: each step declares whether it is derived or inferred, and what it was read from. */}
      {answer.reasoning.length > 0 && (
        <section className="space-y-2">
          <SectionLabel icon={ListTree} text="Reasoning" />
          <ol className="space-y-2">
            {answer.reasoning.map((step, i) => (
              <li key={`${answer.id}-r-${i}`} className="flex gap-2.5">
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-muted-foreground/50" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm leading-snug text-foreground">
                    {step.text}
                    {step.status !== "derived" && (
                      <EpistemicMark
                        status={step.status as EpistemicStatus}
                        className="ml-2 align-middle"
                      />
                    )}
                  </p>
                  <p className="text-[11px] text-muted-foreground/80">Read from {step.basis}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      {/* 3 — Engineering impact, as a sentence (§12.4: a number must say what it means). */}
      {answer.impact?.statement && (
        <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/30 px-3 py-2">
          <Target className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          <p className="text-xs text-foreground">{answer.impact.statement}</p>
        </div>
      )}

      {/* 4 — Evidence: the records the answer was actually read from. */}
      {answer.evidence.length > 0 && (
        <BrokRefGroup
          organizationId={organizationId}
          title="Evidence"
          refs={answer.evidence}
          onFocus={onFocus}
          activeId={activeFocusId}
        />
      )}

      {/* 5 — Recommendations, each with its next action into an existing workflow. */}
      {answer.recommendations.length > 0 && (
        <section className="space-y-2">
          <SectionLabel icon={Lightbulb} text="What to do" />
          <div className="space-y-2">
            {answer.recommendations.map((rec, i) => (
              <RecommendationCard
                key={`${answer.id}-rec-${i}`}
                organizationId={organizationId}
                recommendation={rec}
              />
            ))}
          </div>
        </section>
      )}

      {/* 6 — Follow-up investigations: engineering-specific, never generic. */}
      {answer.followUps.length > 0 && onAsk && (
        <section className="space-y-2">
          <SectionLabel icon={CornerDownRight} text="Ask next" />
          <div className="flex flex-wrap gap-2">
            {answer.followUps.map((f, i) => (
              <button
                key={`${answer.id}-f-${i}`}
                type="button"
                title={f.rationale}
                onClick={() => onAsk(f.question, f.focus)}
                className="rounded-full border border-border px-3 py-1.5 text-left text-xs text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
              >
                {f.question}
              </button>
            ))}
          </div>
        </section>
      )}
    </article>
  );
}

function RecommendationCard({
  organizationId,
  recommendation,
}: {
  organizationId: string;
  recommendation: BrokRecommendation;
}) {
  const href = brokActionHref(organizationId, recommendation.action);
  const confidence =
    CONFIDENCE_WORD[recommendation.confidence as Confidence] ?? recommendation.confidence;

  return (
    <div className="rounded-lg border border-border bg-background p-3 transition-colors hover:border-primary/30">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 flex-1 text-sm font-medium text-foreground">
          {recommendation.title}
          {recommendation.status !== "derived" && (
            <EpistemicMark
              status={recommendation.status as EpistemicStatus}
              className="ml-2 align-middle"
            />
          )}
        </p>
        <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
          {confidence}
        </span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">{recommendation.why}</p>
      <p className="mt-1 text-xs text-foreground/80">{recommendation.impact}</p>
      {recommendation.action && (
        <div className="mt-2.5">
          {href ? (
            <Link
              href={href}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-primary/50"
            >
              {recommendation.action.label}
              <ArrowUpRight className="h-3 w-3" />
            </Link>
          ) : (
            <span className="text-[11px] text-muted-foreground">{recommendation.action.label}</span>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * "Investigated 4 evaluations · 2 revisions · engineering memory" — the audit trail of the answer,
 * counted from its own references rather than asserted. An answer that read nothing says nothing here.
 */
function investigatedLine(answer: BrokAnswer): string | null {
  const parts: string[] = [];
  const add = (count: number, word: string) => {
    if (count > 0) {
      parts.push(`${count} ${word}${count === 1 ? "" : "s"}`);
    }
  };
  add(answer.references.evaluations.length, "evaluation");
  add(answer.references.revisions.length, "AI Git revision");
  add(answer.references.decisions.length, "decision");
  add(answer.references.knowledge.length, "knowledge record");
  if (answer.memory.length > 0) {
    parts.push("engineering memory");
  }
  return parts.length > 0 ? `Investigated ${parts.join(" · ")}` : null;
}

function SectionLabel({ icon: Icon, text }: { icon: typeof Lightbulb; text: string }) {
  return (
    <p className={cn("flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide",
      "text-muted-foreground")}>
      <Icon className="h-3 w-3" />
      {text}
    </p>
  );
}
