"use client";

import { useMemo, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronDown,
  ChevronRight,
  Network,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { SeverityBadge } from "@/components/common/severity";
import { useKnowledgeNode, useKnowledgeNodes } from "@/lib/hooks/use-knowledge";
import { humanize } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  KnowledgeNeighborResponse,
  KnowledgeNodeResponse,
  KnowledgeNodeType,
} from "@/lib/api/knowledge";

const NODE_TYPE_OPTIONS: { value: KnowledgeNodeType; label: string }[] = [
  { value: "FAILURE_MODE", label: "Failure mode" },
  { value: "REGRESSION", label: "Regression" },
  { value: "RECOMMENDATION", label: "Recommendation" },
  { value: "OPTIMIZATION", label: "Optimization" },
];

type Variant = "default" | "secondary" | "outline" | "success" | "destructive" | "muted";

const NODE_TYPE_VARIANT: Record<KnowledgeNodeType, Variant> = {
  FAILURE_MODE: "destructive",
  REGRESSION: "secondary",
  RECOMMENDATION: "default",
  OPTIMIZATION: "success",
};

export function KnowledgePanel() {
  const [type, setType] = useState<"" | KnowledgeNodeType>("");
  const [category, setCategory] = useState<string>("");
  const [expanded, setExpanded] = useState<string | null>(null);

  // Fetch the unfiltered set once to derive the category options.
  const { data: allNodes } = useKnowledgeNodes({});
  const categories = useMemo(() => {
    const set = new Set<string>();
    (allNodes ?? []).forEach((n) => set.add(n.category));
    return Array.from(set).sort();
  }, [allNodes]);

  const { data, isLoading, isError } = useKnowledgeNodes({
    type: type || undefined,
    category: category || undefined,
  });

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Type</span>
          <Select
            value={type}
            onChange={(e) => {
              setType(e.target.value as "" | KnowledgeNodeType);
              setExpanded(null);
            }}
            className="h-9 w-auto min-w-[12rem]"
          >
            <option value="">All types</option>
            {NODE_TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Category</span>
          <Select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setExpanded(null);
            }}
            className="h-9 w-auto min-w-[12rem]"
            disabled={categories.length === 0}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {humanize(c)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Network} title="Couldn't load knowledge" description="Please try again." />
      ) : (data?.length ?? 0) === 0 ? (
        <EmptyState
          icon={Network}
          title="No knowledge nodes"
          description="No entries match the selected filters."
        />
      ) : (
        <div className="space-y-3">
          {(data ?? []).map((node) => (
            <NodeCard
              key={node.nodeKey}
              node={node}
              open={expanded === node.nodeKey}
              onToggle={() =>
                setExpanded(expanded === node.nodeKey ? null : node.nodeKey)
              }
            />
          ))}
        </div>
      )}
    </div>
  );
}

function NodeCard({
  node,
  open,
  onToggle,
}: {
  node: KnowledgeNodeResponse;
  open: boolean;
  onToggle: () => void;
}) {
  return (
    <Card>
      <CardContent className="p-0">
        <button
          type="button"
          onClick={onToggle}
          className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-muted/40"
        >
          <span className="mt-0.5 text-muted-foreground">
            {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </span>
          <div className="min-w-0 flex-1 space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={NODE_TYPE_VARIANT[node.nodeType]}>{humanize(node.nodeType)}</Badge>
              <Badge variant="outline">{humanize(node.category)}</Badge>
            </div>
            <p className="font-medium leading-tight text-foreground">{node.title}</p>
            <p className="line-clamp-2 text-sm text-muted-foreground">{node.summary}</p>
          </div>
          <span className="shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            {node.occurrenceCount} {node.occurrenceCount === 1 ? "occurrence" : "occurrences"}
          </span>
        </button>

        {open && <NodeDetail node={node} />}
      </CardContent>
    </Card>
  );
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm leading-relaxed text-foreground">{children}</div>
    </div>
  );
}

function NodeDetail({ node }: { node: KnowledgeNodeResponse }) {
  const { data, isLoading, isError } = useKnowledgeNode(node.nodeKey);

  return (
    <div className="space-y-4 border-t border-border px-4 py-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Section label="Summary">{node.summary}</Section>
        <Section label="Detection hint">{node.detectionHint}</Section>
        <Section label="Remediation">{node.remediation}</Section>
        <Section label="Expected improvement">
          <span className="text-success">{node.expectedImprovement}</span>
        </Section>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <SeverityBadge severity={node.defaultSeverity} />
        <Badge variant="outline">{humanize(node.defaultConfidence)} confidence</Badge>
      </div>

      {node.tags.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {node.tags.map((tag) => (
            <span key={tag} className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
              #{tag}
            </span>
          ))}
        </div>
      )}

      <div className="space-y-2 border-t border-border pt-3">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Related nodes
        </p>
        {isLoading ? (
          <Skeleton className="h-10 w-full" />
        ) : isError || !data ? (
          <p className="text-xs text-destructive">Couldn&apos;t load related nodes.</p>
        ) : data.neighbors.length === 0 ? (
          <p className="text-sm text-muted-foreground">No related nodes.</p>
        ) : (
          <ul className="space-y-2">
            {data.neighbors.map((neighbor, i) => (
              <NeighborRow key={`${neighbor.node.nodeKey}-${i}`} neighbor={neighbor} />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function NeighborRow({ neighbor }: { neighbor: KnowledgeNeighborResponse }) {
  const outgoing = neighbor.direction === "OUTGOING";
  return (
    <li className="flex items-start gap-2 text-sm">
      <span
        className={cn(
          "mt-0.5 shrink-0",
          outgoing ? "text-primary" : "text-muted-foreground",
        )}
        aria-hidden
      >
        {outgoing ? (
          <ArrowUpRight className="h-4 w-4" />
        ) : (
          <ArrowDownLeft className="h-4 w-4" />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="muted">{humanize(neighbor.relation)}</Badge>
          <span className="text-xs text-muted-foreground">
            {outgoing ? "Outgoing" : "Incoming"}
          </span>
        </div>
        <p className="mt-0.5 font-medium leading-tight text-foreground">{neighbor.node.title}</p>
      </div>
    </li>
  );
}
