"use client";

import Link from "next/link";
import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Brok's presence on every other surface.
 *
 * <p>Constitutionally Brok is not a floating widget that follows you around — it is a workspace you
 * travel to, carrying the engineering context you were already in. This affordance is that journey: it hands
 * the current artifact and, where the surface knows one, the exact question worth asking about it.
 *
 * <p>It is a link rather than a modal on purpose. Opening a conversation should feel like moving into the
 * partner's room with your notes, not like a chat window appearing over your work.
 */
export function AskBrok({
  organizationId,
  projectId,
  focus,
  question,
  label = "Ask Brok",
  variant = "subtle",
  className,
}: {
  organizationId: string;
  projectId?: string | null;
  /** The node id the conversation should open focused on, e.g. "evaluation:<uuid>". */
  focus?: string | null;
  /** The question to ask on arrival. Omit to open the workspace focused but silent. */
  question?: string | null;
  label?: string;
  variant?: "subtle" | "primary";
  className?: string;
}) {
  const params = new URLSearchParams({ org: organizationId });
  if (projectId) {
    params.set("project", projectId);
  }
  if (focus) {
    params.set("focus", focus);
  }
  if (question) {
    params.set("q", question);
  }

  return (
    <Link
      href={`/brok?${params.toString()}`}
      title={question ?? label}
      className={cn(
        // The label is often a whole question about a named artifact ("Show every artifact affected
        // by Checkout Quality — after prompt change"), so it must be free to wrap. It used to be
        // shrink-0, which inside a flex row pins the link at its max-content width and pushed the
        // whole page wider than a phone screen. Wrapping keeps every word visible; clipping would not.
        "inline-flex max-w-full items-center gap-1.5 rounded-lg text-left text-xs font-medium transition-colors",
        variant === "primary"
          ? "bg-primary px-3 py-2 text-primary-foreground hover:opacity-90"
          : "border border-border px-2.5 py-1.5 text-muted-foreground hover:border-primary/40 hover:text-foreground",
        className,
      )}
    >
      <Sparkles className="h-3.5 w-3.5 shrink-0" />
      {label}
    </Link>
  );
}
