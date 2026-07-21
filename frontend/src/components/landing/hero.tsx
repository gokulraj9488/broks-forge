"use client";

import Link from "next/link";
import { motion, useReducedMotion } from "framer-motion";
import { ArrowUpRight, Check, FlaskConical, Github, Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

const TRUST_STRIP = [
  "Multi-Model Evaluation",
  "Regression Detection",
  "Prompt Versioning",
  "AI Judge",
  "Production Monitoring",
];

/** A gently-floating mock benchmark card — decorative, not real data. */
function FloatCard({
  className,
  delay = 0,
  children,
}: {
  className?: string;
  delay?: number;
  children: React.ReactNode;
}) {
  const reduceMotion = useReducedMotion();
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 12 }}
      animate={{
        opacity: 1,
        y: reduceMotion ? 0 : [0, -8, 0],
      }}
      transition={{
        opacity: { duration: 0.6, delay },
        y: reduceMotion
          ? { duration: 0.6, delay }
          : { duration: 6, delay, repeat: Infinity, ease: "easeInOut" },
      }}
    >
      {children}
    </motion.div>
  );
}

export function Hero() {
  return (
    <section className="relative overflow-hidden border-b border-border/60">
      {/* Faint grid — matches app's technical, non-illustrative aesthetic. No blobs, no gradients. */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.35] dark:opacity-[0.15]"
        style={{
          backgroundImage:
            "linear-gradient(hsl(var(--border)) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--border)) 1px, transparent 1px)",
          backgroundSize: "56px 56px",
          maskImage: "radial-gradient(ellipse 70% 60% at 50% 0%, black 40%, transparent 100%)",
        }}
      />

      <div className="container relative grid gap-16 py-20 lg:grid-cols-2 lg:items-center lg:py-28">
        <div>
          <Badge variant="outline" className="mb-6 gap-1.5 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-success" />
            v1.0.0 &middot; open source
          </Badge>

          <h1 className="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl lg:text-[3.25rem] lg:leading-[1.1]">
            From Prompt Guesswork
            <span className="text-primary"> to AI Engineering.</span>
          </h1>

          <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted-foreground">
            Prompt evaluation is fragmented across chat windows and spreadsheets. Benchmarks
            aren&apos;t reproducible. Debugging a bad output means guessing. Brok&apos;s Forge
            replaces all of it with versioned datasets, repeatable evaluations, and regression
            detection — one system, for every model and agent framework you ship.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Button asChild size="lg" className="transition-transform active:scale-[0.97]">
              <Link href="/login">
                Get Started
                <ArrowUpRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="transition-transform active:scale-[0.97]"
            >
              <Link href="https://github.com/your-org/broks-forge" target="_blank" rel="noopener noreferrer">
                <Github className="h-4 w-4" />
                View GitHub
              </Link>
            </Button>
          </div>

          <ul className="mt-8 flex flex-wrap gap-x-5 gap-y-2 text-sm text-muted-foreground">
            {TRUST_STRIP.map((item) => (
              <li key={item} className="flex items-center gap-1.5">
                <Check className="h-3.5 w-3.5 text-success" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        {/* Product scene: real UI vocabulary (Card/Badge), not stock illustration. */}
        <div className="relative hidden h-[420px] lg:block" aria-hidden="true">
          <FloatCard className="absolute left-2 top-4 w-64" delay={0}>
            <Card className="shadow-md">
              <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
                <span className="text-xs font-medium text-muted-foreground">Benchmark</span>
                <Trophy className="h-3.5 w-3.5 text-warning" />
              </CardHeader>
              <CardContent className="pt-0">
                <div className="text-2xl font-semibold tabular-nums text-foreground">94.2</div>
                <div className="mt-2 flex items-center gap-1.5">
                  <Badge variant="success" className="text-[10px]">+3.1 vs baseline</Badge>
                </div>
              </CardContent>
            </Card>
          </FloatCard>

          <FloatCard className="absolute right-0 top-32 w-72" delay={0.8}>
            <Card className="shadow-md">
              <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
                <span className="text-xs font-medium text-muted-foreground">Evaluation Run</span>
                <FlaskConical className="h-3.5 w-3.5 text-primary" />
              </CardHeader>
              <CardContent className="space-y-2 pt-0">
                {[
                  { label: "Exact match", value: 88 },
                  { label: "Latency score", value: 96 },
                  { label: "Cost efficiency", value: 74 },
                ].map((row) => (
                  <div key={row.label} className="flex items-center gap-2">
                    <span className="w-24 shrink-0 text-[11px] text-muted-foreground">{row.label}</span>
                    <div className="h-1.5 flex-1 rounded-full bg-muted">
                      <div
                        className="h-1.5 rounded-full bg-primary"
                        style={{ width: `${row.value}%` }}
                      />
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          </FloatCard>

          <FloatCard className="absolute bottom-6 left-10 w-60" delay={1.6}>
            <Card className="shadow-md">
              <CardHeader className="pb-2">
                <span className="text-xs font-medium text-muted-foreground">Prompt Comparison</span>
              </CardHeader>
              <CardContent className="flex items-center justify-between pt-0">
                <div>
                  <div className="text-[11px] text-muted-foreground">v3 vs v4</div>
                  <div className="text-lg font-semibold text-success">+12%</div>
                </div>
                <div className="flex h-10 items-end gap-1">
                  {[6, 10, 8, 16, 22, 18, 26].map((h, i) => (
                    <div
                      key={i}
                      className="w-1.5 rounded-sm bg-chart-2"
                      style={{ height: `${h}px` }}
                    />
                  ))}
                </div>
              </CardContent>
            </Card>
          </FloatCard>
        </div>
      </div>
    </section>
  );
}
