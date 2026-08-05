"use client";

import { useEffect, useState } from "react";
import { Sparkles, X } from "lucide-react";
import { InfoButton } from "@/components/platform/info-button";
import type { InfoFeature } from "@/lib/intelligence-info";
import { cn } from "@/lib/utils";

const STORAGE_PREFIX = "bf.hint.";

/**
 * A lightweight, one-line contextual explainer that a first-time user meets while exploring — never a modal,
 * never a forced walkthrough. Dismissed hints stay dismissed (per browser), so the platform teaches itself
 * without getting in the way. Pairs with an "i" button for the full explanation.
 */
export function FeatureHint({
  id,
  feature,
  children,
  className,
}: {
  id: string;
  feature?: InfoFeature;
  children: React.ReactNode;
  className?: string;
}) {
  const [dismissed, setDismissed] = useState(true); // start hidden to avoid a flash before we read storage

  useEffect(() => {
    try {
      setDismissed(localStorage.getItem(STORAGE_PREFIX + id) === "1");
    } catch {
      setDismissed(false);
    }
  }, [id]);

  if (dismissed) return null;

  const dismiss = () => {
    try {
      localStorage.setItem(STORAGE_PREFIX + id, "1");
    } catch {
      /* ignore */
    }
    setDismissed(true);
  };

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border border-primary/20 bg-primary/5 px-3.5 py-2.5 text-sm",
        className,
      )}
    >
      <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
      <p className="min-w-0 flex-1 text-foreground/90">{children}</p>
      <div className="flex shrink-0 items-center gap-1.5">
        {feature && <InfoButton feature={feature} label="Learn more" />}
        <button
          onClick={dismiss}
          aria-label="Dismiss"
          className="rounded p-0.5 text-muted-foreground transition-colors hover:text-foreground"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
