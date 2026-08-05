"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  Brain,
  Clock,
  HelpCircle,
  Lightbulb,
  Network,
  Search,
  Sparkles,
  Target,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { ForgeGraph } from "@/components/platform/forge-graph";
import { InfoButton } from "@/components/platform/info-button";
import { EpistemicMark, VerdictBanner } from "@/components/platform/verdict";
import { BrokRefGroup } from "@/components/brok/brok-ref";
import { AskBrok } from "@/components/brok/ask-brok";
import { InvestigationCauses } from "@/components/investigation/investigation-causes";
import { InvestigationTimeline } from "@/components/investigation/investigation-timeline";
import { useInvestigation } from "@/lib/hooks/use-investigation";
import { brokActionHref } from "@/lib/brok-actions";
import {
  CONFIDENCE_WORD,
  type Confidence,
  type EpistemicStatus,
  type VerdictState,
} from "@/lib/verdict";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

/**
 * The Root Cause Explorer (P13) — the Engineering Investigation Workspace.
 *
 * <p>This is a war room, not a page of cards. One request assembles the whole investigation, and the layout
 * puts the three things an engineer needs simultaneously in one field of view: the chronology (what led
 * here), the causal chain (why, at four depths) and the engineering story (the questions that have to be
 * answered before anyone acts). The right rail holds the evidence, knowledge, decision and AI Git chains
 * plus the graph, so following a thread never costs the investigation.
 *
 * <p>Every visual element is borrowed rather than invented: the same VerdictBanner the Engineering Brief
 * uses, the same reference rows Brok uses, the same graph. That is what stops this from becoming a second
 * product — the Explorer is a new arrangement of the platform, not a new platform.
 */
export function InvestigationWorkspace({
  organizationId,
  projectId,
  evaluationId,
}: {
  organizationId: string;
  projectId?: string;
  evaluationId: string;
}) {
  const { data, isLoading, isError } = useInvestigation(organizationId, evaluationId, projectId);
  const [activeEventId, setActiveEventId] = useState<string | null>(null);
  const [focusNodeId, setFocusNodeId] = useState<string | null>(null);

  if (isLoading) {
    return <InvestigationSkeleton />;
  }
  if (isError || !data) {
    return (
      <EmptyState
        icon={Search}
        title="This investigation could not be assembled"
        description="The evaluation may not exist in this workspace, or it may belong to another organization."
      />
    );
  }

  const graphFocus = focusNodeId ?? data.context.graphNodeIds?.[0] ?? data.subject.id;

  return (
    <div className="space-y-5">
      {/* 1 — The finding, in the platform's one verdict language. */}
      <VerdictBanner
        verdict={{
          state: data.verdict.state as VerdictState,
          headline: data.verdict.headline,
          consequence: data.verdict.consequence,
          status: data.verdict.status as EpistemicStatus,
          provenance: { basis: data.verdict.basis },
        }}
        action={
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
              {CONFIDENCE_WORD[data.verdict.confidence as Confidence] ?? data.verdict.confidence}
            </span>
            <AskBrok
              organizationId={organizationId}
              projectId={projectId}
              focus={data.subject.id}
              question={`Why did ${data.subject.label} fail?`}
            />
            <InfoButton feature="root-cause" label="" />
          </div>
        }
      />

      {/* What this investigation actually read — the same audit trail Brok shows. */}
      <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 px-0.5 text-[11px] text-muted-foreground/80">
        <Search className="h-3 w-3 shrink-0" />
        {assembledLine(data)}
      </p>

      <div className="grid grid-cols-1 min-w-0 gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="min-w-0 space-y-5">
          {/* 2 — Why, at four depths. The centre of the investigation. */}
          <Card>
            <CardContent className="space-y-3 p-4">
              <SectionLabel icon={Target} text="Root cause" />
              <InvestigationCauses organizationId={organizationId} causes={data.causes} />
            </CardContent>
          </Card>

          {/* 3 — What led here. */}
          <Card>
            <CardContent className="space-y-3 p-4">
              <div className="flex items-center justify-between gap-2">
                <SectionLabel icon={Clock} text="Engineering timeline" />
                <span className="text-[11px] text-muted-foreground/70">
                  {data.timeline.length} event{data.timeline.length === 1 ? "" : "s"}, oldest first
                </span>
              </div>
              <InvestigationTimeline
                organizationId={organizationId}
                events={data.timeline}
                activeId={activeEventId}
                onSelect={(event) => {
                  setActiveEventId(event.id);
                  if (event.ref) {
                    setFocusNodeId(event.ref.id);
                  }
                }}
              />
            </CardContent>
          </Card>

          {/* 4 — The engineering story: the questions that must be answered before acting. */}
          <Card>
            <CardContent className="space-y-3 p-4">
              <SectionLabel icon={HelpCircle} text="The engineering story" />
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {data.story.map((line) => (
                  <div key={line.question} className="space-y-0.5 border-l border-border pl-3">
                    <dt className="text-xs font-semibold text-foreground">
                      {line.question}
                      {line.status !== "derived" && (
                        <EpistemicMark
                          status={line.status as EpistemicStatus}
                          className="ml-1.5 align-middle"
                        />
                      )}
                    </dt>
                    <dd className="text-xs leading-relaxed text-muted-foreground">{line.answer}</dd>
                    <dd className="text-[10px] text-muted-foreground/60">Read from {line.basis}</dd>
                  </div>
                ))}
              </dl>
            </CardContent>
          </Card>

          {/* 5 — What to do, each continuing into a real workflow. */}
          {data.recommendations.length > 0 && (
            <Card>
              <CardContent className="space-y-3 p-4">
                <SectionLabel icon={Lightbulb} text="What to do" />
                <div className="space-y-2">
                  {data.recommendations.map((rec, i) => {
                    const href = rec.action ? brokActionHref(organizationId, rec.action) : null;
                    return (
                      <div
                        key={`${data.id}-rec-${i}`}
                        className="rounded-lg border border-border bg-background p-3 transition-colors hover:border-primary/30"
                      >
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <p className="min-w-0 flex-1 text-sm font-medium text-foreground">
                            {rec.title}
                          </p>
                          <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
                            {CONFIDENCE_WORD[rec.confidence as Confidence] ?? rec.confidence}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">{rec.why}</p>
                        <p className="mt-1 text-xs text-foreground/80">{rec.impact}</p>
                        {href && rec.action && (
                          <Link
                            href={href}
                            className="mt-2.5 inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-primary/50"
                          >
                            {rec.action.label}
                            <ArrowUpRight className="h-3 w-3" />
                          </Link>
                        )}
                      </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          )}

          {/* 6 — The investigation continues as a conversation. */}
          {data.followUps.length > 0 && (
            <Card>
              <CardContent className="space-y-2.5 p-4">
                <SectionLabel icon={Sparkles} text="Continue in Brok" />
                <div className="flex flex-wrap gap-2">
                  {data.followUps.map((f, i) => (
                    <AskBrok
                      key={`${data.id}-f-${i}`}
                      organizationId={organizationId}
                      projectId={projectId}
                      focus={f.focus}
                      question={f.question}
                      label={f.question}
                    />
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>

        {/* ---------------- The chains ---------------- */}
        <aside className="space-y-4 lg:sticky lg:top-4 lg:self-start">
          <Card>
            <CardContent className="space-y-2.5 p-3.5">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Target className="h-3 w-3" />
                Engineering impact
              </p>
              <p className="text-xs text-foreground">{data.impact.statement}</p>
            </CardContent>
          </Card>

          {data.memory.length > 0 && (
            <Card>
              <CardContent className="space-y-2.5 p-3.5">
                <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  <Brain className="h-3 w-3" />
                  Engineering memory
                </p>
                {data.memory.map((m) => (
                  <div key={m.decisionId} className="space-y-0.5 border-l border-border pl-2.5">
                    <p className="text-xs font-medium text-foreground">{m.question}</p>
                    <p className="text-[11px] text-muted-foreground">{m.answer}</p>
                    <p className="text-[10px] text-muted-foreground/70">{formatDateTime(m.at)}</p>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent className="space-y-4 p-3.5">
              <BrokRefGroup
                organizationId={organizationId}
                title="Evidence chain"
                refs={data.references.evidence}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Precedents"
                refs={data.references.precedents}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Artifacts"
                refs={data.references.artifacts}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="AI Git chain"
                refs={data.references.revisions}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Decisions"
                refs={data.references.decisions}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Knowledge chain"
                refs={data.references.knowledge}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Related evaluations"
                refs={data.references.relatedEvaluations}
                onFocus={setFocusNodeId}
                activeId={focusNodeId}
              />
            </CardContent>
          </Card>

          <div className="space-y-1.5">
            <p className="flex items-center gap-1.5 px-0.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <Network className="h-3 w-3" />
              Graph focus
            </p>
            <ForgeGraph
              organizationId={organizationId}
              compact
              height={240}
              focusNodeId={graphFocus}
              onNodeSelect={setFocusNodeId}
            />
            <p className="px-0.5 text-[11px] text-muted-foreground/80">
              Selecting a record here — or an event on the timeline — moves the graph with it.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}

/** "Assembled from 6 evaluations · 4 AI Git revisions · 2 precedents" — counted, never asserted. */
function assembledLine(data: {
  references: {
    evidence: unknown[];
    revisions: unknown[];
    precedents: unknown[];
    knowledge: unknown[];
    decisions: unknown[];
    relatedEvaluations: unknown[];
  };
  timeline: unknown[];
  memory: unknown[];
}): string {
  const parts: string[] = [];
  const add = (n: number, word: string) => {
    if (n > 0) {
      parts.push(`${n} ${word}${n === 1 ? "" : "s"}`);
    }
  };
  add(data.timeline.length, "engineering event");
  add(data.references.evidence.length, "evidence record");
  add(data.references.revisions.length, "AI Git revision");
  add(data.references.precedents.length, "precedent");
  add(data.references.decisions.length, "decision");
  add(data.references.knowledge.length, "knowledge record");
  add(data.references.relatedEvaluations.length, "related evaluation");
  if (data.memory.length > 0) {
    parts.push("engineering memory");
  }
  return parts.length > 0 ? `Assembled from ${parts.join(" · ")}` : "Assembled from this evaluation alone";
}

function SectionLabel({ icon: Icon, text }: { icon: typeof Target; text: string }) {
  return (
    <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
      <Icon className="h-3 w-3" />
      {text}
    </p>
  );
}

/**
 * The loading state names the assembly work in progress rather than showing an empty box — the same
 * principle as Brok's investigation trace: a wait should tell you what is being done.
 */
function InvestigationSkeleton() {
  return (
    <div className="space-y-5" role="status" aria-live="polite">
      <div className="flex items-center gap-2 rounded-xl border border-border bg-muted/20 px-3.5 py-3 text-xs text-foreground">
        <Search className="h-3.5 w-3.5 shrink-0 animate-pulse" />
        Assembling the investigation — runs, revisions, precedents, knowledge and memory…
      </div>
      <div className="grid grid-cols-1 min-w-0 gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="space-y-5">
          <Skeleton className="h-52 w-full rounded-xl" />
          <Skeleton className="h-72 w-full rounded-xl" />
        </div>
        <div className="space-y-4">
          <Skeleton className="h-24 w-full rounded-xl" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      </div>
    </div>
  );
}

/** Kept adjacent to the workspace so the two stay visually consistent if either changes. */
export const INVESTIGATION_SKELETON_CLASS = cn("space-y-5");
