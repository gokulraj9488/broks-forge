"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

export interface TabItem<T extends string> {
  key: T;
  label: string;
  /** Optional adornment rendered after the label (e.g. a "setup required" dot). */
  badge?: React.ReactNode;
}

/**
 * Underline-style tab strip with WAI-ARIA tabs semantics: roving tabindex,
 * Arrow/Home/End keyboard navigation and aria-selected state.
 */
export function TabsBar<T extends string>({
  tabs,
  value,
  onChange,
  className,
}: {
  tabs: TabItem<T>[];
  value: T;
  onChange: (key: T) => void;
  className?: string;
}) {
  const refs = React.useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (event: React.KeyboardEvent, index: number) => {
    let next: number | null = null;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    if (next === null) return;
    event.preventDefault();
    onChange(tabs[next].key);
    refs.current[next]?.focus();
  };

  return (
    <div role="tablist" className={cn("flex gap-1 border-b border-border", className)}>
      {tabs.map((tab, index) => {
        const active = tab.key === value;
        return (
          <button
            key={tab.key}
            ref={(node) => {
              refs.current[index] = node;
            }}
            type="button"
            role="tab"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            onClick={() => onChange(tab.key)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              "relative -mb-px rounded-t-sm border-b-2 px-3 py-2.5 text-sm font-medium transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring",
              active
                ? "border-primary text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            <span className="inline-flex items-center gap-1.5">
              {tab.label}
              {tab.badge}
            </span>
          </button>
        );
      })}
    </div>
  );
}
