import * as React from "react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

interface FieldProps {
  label?: string;
  htmlFor?: string;
  error?: string;
  hint?: string;
  required?: boolean;
  /** Optional right-aligned meta text next to the label, e.g. "120/500". */
  counter?: string;
  className?: string;
  children: React.ReactNode;
}

/** Label + control + error/hint, used to keep forms consistent. */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  required,
  counter,
  className,
  children,
}: FieldProps) {
  const errorId = htmlFor && error ? `${htmlFor}-error` : undefined;
  const hintId = htmlFor && hint && !error ? `${htmlFor}-hint` : undefined;

  // Wire aria attributes onto the single form control so screen readers
  // announce required state and the error/hint text without every call site
  // repeating it.
  const control =
    React.isValidElement(children) && (errorId || hintId || required)
      ? React.cloneElement(
          children as React.ReactElement<{
            "aria-invalid"?: boolean;
            "aria-describedby"?: string;
            "aria-required"?: boolean;
          }>,
          {
            "aria-invalid": error ? true : undefined,
            "aria-describedby": errorId ?? hintId,
            "aria-required": required || undefined,
          },
        )
      : children;

  return (
    <div className={cn("space-y-1.5", className)}>
      {(label || counter) && (
        <div className="flex items-baseline justify-between gap-2">
          {label && (
            <Label htmlFor={htmlFor}>
              {label}
              {required && (
                <span className="ml-0.5 text-destructive" aria-hidden="true">
                  *
                </span>
              )}
            </Label>
          )}
          {counter && (
            <span className="text-[11px] tabular-nums text-muted-foreground">{counter}</span>
          )}
        </div>
      )}
      {control}
      {error ? (
        <p
          id={errorId}
          role="alert"
          className="animate-in fade-in-0 slide-in-from-top-1 text-xs font-medium text-destructive duration-200"
        >
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
