"use client";

import { HealthBadge } from "@/components/common/badges";
import { AUTH_TYPE_OPTIONS } from "@/lib/api/agents";
import { PROVIDER_TYPE_OPTIONS, type ProviderResponse } from "@/lib/api/providers";

const AUTH_LABELS = Object.fromEntries(AUTH_TYPE_OPTIONS.map((o) => [o.value, o.label]));
const TYPE_LABELS = Object.fromEntries(PROVIDER_TYPE_OPTIONS.map((o) => [o.value, o.label]));

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-0.5">
      <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="truncate text-sm">{children}</div>
    </div>
  );
}

/**
 * Read-only summary of everything an agent linked to `provider` inherits — the single place
 * that shows the Provider abstraction's actual effective configuration, so it's obvious there
 * is no duplicated/conflicting configuration living on the agent itself.
 */
export function InheritedProviderPanel({ provider }: { provider: ProviderResponse }) {
  const headerCount = Object.keys(provider.defaultHeaders ?? {}).length;
  return (
    <div className="space-y-3 rounded-md border border-border bg-muted/30 p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        Inherited configuration
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Item label="Provider">
          {provider.name} <span className="text-muted-foreground">({TYPE_LABELS[provider.type] ?? provider.type})</span>
        </Item>
        <Item label="Base URL">
          <span className="break-all font-mono text-xs">{provider.baseUrl}</span>
        </Item>
        <Item label="Authentication">
          {provider.authType === "NONE"
            ? "None"
            : `${AUTH_LABELS[provider.authType] ?? provider.authType}${provider.apiKeyConfigured ? ` (${provider.apiKeyHint})` : " (not configured yet)"}`}
        </Item>
        <Item label="Default model">{provider.defaultModel || "—"}</Item>
        <Item label="Default headers">{headerCount > 0 ? `${headerCount} configured` : "None"}</Item>
        <Item label="Health status">
          <HealthBadge status={provider.healthStatus} />
        </Item>
      </div>
    </div>
  );
}
