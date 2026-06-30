"use client";

import { useState } from "react";
import Link from "next/link";
import { Bot, Database, FileText, FlaskConical, Gauge, Search, Trophy } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { useSearch } from "@/lib/hooks/use-search";
import { humanize } from "@/lib/format";
import type { SearchHitType } from "@/lib/api/search";

const ICON: Record<SearchHitType, typeof Bot> = {
  AGENT: Bot,
  DATASET: Database,
  PROMPT: FileText,
  EVALUATION_JOB: FlaskConical,
  BENCHMARK: Trophy,
  EVALUATION_PROFILE: Gauge,
};

function hrefFor(organizationId: string, projectId: string, type: SearchHitType, id: string): string {
  const base = `/organizations/${organizationId}/projects/${projectId}`;
  switch (type) {
    case "AGENT":
      return `${base}/agents/${id}`;
    case "DATASET":
      return `${base}/datasets/${id}`;
    case "PROMPT":
      return `${base}/prompts/${id}`;
    case "EVALUATION_JOB":
      return `${base}/evaluations/${id}`;
    case "BENCHMARK":
      return `${base}/benchmarks/${id}`;
    case "EVALUATION_PROFILE":
      return "/evaluations";
    default:
      return base;
  }
}

/** Inline project-scoped search with a live results dropdown. */
export function SearchBox({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [q, setQ] = useState("");
  const [focused, setFocused] = useState(false);
  const { data, isFetching } = useSearch(organizationId, projectId, q, 8);

  const open = focused && q.trim().length >= 2;
  const hits = data?.hits ?? [];

  return (
    <div className="relative w-full max-w-lg">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 150)}
        placeholder="Search agents, datasets, prompts, evaluations…"
        className="pl-9"
      />
      {isFetching && (
        <span className="absolute right-3 top-1/2 -translate-y-1/2">
          <Spinner className="h-4 w-4" />
        </span>
      )}

      {open && (
        <Card className="absolute z-30 mt-2 w-full overflow-hidden shadow-xl">
          <CardContent className="max-h-80 overflow-y-auto p-1">
            {hits.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">
                {isFetching ? "Searching…" : "No matches found."}
              </p>
            ) : (
              hits.map((hit) => {
                const Icon = ICON[hit.type] ?? Search;
                return (
                  <Link
                    key={`${hit.type}-${hit.id}`}
                    href={hrefFor(organizationId, projectId, hit.type, hit.id)}
                    className="flex items-center gap-3 rounded-md px-3 py-2 hover:bg-muted/60"
                  >
                    <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />
                    <div className="min-w-0">
                      <p className="truncate text-sm">{hit.title}</p>
                      {hit.subtitle && (
                        <p className="truncate text-xs text-muted-foreground">{hit.subtitle}</p>
                      )}
                    </div>
                    <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
                      {humanize(hit.type)}
                    </span>
                  </Link>
                );
              })
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
