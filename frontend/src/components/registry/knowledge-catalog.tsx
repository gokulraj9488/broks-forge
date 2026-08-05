"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ChevronLeft, ChevronRight, Lightbulb, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useKnowledgeCatalog } from "@/lib/hooks/use-intelligence";
import type { KnowledgeObject, KnowledgeQuery } from "@/lib/api/platform";
import { knowledgeHref } from "@/lib/artifact-links";
import { REASONING_TREATMENT, substrateMeta } from "@/lib/substrate";
import { humanize } from "@/lib/format";
import { formatDateTime } from "@/lib/utils";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 30;

const KINDS = ["observation", "claim", "decision", "evidence", "knowledge"] as const;

const SORTS = [
  { value: "recent", label: "Newest" },
  { value: "oldest", label: "Oldest" },
  { value: "name", label: "Title (A–Z)" },
  { value: "name_desc", label: "Title (Z–A)" },
];

export function KnowledgeCatalog({ organizationId }: { organizationId: string }) {
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [type, setType] = useState<string | undefined>(undefined);
  const [sort, setSort] = useState("recent");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const query: KnowledgeQuery = useMemo(
    () => ({ q: q || undefined, type, sort, page, size: PAGE_SIZE }),
    [q, type, sort, page],
  );

  const { data, isLoading, isError } = useKnowledgeCatalog(organizationId, query);

  if (isError) {
    return (
      <EmptyState
        icon={Lightbulb}
        title="Engineering knowledge unavailable"
        description="The engineering-knowledge catalog isn't available for this workspace yet."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search observations, claims, decisions, evidence, knowledge…"
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

      <div className="flex flex-wrap gap-1.5">
        <KindChip active={!type} onClick={() => { setType(undefined); setPage(0); }} label="All" />
        {KINDS.map((k) => (
          <KindChip
            key={k}
            active={type === k}
            onClick={() => { setType(k); setPage(0); }}
            label={humanize(k)}
          />
        ))}
      </div>

      {isLoading && !data ? (
        <div className="space-y-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <EmptyState
          icon={Lightbulb}
          title="No engineering knowledge yet"
          description="As artifacts are promoted, evaluated and evidenced, the platform derives knowledge objects here — all traceable to real events."
        />
      ) : (
        <>
          <p className="text-xs text-muted-foreground">
            {data.totalElements} knowledge {data.totalElements === 1 ? "object" : "objects"}
          </p>
          <Card>
            <CardContent className="divide-y divide-border p-0">
              {data.content.map((o) => (
                <KnowledgeCatalogRow key={o.id} organizationId={organizationId} object={o} />
              ))}
            </CardContent>
          </Card>

          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              Page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <div className="flex gap-1.5">
              <PagerButton disabled={data.first} onClick={() => setPage((p) => Math.max(p - 1, 0))} icon={ChevronLeft} label="Previous page" />
              <PagerButton disabled={data.last} onClick={() => setPage((p) => p + 1)} icon={ChevronRight} label="Next page" />
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function KnowledgeCatalogRow({ organizationId, object }: { organizationId: string; object: KnowledgeObject }) {
  const meta = substrateMeta(object.type);
  const Icon = meta.icon;
  // Knowledge objects open their own dedicated engineering page — never a redirect to an evaluation.
  return (
    <Link href={knowledgeHref(organizationId, object.id)} className="block hover:bg-muted/40">
      <div className="flex items-start gap-3 p-3">
        <Icon className={cn("mt-0.5 h-4 w-4 shrink-0", meta.color)} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-sm font-medium text-foreground">{object.title}</span>
            <Badge variant="muted" className="shrink-0 text-[10px] uppercase">{object.type}</Badge>
          </div>
          <p className="truncate text-xs text-muted-foreground">{object.summary}</p>
        </div>
        <span className="shrink-0 text-xs text-muted-foreground">{formatDateTime(object.at)}</span>
      </div>
    </Link>
  );
}

function KindChip({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "border-primary/40 bg-primary/10 text-primary"
          : "border-border text-muted-foreground hover:text-foreground",
      )}
    >
      {label}
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
