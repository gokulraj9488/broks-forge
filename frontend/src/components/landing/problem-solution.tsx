"use client";

import Link from "next/link";
import { ArrowRight, Check, X } from "lucide-react";
import { Reveal } from "./reveal";

/**
 * Problem → Solution, stated as questions rather than adjectives.
 *
 * The strongest argument for this category is not a feature list; it is a table of ordinary
 * engineering questions where the top half is answerable by existing tools and the bottom half is
 * not. That asymmetry is the product's reason to exist, so it is shown directly.
 */
const QUESTIONS = [
  { q: "What did this request do?", tracing: true },
  { q: "Which calls were slow or expensive?", tracing: true },
  { q: "Did this prompt change break anything?", tracing: true },
  { q: "Why was this prompt promoted?", tracing: false },
  { q: "What evidence supports the current configuration?", tracing: false },
  { q: "Has this failure happened before, and what did we do?", tracing: false },
  { q: "Which of our decisions have no evidence behind them?", tracing: false },
  { q: "What would break if I changed this dataset?", tracing: false },
  { q: "Why is the system the way it is?", tracing: false },
];

export function ProblemSolution() {
  return (
    <section id="problem" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">The problem</p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Teams lose the reasoning behind their AI systems.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            The prompt is in version control, but why v8 replaced v7 is in a Slack thread. The
            evaluation results are on a dashboard, but which decision they justified is nowhere. The
            engineer who knew has left. Six weeks later something fails, and nobody can reconstruct
            which change mattered.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="mt-12 overflow-hidden rounded-xl border border-border/60">
            <div className="grid grid-cols-[1fr_auto_auto] items-center gap-x-4 border-b border-border/60 bg-muted/30 px-4 py-2.5 sm:px-6">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                An ordinary engineering question
              </p>
              <p className="w-20 text-center text-xs font-medium uppercase tracking-wide text-muted-foreground sm:w-28">
                Tracing &amp; eval tools
              </p>
              <p className="w-16 text-center text-xs font-medium uppercase tracking-wide text-primary sm:w-24">
                Broks Forge
              </p>
            </div>

            {QUESTIONS.map((row) => (
              <div
                key={row.q}
                className="grid grid-cols-[1fr_auto_auto] items-center gap-x-4 border-b border-border/40 px-4 py-3 last:border-b-0 sm:px-6"
              >
                <p className="text-sm leading-snug text-foreground">{row.q}</p>
                <div className="flex w-20 justify-center sm:w-28">
                  {row.tracing ? (
                    <Check className="h-4 w-4 text-success" aria-label="Yes" />
                  ) : (
                    <X className="h-4 w-4 text-muted-foreground/40" aria-label="No" />
                  )}
                </div>
                <div className="flex w-16 justify-center sm:w-24">
                  <Check className="h-4 w-4 text-success" aria-label="Yes" />
                </div>
              </div>
            ))}
          </div>
        </Reveal>

        <Reveal delay={0.15}>
          <p className="mt-6 max-w-3xl text-sm leading-relaxed text-muted-foreground">
            The bottom half of that table is not a feature gap a better dashboard closes. Those
            questions are about <span className="text-foreground">artifacts, versions, decisions and
            evidence</span> — objects that tracing does not model, because it was never trying to.
            Answering them needs a different data model.
          </p>
        </Reveal>

        {/* Solution */}
        <Reveal delay={0.2}>
          <div className="mt-20">
            <p className="text-sm font-medium uppercase tracking-wide text-primary">The solution</p>
            <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
              Record the engineering act. Then reason over it.
            </h2>
            <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
              You register artifacts and evaluate them — work you were doing anyway. From that,
              Broks Forge derives observations, claims, decisions, evidence and durable knowledge.
              Nobody writes any of it down.
            </p>
          </div>
        </Reveal>

        <Reveal delay={0.25}>
          <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              {
                t: "Derived, not authored",
                d: "Promoting a version is a decision. Running an evaluation is evidence. The record is a by-product of engineering, so it never rots.",
              },
              {
                t: "Nothing is fabricated",
                d: "The reasoning layer is deterministic over real rows — no language model. When the record cannot answer, it says so.",
              },
              {
                t: "Every claim is labelled",
                d: "Derived, inferred, suggested or unknown — with a three-step confidence ladder instead of an invented percentage.",
              },
              {
                t: "Absence is not health",
                d: "An artifact nobody measured is reported as unknown, never as passing. A distinct verdict state, on purpose.",
              },
            ].map((c) => (
              <div key={c.t} className="rounded-xl border border-border/60 bg-card p-5">
                <h3 className="text-sm font-semibold text-foreground">{c.t}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{c.d}</p>
              </div>
            ))}
          </div>
        </Reveal>

        <Reveal delay={0.3}>
          <Link
            href="/docs/ai-engineering-operating-system"
            className="mt-8 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
          >
            What is an AI Engineering Operating System?
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Reveal>
      </div>
    </section>
  );
}
