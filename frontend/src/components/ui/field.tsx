import * as React from "react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

interface FieldProps {
  label?: string;
  htmlFor?: string;
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}

/** Label + control + error/hint, used to keep forms consistent. */
export function Field({ label, htmlFor, error, hint, required, className, children }: FieldProps) {
  const errorId = htmlFor && error ? `${htmlFor}-error` : undefined;
  const hintId = htmlFor && hint && !error ? `${htmlFor}-hint` : undefined;

  // Wire aria-invalid/aria-describedby onto the single form control so screen
  // readers announce the error/hint text without every call site repeating it.
  const control =
    React.isValidElement(children) && (errorId || hintId)
      ? React.cloneElement(children as React.ReactElement<{ "aria-invalid"?: boolean; "aria-describedby"?: string }>, {
          "aria-invalid": error ? true : undefined,
          "aria-describedby": errorId ?? hintId,
        })
      : children;

  return (
    <div className={cn("space-y-1.5", className)}>
      {label && (
        <Label htmlFor={htmlFor}>
          {label}
          {required && <span className="ml-0.5 text-destructive">*</span>}
        </Label>
      )}
      {control}
      {error ? (
        <p id={errorId} role="alert" className="text-xs font-medium text-destructive">
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
