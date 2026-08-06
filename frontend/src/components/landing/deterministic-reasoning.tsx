"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Reveal } from "./reveal";
import { cn } from "@/lib/utils";

/**
 * The claim the whole platform rests on: the reasoning layer contains no language model.
 *
 * This section exists because every other promise on the page is worthless without it. A record
 * that might be confabulating is a record you have to check, and a record you have to check has
 * saved you nothing. So the argument is made by contrast — the same question answered two ways —
 * rather than by asserting "accurate" and "reliable", which is what every AI product asserts.
 *
 * The four epistemic states are the product's Inference Boundary (Vol III, L-33): a reader must be
 * able to tell at a glance how strongly anything is known.
 */
const LADDER = [
  {
    state: "Derived",
    meaning: "Read directly from the record.",
    example: "“4 evaluations cover this revision.”",
    tone: "border-emerald-500/40 bg-emerald-500/5",
    dot: "bg-emerald-400",
  },
  {
    state: "Inferred",
    meaning: "A conclusion drawn from what the record holds, and labelled as one.",
    example: "“These failures read as infrastructure, not quality.”",
    tone: "border-amber-500/40 bg-amber-500/5",
    dot: "bg-amber-400",
  },
  {
    state: "Suggested",
    meaning: "A recommendation, carrying the evidence behind it.",
    example: "“Do not promote v4 — nothing has measured it.”",
    tone: "border-sky-500/40 bg-sky-500/5",
    dot: "bg-sky-400",
  },
  {
    state: "Unknown",
    meaning: "The record cannot answer. Said plainly, never filled in.",
    example: "“No evaluation has run against this dataset.”",
    tone: "border-zinc-500/40 bg-zinc-500/5",
    dot: "bg-zinc-400",
  },
];

export function DeterministicReasoning() {
  return (
    <section id="deterministic" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">
            Deterministic reasoning
          </p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            There is no language model in the reasoning layer.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Not as a limitation — as the point. An answer you have to go and verify has not saved you
            the work of finding it. Broks Forge resolves your question to an engineering intent and
            composes the answer from real rows, so the same question always returns the same answer,
            and every sentence can be traced to the record that produced it.
          </p>
        </Reveal>

        {/* The argument by contrast: the same question, answered two ways. */}
        <Reveal delay={0.1}>
          <div className="mt-12 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="rounded-xl border border-border/60 bg-muted/20 p-6">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                A generated answer
              </p>
              <p className="mt-4 text-sm leading-relaxed text-foreground/70">
                “The evaluation likely failed due to a configuration issue with the agent&apos;s
                credentials. You may want to check your provider settings and re-run the test.”
              </p>
              <ul className="mt-5 space-y-1.5 text-xs text-muted-foreground">
                <li>Plausible whether or not it is true.</li>
                <li>No way to tell which part came from your system.</li>
                <li>Ask twice, get two different answers.</li>
                <li>You still have to go and check.</li>
              </ul>
            </div>

            <div className="rounded-xl border border-primary/30 bg-primary/[0.04] p-6">
              <p className="text-xs font-medium uppercase tracking-wide text-primary">
                A derived answer
              </p>
              <p className="mt-4 text-sm leading-relaxed text-foreground">
                “6 of 8 sampled runs returned HTTP 401 against Local OpenAI-compatible. Credentials
                on that provider were rotated 3 hours before this ran.”
              </p>
              <ul className="mt-5 space-y-1.5 text-xs text-muted-foreground">
                <li>
                  <span className="text-foreground/80">Derived</span> — the run rows say so.
                </li>
                <li>
                  <span className="text-foreground/80">Traceable</span> — every clause opens the row
                  behind it.
                </li>
                <li>
                  <span className="text-foreground/80">Reproducible</span> — the same question
                  returns the same answer.
                </li>
                <li>
                  <span className="text-foreground/80">Bounded</span> — proximity is offered as
                  proximity, not as proof.
                </li>
              </ul>
            </div>
          </div>
        </Reveal>

        {/* ------------------------------------------------ knowledge is derived */}
        <Reveal delay={0.15}>
          <div className="mt-20 max-w-3xl">
            <p className="text-sm font-medium uppercase tracking-wide text-primary">Knowledge</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
              Derived from work already done — never written, never generated.
            </h2>
            <p className="mt-4 text-base leading-relaxed text-muted-foreground">
              Documentation rots because keeping it true is a second job nobody is promoted for.
              Broks Forge never asks. Promoting a version <em>is</em> a decision. Running an
              evaluation <em>is</em> evidence. Knowledge appears only where a decision and its
              evidence genuinely both exist — which is also why the platform can tell you which of
              your decisions have nothing behind them at all.
            </p>
          </div>
        </Reveal>

        <Reveal delay={0.2}>
          <div className="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {LADDER.map((l) => (
              <div key={l.state} className={cn("rounded-xl border p-5", l.tone)}>
                <div className="flex items-center gap-2">
                  <span className={cn("h-1.5 w-1.5 rounded-full", l.dot)} />
                  <h3 className="text-sm font-semibold text-foreground">{l.state}</h3>
                </div>
                <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{l.meaning}</p>
                <p className="mt-3 text-xs italic leading-snug text-foreground/70">{l.example}</p>
              </div>
            ))}
          </div>
        </Reveal>

        <Reveal delay={0.25}>
          <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-3">
            <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">
              <span className="text-foreground">Absence is never reported as health.</span> An
              artifact nobody has measured comes back as unknown — a distinct verdict, on purpose,
              because &ldquo;no failures&rdquo; and &ldquo;no evidence&rdquo; are not the same claim.
            </p>
            <Link
              href="/docs/deterministic-reasoning"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
            >
              How the reasoning works
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
