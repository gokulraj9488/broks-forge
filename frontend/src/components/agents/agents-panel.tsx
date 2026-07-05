"use client";

import { useState } from "react";
import Link from "next/link";
import { Bot, Search } from "lucide-react";
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
import { HealthBadge, StatusBadge } from "@/components/common/badges";
import { RegisterAgentDialog } from "@/components/agents/register-agent-dialog";
import { useAgents } from "@/lib/hooks/use-agents";
import {
  FRAMEWORK_OPTIONS,
  HEALTH_STATUS_OPTIONS,
  type AgentFramework,
  type AgentHealthStatus,
  type AgentLifecycleStatus,
} from "@/lib/api/agents";

const PAGE_SIZE = 12;
const ALL = "all";
const FRAMEWORK_LABELS = Object.fromEntries(FRAMEWORK_OPTIONS.map((o) => [o.value, o.label]));

export function AgentsPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const [q, setQ] = useState("");
  const [framework, setFramework] = useState<AgentFramework | "">("");
  const [status, setStatus] = useState<AgentLifecycleStatus | "">("");
  const [healthStatus, setHealthStatus] = useState<AgentHealthStatus | "">("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useAgents(organizationId, projectId, {
    q: q.trim() || undefined,
    framework: framework || undefined,
    status: status || undefined,
    healthStatus: healthStatus || undefined,
    page,
    size: PAGE_SIZE,
  });

  const resetPageAnd = <T,>(setter: (v: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const agents = data?.content ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => resetPageAnd(setQ)(e.target.value)}
            placeholder="Search agents…"
            className="pl-9"
          />
        </div>
        {canManage && <RegisterAgentDialog organizationId={organizationId} projectId={projectId} />}
      </div>

      <div className="flex flex-wrap gap-2">
        <Select
          value={framework === "" ? ALL : framework}
          onValueChange={(v) => resetPageAnd(setFramework)(v === ALL ? "" : (v as AgentFramework))}
        >
          <SelectTrigger className="h-8 w-auto min-w-[8rem] text-xs" aria-label="Filter by framework">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All frameworks</SelectItem>
            {FRAMEWORK_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={healthStatus === "" ? ALL : healthStatus}
          onValueChange={(v) =>
            resetPageAnd(setHealthStatus)(v === ALL ? "" : (v as AgentHealthStatus))
          }
        >
          <SelectTrigger className="h-8 w-auto min-w-[8rem] text-xs" aria-label="Filter by health">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All health</SelectItem>
            {HEALTH_STATUS_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={status === "" ? ALL : status}
          onValueChange={(v) =>
            resetPageAnd(setStatus)(v === ALL ? "" : (v as AgentLifecycleStatus))
          }
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
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Bot} title="Couldn't load agents" description="Please try again." />
      ) : agents.length === 0 ? (
        <EmptyState
          icon={Bot}
          title="No agents found"
          description={
            q || framework || status || healthStatus
              ? "No agents match your filters."
              : "Register your first agent to start building."
          }
          action={
            canManage && !q && !framework && !status && !healthStatus ? (
              <RegisterAgentDialog organizationId={organizationId} projectId={projectId} />
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2">
            {agents.map((agent) => (
              <Link
                key={agent.id}
                href={`/organizations/${organizationId}/projects/${projectId}/agents/${agent.id}`}
                className="group"
              >
                <Card className="h-full transition-colors group-hover:border-primary/40">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                          <Bot className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate font-medium leading-tight">{agent.name}</h3>
                          <p className="truncate text-xs text-muted-foreground">/{agent.slug}</p>
                        </div>
                      </div>
                      <HealthBadge status={agent.healthStatus} />
                    </div>
                    {agent.description && (
                      <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">
                        {agent.description}
                      </p>
                    )}
                    <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <Badge variant="outline">{FRAMEWORK_LABELS[agent.framework] ?? agent.framework}</Badge>
                      <Badge variant="muted">{agent.language}</Badge>
                      {agent.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
                      {agent.tags.slice(0, 3).map((tag) => (
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
            totalElements={data?.totalElements ?? agents.length}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
