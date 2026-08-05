"use client";

import Link from "next/link";
import {
  ArrowDownRight,
  ArrowUpRight,
  Clock,
  FlaskConical,
  GitBranch,
  Network,
  ShieldCheck,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { VerdictBanner } from "@/components/platform/verdict";
import { DeploymentTimeline } from "@/components/platform/deployment-timeline";
import { useArtifactEvolution } from "@/lib/hooks/use-evolution";
import { plural, type Verdict } from "@/lib/verdict";
import { substrateMeta } from "@/lib/substrate";
import type { ArtifactEvolution as Evolution, EvolutionEvidence, EvolutionRef, EvolutionRevision } from "@/lib/api/platform";
import { artifactHref } from "@/lib/artifact-links";
import { humanize } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

// Structural identity comes from the one grammar (lib/substrate.ts).
const typeMeta = substrateMeta;

export function ArtifactEvolution({
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
  const { data, isLoading, isError } = useArtifactEvolution(organizationId, type, entityId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
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
        title="Engineering evolution unavailable"
        description="This artifact's evolution isn't available for this workspace yet."
      />
    );
  }

  const empty =
    data.dependencies.length === 0 &&
    data.dependents.length === 0 &&
    data.history.length === 0 &&
    data.evidence.length === 0;

  return (
    <div className="space-y-6">
      {/* Evolution opens with what a change here would mean — impact before inventory (P-1, L-78). */}
      <VerdictBanner
        verdict={evolutionVerdict(data)}
        action={
          <div className="flex items-center gap-1.5">
            {/* Evolution shows the lineage; Brok weighs what to do with it. */}
            <AskBrok
              organizationId={organizationId}
              projectId={projectId}
              focus={data.artifact.id}
              question={`Show every artifact affected by ${data.artifact.name}.`}
            />
            <Link
              href={`/knowledge?focus=${encodeURIComponent(data.artifact.id)}`}
              className="inline-flex items-center gap-1.5 rounded-md border border-border bg-card px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              <Network className="h-3.5 w-3.5" />
              See in graph
            </Link>
            <InfoButton feature="evolution" label="" />
          </div>
        }
      />

      {(type === "agent" || type === "prompt" || type === "dataset") && (
        <DeploymentTimeline
          organizationId={organizationId}
          projectId={projectId}
          name={data.artifact.name}
          type={type}
          entityId={entityId}
        />
      )}

      {empty ? (
        <EmptyState
          icon={Network}
          title="No relationships or history yet"
          description="As this artifact is used, versioned and evaluated, its engineering evolution will appear here."
        />
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <RefSection
            title="Where it came from"
            subtitle="What this artifact depends on"
            icon={ArrowUpRight}
            organizationId={organizationId}
            refs={data.dependencies}
          />
          <RefSection
            title="What it influences"
            subtitle="What depends on this artifact"
            icon={ArrowDownRight}
            organizationId={organizationId}
            refs={data.dependents}
          />
          {data.history.length > 0 && <HistorySection revisions={data.history} />}
          {data.evidence.length > 0 && (
            <EvidenceSection organizationId={organizationId} evidence={data.evidence} />
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Evolution's engineering summary — the blast radius stated as a sentence before any list of relationships.
 * This is the question an engineer actually arrives with: *what will I break if I change this?*
 */
function evolutionVerdict(data: Evolution): Verdict {
  const name = data.artifact.name;
  const direct = data.dependents.length;
  const transitive = data.impactCount;
  const deps = data.dependencies.length;
  const evidence = data.evidence.length;

  const evidenceClause =
    evidence === 0
      ? " Nothing has evaluated it, so the effect of a change would be unmeasured."
      : ` ${plural(evidence, "evaluation")} would tell you whether a change here was safe.`;

  if (transitive === 0) {
    return {
      state: deps === 0 && evidence === 0 ? "unknown" : "healthy",
      headline: `Nothing depends on ${name} yet.`,
      consequence:
        deps > 0
          ? `It builds on ${plural(deps, "upstream artifact")}, but a change here affects nothing downstream — this is a safe artifact to change.${evidenceClause}`
          : `It stands alone: nothing feeds it and nothing depends on it.${evidenceClause}`,
      status: "derived",
      provenance: { basis: "the organization's engineering graph" },
    };
  }

  const state = transitive >= 5 ? "attention" : "healthy";
  return {
    state,
    headline: `Changing ${name} affects ${plural(transitive, "artifact")} downstream.`,
    consequence: `${plural(direct, "artifact depends", "artifacts depend")} on it directly${
      transitive > direct ? `, and ${plural(transitive - direct, "more")} further down the chain` : ""
    }.${evidenceClause}`,
    status: "derived",
    provenance: { basis: "real dependency relationships in the engineering graph" },
  };
}

function RefSection({
  title,
  subtitle,
  icon: Icon,
  organizationId,
  refs,
}: {
  title: string;
  subtitle: string;
  icon: typeof ArrowUpRight;
  organizationId: string;
  refs: EvolutionRef[];
}) {
  return (
    <section className="min-w-0 space-y-2">
      <SectionHeader icon={Icon} title={title} subtitle={subtitle} />
      {refs.length === 0 ? (
        <p className="px-1 text-xs text-muted-foreground">None.</p>
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {refs.map((r) => (
              <RefRow key={r.id} organizationId={organizationId} refItem={r} />
            ))}
          </CardContent>
        </Card>
      )}
    </section>
  );
}

function RefRow({ organizationId, refItem }: { organizationId: string; refItem: EvolutionRef }) {
  const meta = typeMeta(refItem.type);
  const Icon = meta.icon;
  const href = artifactHref(organizationId, refItem.type, refItem.entityId, refItem.projectId);
  const body = (
    <div className="flex items-center gap-3 p-3">
      <Icon className={cn("h-4 w-4 shrink-0", meta.color)} />
      <span className="min-w-0 flex-1 truncate text-sm text-foreground">{refItem.name}</span>
      {refItem.relation && (
        <Badge variant="muted" className="shrink-0 text-[10px] uppercase">{humanize(refItem.relation)}</Badge>
      )}
    </div>
  );
  return href ? (
    <Link href={href} className="block hover:bg-muted/40">{body}</Link>
  ) : (
    <div>{body}</div>
  );
}

function HistorySection({ revisions }: { revisions: EvolutionRevision[] }) {
  return (
    <section className="min-w-0 space-y-2">
      <SectionHeader icon={GitBranch} title="How it changed" subtitle="Historical revisions" />
      <Card>
        <CardContent className="space-y-3 p-4">
          {revisions.map((rev, i) => (
            <div key={`${rev.label}-${i}`} className="flex items-start gap-3">
              <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-border text-[10px] text-muted-foreground">
                {revisions.length - i}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-foreground">{rev.label}</span>
                  {rev.active && <Badge variant="success" className="text-[10px]">Active</Badge>}
                  <span className="text-xs text-muted-foreground">{formatDateTime(rev.at)}</span>
                </div>
                {rev.detail && <p className="truncate text-xs text-muted-foreground">{rev.detail}</p>}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </section>
  );
}

function EvidenceSection({
  organizationId,
  evidence,
}: {
  organizationId: string;
  evidence: EvolutionEvidence[];
}) {
  return (
    <section className="min-w-0 space-y-2">
      <SectionHeader icon={ShieldCheck} title="What supports it" subtitle="Evaluations providing evidence" />
      <Card>
        <CardContent className="divide-y divide-border p-0">
          {evidence.map((e) => {
            const href = artifactHref(organizationId, e.type, e.entityId, e.projectId);
            const body = (
              <div className="flex items-center gap-3 p-3">
                <FlaskConical className="h-4 w-4 shrink-0 text-rose-400" />
                <div className="min-w-0 flex-1">
                  <span className="block truncate text-sm text-foreground">{e.name}</span>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <Clock className="h-3 w-3" />
                    {formatDateTime(e.at)}
                  </span>
                </div>
                {e.outcome && <OutcomeBadge outcome={e.outcome} />}
              </div>
            );
            return href ? (
              <Link key={e.id} href={href} className="block hover:bg-muted/40">{body}</Link>
            ) : (
              <div key={e.id}>{body}</div>
            );
          })}
        </CardContent>
      </Card>
    </section>
  );
}

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
  icon: typeof ArrowUpRight;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Icon className="h-4 w-4 text-primary" />
      <h2 className="text-sm font-semibold text-foreground">{title}</h2>
      <span className="text-xs text-muted-foreground">· {subtitle}</span>
    </div>
  );
}
