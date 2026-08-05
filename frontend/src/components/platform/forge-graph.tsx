"use client";

import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MiniMap,
  Panel,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import {
  Download,
  Lightbulb,
  Maximize2,
  Network,
  RotateCcw,
  Search,
  X,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { InfoButton } from "@/components/platform/info-button";
import { usePlatformGraph } from "@/lib/hooks/use-platform-graph";
import type { GraphNode as ApiGraphNode, PlatformGraphResponse } from "@/lib/api/platform";
import { knowledgeHref } from "@/lib/artifact-links";
import { AskBrok } from "@/components/brok/ask-brok";
import { KNOWLEDGE_KINDS } from "@/lib/knowledge-meta";
import { REASONING_TREATMENT, substrateMeta } from "@/lib/substrate";
import { buildGraphSvg, downloadJson, downloadPng, downloadSvg, type ExportNode } from "@/lib/graph-export";
import { humanize } from "@/lib/format";
import { cn } from "@/lib/utils";

const KNOWLEDGE_NODE_TYPES = new Set<string>(KNOWLEDGE_KINDS);

/**
 * Node identity comes from the one structural grammar (lib/substrate.ts) — cool, low-chroma hues that say
 * *what a thing is*. Verdict colours never appear on a node's identity; the graph shows structure, and state
 * is carried by the verdict language wherever it is stated in words.
 */
function meta(type: string) {
  const m = substrateMeta(type);
  return { icon: m.icon, tier: m.tier, accent: m.accent, iconColor: m.color, hex: m.hex };
}

const NODE_WIDTH = 184;
const X_GAP = 214;
const Y_GAP = 128;
const EDGE_DEFAULT = "#3f3f46";
const EDGE_ACTIVE = "#8b5cf6";

type ForgeNodeData = { node: ApiGraphNode; active: boolean };

/** A single artifact card. Handles are near-invisible; edges connect parent (bottom) to child (top). */
function ForgeNode(props: NodeProps) {
  const { node, active } = props.data as unknown as ForgeNodeData;
  const m = meta(node.type);
  const Icon = m.icon;
  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-lg border bg-card px-3 py-2 shadow-sm transition-opacity",
        m.accent,
        KNOWLEDGE_NODE_TYPES.has(node.type) && REASONING_TREATMENT,
        props.selected && "ring-2 ring-primary",
        !active && "opacity-20",
      )}
      style={{ width: NODE_WIDTH }}
    >
      <Handle type="target" position={Position.Top} className="!h-1.5 !w-1.5 !border-0 !bg-muted-foreground/40" />
      <Icon className={cn("h-4 w-4 shrink-0", m.iconColor)} />
      <div className="min-w-0">
        <p className="truncate text-xs font-medium text-foreground">{node.label}</p>
        {node.subtitle && <p className="truncate text-[10px] uppercase tracking-wide text-muted-foreground">{node.subtitle}</p>}
      </div>
      <Handle type="source" position={Position.Bottom} className="!h-1.5 !w-1.5 !border-0 !bg-muted-foreground/40" />
    </div>
  );
}

const nodeTypes = { forge: ForgeNode };

/** Deterministic layered layout: y by artifact tier, x spread evenly within each tier. */
function computeLayout(nodes: ApiGraphNode[]): Map<string, { x: number; y: number }> {
  const byTier = new Map<number, ApiGraphNode[]>();
  for (const n of nodes) {
    const tier = meta(n.type).tier;
    if (!byTier.has(tier)) byTier.set(tier, []);
    byTier.get(tier)!.push(n);
  }
  const pos = new Map<string, { x: number; y: number }>();
  for (const [tier, group] of byTier) {
    group.sort((a, b) => a.label.localeCompare(b.label));
    const count = group.length;
    group.forEach((n, i) => {
      pos.set(n.id, { x: (i - (count - 1) / 2) * X_GAP, y: tier * Y_GAP });
    });
  }
  return pos;
}

export function ForgeGraph({
  organizationId,
  height = 560,
  compact = false,
  focusNodeId,
  onNodeSelect,
}: {
  organizationId: string;
  height?: number;
  compact?: boolean;
  /**
   * The node the visitor arrived about, e.g. "evaluation:<uuid>". The graph selects it and travels to it on
   * open, so an engineer never hunts the canvas for the thing they were already looking at.
   */
  focusNodeId?: string;
  /**
   * Notified when the visitor selects (or deselects) a node. Brok workspace uses this to move the
   * conversation's focus with the graph, so clicking the canvas changes what the next question is about.
   */
  onNodeSelect?: (nodeId: string | null) => void;
}) {
  const [showKnowledge, setShowKnowledge] = useState(false);
  const { data, isLoading, isError } = usePlatformGraph(organizationId, {
    includeKnowledge: !compact && showKnowledge,
  });

  if (isLoading) return <Skeleton style={{ height }} className="w-full rounded-xl" />;
  if (isError || !data) {
    if (compact) return null;
    return (
      <EmptyState
        icon={Network}
        title="Engineering graph unavailable"
        description="The platform graph isn't available for this workspace yet."
      />
    );
  }
  if (data.nodes.length <= 1) {
    if (compact) return null;
    return (
      <EmptyState
        icon={Network}
        title="Your engineering graph is empty"
        description="Add providers, agents, prompts, datasets and evaluations — they'll connect here automatically."
      />
    );
  }

  return (
    <div className="relative w-full overflow-hidden rounded-xl border border-border bg-background" style={{ height }}>
      <ReactFlowProvider>
        <GraphCanvas
          data={data}
          compact={compact}
          organizationId={organizationId}
          showKnowledge={showKnowledge}
          onToggleKnowledge={() => setShowKnowledge((v) => !v)}
          focusNodeId={focusNodeId}
          onNodeSelect={onNodeSelect}
        />
      </ReactFlowProvider>
    </div>
  );
}

function GraphCanvas({
  data,
  compact,
  organizationId,
  showKnowledge,
  onToggleKnowledge,
  focusNodeId,
  onNodeSelect,
}: {
  data: PlatformGraphResponse;
  compact: boolean;
  organizationId: string;
  showKnowledge: boolean;
  onToggleKnowledge: () => void;
  focusNodeId?: string;
  onNodeSelect?: (nodeId: string | null) => void;
}) {
  const rf = useReactFlow();
  const [selectedId, setSelectedId] = useState<string | null>(focusNodeId ?? null);
  const [query, setQuery] = useState("");
  const [hiddenTypes, setHiddenTypes] = useState<Set<string>>(new Set());
  const searchRef = useRef<HTMLInputElement>(null);

  const layout = useMemo(() => computeLayout(data.nodes), [data]);
  const presentTypes = useMemo(() => {
    const seen: string[] = [];
    for (const n of data.nodes) if (!seen.includes(n.type)) seen.push(n.type);
    return seen.sort((a, b) => meta(a).tier - meta(b).tier);
  }, [data]);

  const neighborIds = useMemo(() => {
    if (!selectedId) return null;
    const s = new Set<string>([selectedId]);
    for (const e of data.edges) {
      if (e.source === selectedId) s.add(e.target);
      if (e.target === selectedId) s.add(e.source);
    }
    return s;
  }, [selectedId, data]);

  const matchIds = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return null;
    return new Set(data.nodes.filter((n) => n.label.toLowerCase().includes(q) || n.type.includes(q)).map((n) => n.id));
  }, [query, data]);

  // Animate the viewport to the first search match.
  useEffect(() => {
    if (!matchIds || matchIds.size === 0) return;
    const first = data.nodes.find((n) => matchIds.has(n.id));
    const p = first ? layout.get(first.id) : undefined;
    if (p) rf.setCenter(p.x + NODE_WIDTH / 2, p.y + 24, { zoom: 1.1, duration: 600 });
  }, [matchIds, data, layout, rf]);

  // Arriving from an artifact: travel to the node the engineer came about, so nobody hunts the canvas.
  const focusedRef = useRef<string | null>(null);
  useEffect(() => {
    if (!focusNodeId || focusedRef.current === focusNodeId) return;
    const p = layout.get(focusNodeId);
    if (!p) return;
    focusedRef.current = focusNodeId;
    setSelectedId(focusNodeId);
    // Motion "Focus" (§10): travel rather than teleport, so spatial memory survives.
    rf.setCenter(p.x + NODE_WIDTH / 2, p.y + 24, { zoom: 1.15, duration: 500 });
  }, [focusNodeId, layout, rf]);

  const activeFor = useCallback(
    (id: string) => {
      if (neighborIds && !neighborIds.has(id)) return false;
      if (matchIds && !matchIds.has(id)) return false;
      return true;
    },
    [neighborIds, matchIds],
  );

  const rfNodes: Node[] = useMemo(
    () =>
      data.nodes.map((n) => ({
        id: n.id,
        type: "forge",
        position: layout.get(n.id) ?? { x: 0, y: 0 },
        data: { node: n, active: activeFor(n.id) },
        draggable: false,
        hidden: hiddenTypes.has(n.type),
      })),
    [data, layout, activeFor, hiddenTypes],
  );

  const rfEdges: Edge[] = useMemo(
    () =>
      data.edges.map((e) => {
        const incident = selectedId ? e.source === selectedId || e.target === selectedId : false;
        return {
          id: e.id,
          source: e.source,
          target: e.target,
          type: "smoothstep",
          animated: incident,
          label: compact ? undefined : e.relation,
          labelStyle: { fill: "#a1a1aa", fontSize: 9 },
          labelBgStyle: { fill: "transparent" },
          hidden: hiddenTypes.has(nodeType(data, e.source)) || hiddenTypes.has(nodeType(data, e.target)),
          style: {
            stroke: incident ? EDGE_ACTIVE : EDGE_DEFAULT,
            strokeWidth: incident ? 2 : 1,
            opacity: selectedId ? (incident ? 1 : 0.1) : 0.5,
          },
        };
      }),
    [data, selectedId, compact, hiddenTypes],
  );

  const selectedNode = data.nodes.find((n) => n.id === selectedId) ?? null;

  // Keyboard shortcuts (f = fit, r = reset, / = search, Esc = clear) — ignored while typing.
  useEffect(() => {
    if (compact) return;
    const handler = (e: KeyboardEvent) => {
      const typing = document.activeElement?.tagName === "INPUT" || document.activeElement?.tagName === "TEXTAREA";
      if (e.key === "/" && !typing) {
        e.preventDefault();
        searchRef.current?.focus();
      } else if (e.key === "Escape") {
        setSelectedId(null);
        setQuery("");
      } else if ((e.key === "f" || e.key === "r") && !typing) {
        rf.fitView({ padding: 0.2, duration: 500 });
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [compact, rf]);

  const doExport = async (fmt: "png" | "svg" | "json") => {
    const visible = data.nodes.filter((n) => !hiddenTypes.has(n.type));
    if (fmt === "json") {
      return downloadJson("forge-graph.json", {
        nodes: visible,
        edges: data.edges.filter((e) => !hiddenTypes.has(nodeType(data, e.source)) && !hiddenTypes.has(nodeType(data, e.target))),
      });
    }
    const exportNodes: ExportNode[] = visible.map((n) => {
      const p = layout.get(n.id) ?? { x: 0, y: 0 };
      return { id: n.id, x: p.x, y: p.y, label: n.label, sub: n.subtitle ?? n.type, accent: meta(n.type).hex };
    });
    const exportEdges = data.edges
      .filter((e) => !hiddenTypes.has(nodeType(data, e.source)) && !hiddenTypes.has(nodeType(data, e.target)))
      .map((e) => ({ source: e.source, target: e.target }));
    const { svg, width, height } = buildGraphSvg(exportNodes, exportEdges, "Forge Graph");
    if (fmt === "svg") return downloadSvg("forge-graph.svg", svg);
    await downloadPng("forge-graph.png", svg, width, height);
  };

  return (
    <>
      <ReactFlow
        nodes={rfNodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        onNodeClick={(_, node) =>
          setSelectedId((prev) => {
            const next = prev === node.id ? null : node.id;
            onNodeSelect?.(next);
            return next;
          })
        }
        onPaneClick={() => setSelectedId(null)}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.2}
        maxZoom={1.75}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={false}
        onlyRenderVisibleElements
      >
        <Background variant={BackgroundVariant.Dots} gap={22} size={1} color={EDGE_DEFAULT} />
        {!compact && <Controls showInteractive={false} />}
        {!compact && data.nodes.length > 24 && (
          <MiniMap pannable zoomable nodeColor="#71717a" maskColor="rgba(0,0,0,0.35)" />
        )}

        {!compact && (
          <Panel position="top-left" className="!m-2 flex flex-col gap-1.5">
            <div className="flex items-center gap-1.5">
              <div className="relative">
                <Search className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                <input
                  ref={searchRef}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search graph…  ( / )"
                  className="h-8 w-52 rounded-md border border-border bg-card/90 pl-7 pr-2 text-xs text-foreground outline-none backdrop-blur placeholder:text-muted-foreground focus:border-primary/50"
                />
              </div>
              <ToolButton title="Toggle reasoning overlay" active={showKnowledge} onClick={onToggleKnowledge}>
                <Lightbulb className="h-3.5 w-3.5" />
              </ToolButton>
              <InfoButton feature={showKnowledge ? "reasoning-overlay" : "forge-graph"} label="" />
            </div>
            <div className="flex max-w-[22rem] flex-wrap gap-1">
              {presentTypes.map((t) => {
                const hidden = hiddenTypes.has(t);
                const m = meta(t);
                const Icon = m.icon;
                return (
                  <button
                    key={t}
                    onClick={() =>
                      setHiddenTypes((prev) => {
                        const next = new Set(prev);
                        if (next.has(t)) next.delete(t);
                        else next.add(t);
                        return next;
                      })
                    }
                    className={cn(
                      "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium backdrop-blur transition-colors",
                      hidden
                        ? "border-border bg-card/70 text-muted-foreground/50 line-through"
                        : "border-border bg-card/90 text-foreground",
                    )}
                  >
                    <Icon className={cn("h-3 w-3", hidden ? "text-muted-foreground/40" : m.iconColor)} />
                    {humanize(t)}
                  </button>
                );
              })}
            </div>
          </Panel>
        )}

        {!compact && (
          <Panel position="top-right" className="!m-2 flex items-center gap-1">
            <ToolButton title="Fit to screen (f)" onClick={() => rf.fitView({ padding: 0.2, duration: 500 })}>
              <Maximize2 className="h-3.5 w-3.5" />
            </ToolButton>
            <ToolButton title="Reset view (r)" onClick={() => { setSelectedId(null); setQuery(""); rf.fitView({ padding: 0.2, duration: 500 }); }}>
              <RotateCcw className="h-3.5 w-3.5" />
            </ToolButton>
            <div className="mx-0.5 h-4 w-px bg-border" />
            <ToolButton title="Export PNG" onClick={() => doExport("png")}><Download className="h-3.5 w-3.5" /></ToolButton>
            <ToolButton title="Export SVG" onClick={() => doExport("svg")}><span className="text-[10px] font-semibold">SVG</span></ToolButton>
            <ToolButton title="Export JSON" onClick={() => doExport("json")}><span className="text-[10px] font-semibold">JSON</span></ToolButton>
          </Panel>
        )}
      </ReactFlow>

      {compact && (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 flex justify-end p-3">
          <span className="rounded-md border border-border bg-card/80 px-2 py-1 text-xs text-muted-foreground backdrop-blur">
            {data.nodes.length} artifacts · {data.edges.length} relationships
          </span>
        </div>
      )}

      {!compact && selectedNode && (
        <NodeDetails organizationId={organizationId} node={selectedNode} onClose={() => setSelectedId(null)} />
      )}
    </>
  );
}

function ToolButton({
  title,
  active,
  onClick,
  children,
}: {
  title: string;
  active?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      title={title}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 min-w-8 items-center justify-center rounded-md border border-border px-1.5 backdrop-blur transition-colors",
        active ? "bg-primary/15 text-primary" : "bg-card/90 text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

function nodeType(data: PlatformGraphResponse, id: string): string {
  return data.nodes.find((n) => n.id === id)?.type ?? "";
}

function NodeDetails({
  organizationId,
  node,
  onClose,
}: {
  organizationId: string;
  node: ApiGraphNode;
  onClose: () => void;
}) {
  const m = meta(node.type);
  const Icon = m.icon;
  const href = deepLink(organizationId, node);
  return (
    <div className="absolute right-0 top-0 flex h-full w-72 flex-col border-l border-border bg-card/95 p-4 backdrop-blur">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <Icon className={cn("h-4 w-4", m.iconColor)} />
          <span className="text-xs uppercase tracking-wide text-muted-foreground">{node.type}</span>
        </div>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground" aria-label="Close">
          <X className="h-4 w-4" />
        </button>
      </div>
      <p className="mt-3 break-words text-sm font-semibold text-foreground">{node.label}</p>
      {node.subtitle && <p className="text-xs text-muted-foreground">{node.subtitle}</p>}
      {href && (
        <Link
          href={href}
          className="mt-4 flex w-full items-center justify-center rounded-md border border-border bg-secondary px-3 py-1.5 text-xs font-medium text-secondary-foreground transition-colors hover:bg-secondary/80"
        >
          Open {node.type}
        </Link>
      )}
      <AskBrok
        organizationId={organizationId}
        projectId={node.projectId}
        focus={node.id}
        question={`Explain ${node.label}.`}
        label="Ask Brok"
        className="mt-2 justify-center"
      />
    </div>
  );
}

/** Deep-links a node to its page — reasoning nodes open their dedicated knowledge page, artifacts their workspace. */
function deepLink(org: string, node: ApiGraphNode): string | null {
  if (KNOWLEDGE_NODE_TYPES.has(node.type)) return knowledgeHref(org, node.id);
  const proj = node.projectId;
  const id = node.entityId;
  const scoped = (path: string) => `/organizations/${org}/projects/${proj}${path}`;
  switch (node.type) {
    case "project":
      return id ? `/organizations/${org}/projects/${id}` : null;
    case "agent":
      return proj && id ? scoped(`/agents/${id}`) : null;
    case "prompt":
      return proj && id ? scoped(`/prompts/${id}`) : null;
    case "dataset":
      return proj && id ? scoped(`/datasets/${id}`) : null;
    case "evaluation":
      return proj && id ? scoped(`/evaluations/${id}`) : null;
    case "provider":
      return "/providers";
    default:
      return null;
  }
}
