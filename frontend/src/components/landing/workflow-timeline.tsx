import { motion, useReducedMotion } from "framer-motion";
import {
  Bot,
  FlaskConical,
  Gauge,
  Lightbulb,
  Network,
  Rocket,
  Trophy,
  Wrench,
} from "lucide-react";
import { Reveal } from "./reveal";

const STEPS = [
  { label: "Build", icon: Wrench },
  { label: "Test", icon: FlaskConical },
  { label: "Benchmark", icon: Trophy },
  { label: "Compare", icon: Bot },
  { label: "Root Cause", icon: Network },
  { label: "Improve", icon: Lightbulb },
  { label: "Deploy", icon: Rocket },
  { label: "Monitor", icon: Gauge },
];

export function WorkflowTimeline() {
  const reduceMotion = useReducedMotion();

  return (
    <section id="workflow" className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            One workflow, the full lifecycle
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            Brok&apos;s Forge covers every stage of shipping an AI system, not just the prompt.
          </p>
        </Reveal>

        <div className="mt-16 overflow-x-auto">
          <div className="flex min-w-[720px] items-start justify-between gap-2 px-2 sm:min-w-0">
            {STEPS.map((step, i) => (
              <div key={step.label} className="flex flex-1 items-start">
                <Reveal delay={i * 0.09} className="flex flex-col items-center text-center">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-border bg-card text-primary shadow-sm">
                    <step.icon className="h-5 w-5" />
                  </div>
                  <span className="mt-3 whitespace-nowrap text-sm font-medium text-foreground">
                    {step.label}
                  </span>
                </Reveal>
                {i < STEPS.length - 1 && (
                  <motion.div
                    aria-hidden="true"
                    className="mt-6 h-px flex-1 origin-left bg-gradient-to-r from-border via-border to-transparent"
                    initial={{ scaleX: reduceMotion ? 1 : 0 }}
                    whileInView={{ scaleX: 1 }}
                    viewport={{ once: true, margin: "-80px" }}
                    transition={{ duration: 0.4, delay: i * 0.09 + 0.15, ease: "easeOut" }}
                  />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
