"use client";

import Link from "next/link";
import { ArrowRight, Boxes, GitBranch, Layers, Network, Sparkles } from "lucide-react";
import { Reveal } from "./reveal";
import { cn } from "@/lib/utils";

/**
 * The five layers, rendered as a stack that reads bottom-up like the architecture it describes.
 *
 * Each layer links to its own documentation, because the landing page's job is to make the shape
 * legible in thirty seconds, not to explain all five in depth.
 */
const LAYERS = [
  {
    n: 5,
    name: "Engineering Applications",
    tag: "Brok · Root Cause Explorer · Briefs",
    detail: "Reason over everything below. Own no data, so they cannot drift from the truth.",
    icon: Sparkles,
    href: "/docs/brok",
    accent: "border-l-primary",
  },
  {
    n: 4,
    name: "Forge Graph",
    tag: "the living map",
    detail:
      "Artifacts, their real relationships, and reasoning layered on top. The Execution Graph and " +
      "Failure Graph are the same map narrowed to a single run.",
    icon: Network,
    href: "/docs/forge-graph",
    accent: "border-l-sky-500/60",
  },
  {
    n: 3,
    name: "AI Git",
    tag: "the evolution engine",
    detail:
      "Revisions, promotions, rollbacks and the rationale behind every change. Evolution reads that " +
      "history back as lineage, dependents and blast radius.",
    icon: GitBranch,
    href: "/docs/ai-git",
    accent: "border-l-violet-500/60",
  },
  {
    n: 2,
    name: "Registry",
    tag: "the engineering catalog",
    detail: "Every artifact and every derived knowledge object, discoverable in one place.",
    icon: Boxes,
    href: "/docs/registry",
    accent: "border-l-emerald-500/60",
  },
  {
    n: 1,
    name: "Forge Kernel",
    tag: "the invisible foundation",
    detail: "Identity, tenancy, persistence, execution. You are not supposed to notice it.",
    icon: Layers,
    href: "/docs/the-five-layers",
    accent: "border-l-zinc-500/50",
  },
];

export function FiveLayers() {
  return (
    <section id="layers" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">Architecture</p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Five layers, each earning the one above it.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            A layer may read everything beneath it and nothing above it, and no layer may duplicate
            a layer below it. That rule is why adding a reasoning surface never means adding a
            table.
          </p>
        </Reveal>

        <div className="mt-12 space-y-2.5">
          {LAYERS.map((layer, i) => {
            const Icon = layer.icon;
            return (
              <Reveal key={layer.n} delay={i * 0.06}>
                <Link
                  href={layer.href}
                  className={cn(
                    "group flex items-start gap-4 rounded-xl border border-l-2 border-border/60 bg-card p-5 transition-colors hover:border-primary/40",
                    layer.accent,
                  )}
                >
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                    <Icon className="h-4 w-4 text-foreground/70" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline gap-x-2.5">
                      <h3 className="font-semibold text-foreground">{layer.name}</h3>
                      <span className="text-xs text-muted-foreground">{layer.tag}</span>
                    </div>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                      {layer.detail}
                    </p>
                  </div>
                  <span className="mt-1 hidden shrink-0 items-center text-xs tabular-nums text-muted-foreground/50 sm:flex">
                    Layer {layer.n}
                    <ArrowRight className="ml-2 h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                  </span>
                </Link>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
