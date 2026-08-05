"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Boxes,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  FolderPlus,
  Pin,
  Search,
  Star,
  Tag,
  X,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useProjects } from "@/lib/hooks/use-projects";
import { useRegistry, useRegistryTypes } from "@/lib/hooks/use-registry";
import type { RegistryItem, RegistryQuery } from "@/lib/api/platform";
import {
  LABEL_COLORS,
  useRegistryPersonalization,
} from "@/lib/registry-personalization";
import { conditionOf, useInventoryCondition, type ArtifactCondition } from "@/lib/hooks/use-inventory-condition";
import { VerdictChip } from "@/components/platform/verdict";
import { substrateMeta } from "@/lib/substrate";
import { downloadBlob, downloadJson } from "@/lib/graph-export";
import { humanize } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 30;
const ALL = "all";

// Structural identity resolves from the one grammar (lib/substrate.ts) — never a verdict hue.

const SORTS = [
  { value: "recent", label: "Newest" },
  { value: "oldest", label: "Oldest" },
  { value: "name", label: "Name (A–Z)" },
  { value: "name_desc", label: "Name (Z–A)" },
];

type Scope = { kind: "all" | "favorites" | "pinned" | "recent" | "collection" | "label"; id?: string };

export function RegistryPanel({ organizationId }: { organizationId: string }) {
  const p = useRegistryPersonalization();

  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [type, setType] = useState<string | undefined>(undefined);
  const [projectId, setProjectId] = useState<string | undefined>(undefined);
  const [providerId, setProviderId] = useState<string | undefined>(undefined);
  const [tag, setTag] = useState<string | undefined>(undefined);
  const [sort, setSort] = useState("recent");
  const [page, setPage] = useState(0);

  const [scope, setScope] = useState<Scope>({ kind: "all" });
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [dialog, setDialog] = useState<null | "collection" | "label" | "view">(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const personal = scope.kind !== "all";
  // In a personal scope we fetch a large page and filter to the local id-set client-side; otherwise paginate.
  const query: RegistryQuery = useMemo(
    () => ({
      q: q || undefined,
      type,
      projectId,
      providerId,
      tag,
      sort,
      page: personal ? 0 : page,
      size: personal ? 100 : PAGE_SIZE,
    }),
    [q, type, projectId, providerId, tag, sort, page, personal],
  );

  const { data, isLoading, isError } = useRegistry(organizationId, query);
  const { data: types } = useRegistryTypes(organizationId);
  const { data: projectsPage } = useProjects(organizationId);
  const { data: providersPage } = useRegistry(organizationId, { type: "provider", size: 100 });
  // L-30: rows carry condition, not just identity. One shared derivation for the whole page.
  const conditions = useInventoryCondition(organizationId);

  const projects = projectsPage?.content ?? [];
  const providers = providersPage?.content ?? [];
  const total = types?.reduce((sum, t) => sum + t.count, 0) ?? 0;

  const scopeIds = useMemo(() => {
    switch (scope.kind) {
      case "favorites":
        return new Set(p.state.favorites);
      case "pinned":
        return new Set(p.state.pins);
      case "recent":
        return new Set(p.state.recent);
      case "collection":
        return new Set(p.state.collections.find((c) => c.id === scope.id)?.items ?? []);
      case "label":
        return new Set(
          Object.entries(p.state.itemLabels).filter(([, ls]) => ls.includes(scope.id!)).map(([id]) => id),
        );
      default:
        return null;
    }
  }, [scope, p.state]);

  const items = useMemo(() => {
    const content = data?.content ?? [];
    if (!scopeIds) return content;
    const filtered = content.filter((i) => scopeIds.has(i.id));
    if (scope.kind === "recent") {
      const order = new Map(p.state.recent.map((id, i) => [id, i]));
      return [...filtered].sort((a, b) => (order.get(a.id) ?? 0) - (order.get(b.id) ?? 0));
    }
    // Favorites / pinned float to the top when present.
    return [...filtered].sort((a, b) => Number(p.state.pins.includes(b.id)) - Number(p.state.pins.includes(a.id)));
  }, [data, scopeIds, scope.kind, p.state.recent, p.state.pins]);

  const reset = () => {
    setType(undefined);
    setProjectId(undefined);
    setProviderId(undefined);
    setTag(undefined);
    setPage(0);
  };

  const toggleSelect = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const selectedItems = items.filter((i) => selected.has(i.id));

  const exportSelected = (fmt: "json" | "csv") => {
    if (fmt === "json") {
      downloadJson("registry-selection.json", selectedItems);
      return;
    }
    const header = "id,type,name,project,createdAt";
    const rows = selectedItems.map((i) =>
      [i.id, i.type, i.name, i.projectName ?? "", i.createdAt].map((v) => `"${String(v).replace(/"/g, '""')}"`).join(","),
    );
    downloadBlob("registry-selection.csv", new Blob([[header, ...rows].join("\n")], { type: "text/csv" }));
  };

  const applyView = (viewId: string) => {
    const v = p.state.savedViews.find((x) => x.id === viewId);
    if (!v) return;
    const query2 = v.query as RegistryQuery;
    setType(query2.type);
    setProjectId(query2.projectId);
    setProviderId(query2.providerId);
    setTag(query2.tag);
    setSort(query2.sort ?? "recent");
    setSearch(query2.q ?? "");
    setScope({ kind: "all" });
    setPage(0);
  };

  if (isError) {
    return (
      <EmptyState
        icon={Search}
        title="Registry unavailable"
        description="The engineering registry isn't available for this workspace yet."
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Personal scopes + views + select mode */}
      <div className="flex flex-wrap items-center gap-1.5">
        <ScopeChip active={scope.kind === "all"} onClick={() => setScope({ kind: "all" })} icon={Boxes} label="All" />
        <ScopeChip active={scope.kind === "favorites"} onClick={() => setScope({ kind: "favorites" })} icon={Star} label={`Favorites (${p.state.favorites.length})`} />
        <ScopeChip active={scope.kind === "pinned"} onClick={() => setScope({ kind: "pinned" })} icon={Pin} label={`Pinned (${p.state.pins.length})`} />
        <ScopeChip active={scope.kind === "recent"} onClick={() => setScope({ kind: "recent" })} icon={Clock} label="Recent" />

        {p.state.collections.length > 0 && (
          <Select
            value={scope.kind === "collection" ? scope.id : "none"}
            onValueChange={(v) => setScope(v === "none" ? { kind: "all" } : { kind: "collection", id: v })}
          >
            <SelectTrigger className="h-8 w-44"><SelectValue placeholder="Collection" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="none">All collections</SelectItem>
              {p.state.collections.map((c) => (
                <SelectItem key={c.id} value={c.id}>{c.name} ({c.items.length})</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}

        {p.state.labels.length > 0 && (
          <Select
            value={scope.kind === "label" ? scope.id : "none"}
            onValueChange={(v) => setScope(v === "none" ? { kind: "all" } : { kind: "label", id: v })}
          >
            <SelectTrigger className="h-8 w-40"><SelectValue placeholder="Label" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="none">All labels</SelectItem>
              {p.state.labels.map((l) => (
                <SelectItem key={l.id} value={l.id}>{l.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}

        <div className="ml-auto flex flex-wrap items-center gap-1.5">
          {p.state.savedViews.length > 0 && (
            <Select value="none" onValueChange={applyView}>
              <SelectTrigger className="h-8 w-40"><SelectValue placeholder="Saved views" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none" disabled>Saved views</SelectItem>
                {p.state.savedViews.map((v) => (
                  <SelectItem key={v.id} value={v.id}>{v.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <SmallButton onClick={() => setDialog("view")} icon={Check}>Save view</SmallButton>
          <SmallButton onClick={() => setDialog("collection")} icon={FolderPlus}>New collection</SmallButton>
          <SmallButton onClick={() => setDialog("label")} icon={Tag}>New label</SmallButton>
          <SmallButton onClick={() => { setSelectMode((v) => !v); setSelected(new Set()); }} active={selectMode} icon={Check}>
            {selectMode ? "Done" : "Select"}
          </SmallButton>
        </div>
      </div>

      {/* Search + sort */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search every artifact by name…"
            className="pl-9"
          />
        </div>
        <Select value={sort} onValueChange={setSort}>
          <SelectTrigger className="w-full sm:w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            {SORTS.map((s) => (
              <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Type chips */}
      <div className="flex flex-wrap gap-1.5">
        <TypeChip active={!type} onClick={() => { setType(undefined); setPage(0); }} label="All" count={total} />
        {(types ?? [])
          .filter((t) => t.count > 0)
          .map((t) => (
            <TypeChip
              key={t.type}
              active={type === t.type}
              onClick={() => { setType(t.type); setPage(0); }}
              label={humanize(t.type)}
              count={t.count}
            />
          ))}
      </div>

      {/* Project / provider filters + active tag */}
      <div className="flex flex-wrap items-center gap-2">
        <Select value={projectId ?? ALL} onValueChange={(v) => { setProjectId(v === ALL ? undefined : v); setPage(0); }}>
          <SelectTrigger className="w-52"><SelectValue placeholder="All projects" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All projects</SelectItem>
            {projects.map((pr) => (
              <SelectItem key={pr.id} value={pr.id}>{pr.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={providerId ?? ALL} onValueChange={(v) => { setProviderId(v === ALL ? undefined : v); setPage(0); }}>
          <SelectTrigger className="w-52"><SelectValue placeholder="All providers" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All providers</SelectItem>
            {providers.map((pr) => (
              <SelectItem key={pr.id} value={pr.entityId ?? pr.id}>{pr.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        {tag && (
          <button
            onClick={() => { setTag(undefined); setPage(0); }}
            className="inline-flex items-center gap-1 rounded-md border border-border bg-muted px-2 py-1 text-xs text-muted-foreground hover:text-foreground"
          >
            tag: {tag}
            <X className="h-3 w-3" />
          </button>
        )}

        {(type || projectId || providerId || tag) && (
          <button onClick={reset} className="text-xs text-primary hover:underline">Clear filters</button>
        )}
      </div>

      {/* Results */}
      {isLoading && !data ? (
        <div className="space-y-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={personal ? Star : Search}
          title={personal ? "Nothing here yet" : "No artifacts found"}
          description={personal ? "Favorite, pin, tag or collect artifacts to see them in this view." : "Try a different search or clear the filters."}
        />
      ) : (
        <>
          <p className="text-xs text-muted-foreground">
            {personal ? `${items.length} in this view` : `${data?.totalElements ?? items.length} ${(data?.totalElements ?? 0) === 1 ? "artifact" : "artifacts"}`}
          </p>
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {items.map((item) => (
                <RegistryRow
                  key={item.id}
                  organizationId={organizationId}
                  item={item}
                  condition={conditionOf(conditions, item)}
                  p={p}
                  selectMode={selectMode}
                  selected={selected.has(item.id)}
                  onToggleSelect={() => toggleSelect(item.id)}
                  onTag={(t) => { setTag(t); setPage(0); }}
                />
              ))}
            </CardContent>
          </Card>

          {!personal && (
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">
                Page {(data?.page ?? 0) + 1} of {Math.max(data?.totalPages ?? 1, 1)}
              </span>
              <div className="flex gap-1.5">
                <PagerButton disabled={data?.first ?? true} onClick={() => setPage((pg) => Math.max(pg - 1, 0))} icon={ChevronLeft} label="Previous page" />
                <PagerButton disabled={data?.last ?? true} onClick={() => setPage((pg) => pg + 1)} icon={ChevronRight} label="Next page" />
              </div>
            </div>
          )}
        </>
      )}

      {/* Bulk action bar */}
      {selectMode && selected.size > 0 && (
        <div className="sticky bottom-4 z-20 flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card/95 p-2.5 shadow-lg backdrop-blur">
          <span className="px-1 text-sm font-medium text-foreground">{selected.size} selected</span>
          <SmallButton icon={Star} onClick={() => selectedItems.forEach((i) => { if (!p.isFavorite(i.id)) p.toggleFavorite(i.id); })}>Favorite</SmallButton>
          <SmallButton icon={Pin} onClick={() => selectedItems.forEach((i) => { if (!p.isPinned(i.id)) p.togglePin(i.id); })}>Pin</SmallButton>
          {p.state.collections.length > 0 && (
            <Select value="none" onValueChange={(cid) => p.setInCollection(cid, [...selected], true)}>
              <SelectTrigger className="h-8 w-40"><SelectValue placeholder="Add to collection" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none" disabled>Add to collection</SelectItem>
                {p.state.collections.map((c) => (
                  <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          {p.state.labels.length > 0 && (
            <Select value="none" onValueChange={(lid) => p.applyLabel([...selected], lid, true)}>
              <SelectTrigger className="h-8 w-36"><SelectValue placeholder="Apply label" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none" disabled>Apply label</SelectItem>
                {p.state.labels.map((l) => (
                  <SelectItem key={l.id} value={l.id}>{l.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <SmallButton onClick={() => exportSelected("json")}>Export JSON</SmallButton>
          <SmallButton onClick={() => exportSelected("csv")}>Export CSV</SmallButton>
          <button onClick={() => setSelected(new Set())} className="ml-auto text-xs text-muted-foreground hover:text-foreground">Clear</button>
        </div>
      )}

      {dialog === "collection" && (
        <CreateDialog
          title="New collection"
          onClose={() => setDialog(null)}
          onCreate={(name, color) => { p.createCollection(name, color); setDialog(null); }}
        />
      )}
      {dialog === "label" && (
        <CreateDialog
          title="New label"
          onClose={() => setDialog(null)}
          onCreate={(name, color) => { p.createLabel(name, color); setDialog(null); }}
        />
      )}
      {dialog === "view" && (
        <CreateDialog
          title="Save current view"
          withColor={false}
          onClose={() => setDialog(null)}
          onCreate={(name) => { p.saveView(name, query as Record<string, unknown>); setDialog(null); }}
        />
      )}
    </div>
  );
}

function RegistryRow({
  organizationId,
  item,
  condition,
  p,
  selectMode,
  selected,
  onToggleSelect,
  onTag,
}: {
  organizationId: string;
  item: RegistryItem;
  condition: ArtifactCondition;
  p: ReturnType<typeof useRegistryPersonalization>;
  selectMode: boolean;
  selected: boolean;
  onToggleSelect: () => void;
  onTag: (tag: string) => void;
}) {
  const metaIcon = substrateMeta(item.type);
  const Icon = metaIcon.icon;
  const href = deepLink(organizationId, item);
  const fav = p.isFavorite(item.id);
  const pinned = p.isPinned(item.id);
  const labelIds = p.itemLabelIds(item.id);

  const inner = (
    <div className="flex items-center gap-3 p-3">
      {selectMode && (
        <button
          onClick={(e) => { e.preventDefault(); e.stopPropagation(); onToggleSelect(); }}
          className={cn(
            "flex h-4 w-4 shrink-0 items-center justify-center rounded border",
            selected ? "border-primary bg-primary text-primary-foreground" : "border-border",
          )}
          aria-label={selected ? "Deselect" : "Select"}
        >
          {selected && <Check className="h-3 w-3" />}
        </button>
      )}
      <Icon className={cn("h-4 w-4 shrink-0", metaIcon.color)} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-medium text-foreground">{item.name}</span>
          <Badge variant="muted" className="shrink-0 text-[10px] uppercase">{item.type}</Badge>
          {pinned && <Pin className="h-3 w-3 shrink-0 text-primary" />}
          {labelIds.map((lid) => {
            const l = p.state.labels.find((x) => x.id === lid);
            return l ? <span key={lid} className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: l.color }} title={l.name} /> : null;
          })}
        </div>
        {/* Condition before metadata: the row answers "how is this doing?" (L-30). */}
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
          <VerdictChip state={condition.state} label={condition.summary} />
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
          {item.projectName && <span className="truncate">{item.projectName}</span>}
          {item.subtitle && <span className="text-muted-foreground/70">· {humanize(item.subtitle)}</span>}
          {item.tags.slice(0, 4).map((t) => (
            <button
              key={t}
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); onTag(t); }}
              className="rounded bg-muted px-1.5 py-0.5 text-[10px] hover:text-foreground"
            >
              #{t}
            </button>
          ))}
        </div>
      </div>
      <button
        onClick={(e) => { e.preventDefault(); e.stopPropagation(); p.toggleFavorite(item.id); }}
        className={cn("shrink-0 rounded p-1 transition-colors", fav ? "text-amber-400" : "text-muted-foreground/50 hover:text-foreground")}
        aria-label={fav ? "Unfavorite" : "Favorite"}
      >
        <Star className={cn("h-4 w-4", fav && "fill-current")} />
      </button>
      <span className="shrink-0 text-xs text-muted-foreground">{formatDateTime(item.createdAt)}</span>
    </div>
  );

  if (selectMode || !href) {
    return <div className={cn(selectMode && "cursor-pointer")} onClick={selectMode ? onToggleSelect : undefined}>{inner}</div>;
  }
  return (
    <Link href={href} onClick={() => p.addRecent(item.id)} className="block hover:bg-muted/40">
      {inner}
    </Link>
  );
}

function CreateDialog({
  title,
  withColor = true,
  onClose,
  onCreate,
}: {
  title: string;
  withColor?: boolean;
  onClose: () => void;
  onCreate: (name: string, color: string) => void;
}) {
  const [name, setName] = useState("");
  const [color, setColor] = useState(LABEL_COLORS[0]);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" />
          {withColor && (
            <div className="flex flex-wrap gap-1.5">
              {LABEL_COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => setColor(c)}
                  className={cn("h-6 w-6 rounded-full border-2", color === c ? "border-foreground" : "border-transparent")}
                  style={{ backgroundColor: c }}
                  aria-label={`Color ${c}`}
                />
              ))}
            </div>
          )}
        </div>
        <DialogFooter>
          <button onClick={onClose} className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground">Cancel</button>
          <button
            onClick={() => name.trim() && onCreate(name.trim(), color)}
            disabled={!name.trim()}
            className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
          >
            Create
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ScopeChip({ active, onClick, icon: Icon, label }: { active: boolean; onClick: () => void; icon: typeof Star; label: string }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active ? "border-primary/40 bg-primary/10 text-primary" : "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

function SmallButton({
  onClick,
  icon: Icon,
  active,
  children,
}: {
  onClick: () => void;
  icon?: typeof Star;
  active?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "inline-flex h-8 items-center gap-1.5 rounded-md border px-2.5 text-xs font-medium transition-colors",
        active ? "border-primary/40 bg-primary/10 text-primary" : "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      {Icon && <Icon className="h-3.5 w-3.5" />}
      {children}
    </button>
  );
}

function TypeChip({ active, onClick, label, count }: { active: boolean; onClick: () => void; label: string; count: number }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active ? "border-primary/40 bg-primary/10 text-primary" : "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      {label}
      <span className={cn("rounded-full px-1.5 text-[10px]", active ? "bg-primary/20" : "bg-muted")}>{count}</span>
    </button>
  );
}

// Icon-only, so the label is the only thing a screen reader has to go on.
function PagerButton({
  disabled,
  onClick,
  icon: Icon,
  label,
}: {
  disabled: boolean;
  onClick: () => void;
  icon: typeof ChevronLeft;
  label: string;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-label={label}
      title={label}
      className="rounded-md border border-border p-1.5 text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}

/** Deep-links a registry item to its existing management page — reusing current routes only. */
function deepLink(org: string, item: RegistryItem): string | null {
  const proj = item.projectId;
  const id = item.entityId;
  const scoped = (path: string) => `/organizations/${org}/projects/${proj}${path}`;
  switch (item.type) {
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
