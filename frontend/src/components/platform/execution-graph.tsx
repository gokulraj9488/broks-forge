"use client";

import "@xyflow/react/dist/style.css";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  Position,
  ReactFlow,
  ReactFlowProvider,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import {
  AlertTriangle,
  Bot,
  Boxes,
  CheckCircle2,
  Cpu,
  Download,
  FileText,
  FlaskConical,
  Gauge,
  Plug,
  RefreshCw,
  ScrollText,
  Search,
  Sparkles,
  XCircle,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { InfoButton } from "@/components/platform/info-button";
import { VerdictBanner } from "@/components/platform/verdict";
import { useEvaluationRuns, useEvaluationRunResults } from "@/lib/hooks/use-evaluation-jobs";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";
import {
  buildExecutionGraph,
  type ExecNode,
  type ExecNodeKind,
  type ExecStatus,
  type ExecutionGraph as ExecGraph,
} from "@/lib/execution-graph-model";
import { buildGraphSvg, downloadJson, downloadPng, downloadSvg, type ExportNode } from "@/lib/graph-export";
import { cn } from "@/lib/utils";

const KIND_ICON: Record<ExecNodeKind, typeof Bot> = {
  input: Boxes,
  prompt: FileText,
  variables: ScrollText,
  provider: Plug,
  model: Cpu,
  llm: Bot,
  retry: RefreshCw,
  judge: Gauge,
  metric: Sparkles,
  result: FlaskConical,
};

/**
 * In the execution graph every node is a step in one causal chain, so colour here carries STATE only — the
 * verdict palette, nothing else. A step with no verdict of its own stays deliberately neutral chrome rather
 * than borrowing a structural hue, which would blur "what this step is" with "how it went" (L-20).
 */
const STATUS_STYLE: Record<ExecStatus, { border: string; icon: string; hex: string }> = {
  ok: { border: "border-emerald-500/50", icon: "text-emerald-400", hex: "#34d399" },
  fail: { border: "border-rose-500/60", icon: "text-rose-400", hex: "#fb7185" },
  warn: { border: "border-amber-500/50", icon: "text-amber-400", hex: "#fbbf24" },
  skip: { border: "border-zinc-600/50", icon: "text-zinc-400", hex: "#a1a1aa" },
  neutral: { border: "border-border", icon: "text-muted-foreground", hex: "#71717a" },
};

const NODE_W = 210;
const X_GAP = 236;
const Y_GAP = 122;

type ExecNodeData = { node: ExecNode; dim: boolean };

function ExecNodeCard(props: NodeProps) {
  const { node, dim } = props.data as unknown as ExecNodeData;
  const Icon = KIND_ICON[node.kind] ?? Bot;
  const s = STATUS_STYLE[node.status];
  return (
    <div
      className={cn(
        "rounded-lg border-2 bg-card px-3 py-2 shadow-sm transition-opacity",
        "fill-mode-both duration-300 animate-in fade-in slide-in-from-bottom-1",
        s.border,
        props.selected && "ring-2 ring-primary",
        dim && "opacity-25",
        // The exact place the chain stopped — a still red glow while everything alive keeps flowing.
        node.status === "fail" && "shadow-[0_0_0_4px_rgba(251,113,133,0.12)]",
      )}
      style={{ width: NODE_W, animationDelay: `${Math.min(node.tier, 8) * 60}ms` }}
    >
      <Handle type="target" position={Position.Top} className="!h-1.5 !w-1.5 !border-0 !bg-muted-foreground/40" />
      <div className="flex items-center gap-2">
        <Icon className={cn("h-4 w-4 shrink-0", s.icon)} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs font-semibold text-foreground">{node.title}</p>
          {node.subtitle && <p className="truncate text-[10px] text-muted-foreground">{node.subtitle}</p>}
        </div>
        {node.status === "fail" && <XCircle className="h-3.5 w-3.5 shrink-0 text-rose-400" />}
        {node.status === "ok" && <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-emerald-400" />}
        {node.status === "warn" && <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-400" />}
      </div>
      {node.meta.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-x-2 gap-y-0.5 border-t border-border/60 pt-1.5">
          {node.meta.slice(0, 4).map((m) => (
            <span key={m.label} className="text-[10px] text-muted-foreground">
              <span className="text-muted-foreground/60">{m.label}:</span> <span className="text-foreground/80">{m.value}</span>
            </span>
          ))}
        </div>
      )}
      <Handle type="source" position={Position.Bottom} className="!h-1.5 !w-1.5 !border-0 !bg-muted-foreground/40" />
    </div>
  );
}

const nodeTypes = { exec: ExecNodeCard };

function layout(graph: ExecGraph): Map<string, { x: number; y: number }> {
  const byTier = new Map<number, ExecNode[]>();
  for (const n of graph.nodes) {
    if (!byTier.has(n.tier)) byTier.set(n.tier, []);
    byTier.get(n.tier)!.push(n);
  }
  const pos = new Map<string, { x: number; y: number }>();
  for (const [tier, group] of byTier) {
    const count = group.length;
    group.forEach((n, i) => pos.set(n.id, { x: (i - (count - 1) / 2) * X_GAP, y: tier * Y_GAP }));
  }
  return pos;
}

export function ExecutionGraph({
  organizationId,
  projectId,
  job,
}: {
  organizationId: string;
  projectId: string;
  job: EvaluationJobResponse;
}) {
  const { data: runsPage, isLoading } = useEvaluationRuns(organizationId, projectId, job.id, { page: 0, size: 100 });
  const runs = useMemo(() => runsPage?.content ?? [], [runsPage]);

  const defaultRunId = useMemo(() => {
    const failed = runs.find((r) => r.status === "FAILED" || r.passed === false);
    return (failed ?? runs[0])?.id;
  }, [runs]);

  const [selectedRunId, setSelectedRunId] = useState<string | undefined>(undefined);
  // Arriving from Brok's "view the failure graph" opens already narrowed to the broken links, so the
  // engineer lands on what the answer was about rather than on a filter they have to find.
  const [failuresOnly, setFailuresOnly] = useState(false);
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("view") === "failures") {
      setFailuresOnly(true);
    }
  }, []);
  const [selectedNode, setSelectedNode] = useState<ExecNode | null>(null);
  const runId = selectedRunId ?? defaultRunId;
  const run = runs.find((r) => r.id === runId);

  const { data: results } = useEvaluationRunResults(organizationId, projectId, job.id, runId);

  if (isLoading) return <Skeleton className="h-[520px] w-full rounded-xl" />;

  if (runs.length === 0 || !run) {
    return (
      <EmptyState
        icon={FlaskConical}
        title="No runs to visualize yet"
        description="Run this evaluation to capture the runtime execution path — every provider call, its telemetry, and the metrics that judged it."
      />
    );
  }

  const graph = buildExecutionGraph(job, run, results ?? []);
  const pos = layout(graph);
  const dimId = (n: ExecNode) => failuresOnly && n.status === "ok";

  const rfNodes: Node[] = graph.nodes.map((n) => ({
    id: n.id,
    type: "exec",
    position: pos.get(n.id) ?? { x: 0, y: 0 },
    data: { node: n, dim: dimId(n) },
    draggable: false,
  }));
  // Data flows along the paths that stayed alive; a failure edge is deliberately still. The animation
  // stopping is the visualization of the break — the chain moved until here, then it didn't.
  const rfEdges: Edge[] = graph.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    type: "smoothstep",
    animated: e.status === "ok" || e.status === "warn",
    label: e.label,
    labelStyle: { fill: "#a1a1aa", fontSize: 9 },
    labelBgStyle: { fill: "transparent" },
    style: {
      stroke: e.status === "fail" ? "#fb7185" : e.status === "warn" ? "#fbbf24" : e.status === "ok" ? "#34d399" : "#3f3f46",
      strokeWidth: e.status === "fail" ? 2.25 : 1.4,
      opacity: failuresOnly && e.status === "ok" ? 0.15 : 0.85,
    },
  }));

  const exportNodes: ExportNode[] = graph.nodes.map((n) => {
    const p = pos.get(n.id) ?? { x: 0, y: 0 };
    return { id: n.id, x: p.x, y: p.y, label: n.title, sub: n.subtitle, accent: STATUS_STYLE[n.status].hex };
  });
  const runExport = () => ({
    evaluation: { id: job.id, name: job.name, status: job.status },
    run: { id: run.id, sequence: run.sequence, status: run.status, attempt: run.attempt },
    headline: graph.headline,
    nodes: graph.nodes,
    edges: graph.edges,
  });
  const doExport = async (fmt: "json" | "svg" | "png") => {
    const base = `execution-${job.name.replace(/\s+/g, "-").toLowerCase()}-run${run.sequence}`;
    if (fmt === "json") return downloadJson(`${base}.json`, runExport());
    const { svg, width, height } = buildGraphSvg(exportNodes, graph.edges, `${job.name} · run #${run.sequence}`);
    if (fmt === "svg") return downloadSvg(`${base}.svg`, svg);
    await downloadPng(`${base}.png`, svg, width, height);
  };

  return (
    <div className="space-y-3">
      {/* One verdict language across every graph — structure, execution, failure and reasoning. */}
      <VerdictBanner
        verdict={{
          state: graph.failed ? "failed" : "healthy",
          headline: graph.headline,
          consequence: `This is the runtime path for run #${run.sequence} of ${runs.length}, reconstructed from the run's own telemetry and metric results.`,
          status: "derived",
          provenance: { basis: "the run's recorded execution and metric outcomes" },
        }}
        action={<InfoButton feature="forge-graph" label="" />}
      />

      <div className="flex flex-wrap items-center gap-2">
        <Select value={runId} onValueChange={(v) => { setSelectedRunId(v); setSelectedNode(null); }}>
          <SelectTrigger className="w-56"><SelectValue /></SelectTrigger>
          <SelectContent>
            {runs.map((r) => (
              <SelectItem key={r.id} value={r.id}>
                Run #{r.sequence} · {r.status.toLowerCase()}
                {r.passed === false ? " · failed" : r.passed ? " · passed" : ""}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          variant={failuresOnly ? "default" : "outline"}
          size="sm"
          onClick={() => setFailuresOnly((v) => !v)}
        >
          <AlertTriangle className="h-4 w-4" />
          Failures only
        </Button>
        {/*
         * The graph shows where the chain stopped. The investigation says why it stopped, what led there and
         * whether it has stopped here before — so the red graph is exactly where that offer belongs.
         */}
        {graph.failed && (
          <Link
            href={`/organizations/${organizationId}/projects/${projectId}/evaluations/${job.id}/investigate`}
            className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
          >
            <Search className="h-3.5 w-3.5" />
            Investigate this failure
          </Link>
        )}
        <div className="ml-auto flex flex-wrap items-center gap-1.5">
          <Button variant="outline" size="sm" onClick={() => doExport("png")}><Download className="h-4 w-4" />PNG</Button>
          <Button variant="outline" size="sm" onClick={() => doExport("svg")}>SVG</Button>
          <Button variant="outline" size="sm" onClick={() => doExport("json")}>JSON</Button>
        </div>
      </div>

      <div className="relative h-[520px] w-full overflow-hidden rounded-xl border border-border bg-background">
        <ReactFlowProvider>
          <ReactFlow
            nodes={rfNodes}
            edges={rfEdges}
            nodeTypes={nodeTypes}
            onNodeClick={(_, n) => setSelectedNode(graph.nodes.find((x) => x.id === n.id) ?? null)}
            onPaneClick={() => setSelectedNode(null)}
            fitView
            fitViewOptions={{ padding: 0.2 }}
            minZoom={0.2}
            maxZoom={1.75}
            proOptions={{ hideAttribution: true }}
            nodesDraggable={false}
            nodesConnectable={false}
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1} color="#3f3f46" />
            <Controls showInteractive={false} />
          </ReactFlow>
        </ReactFlowProvider>

        {selectedNode && <NodeDetail node={selectedNode} onClose={() => setSelectedNode(null)} />}
      </div>
    </div>
  );
}

function NodeDetail({ node, onClose }: { node: ExecNode; onClose: () => void }) {
  const Icon = KIND_ICON[node.kind] ?? Bot;
  const s = STATUS_STYLE[node.status];
  return (
    <div className="absolute right-0 top-0 flex h-full w-80 flex-col gap-3 overflow-auto border-l border-border bg-card/95 p-4 backdrop-blur">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <Icon className={cn("h-4 w-4", s.icon)} />
          <span className="text-xs uppercase tracking-wide text-muted-foreground">{node.kind}</span>
        </div>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground" aria-label="Close">
          <XCircle className="h-4 w-4" />
        </button>
      </div>
      <div>
        <p className="text-sm font-semibold text-foreground">{node.title}</p>
        {node.subtitle && <p className="text-xs text-muted-foreground">{node.subtitle}</p>}
      </div>
      <Badge
        variant={node.status === "ok" ? "success" : node.status === "fail" ? "destructive" : node.status === "warn" ? "warning" : "muted"}
        className="w-fit text-[10px] uppercase"
      >
        {node.status}
      </Badge>
      {node.meta.length > 0 && (
        <div className="space-y-1.5 border-t border-border pt-3">
          {node.meta.map((m) => (
            <div key={m.label} className="flex items-center justify-between gap-3 text-xs">
              <span className="text-muted-foreground">{m.label}</span>
              <span className="font-mono text-foreground">{m.value}</span>
            </div>
          ))}
        </div>
      )}
      {node.body && (
        <div className="space-y-1 border-t border-border pt-3">
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Detail</p>
          <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-2 text-[11px] text-foreground/90">
            {node.body}
          </pre>
        </div>
      )}
    </div>
  );
}
