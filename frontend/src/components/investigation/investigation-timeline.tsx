"use client";

import Link from "next/link";
import {
  ArrowUpRight,
  BookOpen,
  Database,
  FlaskConical,
  GitBranch,
  History,
  Rocket,
  ScrollText,
  XCircle,
} from "lucide-react";
import type { InvestigationEvent } from "@/lib/api/investigation";
import { brokRefHref } from "@/lib/brok-actions";
import { VERDICT_STYLE, type VerdictState } from "@/lib/verdict";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

/**
 * The engineering chronology — the spine of the investigation.
 *
 * Time is treated as reasoning, not metadata: a promotion an hour before a run and the failure it preceded
 * sit on the same axis, which is the only way an engineer can see the relationship at all. The rail is
 * colour-coded by the verdict palette (state only — never structure), so the eye finds the break before it
 * reads a single word, and the gap between two events is labelled when it is long enough to matter.
 */
const EVENT_ICON: Record<string, typeof Rocket> = {
  promotion: Rocket,
  revision: GitBranch,
  dataset: Database,
  evaluation: FlaskConical,
  run: XCircle,
  knowledge: BookOpen,
  decision: ScrollText,
  precedent: History,
};

export function InvestigationTimeline({
  organizationId,
  events,
  activeId,
  onSelect,
}: {
  organizationId: string;
  events: InvestigationEvent[];
  activeId?: string | null;
  onSelect?: (event: InvestigationEvent) => void;
}) {
  if (events.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        This evaluation has no dated engineering events yet — the chronology begins with its first run.
      </p>
    );
  }

  return (
    <ol className="relative space-y-0 border-l border-border pl-6">
      {events.map((event, i) => {
        const Icon = EVENT_ICON[event.kind] ?? FlaskConical;
        const style = VERDICT_STYLE[event.state as VerdictState] ?? VERDICT_STYLE.unknown;
        const href = event.ref ? brokRefHref(organizationId, event.ref) : null;
        const active = !!activeId && event.id === activeId;
        const gap = gapLabel(events[i - 1]?.at, event.at);

        return (
          <li key={event.id} className="relative pb-5 last:pb-0">
            {/* The elapsed time between two events is itself evidence when it is large. */}
            {gap && (
              <p className="mb-2 -ml-6 pl-6 text-[10px] uppercase tracking-wide text-muted-foreground/60">
                {gap} later
              </p>
            )}
            <span
              className={cn(
                "absolute -left-[1.72rem] flex h-6 w-6 items-center justify-center rounded-full border bg-background",
                style.border,
                event.state === "failed" && "shadow-[0_0_0_4px_rgba(251,113,133,0.10)]",
              )}
            >
              <Icon className={cn("h-3 w-3", style.fg)} />
            </span>

            <div
              data-interactive={onSelect ? "" : undefined}
              onClick={onSelect ? () => onSelect(event) : undefined}
              className={cn(
                "rounded-lg border px-3 py-2 transition-colors",
                active ? "border-primary/50 bg-muted/40" : "border-transparent hover:border-border",
              )}
            >
              <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                <p className="text-sm font-medium leading-snug text-foreground">{event.title}</p>
                <time className="text-[10px] text-muted-foreground/70">{formatDateTime(event.at)}</time>
              </div>
              {event.detail && (
                <p className="mt-0.5 text-xs leading-snug text-muted-foreground">{event.detail}</p>
              )}
              {href && (
                <Link
                  href={href}
                  onClick={(e) => e.stopPropagation()}
                  className="mt-1.5 inline-flex items-center gap-1 text-[11px] font-medium text-muted-foreground transition-colors hover:text-foreground"
                >
                  Open {event.ref?.label}
                  <ArrowUpRight className="h-3 w-3" />
                </Link>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/** "3 days", "4 hours" — only when the gap is long enough to change how the sequence reads. */
function gapLabel(previous: string | undefined, current: string): string | null {
  if (!previous) {
    return null;
  }
  const ms = new Date(current).getTime() - new Date(previous).getTime();
  if (!Number.isFinite(ms) || ms < 60 * 60 * 1000) {
    return null;
  }
  const hours = Math.round(ms / (60 * 60 * 1000));
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? "" : "s"}`;
  }
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"}`;
}
