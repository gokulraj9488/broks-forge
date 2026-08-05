"use client";

import { useEffect, useState } from "react";
import { List, Network } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { WorkspaceSelector } from "@/components/common/workspace-selector";
import { KnowledgePanel } from "@/components/knowledge/knowledge-panel";
import { ForgeGraph } from "@/components/platform/forge-graph";
import { InfoButton } from "@/components/platform/info-button";
import { AskBrok } from "@/components/brok/ask-brok";
import { cn } from "@/lib/utils";

type View = "graph" | "library";

export default function KnowledgePage() {
  const [view, setView] = useState<View>("graph");
  const [focusNodeId, setFocusNodeId] = useState<string | undefined>(undefined);

  // Arriving from an artifact ("see this in the graph") focuses that node instead of dropping the
  // engineer into an unfamiliar canvas (L-48 addressability, §37.8 the graph is an entry, not an exit).
  useEffect(() => {
    const focus = new URLSearchParams(window.location.search).get("focus");
    if (focus) {
      setFocusNodeId(focus);
      setView("graph");
    }
  }, []);

  return (
    <div>
      <PageHeader
        title="Engineering graph"
        description="Your AI engineering organization as one connected system — what exists, what depends on what, and the reasoning layered on top of it."
        action={
          <div className="flex items-center gap-2">
            <div className="inline-flex rounded-lg border border-border p-0.5">
              <ToggleButton active={view === "graph"} onClick={() => setView("graph")} icon={Network} label="Graph" />
              <ToggleButton
                active={view === "library"}
                onClick={() => setView("library")}
                icon={List}
                label="Knowledge library"
              />
            </div>
            <InfoButton feature="forge-graph" />
          </div>
        }
      />

      {view === "graph" ? (
        <WorkspaceSelector>
          {({ organizationId, projectId }) => (
            <div className="space-y-3">
              <div className="flex justify-end">
                {/* The graph shows the system; Brok explains it. */}
                <AskBrok
                  organizationId={organizationId}
                  projectId={projectId}
                  focus={focusNodeId}
                  question="Open the graph."
                />
              </div>
              <ForgeGraph organizationId={organizationId} height={640} focusNodeId={focusNodeId} />
            </div>
          )}
        </WorkspaceSelector>
      ) : (
        <KnowledgePanel />
      )}
    </div>
  );
}

function ToggleButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: typeof Network;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
        active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}
