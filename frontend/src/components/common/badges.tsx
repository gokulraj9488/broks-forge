import { Badge } from "@/components/ui/badge";
import type { OrganizationRole } from "@/lib/api/types";
import type { AgentHealthStatus } from "@/lib/api/agents";

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export function RoleBadge({ role }: { role: OrganizationRole }) {
  const variant = role === "OWNER" ? "default" : role === "ADMIN" ? "secondary" : "muted";
  return <Badge variant={variant}>{titleCase(role)}</Badge>;
}

export function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase();
  const variant = normalized === "ACTIVE" ? "success" : "muted";
  return <Badge variant={variant}>{titleCase(normalized)}</Badge>;
}

const HEALTH_VARIANT: Record<AgentHealthStatus, "success" | "default" | "destructive" | "muted"> = {
  HEALTHY: "success",
  DEGRADED: "default",
  UNHEALTHY: "destructive",
  UNKNOWN: "muted",
};

export function HealthBadge({ status }: { status: AgentHealthStatus }) {
  return <Badge variant={HEALTH_VARIANT[status]}>{titleCase(status)}</Badge>;
}
