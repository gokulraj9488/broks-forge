"use client";

import { VerdictBanner } from "@/components/platform/verdict";
import { Skeleton } from "@/components/ui/skeleton";
import { useAgents } from "@/lib/hooks/use-agents";
import { usePrompts } from "@/lib/hooks/use-prompts";
import { useDatasets } from "@/lib/hooks/use-datasets";
import { useProviders } from "@/lib/hooks/use-providers";
import { useBenchmarks } from "@/lib/hooks/use-benchmarks";
import { useEvaluationJobs } from "@/lib/hooks/use-evaluation-jobs";
import { useAnalytics } from "@/lib/hooks/use-analytics";
import { plural, type Verdict } from "@/lib/verdict";
import { formatCost, formatLatency } from "@/lib/format";

/**
 * The Engineering Summary for a collection surface.
 *
 * The V1 module pages (Agents, Prompts, Providers, Datasets, Evaluations, Benchmarks, Analytics) opened
 * directly onto tables — raw objects with no statement about what they mean. Under the Constitution every
 * surface must answer *what happened · why it matters · how healthy is it · what next* before exposing raw
 * information (P-1), and it must do so in the same voice as the rest of the product (P-7).
 *
 * Rather than redesigning seven pages, this component states each surface's condition in one sentence, using
 * the same VerdictBanner the Brief, Intelligence, Evolution and the graphs already use — so a legacy page and
 * a V2 page are indistinguishable. Every sentence is derived from data the page already loads; nothing new is
 * fetched from the backend, and where there is no evidence the summary says so rather than implying health
 * (L-34).
 */
export type SurfaceKind =
  | "agents"
  | "prompts"
  | "providers"
  | "datasets"
  | "evaluations"
  | "benchmarks"
  | "analytics";

export function SurfaceSummary({
  kind,
  organizationId,
  projectId,
}: {
  kind: SurfaceKind;
  organizationId: string;
  projectId: string;
}) {
  const enabled = { size: 100 };
  const agents = useAgents(kind === "agents" ? organizationId : undefined, projectId, enabled);
  const prompts = usePrompts(kind === "prompts" ? organizationId : undefined, projectId, enabled);
  const datasets = useDatasets(kind === "datasets" ? organizationId : undefined, projectId, enabled);
  const providers = useProviders(kind === "providers" ? organizationId : undefined, projectId, enabled);
  const benchmarks = useBenchmarks(kind === "benchmarks" ? organizationId : undefined, projectId, enabled);
  const jobs = useEvaluationJobs(kind === "evaluations" ? organizationId : undefined, projectId, enabled);
  const analytics = useAnalytics(kind === "analytics" ? organizationId : undefined, projectId, 30);

  const active = {
    agents,
    prompts,
    datasets,
    providers,
    benchmarks,
    evaluations: jobs,
    analytics,
  }[kind];

  if (active.isLoading) return <Skeleton className="h-20 w-full rounded-xl" />;
  // A surface that cannot report on itself says nothing rather than guessing (P-2).
  if (active.isError || !active.data) return null;

  const verdict = summarize(kind, {
    agents: agents.data?.content,
    prompts: prompts.data?.content,
    datasets: datasets.data?.content,
    providers: providers.data?.content,
    benchmarks: benchmarks.data?.content,
    jobs: jobs.data?.content,
    analytics: analytics.data,
  });

  return verdict ? <VerdictBanner verdict={verdict} /> : null;
}

// ---------------------------------------------------------------------------
// Derivation — one honest sentence per surface
// ---------------------------------------------------------------------------

interface SurfaceData {
  agents?: { healthStatus?: string | null; status?: string | null }[];
  prompts?: { status?: string | null; currentActiveVersionId?: string | null; latestVersionNumber?: number | null }[];
  datasets?: { status?: string | null; currentItemCount?: number | null }[];
  providers?: { healthStatus?: string | null }[];
  benchmarks?: { entryCount?: number | null }[];
  jobs?: { status?: string | null; failedItems?: number | null }[];
  analytics?: {
    runCount: number;
    jobCount: number;
    passRate: number;
    windowDays: number;
    totalCost?: number | null;
    avgLatencyMs?: number | null;
  } | null;
}

function summarize(kind: SurfaceKind, d: SurfaceData): Verdict | null {
  switch (kind) {
    case "agents":
      return agentsVerdict(d.agents ?? []);
    case "prompts":
      return promptsVerdict(d.prompts ?? []);
    case "datasets":
      return datasetsVerdict(d.datasets ?? []);
    case "providers":
      return providersVerdict(d.providers ?? []);
    case "benchmarks":
      return benchmarksVerdict(d.benchmarks ?? []);
    case "evaluations":
      return evaluationsVerdict(d.jobs ?? []);
    case "analytics":
      return analyticsVerdict(d.analytics ?? null);
    default:
      return null;
  }
}

function agentsVerdict(items: SurfaceData["agents"] & object[]): Verdict {
  const live = items.filter((a) => a.status !== "ARCHIVED");
  if (live.length === 0) {
    return {
      state: "unknown",
      headline: "No agents are registered in this project yet.",
      consequence:
        "An agent is an AI system Broks Forge can evaluate, version and reason about. Register one and everything it does becomes part of your engineering record.",
      status: "derived",
    };
  }
  const unhealthy = live.filter((a) => a.healthStatus === "UNHEALTHY" || a.healthStatus === "DEGRADED").length;
  const unknown = live.filter((a) => !a.healthStatus || a.healthStatus === "UNKNOWN").length;

  if (unhealthy > 0) {
    return {
      state: "failed",
      headline: `${plural(unhealthy, "agent")} of ${live.length} cannot be reached.`,
      consequence:
        "Evaluations against an unreachable agent will fail for infrastructure reasons, not quality ones — fix the connection before trusting any result.",
      status: "derived",
      provenance: { basis: "the most recent health check per agent" },
    };
  }
  if (unknown === live.length) {
    return {
      state: "unknown",
      headline: `${plural(live.length, "agent is", "agents are")} registered, but none has been health-checked.`,
      consequence: "Until a check runs, their reachability is unproven.",
      status: "derived",
      provenance: { basis: "the absence of any health check" },
    };
  }
  return {
    state: "healthy",
    headline: `All ${plural(live.length, "registered agent is", "registered agents are")} reachable.`,
    consequence:
      unknown > 0
        ? `${plural(unknown, "agent")} has not been checked yet.`
        : "Every agent responded to its last health check, so evaluation failures would be quality problems rather than connection problems.",
    status: "derived",
    provenance: { basis: "the most recent health check per agent" },
  };
}

function promptsVerdict(items: SurfaceData["prompts"] & object[]): Verdict {
  const live = items.filter((p) => p.status !== "ARCHIVED");
  if (live.length === 0) {
    return {
      state: "unknown",
      headline: "No prompts exist in this project yet.",
      consequence:
        "A prompt is versioned intent — every change is recorded with its rationale, so the reasoning behind your instructions is never lost.",
      status: "derived",
    };
  }
  const unpromoted = live.filter((p) => !p.currentActiveVersionId).length;
  if (unpromoted > 0) {
    return {
      state: "attention",
      headline: `${plural(unpromoted, "prompt has", "prompts have")} no promoted version.`,
      consequence: `Of ${plural(live.length, "prompt")}, ${unpromoted} ${unpromoted === 1 ? "has" : "have"} no canonical revision — there is no production truth for ${unpromoted === 1 ? "it" : "them"} to reason about.`,
      status: "derived",
      provenance: { basis: "each prompt's active version record" },
    };
  }
  return {
    state: "healthy",
    headline: `Every one of your ${plural(live.length, "prompt")} has a promoted version.`,
    consequence: "Each has a canonical revision, so its production behaviour is defined and comparable.",
    status: "derived",
    provenance: { basis: "each prompt's active version record" },
  };
}

function datasetsVerdict(items: SurfaceData["datasets"] & object[]): Verdict {
  const live = items.filter((d) => d.status !== "ARCHIVED");
  if (live.length === 0) {
    return {
      state: "unknown",
      headline: "No datasets exist in this project yet.",
      consequence:
        "A dataset is the ground truth your evaluations measure against. Without one, quality cannot be evidenced — only asserted.",
      status: "derived",
    };
  }
  const empty = live.filter((d) => !d.currentItemCount).length;
  const items_total = live.reduce((sum, d) => sum + (d.currentItemCount ?? 0), 0);
  if (empty > 0) {
    return {
      state: "attention",
      headline: `${plural(empty, "dataset")} of ${live.length} has no items.`,
      consequence: "An empty dataset cannot evidence anything — import a version before evaluating against it.",
      status: "derived",
      provenance: { basis: "each dataset's current version" },
    };
  }
  return {
    state: "healthy",
    headline: `${plural(live.length, "dataset")} ready, holding ${plural(items_total, "test case")}.`,
    consequence: "These are the ground truth your evaluations measure against.",
    status: "derived",
    provenance: { basis: "each dataset's current version" },
  };
}

function providersVerdict(items: SurfaceData["providers"] & object[]): Verdict {
  if (items.length === 0) {
    return {
      state: "unknown",
      headline: "No providers are configured in this project yet.",
      consequence:
        "A provider is how your agents reach a model. Register one and every call made through it becomes attributable and costed.",
      status: "derived",
    };
  }
  const healthy = items.filter((p) => p.healthStatus === "HEALTHY").length;
  const broken = items.filter((p) => p.healthStatus === "UNHEALTHY" || p.healthStatus === "DEGRADED").length;
  if (broken > 0) {
    return {
      state: "failed",
      headline: `${plural(broken, "provider is", "providers are")} not answering.`,
      consequence:
        "Every agent and evaluation routed through an unreachable provider will fail for infrastructure reasons. This is the first thing to fix.",
      status: "derived",
      provenance: { basis: "the most recent connection check per provider" },
    };
  }
  if (healthy === 0) {
    return {
      state: "unknown",
      headline: `${plural(items.length, "provider is", "providers are")} configured, but none has been checked.`,
      consequence: "Their reachability is unproven until a connection check runs.",
      status: "derived",
      provenance: { basis: "the absence of any connection check" },
    };
  }
  return {
    state: "healthy",
    headline: `${healthy} of ${plural(items.length, "provider is", "providers are")} answering.`,
    consequence: "Your agents can reach their models, so evaluation results reflect quality rather than connectivity.",
    status: "derived",
    provenance: { basis: "the most recent connection check per provider" },
  };
}

function benchmarksVerdict(items: SurfaceData["benchmarks"] & object[]): Verdict {
  if (items.length === 0) {
    return {
      state: "unknown",
      headline: "No benchmarks exist in this project yet.",
      consequence:
        "A benchmark is a shared standard your artifacts are measured against — the way you compare today's system to a known bar rather than to a feeling.",
      status: "derived",
    };
  }
  const entries = items.reduce((sum, b) => sum + (b.entryCount ?? 0), 0);
  const empty = items.filter((b) => !b.entryCount).length;
  return {
    state: empty > 0 ? "attention" : "healthy",
    headline:
      empty > 0
        ? `${plural(empty, "benchmark")} of ${items.length} has no entries.`
        : `${plural(items.length, "benchmark")} holding ${plural(entries, "entry", "entries")}.`,
    consequence:
      empty > 0
        ? "An empty benchmark sets no bar — add entries before measuring against it."
        : "These are the standards your artifacts are measured against.",
    status: "derived",
    provenance: { basis: "each benchmark's recorded entries" },
  };
}

function evaluationsVerdict(items: SurfaceData["jobs"] & object[]): Verdict {
  if (items.length === 0) {
    return {
      state: "unknown",
      headline: "No evaluations have been run in this project yet.",
      consequence:
        "Evaluations are where evidence comes from. Until one runs, nothing about your system's quality is known — only assumed.",
      status: "derived",
    };
  }
  const failed = items.filter((j) => j.status === "FAILED").length;
  const running = items.filter((j) => j.status === "RUNNING" || j.status === "PENDING").length;
  const completed = items.filter((j) => j.status === "COMPLETED").length;

  if (failed > 0) {
    return {
      state: "failed",
      headline: `${plural(failed, "evaluation")} of ${items.length} failed.`,
      consequence:
        "Open a failing evaluation's execution graph to see exactly where the chain broke — provider, model, or the metrics that judged it.",
      status: "derived",
      provenance: { basis: "the recorded outcome of each evaluation" },
    };
  }
  return {
    state: completed > 0 ? "healthy" : "unknown",
    headline:
      completed > 0
        ? `${plural(completed, "evaluation")} completed without failure.`
        : `${plural(running, "evaluation is", "evaluations are")} still running.`,
    consequence:
      running > 0
        ? `${plural(running, "evaluation is", "evaluations are")} still in flight — this may change.`
        : "Every evaluation on record produced evidence you can reason about.",
    status: "derived",
    provenance: { basis: "the recorded outcome of each evaluation" },
  };
}

function analyticsVerdict(a: SurfaceData["analytics"]): Verdict {
  if (!a || a.runCount === 0) {
    return {
      state: "unknown",
      headline: "There is nothing to analyse yet.",
      consequence:
        "Analytics summarize evidence your evaluations produced. With no runs recorded, any trend shown here would be invented.",
      status: "derived",
      provenance: { basis: "the absence of recorded runs" },
    };
  }
  const pct = Math.round(a.passRate * 100);
  const state = pct >= 80 ? "healthy" : pct >= 50 ? "attention" : "risk";
  const costClause = a.totalCost != null ? ` They cost ${formatCost(a.totalCost)}` : "";
  const latencyClause = a.avgLatencyMs != null ? ` at ${formatLatency(a.avgLatencyMs)} average latency` : "";

  return {
    state,
    headline: `${pct}% of runs passed over the last ${plural(a.windowDays, "day")}.`,
    // L-41: quality is never presented without its price.
    consequence: `${plural(a.runCount, "run")} across ${plural(a.jobCount, "evaluation")}.${costClause}${latencyClause}${costClause || latencyClause ? "." : ""}`,
    status: "derived",
    provenance: { basis: `${plural(a.runCount, "recorded run")}` },
  };
}
