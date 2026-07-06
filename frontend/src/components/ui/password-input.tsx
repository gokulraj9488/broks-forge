"use client";

import * as React from "react";
import { ArrowBigUp, Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Tooltip } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

/**
 * Password input with a show/hide toggle and Caps Lock detection. The underlying
 * <input> keeps its name/autocomplete attributes so password managers keep
 * working; the toggle only flips the type between "password" and "text". When
 * Caps Lock is on while the field is focused, a small warning glyph appears.
 */
const PasswordInput = React.forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">
>(({ className, disabled, onKeyUp, onKeyDown, onBlur, ...props }, ref) => {
  const [visible, setVisible] = React.useState(false);
  const [capsLock, setCapsLock] = React.useState(false);

  const detectCapsLock = (event: React.KeyboardEvent<HTMLInputElement>) => {
    try {
      setCapsLock(event.getModifierState("CapsLock"));
    } catch {
      /* getModifierState unsupported — silently skip the hint */
    }
  };

  return (
    <div className="relative">
      <Input
        ref={ref}
        type={visible ? "text" : "password"}
        disabled={disabled}
        className={cn("pr-10", capsLock && "pr-16", className)}
        onKeyUp={(event) => {
          detectCapsLock(event);
          onKeyUp?.(event);
        }}
        onKeyDown={(event) => {
          detectCapsLock(event);
          onKeyDown?.(event);
        }}
        onBlur={(event) => {
          setCapsLock(false);
          onBlur?.(event);
        }}
        {...props}
      />
      {capsLock && (
        <span className="absolute inset-y-0 right-9 flex items-center">
          <Tooltip content="Caps Lock is on">
            <ArrowBigUp className="h-4 w-4 text-warning" aria-label="Caps Lock is on" />
          </Tooltip>
        </span>
      )}
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        disabled={disabled}
        aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}
        className={cn(
          "absolute inset-y-0 right-0 flex w-9 items-center justify-center rounded-md text-muted-foreground transition-colors",
          "hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:ring-offset-background",
          "disabled:pointer-events-none disabled:opacity-50",
        )}
      >
        {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
      </button>
    </div>
  );
});
PasswordInput.displayName = "PasswordInput";

export { PasswordInput };
