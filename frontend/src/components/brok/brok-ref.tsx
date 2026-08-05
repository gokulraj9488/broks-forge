"use client";

import Link from "next/link";
import { ArrowUpRight, Crosshair } from "lucide-react";
import type { BrokRef } from "@/lib/api/brok";
import { brokRefHref } from "@/lib/brok-actions";
import { REASONING_TREATMENT, isReasoning, substrateMeta } from "@/lib/substrate";
import { VerdictChip } from "@/components/platform/verdict";
import { verdictOfJobStatus } from "@/lib/verdict";
import { cn } from "@/lib/utils";

/**
 * One referenced engineering record, rendered in Brok's own visual language — which is simply the
 * platform's. Structural identity (what the thing is) comes from the substrate palette; where the record has
 * an outcome, the verdict palette carries it. The two never mix, so an engineer reading the evidence panel
 * can tell "this is a decision" from "this is failing" without reading a word.
 *
 * The row does two things at once, which is what keeps the workspace synchronized: clicking the body focuses
 * the conversation on this record, and the arrow opens the surface it lives on.
 */
export function BrokRefRow({
  organizationId,
  refItem,
  onFocus,
  active,
}: {
  organizationId: string;
  refItem: BrokRef;
  onFocus?: (nodeId: string) => void;
  active?: boolean;
}) {
  const meta = substrateMeta(refItem.type);
  const Icon = meta.icon;
  const href = brokRefHref(organizationId, refItem);
  const outcome = outcomeChip(refItem);

  return (
    <div
      className={cn(
        "group flex items-start gap-2.5 rounded-lg border px-2.5 py-2 transition-colors",
        isReasoning(refItem.type) ? REASONING_TREATMENT : "bg-background",
        active ? "border-primary/50" : "border-border hover:border-primary/30",
      )}
    >
      <Icon className={cn("mt-0.5 h-3.5 w-3.5 shrink-0", meta.color)} />
      <button
        type="button"
        onClick={() => onFocus?.(refItem.id)}
        disabled={!onFocus}
        title={onFocus ? `Focus the conversation on ${refItem.label}` : undefined}
        className="min-w-0 flex-1 text-left disabled:cursor-default"
      >
        <span className="flex items-center gap-1.5">
          <span className="truncate text-xs font-medium text-foreground">{refItem.label}</span>
          {onFocus && (
            <Crosshair className="h-3 w-3 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          )}
        </span>
        <span className="mt-0.5 flex flex-wrap items-center gap-1.5">
          <span className="text-[10px] uppercase tracking-wide text-muted-foreground">{meta.label}</span>
          {outcome}
        </span>
        {refItem.detail && (
          <span className="mt-0.5 line-clamp-2 block text-[11px] text-muted-foreground">{refItem.detail}</span>
        )}
      </button>
      {href && (
        <Link
          href={href}
          title={`Open ${refItem.label}`}
          className="mt-0.5 shrink-0 text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      )}
    </div>
  );
}

/** Only records that genuinely carry an outcome show one — absence is never dressed up as health (L-34). */
function outcomeChip(refItem: BrokRef) {
  if (!refItem.outcome) {
    return null;
  }
  if (refItem.type === "evaluation" || refItem.type === "run") {
    return <VerdictChip state={verdictOfJobStatus(refItem.outcome)} className="scale-90" />;
  }
  if (refItem.outcome === "ARCHIVED") {
    return <VerdictChip state="attention" label="Archived" className="scale-90" />;
  }
  if (refItem.outcome === "ACTIVE") {
    return <span className="text-[10px] uppercase tracking-wide text-muted-foreground">Active</span>;
  }
  return null;
}

/** A titled group of references — one panel of the workspace's context rail. */
export function BrokRefGroup({
  organizationId,
  title,
  refs,
  onFocus,
  activeId,
  emptyHint,
}: {
  organizationId: string;
  title: string;
  refs: BrokRef[];
  onFocus?: (nodeId: string) => void;
  activeId?: string | null;
  emptyHint?: string;
}) {
  if (refs.length === 0 && !emptyHint) {
    return null;
  }
  return (
    <div className="space-y-1.5">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
        {refs.length > 0 && <span className="ml-1.5 font-normal">· {refs.length}</span>}
      </p>
      {refs.length === 0 ? (
        <p className="text-[11px] text-muted-foreground/80">{emptyHint}</p>
      ) : (
        <div className="space-y-1.5">
          {refs.map((r) => (
            <BrokRefRow
              key={r.id}
              organizationId={organizationId}
              refItem={r}
              onFocus={onFocus}
              active={activeId === r.id}
            />
          ))}
        </div>
      )}
    </div>
  );
}
