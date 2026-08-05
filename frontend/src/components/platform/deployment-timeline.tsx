"use client";

import { Rocket, RotateCcw, GitBranch, CircleDot } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { useArtifactRevisions } from "@/lib/hooks/use-intelligence";
import type { EngineeringRevision } from "@/lib/api/platform";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

type Phase = "current" | "superseded" | "historical";

function phaseOf(index: number, activeIndex: number): Phase {
  if (activeIndex >= 0 && index === activeIndex) return "current";
  if (activeIndex >= 0 && index < activeIndex) return "superseded"; // newer than the active one → rolled past
  return "historical";
}

const PHASE_STYLE: Record<Phase, { dot: string; ring: string; label: string; badge: "success" | "warning" | "muted" }> = {
  current: { dot: "bg-emerald-400", ring: "border-emerald-500/50", label: "Current production", badge: "success" },
  superseded: { dot: "bg-amber-400", ring: "border-amber-500/40", label: "Rolled past", badge: "warning" },
  historical: { dot: "bg-zinc-500", ring: "border-border", label: "Superseded", badge: "muted" },
};

/**
 * A deployment & rollback timeline for a versioned artifact, derived from its real revision records (their
 * promotion and rollback-ready flags). Reads only the existing revisions endpoint — no new backend, no new
 * storage. When the active revision isn't the newest, that's a rollback, and it's shown as one.
 */
export function DeploymentTimeline({
  organizationId,
  projectId,
  name,
  type,
  entityId,
}: {
  organizationId: string;
  projectId?: string | null;
  /** The artifact's name, so the question Brok opens with reads like the engineer's own. */
  name?: string;
  type: string;
  entityId: string;
}) {
  const { data, isLoading } = useArtifactRevisions(organizationId, type, entityId);

  if (isLoading) return <Skeleton className="h-56 w-full" />;
  const revisions = data?.revisions ?? [];
  if (revisions.length === 0) return null; // non-versioned artifacts (evaluation/provider) simply have no timeline

  const activeIndex = revisions.findIndex((r) => r.active);
  const active = activeIndex >= 0 ? revisions[activeIndex] : null;
  const newest = revisions[0];
  const rolledBack = activeIndex > 0;
  const rollbackReady = revisions.filter((r) => r.rollbackReady).length;

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Rocket className="h-4 w-4 text-primary" />
        <h2 className="text-sm font-semibold text-foreground">Deployment timeline</h2>
        <span className="text-xs text-muted-foreground">· every promotion and rollback</span>
        {/*
         * AI Git says what moved; Brok says why. A rolled-back artifact is asked for the recorded
         * reasoning behind the revision production actually runs; otherwise the question the timeline
         * itself provokes is why the current one was promoted.
         */}
        <AskBrok
          organizationId={organizationId}
          projectId={projectId}
          focus={`${type}:${entityId}`}
          question={
            rolledBack
              ? "What was the reasoning?"
              : `Why was ${name ?? "this artifact"} promoted?`
          }
          className="ml-auto"
        />
        <InfoButton feature="ai-git" label="" />
      </div>

      <div className="flex flex-wrap gap-2 text-xs">
        <Stat label="Revisions" value={String(revisions.length)} />
        <Stat label="Promotions" value={String(data?.promotions ?? 0)} />
        <Stat label="Current" value={active ? active.label : "none"} tone={active ? "good" : "muted"} />
        <Stat label="Rollback-ready" value={String(rollbackReady)} />
      </div>

      {rolledBack && active && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-sm">
          <RotateCcw className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
          <p className="text-foreground/90">
            <strong>Rolled back.</strong> Production is on <strong>{active.label}</strong> even though a newer
            revision (<strong>{newest.label}</strong>) exists — the newer revision was rolled past.
          </p>
        </div>
      )}

      <Card>
        <CardContent className="p-4">
          <ol className="relative space-y-4 border-l border-border pl-5">
            {revisions.map((rev, i) => (
              <TimelineRow key={rev.id} rev={rev} phase={phaseOf(i, activeIndex)} isRollbackTarget={rolledBack && i === activeIndex} />
            ))}
          </ol>
        </CardContent>
      </Card>
    </section>
  );
}

function TimelineRow({
  rev,
  phase,
  isRollbackTarget,
}: {
  rev: EngineeringRevision;
  phase: Phase;
  isRollbackTarget: boolean;
}) {
  const s = PHASE_STYLE[phase];
  const snapshotKeys = Object.entries(rev.snapshot).filter(([, v]) => v != null && v !== "").slice(0, 3);
  return (
    <li className="relative">
      <span className={cn("absolute -left-[1.42rem] top-1 h-3 w-3 rounded-full border-2 border-background", s.dot)} />
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold text-foreground">{rev.label}</span>
        <Badge variant={s.badge} className="text-[10px] uppercase">{s.label}</Badge>
        {isRollbackTarget && (
          <span className="inline-flex items-center gap-1 text-[10px] text-amber-400">
            <RotateCcw className="h-3 w-3" /> rollback target
          </span>
        )}
        {rev.rollbackReady && phase !== "current" && (
          <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
            <CircleDot className="h-3 w-3" /> rollback-ready
          </span>
        )}
        <span className="text-xs text-muted-foreground">{formatDateTime(rev.at)}</span>
      </div>
      {rev.detail && <p className="mt-0.5 text-xs text-muted-foreground">{rev.detail}</p>}
      {rev.rationale && (
        <p className="mt-1 border-l-2 border-border pl-2 text-xs italic text-muted-foreground/90">“{rev.rationale}”</p>
      )}
      {snapshotKeys.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5">
          {snapshotKeys.map(([k, v]) => (
            <span key={k} className="text-[10px] text-muted-foreground">
              <span className="text-muted-foreground/60">{k}:</span>{" "}
              <span className="text-foreground/80">{truncate(String(v), 40)}</span>
            </span>
          ))}
        </div>
      )}
    </li>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: "good" | "muted" }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md border border-border bg-card px-2.5 py-1",
        tone === "good" && "border-emerald-500/30",
      )}
    >
      <GitBranch className="h-3 w-3 text-muted-foreground" />
      <span className="text-muted-foreground">{label}</span>
      <span className={cn("font-medium", tone === "muted" ? "text-muted-foreground" : "text-foreground")}>{value}</span>
    </span>
  );
}

function truncate(v: string, max: number) {
  return v.length > max ? v.slice(0, max) + "…" : v;
}
