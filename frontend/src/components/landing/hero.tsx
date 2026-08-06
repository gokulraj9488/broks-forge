"use client";

import Link from "next/link";
import { motion, useReducedMotion } from "framer-motion";
import { ArrowUpRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

/**
 * The hero has one job: a first-time visitor must infer "AI Engineering Operating System" within
 * thirty seconds, without documentation.
 *
 * So it states the category outright rather than describing features, and the visual is a real
 * Brok answer in the product's own vocabulary — verdict, epistemic status, evidence, next action.
 * That single artifact communicates more about what this is than any list of capabilities, because
 * nothing else on the market answers a question that way.
 */
const PILLARS = [
  { name: "Build", detail: "agents, prompts, datasets, providers" },
  { name: "Evaluate", detail: "reproducible measurement" },
  { name: "Understand", detail: "intelligence, knowledge, memory" },
  { name: "Evolve", detail: "AI Git, promotion, rollback" },
];

export function Hero() {
  const reduceMotion = useReducedMotion();

  return (
    <section className="relative overflow-hidden border-b border-border/60">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.35] dark:opacity-[0.15]"
        style={{
          backgroundImage:
            "linear-gradient(hsl(var(--border)) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--border)) 1px, transparent 1px)",
          backgroundSize: "56px 56px",
          maskImage: "radial-gradient(ellipse 70% 60% at 50% 0%, black 40%, transparent 100%)",
        }}
      />

      <div className="container relative grid grid-cols-1 gap-14 py-20 lg:grid-cols-[1.05fr_1fr] lg:items-center lg:py-28">
        <div>
          <Badge variant="outline" className="mb-6 gap-1.5 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-success" />
            Open source · Apache 2.0
          </Badge>

          <h1 className="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl lg:text-[3.4rem] lg:leading-[1.05]">
            The AI Engineering
            <span className="text-primary"> Operating System.</span>
          </h1>

          <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted-foreground">
            Your AI system is a sequence of engineering decisions — a prompt promoted, a dataset
            regenerated, a model swapped. Broks Forge records those decisions with the evidence and
            the reasoning behind them, then answers the questions that follow:{" "}
            <span className="text-foreground">why did this fail, what changed, has this happened
            before, and can we defend it?</span>
          </p>

          {/* Two doors, not three. GitHub lives in the nav and in the closing call to action;
              repeating it here only competes with the action that matters. */}
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Button asChild size="lg" className="transition-transform active:scale-[0.97]">
              <Link href="/register">
                Get started
                <ArrowUpRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="transition-transform active:scale-[0.97]">
              <Link href="/docs/what-is-broks-forge">See how it works</Link>
            </Button>
          </div>

          <dl className="mt-10 grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
            {PILLARS.map((p) => (
              <div key={p.name}>
                <dt className="text-sm font-medium text-foreground">{p.name}</dt>
                <dd className="mt-0.5 text-[11px] leading-snug text-muted-foreground">{p.detail}</dd>
              </div>
            ))}
          </dl>
        </div>

        {/* A real answer in the product's own vocabulary — the fastest way to show what this is. */}
        <motion.div
          initial={{ opacity: 0, y: reduceMotion ? 0 : 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          className="relative"
        >
          {/* The decorative rendering is hidden from assistive tech, but the narrative it carries is
              not — it is the clearest explanation on the page, so it is restated as text. Vol III
              L-92: the narrative IS the accessible product. */}
          <p className="sr-only">
            An example answer from Brok. Question: has this happened before? Answer, near-certain:
            yes. Checkout Quality number one failed nineteen days ago against the same agent and
            dataset, with an identical recorded cause, which makes this a recurrence rather than a
            coincidence. This is derived, read from earlier evaluations sharing an artifact with this
            one. Both failures recorded &ldquo;Connection refused&rdquo;. Engineering memory records
            &ldquo;Moved the endpoint behind the internal gateway&rdquo;. Two downstream artifacts
            are left without evidence.
          </p>

          <div
            aria-hidden="true"
            className="rounded-xl border border-border bg-card/80 p-5 shadow-lg backdrop-blur"
          >
            <p className="text-xs text-muted-foreground">
              <span className="font-medium text-foreground">Ask Brok</span> · Has this happened
              before?
            </p>

            <div className="mt-4 rounded-lg border border-rose-500/30 bg-rose-500/5 p-3.5">
              <div className="flex items-start justify-between gap-3">
                <p className="text-sm font-medium leading-snug text-foreground">
                  Yes — this has happened before.
                </p>
                <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
                  near-certain
                </span>
              </div>
              <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
                Checkout Quality #1 failed 19 days ago against the same agent and dataset. The
                recorded cause is identical, which makes this a recurrence, not a coincidence.
              </p>
              <p className="mt-2 text-[10px] uppercase tracking-wide text-muted-foreground/70">
                Derived · read from earlier evaluations sharing an artifact with this one
              </p>
            </div>

            <div className="mt-3 space-y-2">
              {[
                { k: "Reasoning", v: "Both failures recorded: “Connection refused”." },
                { k: "Memory", v: "“Moved the endpoint behind the internal gateway.”" },
                { k: "Impact", v: "2 downstream artifacts left without evidence." },
              ].map((row) => (
                <div key={row.k} className="flex gap-2.5 text-xs">
                  <span className="w-16 shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground/70">
                    {row.k}
                  </span>
                  <span className="min-w-0 flex-1 leading-snug text-foreground/85">{row.v}</span>
                </div>
              ))}
            </div>

            <div className="mt-4 flex flex-wrap gap-2 border-t border-border/60 pt-3">
              {["View the failure graph", "Compare revisions", "Investigate the precedent"].map(
                (a) => (
                  <span
                    key={a}
                    className="rounded-md border border-border px-2 py-1 text-[11px] text-muted-foreground"
                  >
                    {a}
                  </span>
                ),
              )}
            </div>
          </div>

          <p className="mt-3 text-center text-[11px] text-muted-foreground/70">
            Every statement declares how it is known. Nothing is generated.
          </p>
        </motion.div>
      </div>
    </section>
  );
}
