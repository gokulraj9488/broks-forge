"use client";

import Link from "next/link";
import { ArrowRight, Check, Minus } from "lucide-react";
import { Reveal } from "./reveal";

/**
 * Where Broks Forge sits relative to adjacent tools.
 *
 * The tone here is deliberately non-competitive: these products are good, several are more mature,
 * and the honest framing is "different scope" rather than "better". The maturity row is included
 * precisely because leaving it out would be the kind of claim this platform exists to avoid — and a
 * comparison that never concedes anything is not read as credible by engineers.
 */
const ROWS: { label: string; others: "yes" | "partial" | "no"; forge: "yes" | "partial" | "no" }[] = [
  { label: "Production request tracing", others: "yes", forge: "no" },
  { label: "Evaluate against datasets", others: "yes", forge: "yes" },
  { label: "Prompt versioning", others: "yes", forge: "yes" },
  { label: "Decisions as first-class objects", others: "no", forge: "yes" },
  { label: "Evidence linked to decisions", others: "no", forge: "yes" },
  { label: "Engineering memory — why it changed", others: "no", forge: "yes" },
  { label: "Artifact dependency graph", others: "partial", forge: "yes" },
  { label: "Precedent search over failures", others: "no", forge: "yes" },
  { label: "Assembled root-cause investigation", others: "no", forge: "yes" },
  { label: "Grounded Q&A with no LLM", others: "no", forge: "yes" },
  { label: "Maturity and ecosystem", others: "yes", forge: "partial" },
];

const TOOLS = [
  { name: "LangFuse", href: "/docs/vs-langfuse", what: "Tracing & evaluation for LLM apps" },
  { name: "LangSmith", href: "/docs/vs-langsmith", what: "LangChain-native observability" },
  { name: "Promptfoo", href: "/docs/vs-promptfoo", what: "Developer-first prompt testing" },
  { name: "Helicone", href: "/docs/vs-helicone", what: "LLM gateway & observability proxy" },
  { name: "Weights & Biases", href: "/docs/vs-weights-and-biases", what: "Experiment tracking" },
];

function Mark({ value }: { value: "yes" | "partial" | "no" }) {
  if (value === "yes") return <Check className="h-4 w-4 text-success" aria-label="Yes" />;
  if (value === "partial") return <Minus className="h-4 w-4 text-warning" aria-label="Partial" />;
  return <Minus className="h-4 w-4 text-muted-foreground/30" aria-label="No" />;
}

export function Comparison() {
  return (
    <section id="comparison" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">Comparisons</p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            A different scope, not a better dashboard.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Tracing, evaluation and gateway tools are good at what they do, and several are more
            mature than this project. Most teams should run one alongside Broks Forge. The useful
            question is not which is better — it is what each one is for.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="mt-10 overflow-hidden rounded-xl border border-border/60">
            <div className="grid grid-cols-[1fr_auto_auto] items-center gap-x-4 border-b border-border/60 bg-muted/30 px-4 py-2.5 sm:px-6">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Capability
              </span>
              <span className="w-24 text-center text-xs font-medium uppercase tracking-wide text-muted-foreground sm:w-32">
                Tracing / eval tools
              </span>
              <span className="w-16 text-center text-xs font-medium uppercase tracking-wide text-primary sm:w-24">
                Broks Forge
              </span>
            </div>
            {ROWS.map((r) => (
              <div
                key={r.label}
                className="grid grid-cols-[1fr_auto_auto] items-center gap-x-4 border-b border-border/40 px-4 py-2.5 last:border-b-0 sm:px-6"
              >
                <span className="text-sm text-foreground">{r.label}</span>
                <span className="flex w-24 justify-center sm:w-32">
                  <Mark value={r.others} />
                </span>
                <span className="flex w-16 justify-center sm:w-24">
                  <Mark value={r.forge} />
                </span>
              </div>
            ))}
          </div>
          <p className="mt-3 text-xs text-muted-foreground/80">
            Generalised across the tools compared below; capabilities differ between them and change
            over time. Each comparison page is specific, and cites what the other tool does well.
          </p>
        </Reveal>

        <Reveal delay={0.15}>
          <div className="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {TOOLS.map((t) => (
              <Link
                key={t.name}
                href={t.href}
                className="group flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-card px-4 py-3 transition-colors hover:border-primary/40"
              >
                <span className="min-w-0">
                  <span className="block text-sm font-medium text-foreground">vs {t.name}</span>
                  <span className="block truncate text-xs text-muted-foreground">{t.what}</span>
                </span>
                <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
              </Link>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
