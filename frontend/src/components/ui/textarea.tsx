"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Grow with content up to maxAutoHeight, then scroll. On by default. */
  autoResize?: boolean;
  /** Height cap in px once auto-resizing (default 320). */
  maxAutoHeight?: number;
}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, autoResize = true, maxAutoHeight = 320, onInput, rows = 3, ...props }, ref) => {
    const innerRef = React.useRef<HTMLTextAreaElement | null>(null);

    const resize = React.useCallback(() => {
      const el = innerRef.current;
      if (!el || !autoResize) return;
      // Collapse to measure the real content height, then grow to it (capped).
      el.style.height = "auto";
      const next = Math.min(el.scrollHeight + 2, maxAutoHeight);
      el.style.height = `${next}px`;
      el.style.overflowY = el.scrollHeight + 2 > maxAutoHeight ? "auto" : "hidden";
    }, [autoResize, maxAutoHeight]);

    // Runs after every render so programmatic value changes (react-hook-form
    // reset(), defaultValue swaps) are measured too, not just user input.
    React.useLayoutEffect(() => {
      resize();
    });

    return (
      <textarea
        rows={rows}
        className={cn(
          "flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm leading-relaxed shadow-sm",
          "transition-[height,border-color,box-shadow] duration-100 ease-out",
          "placeholder:text-muted-foreground",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background",
          "disabled:cursor-not-allowed disabled:opacity-50",
          autoResize && "resize-none",
          className,
        )}
        ref={(node) => {
          innerRef.current = node;
          if (typeof ref === "function") ref(node);
          else if (ref) ref.current = node;
        }}
        onInput={(e) => {
          resize();
          onInput?.(e);
        }}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
