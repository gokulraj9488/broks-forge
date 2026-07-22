"use client";

import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import { FlaskConical } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Reveal } from "./reveal";

/**
 * Entirely mock, frontend-only — no backend call. Fixed lookup table so switching
 * prompt/model is instant and never shows an inconsistent combination.
 */
const PROMPTS = [
  { id: "p1", label: "Prompt A — direct instruction" },
  { id: "p2", label: "Prompt B — chain-of-thought" },
] as const;

const MODELS = [
  { id: "m1", label: "Groq · Llama 3.3 70B" },
  { id: "m2", label: "OpenAI · GPT-4.1" },
  { id: "m3", label: "Anthropic · Claude" },
] as const;

type Metrics = { score: number; accuracy: number; latencyMs: number; costPer1k: number };

const RESULTS: Record<string, Metrics> = {
  "p1-m1": { score: 78, accuracy: 81, latencyMs: 420, costPer1k: 0.12 },
  "p1-m2": { score: 86, accuracy: 89, latencyMs: 980, costPer1k: 2.4 },
  "p1-m3": { score: 88, accuracy: 91, latencyMs: 860, costPer1k: 2.1 },
  "p2-m1": { score: 84, accuracy: 87, latencyMs: 540, costPer1k: 0.15 },
  "p2-m2": { score: 93, accuracy: 95, latencyMs: 1120, costPer1k: 2.9 },
  "p2-m3": { score: 94, accuracy: 96, latencyMs: 990, costPer1k: 2.5 },
};

export function LiveDemo() {
  const [promptId, setPromptId] = useState<(typeof PROMPTS)[number]["id"]>("p1");
  const [modelId, setModelId] = useState<(typeof MODELS)[number]["id"]>("m1");

  const metrics = useMemo(() => RESULTS[`${promptId}-${modelId}`], [promptId, modelId]);

  return (
    <section className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <Badge variant="outline" className="mb-4">
            Try it — no account needed
          </Badge>
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            See a benchmark run
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            Sample data, wired the same way the real evaluation view reads results.
          </p>
        </Reveal>

        <Reveal delay={0.1} className="mx-auto mt-12 max-w-2xl">
          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle className="flex items-center gap-2 text-base">
                  <FlaskConical className="h-4 w-4 text-primary" />
                  Evaluation Preview
                </CardTitle>
                <CardDescription>Switch the prompt and model to compare.</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                    Prompt version
                  </label>
                  <Select value={promptId} onValueChange={(v) => setPromptId(v as typeof promptId)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PROMPTS.map((p) => (
                        <SelectItem key={p.id} value={p.id}>
                          {p.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <label className="mb-1.5 block text-xs font-medium text-muted-foreground">
                    Model
                  </label>
                  <Select value={modelId} onValueChange={(v) => setModelId(v as typeof modelId)}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {MODELS.map((m) => (
                        <SelectItem key={m.id} value={m.id}>
                          {m.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  { label: "Score", value: metrics.score, suffix: "" },
                  { label: "Accuracy", value: metrics.accuracy, suffix: "%" },
                  { label: "Latency", value: metrics.latencyMs, suffix: "ms" },
                  { label: "Cost / 1K", value: metrics.costPer1k, suffix: "$" },
                ].map((stat) => (
                  <div
                    key={stat.label}
                    className="overflow-hidden rounded-lg border border-border/60 bg-secondary/40 p-3 text-center transition-colors duration-200 hover:border-primary/30"
                  >
                    <motion.div
                      key={`${stat.label}-${stat.value}`}
                      initial={{ opacity: 0, y: 6 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ type: "spring", stiffness: 380, damping: 28 }}
                      className="text-xl font-semibold tabular-nums text-foreground"
                    >
                      {stat.suffix === "$" ? `$${stat.value}` : `${stat.value}${stat.suffix}`}
                    </motion.div>
                    <div className="mt-1 text-[11px] text-muted-foreground">{stat.label}</div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </Reveal>
      </div>
    </section>
  );
}
