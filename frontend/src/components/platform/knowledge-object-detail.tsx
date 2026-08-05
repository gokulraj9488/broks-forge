"use client";

import Link from "next/link";
import {
  ArrowLeft,
  ArrowUpRight,
  Boxes,
  Clock,
  GitBranch,
  HelpCircle,
  Network,
  Route,
  Sparkles,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { VerdictBanner } from "@/components/platform/verdict";
import { plural, type Verdict } from "@/lib/verdict";
import { useArtifactIntelligence, useKnowledgeObject } from "@/lib/hooks/use-intelligence";
import type { KnowledgeLink, KnowledgeObject } from "@/lib/api/platform";
import type { InfoFeature } from "@/lib/intelligence-info";
import { artifactHref, knowledgeHref } from "@/lib/artifact-links";
import { knowledgeKindMeta, knowledgeKindOf } from "@/lib/knowledge-meta";
import { humanize } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

const ARTIFACT_TYPES = ["agent", "prompt", "dataset", "evaluation", "provider", "project"];

/** Resolve a knowledge link to a page: another knowledge object, or an artifact's engineering workspace. */
function linkHref(org: string, link: KnowledgeLink, fallbackProject: string | null): string | null {
  if (knowledgeKindOf(link.id)) return knowledgeHref(org, link.id);
  const idx = link.id.indexOf(":");
  if (idx > 0) {
    const t = link.id.slice(0, idx);
    const uuid = link.id.slice(idx + 1);
    if (ARTIFACT_TYPES.includes(t)) {
      return artifactHref(org, t, uuid, fallbackProject, { tab: "intelligence" });
    }
  }
  return null;
}

/** The question this kind of knowledge object most invites — a decision asks for its evidence, a claim for
 * whether anything contradicts it, an observation for the run behind it. */
function brokQuestionFor(type: string, title: string): string {
  switch (type) {
    case "decision":
      return "Which evaluations support this decision?";
    case "claim":
      return "Show contradictions in our engineering knowledge.";
    case "observation":
    case "evidence":
      return "Explain this evaluation.";
    default:
      return `Explain ${title}.`;
  }
}

export function KnowledgeObjectDetail({ organizationId, id }: { organizationId: string; id: string }) {
  const { data: object, isLoading, isError } = useKnowledgeObject(organizationId, id);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError || !object) {
    return (
      <EmptyState
        icon={Network}
        title="Knowledge object not found"
        description="It may not exist for this organization, or the platform isn't available for this workspace."
      />
    );
  }

  const meta = knowledgeKindMeta(object.type);
  const Icon = meta.icon;
  const artifactWorkspace = artifactHref(organizationId, object.artifactType, object.artifactEntityId, object.projectId, {
    tab: "intelligence",
  });

  return (
    <div className="space-y-6">
      <Link
        href="/registry"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to registry
      </Link>

      {/* Identity */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className={cn("flex h-12 w-12 items-center justify-center rounded-xl border bg-card", meta.ring)}>
            <Icon className={cn("h-6 w-6", meta.color)} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <Badge variant="muted" className="text-[10px] uppercase">{meta.label}</Badge>
              {object.outcome && <Badge variant="muted" className="text-[10px] uppercase">{humanize(object.outcome)}</Badge>}
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <Clock className="h-3 w-3" />
                {formatDateTime(object.at)}
              </span>
            </div>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight">{object.title}</h1>
            <p className="text-sm text-muted-foreground">{meta.blurb}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <AskBrok
            organizationId={organizationId}
            projectId={object.projectId}
            focus={object.id}
            question={brokQuestionFor(object.type, object.title)}
          />
          <InfoButton feature={object.type as InfoFeature} label="What is this?" />
        </div>
      </div>

      {/* Why this exists, why it matters, and what it was derived from — before any structure (P-1). */}
      <VerdictBanner verdict={knowledgeVerdict(object, meta.label)} />

      {/* What an engineer should do with it. */}
      <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/30 px-4 py-3">
        <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <div className="space-y-0.5 text-sm">
          <p className="font-medium text-foreground">Why this matters</p>
          <p className="text-muted-foreground">{whyItMatters(object.type)}</p>
        </div>
      </div>

      {object.rationale && (
        <Card>
          <CardContent className="space-y-1 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Engineering rationale</p>
            <p className="border-l-2 border-border pl-3 text-sm italic text-foreground/90">“{object.rationale}”</p>
          </CardContent>
        </Card>
      )}

      {/* How this connects — the object's own links, grouped by relation */}
      {object.links.length > 0 && (
        <section className="space-y-2">
          <SectionHeader icon={Route} title="How this connects" subtitle="What created it and what it relates to" />
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {object.links.map((l, i) => (
                <LinkRow key={`${l.id}-${i}`} organizationId={organizationId} link={l} fallbackProject={object.projectId} />
              ))}
            </CardContent>
          </Card>
        </section>
      )}

      {/* Affected artifact workspace */}
      {artifactWorkspace && (
        <section className="space-y-2">
          <SectionHeader icon={Boxes} title="Affected artifact" subtitle="Open its engineering workspace" />
          <Link
            href={artifactWorkspace}
            className="flex items-center justify-between rounded-lg border border-border bg-card px-4 py-3 transition-colors hover:bg-muted/40"
          >
            <div className="flex items-center gap-2">
              <Badge variant="muted" className="text-[10px] uppercase">{object.artifactType}</Badge>
              <span className="text-sm font-medium text-foreground">Open the {object.artifactType}&rsquo;s workspace</span>
            </div>
            <ArrowUpRight className="h-4 w-4 text-muted-foreground" />
          </Link>
        </section>
      )}

      {/* The rest of the artifact's intelligence, for rich exploration */}
      <RelatedIntelligence
        organizationId={organizationId}
        artifactType={object.artifactType}
        entityId={object.artifactEntityId}
        currentId={object.id}
        workspaceHref={artifactWorkspace}
      />
    </div>
  );
}

/**
 * A knowledge object's own verdict: what it says, and on what footing. Everything on this page is derived,
 * so the epistemic status is stated plainly and the provenance names the real sources it came from (P-2).
 */
function knowledgeVerdict(object: KnowledgeObject, kindLabel: string): Verdict {
  const sources = object.links.length;
  const supporting = object.links.filter((l) => l.relation === "supportedBy" || l.relation === "informedBy").length;

  return {
    state: object.outcome === "FAILED" || object.outcome === "CANCELLED" ? "attention" : "healthy",
    headline: object.summary,
    consequence: `This ${kindLabel.toLowerCase()} was not written by anyone — the platform derived it from what your engineering actually did${
      supporting > 0 ? `, and ${plural(supporting, "piece")} of evidence stand behind it` : ""
    }.`,
    status: "derived",
    provenance: {
      basis: sources > 0 ? `${plural(sources, "linked engineering record")}` : "the artifact's engineering history",
    },
  };
}

/** Constitutional teaching copy (L-11): every derived object explains why an engineer should care. */
function whyItMatters(type: string): string {
  switch (type) {
    case "decision":
      return "Decisions are how your organization remembers its judgment. Months from now, this is the answer to \"why is it built this way?\" — without it, that reasoning is lost with the people who made it.";
    case "claim":
      return "A claim is an assertion your engineering is standing on. If it is wrong, everything built on top of it inherits the mistake — so it always shows the evidence supporting it.";
    case "evidence":
      return "Evidence is what separates an engineering conclusion from an opinion. This is the receipt behind a decision — the thing you can point at in a review.";
    case "observation":
      return "Observations are the measured facts everything else is built from. They are raw ground truth: no interpretation, no inference, just what the platform actually recorded.";
    case "knowledge":
      return "Knowledge is what your organization now knows and no longer has to rediscover. It exists only where a real decision met real evidence — which is why it can be trusted.";
    default:
      return "This object is part of your organization's engineering reasoning, derived from real acts.";
  }
}

function LinkRow({
  organizationId,
  link,
  fallbackProject,
}: {
  organizationId: string;
  link: KnowledgeLink;
  fallbackProject: string | null;
}) {
  const kind = knowledgeKindOf(link.id);
  const meta = kind ? knowledgeKindMeta(kind) : null;
  const Icon = meta?.icon ?? Boxes;
  const href = linkHref(organizationId, link, fallbackProject);
  const body = (
    <div className="flex items-center gap-3 p-3">
      <Icon className={cn("h-4 w-4 shrink-0", meta?.color ?? "text-muted-foreground")} />
      <span className="w-28 shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">{humanize(link.relation)}</span>
      <span className="min-w-0 flex-1 truncate text-sm text-foreground">{link.label}</span>
      {href && <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />}
    </div>
  );
  return href ? (
    <Link href={href} className="block hover:bg-muted/40">{body}</Link>
  ) : (
    <div>{body}</div>
  );
}

function RelatedIntelligence({
  organizationId,
  artifactType,
  entityId,
  currentId,
  workspaceHref,
}: {
  organizationId: string;
  artifactType: string;
  entityId: string | null;
  currentId: string;
  workspaceHref: string | null;
}) {
  const { data } = useArtifactIntelligence(
    organizationId,
    entityId ? artifactType : undefined,
    entityId ?? undefined,
  );
  if (!data) return null;

  const groups: { title: string; objects: KnowledgeObject[] }[] = [
    { title: "Decisions", objects: data.decisions },
    { title: "Claims", objects: data.claims },
    { title: "Evidence", objects: data.evidence },
    { title: "Observations", objects: data.observations },
    { title: "Knowledge", objects: data.knowledge },
  ]
    .map((g) => ({ title: g.title, objects: g.objects.filter((o) => o.id !== currentId) }))
    .filter((g) => g.objects.length > 0);

  const hasMemory = data.memory.length > 0;
  if (groups.length === 0 && !hasMemory) return null;

  return (
    <section className="space-y-3">
      <SectionHeader icon={Network} title="Related engineering intelligence" subtitle="Explore the surrounding reasoning" />

      {hasMemory && (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {data.memory.map((m) => (
              <div key={m.decisionId} className="space-y-0.5 p-3">
                <p className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <HelpCircle className="h-3.5 w-3.5 shrink-0 text-primary" />
                  {m.question}
                </p>
                <p className="pl-5 text-sm text-muted-foreground">{m.answer}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {groups.map((g) => (
          <div key={g.title} className="min-w-0 space-y-1.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground [overflow-wrap:anywhere]">{g.title}</p>
            <Card>
              <CardContent className="divide-y divide-border p-0">
                {g.objects.map((o) => (
                  <RelatedRow key={o.id} organizationId={organizationId} object={o} />
                ))}
              </CardContent>
            </Card>
          </div>
        ))}
      </div>

      {workspaceHref && (
        <div className="flex flex-wrap gap-2">
          <Link
            href={workspaceHref}
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-secondary px-3 py-1.5 text-xs font-medium text-secondary-foreground transition-colors hover:bg-secondary/80"
          >
            <GitBranch className="h-3.5 w-3.5" /> Intelligence &amp; AI Git
          </Link>
          <Link
            href="/knowledge"
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-secondary px-3 py-1.5 text-xs font-medium text-secondary-foreground transition-colors hover:bg-secondary/80"
          >
            <Network className="h-3.5 w-3.5" /> View in graph
          </Link>
        </div>
      )}
    </section>
  );
}

function RelatedRow({ organizationId, object }: { organizationId: string; object: KnowledgeObject }) {
  const meta = knowledgeKindMeta(object.type);
  const Icon = meta.icon;
  return (
    <Link href={knowledgeHref(organizationId, object.id)} className="block hover:bg-muted/40">
      <div className="flex items-center gap-3 p-3">
        <Icon className={cn("h-4 w-4 shrink-0", meta.color)} />
        <span className="min-w-0 flex-1 truncate text-sm text-foreground">{object.title}</span>
        <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
      </div>
    </Link>
  );
}

function SectionHeader({
  icon: Icon,
  title,
  subtitle,
}: {
  icon: typeof Network;
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
