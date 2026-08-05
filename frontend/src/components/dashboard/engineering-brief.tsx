"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  Clock,
  FlaskConical,
  Gavel,
  Lightbulb,
  Network,
  Sparkles,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { VerdictBanner, VerdictChip, VerdictLine } from "@/components/platform/verdict";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { useDashboard } from "@/lib/hooks/use-dashboard";
import { usePlatformHealth } from "@/lib/hooks/use-platform-health";
import { useKnowledgeCatalog } from "@/lib/hooks/use-intelligence";
import type { KnowledgeObject } from "@/lib/api/platform";
import { knowledgeHref } from "@/lib/artifact-links";
import { knowledgeKindMeta } from "@/lib/knowledge-meta";
import { plural, verdictOfJobStatus, worseOf, type Verdict, type VerdictState } from "@/lib/verdict";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

/**
 * The Engineering Brief — Volume III §13.1.
 *
 * This is the product's thesis statement, and the surface most people will judge Broks Forge by. It is NOT a
 * dashboard: no widget grid, no counters above the fold, no time picker. It is a briefing — the thing a chief
 * of staff hands you at 9am:
 *
 *   1. System verdict — one sentence about the state of your engineering.
 *   2. Production risk — a derived level with its reason, never a gauge.
 *   3. The attention queue — decisions needing a human, each with its consequence.
 *   4. What changed — the knowledge your engineering produced recently.
 *   5. Open the story — one affordance into the most consequential thing available.
 *
 * Everything here is derived from endpoints that already exist (dashboard, platform health, knowledge
 * catalog). Nothing is fabricated: where evidence is absent, the Brief says so (L-34).
 */
export function EngineeringBrief({
  organizationId,
  projectId,
  greeting,
}: {
  organizationId: string;
  projectId: string;
  greeting: string;
}) {
  const { data, isLoading } = useDashboard(organizationId, projectId);
  const { data: health } = usePlatformHealth(organizationId);
  const { data: knowledge } = useKnowledgeCatalog(organizationId, { size: 50, sort: "recent" });

  const brief = useMemo(
    () => composeBrief(data, health, knowledge?.content ?? [], organizationId, projectId),
    [data, health, knowledge, organizationId, projectId],
  );

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-24 w-full rounded-xl" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1 — Greeting is subordinate typography; it never appears without a verdict (§13.1 ruling). */}
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground">{greeting}</p>
        <VerdictBanner
          verdict={brief.system}
          headingLevel="h1"
          action={<InfoButton feature="engineering-intelligence" label="What am I looking at?" />}
        />
      </div>

      {/* 2 — Production risk, in words, with its reason. */}
      <Card>
        <CardContent className="flex flex-wrap items-start justify-between gap-3 p-4">
          <div className="min-w-0 flex-1 space-y-1 sm:min-w-[16rem]">
            <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Production risk</p>
            <div className="flex items-center gap-2">
              <VerdictChip state={brief.risk.state} label={brief.riskLabel} />
              <span className="text-sm text-foreground">{brief.risk.headline}</span>
            </div>
            {brief.risk.consequence && (
              <p className="text-xs text-muted-foreground">{brief.risk.consequence}</p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 3 — The attention queue: decisions, never notifications (X-1). */}
      <section className="space-y-2">
        <SectionHeader
          icon={Gavel}
          title="What needs you"
          subtitle={brief.attention.length === 0 ? "nothing right now" : plural(brief.attention.length, "open item")}
        />
        {brief.attention.length === 0 ? (
          <Card>
            <CardContent className="p-4">
              <VerdictLine
                verdict={{
                  state: brief.anyEvidence ? "healthy" : "unknown",
                  headline: brief.anyEvidence
                    ? "Nothing needs you."
                    : "Nothing needs you yet — but nothing has been evaluated either.",
                  consequence: brief.anyEvidence
                    ? "No evaluation is failing and no revision is waiting on a decision."
                    : "Run an evaluation and this brief will start telling you what your engineering is doing.",
                  status: "derived",
                }}
                size="sm"
              />
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {brief.attention.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-start gap-3 p-3.5 transition-colors hover:bg-muted/40"
                >
                  <VerdictLine verdict={item.verdict} size="sm" className="min-w-0 flex-1" />
                  <ArrowUpRight className="mt-1 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                </Link>
              ))}
            </CardContent>
          </Card>
        )}
      </section>

      {/* 4 — What your engineering learned recently. */}
      <section className="space-y-2">
        <SectionHeader
          icon={Lightbulb}
          title="What your engineering learned"
          subtitle={brief.recentKnowledge.length === 0 ? "no new knowledge yet" : "most recent first"}
        />
        {brief.recentKnowledge.length === 0 ? (
          <p className="px-1 text-xs text-muted-foreground">
            Knowledge appears here as your artifacts are promoted, evaluated and evidenced — always derived from
            real acts, never invented.
          </p>
        ) : (
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {brief.recentKnowledge.map((k) => {
                const meta = knowledgeKindMeta(k.type);
                const Icon = meta.icon;
                return (
                  <Link
                    key={k.id}
                    href={knowledgeHref(organizationId, k.id)}
                    className="flex items-center gap-3 p-3 transition-colors hover:bg-muted/40"
                  >
                    <Icon className={cn("h-4 w-4 shrink-0", meta.color)} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm text-foreground">{k.title}</p>
                      <p className="truncate text-xs text-muted-foreground">{k.summary}</p>
                    </div>
                    <span className="hidden shrink-0 items-center gap-1 text-[11px] text-muted-foreground sm:inline-flex">
                      <Clock className="h-3 w-3" />
                      {formatDateTime(k.at)}
                    </span>
                  </Link>
                );
              })}
            </CardContent>
          </Card>
        )}
      </section>

      {/* 5 — One way into the most consequential thing available. */}
      <div className="flex flex-wrap gap-2">
        <Link
          href={brief.storyHref}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
        >
          <Sparkles className="h-4 w-4" />
          {brief.storyLabel}
        </Link>
        <Link
          href="/knowledge"
          className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          <Network className="h-4 w-4" />
          See the engineering graph
        </Link>
        {/* The brief states the position; Brok is where you interrogate it. */}
        <AskBrok
          organizationId={organizationId}
          projectId={projectId}
          question="What should my team work on next?"
          label="Ask Brok"
          className="px-4 py-2 text-sm"
        />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Derivation — every sentence below traces to a real record (P-2)
// ---------------------------------------------------------------------------

interface AttentionItem {
  href: string;
  verdict: Verdict;
}

interface Brief {
  system: Verdict;
  risk: Verdict;
  riskLabel: string;
  attention: AttentionItem[];
  recentKnowledge: KnowledgeObject[];
  anyEvidence: boolean;
  storyHref: string;
  storyLabel: string;
}

type DashboardData = ReturnType<typeof useDashboard>["data"];
type HealthData = ReturnType<typeof usePlatformHealth>["data"];

function composeBrief(
  data: DashboardData,
  health: HealthData,
  knowledge: KnowledgeObject[],
  organizationId: string,
  projectId: string,
): Brief {
  const jobs = data?.recentJobs ?? [];
  const analytics = data?.analytics ?? null;
  const counts = data?.counts;
  const base = `/organizations/${organizationId}/projects/${projectId}`;

  const failing = jobs.filter((j) => j.status === "FAILED");
  const completed = jobs.filter((j) => j.status === "COMPLETED");
  const running = jobs.filter((j) => j.status === "RUNNING" || j.status === "PENDING");
  const anyEvidence = (analytics?.runCount ?? 0) > 0 || completed.length > 0;
  const hasArtifacts = !!counts && (counts.agents > 0 || counts.prompts > 0 || counts.datasets > 0);

  // ---- attention queue: decisions, ranked by consequence then certainty (§34.2) ----
  const attention: AttentionItem[] = [];
  for (const job of failing.slice(0, 4)) {
    attention.push({
      href: `${base}/evaluations/${job.id}?tab=execution`,
      verdict: {
        state: "failed",
        headline: `${job.name} failed and needs review.`,
        consequence:
          job.failedItems > 0
            ? `${plural(job.failedItems, "item")} did not complete. Open the execution graph to see where the chain broke.`
            : "Open the execution graph to see where the chain broke.",
        status: "derived",
        provenance: { basis: "the evaluation's recorded runs" },
      },
    });
  }
  const lowPass = analytics && analytics.runCount > 0 && analytics.passRate < 0.5;
  if (lowPass && attention.length < 5) {
    attention.push({
      href: "/analytics",
      verdict: {
        state: "risk",
        headline: `Quality is below half — ${Math.round(analytics!.passRate * 100)}% of runs are passing.`,
        consequence: "Evidence suggests a systemic problem rather than isolated failures.",
        status: "inferred",
        provenance: { basis: `${plural(analytics!.runCount, "run")} over ${analytics!.windowDays} days` },
      },
    });
  }
  if (health && (!health.chainValid || !health.integrityClean) && attention.length < 5) {
    attention.push({
      href: "/insights",
      verdict: {
        state: "risk",
        headline: "Part of the engineering record could not be verified.",
        consequence: "Conclusions drawn from the affected records are unproven until this is reconciled.",
        status: "derived",
        provenance: { basis: "the platform integrity scan" },
      },
    });
  }

  // ---- system verdict ----
  let system: Verdict;
  if (!hasArtifacts) {
    system = {
      state: "unknown",
      headline: "Your engineering system is empty.",
      consequence:
        "Register an agent, a prompt or a dataset and Broks Forge will begin recording what you build, what you decide, and what you learn.",
      status: "derived",
    };
  } else if (!anyEvidence) {
    system = {
      state: "unknown",
      headline: "Your engineering system has not been evaluated yet.",
      consequence:
        "There is no evidence to reason about, so nothing here is known to be healthy — run an evaluation to give the platform something real to observe.",
      status: "derived",
      provenance: { basis: "the absence of any recorded runs" },
    };
  } else if (failing.length > 0) {
    system = {
      state: "failed",
      headline:
        failing.length === 1
          ? "One evaluation is failing."
          : `${plural(failing.length, "evaluation")} are failing.`,
      consequence: "Until they pass, the quality of the affected artifacts is unproven.",
      status: "derived",
      provenance: { basis: "recent evaluation outcomes" },
    };
  } else if (lowPass) {
    system = {
      state: "risk",
      headline: "Your engineering system is running, but quality is low.",
      consequence: `${Math.round(analytics!.passRate * 100)}% of runs are passing across ${plural(analytics!.runCount, "run")}.`,
      status: "derived",
      provenance: { basis: `${analytics!.windowDays} days of recorded runs` },
    };
  } else {
    system = {
      state: "healthy",
      headline: "Your engineering system is healthy.",
      consequence: `${Math.round((analytics?.passRate ?? 1) * 100)}% of runs are passing and no evaluation is failing.`,
      status: "derived",
      provenance: { basis: `${plural(analytics?.runCount ?? completed.length, "recorded run")}` },
    };
  }

  // ---- production risk ----
  let riskState: VerdictState = "healthy";
  let riskLabel = "Low";
  let riskWhy = "No failing evaluations and no unverified records.";
  if (!anyEvidence) {
    riskState = "unknown";
    riskLabel = "Unknown";
    riskWhy = "Risk cannot be assessed without evidence.";
  } else {
    if (failing.length > 0) {
      riskState = worseOf(riskState, "failed");
      riskLabel = "High";
      riskWhy = `${plural(failing.length, "failing evaluation")} in the current workspace.`;
    } else if (lowPass) {
      riskState = worseOf(riskState, "risk");
      riskLabel = "Elevated";
      riskWhy = "Pass rate is below half across recent runs.";
    }
    if (health && (!health.chainValid || !health.integrityClean)) {
      riskState = worseOf(riskState, "risk");
      riskLabel = riskLabel === "Low" ? "Elevated" : riskLabel;
      riskWhy += " Part of the engineering record could not be verified.";
    }
  }

  const risk: Verdict = {
    state: riskState,
    headline: riskWhy,
    consequence:
      running.length > 0 ? `${plural(running.length, "evaluation")} still running — this may change.` : null,
    status: "derived",
    provenance: { basis: "evaluations and record integrity in this workspace" },
  };

  // ---- the story entry point: the most consequential thing available ----
  let storyHref = "/registry";
  let storyLabel = "Open the engineering story";
  if (failing.length > 0) {
    storyHref = `${base}/evaluations/${failing[0].id}?tab=execution`;
    storyLabel = `Investigate ${failing[0].name}`;
  } else if (knowledge.length > 0) {
    storyHref = `/registry`;
    storyLabel = "Explore what your engineering knows";
  } else if (jobs.length > 0) {
    storyHref = `${base}/evaluations/${jobs[0].id}?tab=intelligence`;
    storyLabel = `Review ${jobs[0].name}`;
  }

  return {
    system,
    risk,
    riskLabel,
    attention: attention.slice(0, 5),
    recentKnowledge: knowledge.slice(0, 5),
    anyEvidence,
    storyHref,
    storyLabel,
  };
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

export { FlaskConical as BriefIcon };
