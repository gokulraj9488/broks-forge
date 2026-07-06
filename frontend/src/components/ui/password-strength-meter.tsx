"use client";

import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { evaluatePasswordStrength } from "@/lib/password-strength";

/**
 * A compact password-strength meter: a coloured bar plus a one-word verdict.
 * Renders nothing until the user starts typing.
 */
export function PasswordStrengthMeter({ value, className }: { value: string; className?: string }) {
  if (!value) return null;
  const { label, percent, indicatorClassName } = evaluatePasswordStrength(value);

  return (
    <div className={cn("space-y-1", className)} aria-live="polite">
      <Progress value={percent} indicatorClassName={indicatorClassName} className="h-1.5" />
      <p className="text-[11px] text-muted-foreground">
        Password strength: <span className="font-medium text-foreground">{label}</span>
      </p>
    </div>
  );
}
