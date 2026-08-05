"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Reveal } from "./reveal";

/**
 * The engineering loop, as the product actually models it.
 *
 * Broks Forge is organised around this workflow rather than around pages, so showing the loop
 * explains the navigation model at the same time as the value. The final step closing back to the
 * first is the whole argument for keeping a record: learning is only real if the next failure can
 * find it.
 */
const STEPS = [
  { n: "01", t: "Problem", d: "Quality dropped, cost rose, something failed overnight." },
  { n: "02", t: "Execution", d: "Run a reproducible evaluation with a pinned configuration." },
  { n: "03", t: "Evidence", d: "The result enters the record as an observation, then as evidence." },
  { n: "04", t: "Knowledge", d: "Where a decision and evidence both exist, a durable fact emerges." },
  { n: "05", t: "Decision", d: "Promote or don't. Brok refuses to bless an unmeasured revision." },
  { n: "06", t: "Revision", d: "A new version — and one honest sentence saying why." },
  { n: "07", t: "Promotion", d: "AI Git records what was promoted and what it superseded." },
  { n: "08", t: "Deployment", d: "Production runs it. A rollback is displayed as a rollback." },
  { n: "09", t: "Learning", d: "It becomes precedent. The next failure can find it." },
];

export function EngineeringJourney() {
  return (
    <section id="workflow" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">
            The engineering journey
          </p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            A loop, not a set of pages.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Every surface in the product serves one step of this loop — and the loop closes.
            Learning becomes the precedent the next failure searches.
          </p>
        </Reveal>

        <div className="mt-12 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {STEPS.map((s, i) => (
            <Reveal key={s.n} delay={i * 0.04}>
              <div className="h-full rounded-xl border border-border/60 bg-card p-5">
                <div className="flex items-baseline gap-3">
                  <span className="font-mono text-xs tabular-nums text-primary">{s.n}</span>
                  <h3 className="font-semibold text-foreground">{s.t}</h3>
                </div>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.d}</p>
              </div>
            </Reveal>
          ))}
        </div>

        <Reveal delay={0.1}>
          <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-3">
            <p className="text-sm text-muted-foreground">
              <span className="text-foreground">The shortest useful loop:</span> register, write a
              reason on every version, evaluate before you promote, investigate instead of re-running.
            </p>
            <Link
              href="/docs/engineering-workflow"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
            >
              The full workflow
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
