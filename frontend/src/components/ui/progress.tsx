import * as React from "react";
import { cn } from "@/lib/utils";

interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Current value, 0–100. */
  value?: number;
  /** Tailwind class for the fill, e.g. "bg-primary" (default), "bg-destructive". */
  indicatorClassName?: string;
}

/**
 * A slim, accessible progress bar (`role="progressbar"`). Used for determinate
 * progress and, e.g., the password-strength meter. Purely presentational.
 */
const Progress = React.forwardRef<HTMLDivElement, ProgressProps>(
  ({ className, value = 0, indicatorClassName, ...props }, ref) => {
    const clamped = Math.min(100, Math.max(0, value));
    return (
      <div
        ref={ref}
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(clamped)}
        className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}
        {...props}
      >
        <div
          className={cn("h-full rounded-full bg-primary transition-[width] duration-300 ease-out", indicatorClassName)}
          style={{ width: `${clamped}%` }}
        />
      </div>
    );
  },
);
Progress.displayName = "Progress";

export { Progress };
