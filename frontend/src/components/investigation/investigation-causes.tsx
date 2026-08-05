"use client";

import Link from "next/link";
import { ArrowUpRight, CornerDownRight, GitCompare, History, Layers, Zap } from "lucide-react";
import type { InvestigationCause } from "@/lib/api/investigation";
import { brokActionHref } from "@/lib/brok-actions";
import { EpistemicMark } from "@/components/platform/verdict";
import { CONFIDENCE_WORD, type Confidence, type EpistemicStatus } from "@/lib/verdict";
import { cn } from "@/lib/utils";

/**
 * The causal chain, at four depths.
 *
 * An error viewer shows what broke. An investigation separates what broke (immediate) from what made it
 * likely (contributing), from what the record has already lived through (historical), from what moved just
 * before it (related change). The layers are rendered as a descent rather than a flat list, because the
 * whole claim of this workspace is that the first answer is not the last one.
 *
 * Every cause carries its epistemic status and its confidence, and every cause ends in a workflow that
 * tests it — a cause you cannot act on is an observation, not a diagnosis.
 */
const LAYER_META: Record<
  string,
  { label: string; icon: typeof Zap; blurb: string; accent: string }
> = {
  immediate: {
    label: "Immediate cause",
    icon: Zap,
    blurb: "What actually broke.",
    accent: "border-l-rose-500/60",
  },
  contributing: {
    label: "Contributing causes",
    icon: Layers,
    blurb: "What made this failure possible, or harder to read.",
    accent: "border-l-amber-500/50",
  },
  historical: {
    label: "Historical causes",
    icon: History,
    blurb: "What the record has already lived through.",
    accent: "border-l-sky-500/50",
  },
  "related-change": {
    label: "Related changes",
    icon: GitCompare,
    blurb: "What moved shortly before this ran. Proximity, not proof.",
    accent: "border-l-violet-500/50",
  },
};

const ORDER = ["immediate", "contributing", "historical", "related-change"];

export function InvestigationCauses({
  organizationId,
  causes,
}: {
  organizationId: string;
  causes: InvestigationCause[];
}) {
  const grouped = ORDER.map((layer) => ({
    layer,
    meta: LAYER_META[layer],
    items: causes.filter((c) => c.layer === layer),
  })).filter((g) => g.items.length > 0);

  if (grouped.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        No cause could be derived from this evaluation&apos;s record.
      </p>
    );
  }

  return (
    <div className="space-y-5">
      {grouped.map(({ layer, meta, items }, groupIndex) => {
        const Icon = meta.icon;
        return (
          <section key={layer} className="space-y-2">
            {/* The label and its blurb sit side by side where there is room and stack where there is not. */}
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
              {/* The descent is visible: each deeper layer is indented from the one above it. */}
              {groupIndex > 0 && (
                <CornerDownRight className="h-3 w-3 shrink-0 text-muted-foreground/40" aria-hidden />
              )}
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Icon className="h-3 w-3" />
                {meta.label}
              </p>
              <p className="text-[11px] text-muted-foreground/70">{meta.blurb}</p>
            </div>

            {/* The descent is worth 20px of indent on a real screen, not on a phone where it costs
                more width than it communicates. */}
            <div className={cn("min-w-0 space-y-2", groupIndex > 0 && "sm:ml-5")}>
              {items.map((cause, i) => (
                <CauseCard
                  key={`${layer}-${i}`}
                  organizationId={organizationId}
                  cause={cause}
                  accent={meta.accent}
                />
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function CauseCard({
  organizationId,
  cause,
  accent,
}: {
  organizationId: string;
  cause: InvestigationCause;
  accent: string;
}) {
  const href = cause.action ? brokActionHref(organizationId, cause.action) : null;
  const confidence = CONFIDENCE_WORD[cause.confidence as Confidence] ?? cause.confidence;

  return (
    <div
      className={cn(
        "rounded-lg border border-l-2 border-border bg-background p-3 transition-colors hover:border-primary/30",
        accent,
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 flex-1 text-sm font-medium leading-snug text-foreground">
          {cause.title}
          {cause.status !== "derived" && (
            <EpistemicMark status={cause.status as EpistemicStatus} className="ml-2 align-middle" />
          )}
        </p>
        <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
          {confidence}
        </span>
      </div>
      <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{cause.explanation}</p>
      {cause.action && (
        <div className="mt-2.5">
          {href ? (
            <Link
              href={href}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-primary/50"
            >
              {cause.action.label}
              <ArrowUpRight className="h-3 w-3" />
            </Link>
          ) : (
            <span className="text-[11px] text-muted-foreground">{cause.action.label}</span>
          )}
        </div>
      )}
    </div>
  );
}
