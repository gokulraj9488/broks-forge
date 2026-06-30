import { Hammer } from "lucide-react";
import { cn } from "@/lib/utils";

export function Logo({ className, showWordmark = true }: { className?: string; showWordmark?: boolean }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-violet-500 shadow-sm">
        <Hammer className="h-4 w-4 text-white" />
      </div>
      {showWordmark && (
        <div className="flex flex-col leading-none">
          <span className="text-sm font-semibold tracking-tight text-foreground">Brok&apos;s Forge</span>
          <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
            AI Engineering
          </span>
        </div>
      )}
    </div>
  );
}
