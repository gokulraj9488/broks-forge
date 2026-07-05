"use client";

import { useState } from "react";
import Link from "next/link";
import { FileText, Search } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/common/badges";
import { CreatePromptDialog } from "@/components/prompts/create-prompt-dialog";
import { usePrompts } from "@/lib/hooks/use-prompts";
import { formatDate } from "@/lib/utils";
import type { PromptStatus } from "@/lib/api/prompts";

const PAGE_SIZE = 12;
const ALL = "all";

export function PromptsPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<PromptStatus | "">("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = usePrompts(organizationId, projectId, {
    q: q.trim() || undefined,
    status: status || undefined,
    page,
    size: PAGE_SIZE,
  });

  const reset = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const prompts = data?.content ?? [];
  const filtered = !!q || !!status;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => reset(setQ)(e.target.value)}
            placeholder="Search prompts…"
            className="pl-9"
          />
        </div>
        {canManage && <CreatePromptDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      <div className="flex flex-wrap gap-2">
        <Select
          value={status === "" ? ALL : status}
          onValueChange={(v) => reset(setStatus)(v === ALL ? "" : (v as PromptStatus))}
        >
          <SelectTrigger className="h-8 w-auto min-w-[8rem] text-xs" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ARCHIVED">Archived</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={FileText} title="Couldn't load prompts" description="Please try again." />
      ) : prompts.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No prompts found"
          description={filtered ? "No prompts match your filters." : "Create your first prompt to version a template."}
          action={
            canManage && !filtered ? (
              <CreatePromptDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {prompts.map((prompt) => (
              <Link
                key={prompt.id}
                href={`/organizations/${organizationId}/projects/${projectId}/prompts/${prompt.id}`}
                className="group"
              >
                <Card className="h-full transition-colors group-hover:border-primary/40">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                          <FileText className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate font-medium leading-tight">{prompt.name}</h3>
                          <p className="truncate text-xs text-muted-foreground">/{prompt.slug}</p>
                        </div>
                      </div>
                      {prompt.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
                    </div>
                    <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant="outline">v{prompt.latestVersionNumber ?? 0}</Badge>
                      {prompt.currentActiveVersionId && <Badge variant="success">Has active</Badge>}
                      <span>Updated {formatDate(prompt.updatedAt)}</span>
                      {prompt.tags.slice(0, 3).map((tag) => (
                        <span key={tag} className="rounded bg-muted px-1.5 py-0.5">
                          #{tag}
                        </span>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
          <Pagination
            page={data?.page ?? 0}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? prompts.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
