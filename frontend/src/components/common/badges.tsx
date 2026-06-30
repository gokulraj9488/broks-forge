import { Badge } from "@/components/ui/badge";
import type { OrganizationRole } from "@/lib/api/types";

export function RoleBadge({ role }: { role: OrganizationRole }) {
  const variant = role === "OWNER" ? "default" : role === "ADMIN" ? "secondary" : "muted";
  return <Badge variant={variant}>{role.charAt(0) + role.slice(1).toLowerCase()}</Badge>;
}

export function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase();
  const variant = normalized === "ACTIVE" ? "success" : "muted";
  return <Badge variant={variant}>{normalized.charAt(0) + normalized.slice(1).toLowerCase()}</Badge>;
}
