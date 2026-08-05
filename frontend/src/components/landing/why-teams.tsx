"use client";

import { Reveal } from "./reveal";

/**
 * Who this is for, framed by the question each role actually asks.
 *
 * Deliberately not testimonials: this is a young open-source project and inventing social proof
 * would contradict the honesty the product itself is built on. Roles and their real questions are
 * more useful to a reader deciding whether this applies to them.
 */
const ROLES = [
  {
    role: "AI / ML engineers",
    ask: "“Why did this fail, and has it failed before?”",
    gain: "Reproducible evaluations, a versioned history of every prompt and agent, and an assembled investigation instead of a log search.",
  },
  {
    role: "Staff & lead engineers",
    ask: "“Can we defend this configuration?”",
    gain: "One engineering record to reason over — including precedent, contradictions, and decisions carrying no evidence.",
  },
  {
    role: "Engineering managers & CTOs",
    ask: "“What can we actually prove?”",
    gain: "Evidence behind decisions. Which promotions are defensible, which are being carried on faith, and what the system cannot yet prove.",
  },
  {
    role: "Teams inheriting a system",
    ask: "“Why is it like this?”",
    gain: "Engineering Memory. The reasoning behind the current state survives the person who made it.",
  },
];

export function WhyTeams() {
  return (
    <section id="why" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">Who it is for</p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Built for the questions asked after the demo works.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Getting an AI feature working is the first week. Explaining, defending and evolving it is
            every week after that.
          </p>
        </Reveal>

        <div className="mt-12 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {ROLES.map((r, i) => (
            <Reveal key={r.role} delay={i * 0.05}>
              <div className="h-full rounded-xl border border-border/60 bg-card p-6">
                <h3 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                  {r.role}
                </h3>
                <p className="mt-3 text-lg font-medium leading-snug tracking-tight text-foreground">
                  {r.ask}
                </p>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{r.gain}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
