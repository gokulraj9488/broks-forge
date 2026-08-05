"use client";

import { useState } from "react";
import { Boxes, Lightbulb } from "lucide-react";
import { RegistryPanel } from "@/components/registry/registry-panel";
import { KnowledgeCatalog } from "@/components/registry/knowledge-catalog";
import { InfoButton } from "@/components/platform/info-button";
import { FeatureHint } from "@/components/platform/feature-hint";
import { cn } from "@/lib/utils";

type Scope = "artifacts" | "knowledge";

/**
 * One registry, two catalogs: real engineering artifacts (P9) and the derived engineering-knowledge objects
 * (P11). The scope toggle keeps this a single discovery surface rather than introducing a second registry.
 */
export function RegistryExplorer({ organizationId }: { organizationId: string }) {
  const [scope, setScope] = useState<Scope>("artifacts");
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="inline-flex rounded-lg border border-border bg-muted/40 p-0.5 text-sm">
          <ScopeTab active={scope === "artifacts"} onClick={() => setScope("artifacts")} icon={Boxes} label="Artifacts" />
          <ScopeTab active={scope === "knowledge"} onClick={() => setScope("knowledge")} icon={Lightbulb} label="Knowledge" />
        </div>
        <InfoButton feature={scope === "artifacts" ? "registry" : "knowledge"} />
      </div>

      {scope === "artifacts" ? (
        <>
          <FeatureHint id="registry-scopes" feature="registry">
            The registry has two scopes. <strong>Artifacts</strong> are the things you build — agents, prompts,
            datasets. <strong>Knowledge</strong> is what your engineering has proven about them. Switch scopes above.
          </FeatureHint>
          <RegistryPanel organizationId={organizationId} />
        </>
      ) : (
        <>
          <FeatureHint id="registry-knowledge" feature="knowledge">
            These are <strong>engineering-knowledge objects</strong> — observations, decisions, evidence and
            knowledge derived from your real work. Open any one to see what created it. This is what a traditional
            observability tool can&rsquo;t show you.
          </FeatureHint>
          <KnowledgeCatalog organizationId={organizationId} />
        </>
      )}
    </div>
  );
}

function ScopeTab({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof Boxes;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-colors",
        active ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </button>
  );
}
