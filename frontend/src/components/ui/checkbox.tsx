import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * A palette-consistent checkbox. Wraps a native `<input type="checkbox">` so it
 * works as a drop-in with React Hook Form `register(...)` and stays fully
 * accessible; the accent colour paints the check in the brand sage.
 */
const Checkbox = React.forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">
>(({ className, ...props }, ref) => (
  <input
    ref={ref}
    type="checkbox"
    className={cn(
      "h-4 w-4 shrink-0 cursor-pointer rounded border border-input bg-background align-middle",
      "accent-[hsl(var(--primary))] transition-colors",
      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background",
      "disabled:cursor-not-allowed disabled:opacity-50",
      className,
    )}
    {...props}
  />
));
Checkbox.displayName = "Checkbox";

export { Checkbox };
