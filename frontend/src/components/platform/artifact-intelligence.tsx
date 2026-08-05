"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Clock,
  Gavel,
  GitCompare,
  HelpCircle,
  History,
  Lightbulb,
  Network,
  RotateCcw,
  ShieldCheck,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { VerdictBanner } from "@/components/platform/verdict";
import {
  confidenceFromEvidence,
  CONFIDENCE_WORD,
  plural,
  type Verdict,
} from "@/lib/verdict";
import {
  useArtifactIntelligence,
  useArtifactRevisions,
  useRevisionComparison,
} from "@/lib/hooks/use-intelligence";
import type {
  ArtifactIntelligence as Intel,
  EngineeringRevision,
  KnowledgeLink,
  KnowledgeObject,
  MemoryEntry,
} from "@/lib/api/platform";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { FeatureHint } from "@/components/platform/feature-hint";
import { knowledgeHref } from "@/lib/artifact-links";
import { substrateMeta } from "@/lib/substrate";
import { humanize } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

// Reasoning identity comes from the one grammar (lib/substrate.ts) — cool hues, never verdict colours.
const kMeta = substrateMeta;

export function ArtifactIntelligence({
  organizationId,
  projectId,
  type,
  entityId,
}: {
  organizationId: string;
  /** Lets Brok open in the workspace this artifact actually lives in. */
  projectId?: string;
  type: string;
  entityId: string;
}) {
  const [view, setView] = useState<"reasoning" | "revisions">("reasoning");
  return (
    <div className="space-y-4">
      <FeatureHint id="artifact-intelligence" feature="engineering-intelligence">
        This is the artifact&rsquo;s <strong>Engineering Intelligence</strong> — the observations, decisions,
        evidence and knowledge derived from your real work. It&rsquo;s what makes this a workspace, not a log.
      </FeatureHint>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="inline-flex rounded-lg border border-border bg-muted/40 p-0.5 text-sm">
          <ViewTab active={view === "reasoning"} onClick={() => setView("reasoning")} icon={Lightbulb} label="Reasoning" />
          <ViewTab active={view === "revisions"} onClick={() => setView("revisions")} icon={History} label="AI Git" />
        </div>
        <div className="flex items-center gap-2">
          <AskBrok
            organizationId={organizationId}
            projectId={projectId}
            focus={`${type}:${entityId}`}
            question={
              view === "reasoning"
                ? "Which evaluations support this decision?"
                : "What changed between these revisions?"
            }
          />
          <InfoButton feature={view === "reasoning" ? "engineering-intelligence" : "ai-git"} />
        </div>
      </div>

      {view === "reasoning" ? (
        <ReasoningView organizationId={organizationId} type={type} entityId={entityId} />
      ) : (
        <RevisionsView organizationId={organizationId} type={type} entityId={entityId} />
      )}
    </div>
  );
}

function ViewTab({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof Gavel;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-colors",
        active ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </button>
  );
}

// ============================================================================
// Reasoning — observations, claims, decisions, evidence, knowledge, memory
// ============================================================================

function ReasoningView({
  organizationId,
  type,
  entityId,
}: {
  organizationId: string;
  type: string;
  entityId: string;
}) {
  const { data, isLoading, isError } = useArtifactIntelligence(organizationId, type, entityId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState
        icon={Network}
        title="Engineering intelligence unavailable"
        description="This artifact's intelligence isn't available for this workspace yet."
      />
    );
  }

  const empty =
    data.observations.length === 0 &&
    data.claims.length === 0 &&
    data.decisions.length === 0 &&
    data.evidence.length === 0 &&
    data.knowledge.length === 0;

  if (empty) {
    return (
      <EmptyState
        icon={Lightbulb}
        title="No engineering knowledge yet"
        description="As this artifact is promoted, evaluated and evidenced, the platform derives its observations, decisions and knowledge here — all traceable to real events."
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* P-1: the reading order is summary → confidence → evidence → decision → knowledge → raw objects.
          Counts are receipts at the bottom of this banner, never the first thing the engineer meets. */}
      <VerdictBanner verdict={intelligenceVerdict(data)} />

      {data.memory.length > 0 && <MemorySection memory={data.memory} />}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {data.knowledge.length > 0 && (
          <KnowledgeSection title="Knowledge" subtitle="What we now know" objects={data.knowledge} organizationId={organizationId} />
        )}
        {data.decisions.length > 0 && (
          <KnowledgeSection title="Decisions" subtitle="What was decided, and why" objects={data.decisions} organizationId={organizationId} />
        )}
        {data.claims.length > 0 && (
          <KnowledgeSection title="Claims" subtitle="What is asserted" objects={data.claims} organizationId={organizationId} />
        )}
        {data.evidence.length > 0 && (
          <KnowledgeSection title="Evidence" subtitle="What supports it" objects={data.evidence} organizationId={organizationId} />
        )}
        {data.observations.length > 0 && (
          <KnowledgeSection title="Observations" subtitle="What was measured" objects={data.observations} organizationId={organizationId} />
        )}
      </div>
    </div>
  );
}

/**
 * The engineering summary for an artifact's reasoning — the sentence an engineer reads before any object.
 *
 * Composed strictly from what was derived: the decision that set the current state, the evidence behind it,
 * and the durable knowledge that emerged. Confidence is verbal and tied to evidence volume (L-57); where
 * evidence is thin, the summary says so rather than sounding certain.
 */
function intelligenceVerdict(data: Intel): Verdict {
  const evidenceCount = data.evidence.length;
  const decision = data.decisions[0] ?? null;
  const knowledge = data.knowledge[0] ?? null;
  const name = data.artifact.name;

  // No evidence at all — say it plainly rather than implying health (L-34).
  if (evidenceCount === 0) {
    return {
      state: "unknown",
      headline: decision
        ? `${name} has a recorded decision, but no evidence yet.`
        : `Nothing has been evidenced about ${name} yet.`,
      consequence: decision
        ? `${decision.summary} No evaluation has tested that decision, so its effect is unproven.`
        : "Its behaviour has never been measured, so any statement about its quality would be a guess.",
      status: "derived",
      provenance: { basis: "this artifact's evaluations and version history" },
    };
  }

  const confidence = CONFIDENCE_WORD[confidenceFromEvidence(evidenceCount)];
  const failing = data.evidence.filter((e) => e.outcome === "FAILED").length;
  const headline = knowledge
    ? knowledge.summary
    : decision
      ? decision.summary
      : `${name} has been evaluated ${plural(evidenceCount, "time")}.`;

  return {
    state: failing > 0 ? "attention" : "healthy",
    headline: knowledge ? `${name}: ${lowerFirst(headline)}` : headline,
    consequence:
      failing > 0
        ? `${plural(failing, "supporting evaluation")} failed — the conclusion above is ${confidence} the evidence, not settled.`
        : `This reading is ${confidence} the evidence: ${plural(evidenceCount, "evaluation")} and ${plural(
            data.decisions.length,
            "recorded decision",
          )}.`,
    status: "derived",
    provenance: {
      basis: `${plural(evidenceCount, "evaluation")}, ${plural(data.observations.length, "observation")} and ${plural(
        data.decisions.length,
        "decision",
      )}`,
    },
  };
}

function lowerFirst(v: string) {
  return v.charAt(0).toLowerCase() + v.slice(1);
}

function MemorySection({ memory }: { memory: MemoryEntry[] }) {
  return (
    <section className="space-y-2">
      <SectionHeader icon={HelpCircle} title="Engineering memory" subtitle="Why this artifact is the way it is" />
      <Card>
        <CardContent className="divide-y divide-border p-0">
          {memory.map((m) => (
            <div key={m.decisionId} className="space-y-1 p-4">
              <p className="flex items-center gap-2 text-sm font-medium text-foreground">
                <HelpCircle className="h-4 w-4 shrink-0 text-primary" />
                {m.question}
              </p>
              <p className="pl-6 text-sm text-muted-foreground">{m.answer}</p>
              <p className="pl-6 text-xs text-muted-foreground/70">{formatDateTime(m.at)}</p>
            </div>
          ))}
        </CardContent>
      </Card>
    </section>
  );
}

function KnowledgeSection({
  title,
  subtitle,
  objects,
  organizationId,
}: {
  title: string;
  subtitle: string;
  objects: KnowledgeObject[];
  organizationId: string;
}) {
  const Icon = kMeta(objects[0]?.type ?? "knowledge").icon;
  return (
    <section className="space-y-2">
      <SectionHeader icon={Icon} title={title} subtitle={subtitle} />
      <Card>
        <CardContent className="divide-y divide-border p-0">
          {objects.map((o) => (
            <KnowledgeRow key={o.id} object={o} organizationId={organizationId} />
          ))}
        </CardContent>
      </Card>
    </section>
  );
}

function KnowledgeRow({ object, organizationId }: { object: KnowledgeObject; organizationId: string }) {
  const meta = kMeta(object.type);
  const Icon = meta.icon;
  // Every knowledge object opens its own dedicated engineering page — a first-class object, not a redirect.
  const href = knowledgeHref(organizationId, object.id);

  return (
    <Link href={href} className="block hover:bg-muted/40">
      <div className="space-y-1.5 p-3">
        <div className="flex items-start gap-2.5">
          <Icon className={cn("mt-0.5 h-4 w-4 shrink-0", meta.color)} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="truncate text-sm font-medium text-foreground">{object.title}</span>
              {object.outcome && <OutcomeBadge outcome={object.outcome} />}
            </div>
            <p className="text-xs text-muted-foreground">{object.summary}</p>
            {object.rationale && (
              <p className="mt-1 border-l-2 border-border pl-2 text-xs italic text-muted-foreground/90">
                “{object.rationale}”
              </p>
            )}
          </div>
          <span className="shrink-0 text-[11px] text-muted-foreground">{formatDateTime(object.at)}</span>
        </div>
        {object.links.length > 0 && <LinkChips links={object.links} />}
      </div>
    </Link>
  );
}

function LinkChips({ links }: { links: KnowledgeLink[] }) {
  return (
    <div className="flex flex-wrap gap-1.5 pl-6">
      {links.slice(0, 6).map((l, i) => (
        <span
          key={`${l.id}-${i}`}
          className="inline-flex items-center gap-1 rounded border border-border bg-muted/50 px-1.5 py-0.5 text-[10px] text-muted-foreground"
          title={`${l.relation} → ${l.label}`}
        >
          <span className="uppercase tracking-wide text-muted-foreground/70">{humanize(l.relation)}</span>
          <ArrowRight className="h-2.5 w-2.5" />
          <span className="max-w-[10rem] truncate text-foreground/80">{l.label}</span>
        </span>
      ))}
    </div>
  );
}

// ============================================================================
// Revisions — the "AI Git" timeline + comparison
// ============================================================================

function RevisionsView({
  organizationId,
  type,
  entityId,
}: {
  organizationId: string;
  type: string;
  entityId: string;
}) {
  const { data, isLoading, isError } = useArtifactRevisions(organizationId, type, entityId);
  const revisions = useMemo(() => data?.revisions ?? [], [data]);
  const [baseId, setBaseId] = useState<string | undefined>(undefined);
  const [targetId, setTargetId] = useState<string | undefined>(undefined);

  // Default the comparison to (previous → newest) once revisions load.
  const effBase = baseId ?? (revisions.length > 1 ? revisions[1].id : undefined);
  const effTarget = targetId ?? (revisions.length > 0 ? revisions[0].id : undefined);

  const { data: comparison } = useRevisionComparison(organizationId, type, entityId, effBase, effTarget);

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  if (isError || !data || revisions.length === 0) {
    return (
      <EmptyState
        icon={History}
        title="No engineering revisions yet"
        description="This artifact has no versioned revisions to reason over. Datasets, prompts and agents build a revision timeline as new versions are created."
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* AI Git leads with how this artifact evolved — the counts are receipts inside the sentence. */}
      <VerdictBanner verdict={revisionVerdict(revisions, data.promotions, data.artifact.name)} />

      <section className="space-y-2">
        <SectionHeader icon={History} title="Revision timeline" subtitle="Every engineering change, newest first" />
        <Card>
          <CardContent className="space-y-4 p-4">
            {revisions.map((rev, i) => (
              <RevisionRow key={rev.id} rev={rev} index={revisions.length - i} />
            ))}
          </CardContent>
        </Card>
      </section>

      {revisions.length > 1 && (
        <section className="space-y-2">
          <SectionHeader icon={GitCompare} title="Compare revisions" subtitle="What changed between two revisions" />
          <div className="flex flex-wrap items-center gap-2">
            <RevisionSelect value={effBase} onChange={setBaseId} revisions={revisions} placeholder="Base" />
            <ArrowRight className="h-4 w-4 text-muted-foreground" />
            <RevisionSelect value={effTarget} onChange={setTargetId} revisions={revisions} placeholder="Target" />
          </div>
          {effBase === effTarget ? (
            <p className="px-1 text-xs text-muted-foreground">Choose two different revisions to see the diff.</p>
          ) : comparison ? (
            <DiffTable diffs={comparison.diffs} />
          ) : (
            <Skeleton className="h-32 w-full" />
          )}
        </section>
      )}
    </div>
  );
}

/**
 * The AI Git summary — how this artifact evolved, stated as engineering history rather than a version count.
 * Detects the rollback case (active revision is not the newest) because that is the single most consequential
 * thing a revision timeline can tell an engineer (L-82).
 */
function revisionVerdict(revisions: EngineeringRevision[], promotions: number, name: string): Verdict {
  const activeIndex = revisions.findIndex((r) => r.active);
  const active = activeIndex >= 0 ? revisions[activeIndex] : null;
  const newest = revisions[0];
  const withRationale = revisions.filter((r) => !!r.rationale).length;
  const missingRationale = revisions.length - withRationale;

  // L-29: a revision without rationale is an incomplete object; make the gap conspicuous.
  const rationaleNote =
    missingRationale > 0
      ? ` ${plural(missingRationale, "revision")} ${missingRationale === 1 ? "has" : "have"} no recorded rationale — the reasoning behind ${missingRationale === 1 ? "it" : "them"} is lost unless someone remembers it.`
      : " Every revision records why it was made.";

  if (activeIndex > 0 && active) {
    return {
      state: "attention",
      headline: `${name} is running ${active.label} even though ${newest.label} exists.`,
      consequence: `${newest.label} was rolled past — production deliberately sits on an earlier revision.${rationaleNote}`,
      status: "derived",
      provenance: { basis: `${plural(revisions.length, "recorded revision")}` },
    };
  }

  if (!active) {
    return {
      state: "unknown",
      headline: `${name} has ${plural(revisions.length, "revision")} but none is promoted.`,
      consequence: `Nothing is marked as the canonical revision, so there is no production truth to reason about.${rationaleNote}`,
      status: "derived",
      provenance: { basis: `${plural(revisions.length, "recorded revision")}` },
    };
  }

  return {
    state: "healthy",
    headline: `${name} evolved through ${plural(revisions.length, "revision")} to ${active.label}.`,
    consequence: `${plural(promotions, "revision")} reached production${
      revisions.length > 1 ? `, superseding ${plural(revisions.length - 1, "earlier revision")}` : ""
    }.${rationaleNote}`,
    status: "derived",
    provenance: { basis: "this artifact's immutable version records" },
  };
}

function RevisionRow({ rev, index }: { rev: EngineeringRevision; index: number }) {
  return (
    <div className="flex items-start gap-3">
      <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-border text-[10px] text-muted-foreground">
        {index}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium text-foreground">{rev.label}</span>
          {rev.active && <Badge variant="success" className="text-[10px]">Active</Badge>}
          {rev.rollbackReady && (
            <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
              <RotateCcw className="h-3 w-3" /> rollback-ready
            </span>
          )}
          <span className="text-xs text-muted-foreground">{formatDateTime(rev.at)}</span>
        </div>
        {rev.detail && <p className="text-xs text-muted-foreground">{rev.detail}</p>}
        {rev.rationale && (
          <p className="mt-1 border-l-2 border-border pl-2 text-xs italic text-muted-foreground/90">“{rev.rationale}”</p>
        )}
      </div>
    </div>
  );
}

function RevisionSelect({
  value,
  onChange,
  revisions,
  placeholder,
}: {
  value: string | undefined;
  onChange: (v: string) => void;
  revisions: EngineeringRevision[];
  placeholder: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-40"><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent>
        {revisions.map((r) => (
          <SelectItem key={r.id} value={r.id}>
            {r.label}
            {r.active ? " · active" : ""}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function DiffTable({ diffs }: { diffs: { field: string; before: string | null; after: string | null; change: string }[] }) {
  const changed = diffs.filter((d) => d.change !== "unchanged");
  const unchanged = diffs.filter((d) => d.change === "unchanged");
  if (changed.length === 0) {
    return <p className="px-1 text-xs text-muted-foreground">These revisions are identical across all recorded fields.</p>;
  }
  return (
    <Card>
      <CardContent className="divide-y divide-border p-0">
        {changed.map((d) => (
          <DiffRow key={d.field} diff={d} />
        ))}
        {unchanged.length > 0 && (
          <p className="p-3 text-[11px] text-muted-foreground/70">
            {unchanged.length} field{unchanged.length === 1 ? "" : "s"} unchanged
            {" · "}
            {unchanged.map((d) => d.field).join(", ")}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function DiffRow({ diff }: { diff: { field: string; before: string | null; after: string | null; change: string } }) {
  const tone =
    diff.change === "added"
      ? "success"
      : diff.change === "removed"
        ? "destructive"
        : "warning";
  return (
    <div className="space-y-1.5 p-3">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium text-foreground">{humanize(diff.field)}</span>
        <Badge variant={tone} className="text-[10px] uppercase">{diff.change}</Badge>
      </div>
      <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
        <DiffCell label="Before" value={diff.before} muted />
        <DiffCell label="After" value={diff.after} />
      </div>
    </div>
  );
}

function DiffCell({ label, value, muted }: { label: string; value: string | null; muted?: boolean }) {
  return (
    <div className="rounded-md border border-border bg-muted/30 p-2">
      <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={cn("mt-0.5 whitespace-pre-wrap break-words text-xs", muted ? "text-muted-foreground" : "text-foreground")}>
        {value == null || value === "" ? "—" : value}
      </p>
    </div>
  );
}

// ============================================================================
// Shared
// ============================================================================

function OutcomeBadge({ outcome }: { outcome: string }) {
  const variant =
    outcome === "COMPLETED" ? "success" : outcome === "FAILED" || outcome === "CANCELLED" ? "destructive" : "muted";
  return <Badge variant={variant} className="shrink-0 text-[10px] uppercase">{humanize(outcome)}</Badge>;
}

function SectionHeader({
  icon: Icon,
  title,
  subtitle,
}: {
  icon: typeof Gavel;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <Icon className="h-4 w-4 text-primary" />
      <h2 className="text-sm font-semibold text-foreground">{title}</h2>
      <span className="text-xs text-muted-foreground">· {subtitle}</span>
    </div>
  );
}

export { ArtifactIntelligence as default };
