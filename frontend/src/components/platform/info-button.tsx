"use client";

import { useState } from "react";
import { Info, Layers, Lightbulb, Sparkles, Wrench } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { INTELLIGENCE_INFO, type InfoFeature } from "@/lib/intelligence-info";
import { cn } from "@/lib/utils";

/**
 * A small "i" affordance beside a feature that opens a concise, value-focused explanation — what it is, why it
 * exists, how it differs from traditional observability, and how to use it. This is how a first-time user comes
 * to understand Engineering Intelligence without leaving the page.
 */
export function InfoButton({
  feature,
  label,
  className,
}: {
  feature: InfoFeature;
  label?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const info = INTELLIGENCE_INFO[feature];

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`What is ${info.title}?`}
        title={`What is ${info.title}?`}
        className={cn(
          "inline-flex h-6 items-center gap-1 rounded-full border border-border bg-muted/40 px-2 text-[11px] font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground",
          className,
        )}
      >
        <Info className="h-3.5 w-3.5" />
        {label ?? "What's this?"}
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
                <Sparkles className="h-4 w-4 text-primary" />
              </span>
              <div>
                <DialogTitle>{info.title}</DialogTitle>
                <DialogDescription>{info.tagline}</DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <div className="space-y-4 pt-1">
            <Section icon={Lightbulb} title="What it is">{info.what}</Section>
            <Section icon={Wrench} title="Why it exists">{info.why}</Section>
            <Section icon={Layers} title="How it's different from observability">
              {info.vsObservability}
            </Section>
            <Section icon={Sparkles} title="How to use it">{info.howToUse}</Section>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Section({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Lightbulb;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <Icon className="h-3.5 w-3.5 text-primary" />
        {title}
      </p>
      <p className="text-sm leading-relaxed text-foreground/90">{children}</p>
    </div>
  );
}
