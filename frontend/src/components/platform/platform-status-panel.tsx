"use client";

import { Activity, Database, ShieldCheck } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { VerdictLine } from "@/components/platform/verdict";
import { InfoButton } from "@/components/platform/info-button";
import { usePlatformHealth } from "@/lib/hooks/use-platform-health";
import { formatNumber } from "@/lib/format";
import type { Verdict } from "@/lib/verdict";

/**
 * Engineering readiness — P7 rendered under the constitution.
 *
 * The old panel led with infrastructure ("Integrity: Verified · Ledger: Consistent · Knowledge: 412 entries"),
 * which is system status, not engineering meaning. Volume III forbids exposing infrastructure before insight
 * (P-1): this surface now leads with what the state means for the engineer — *can I trust this record?* — and
 * demotes chain/integrity/ledger counts to the receipts that substantiate that sentence.
 *
 * Reads the same read-only {@code /platform/health} endpoint; no backend change.
 */
export function PlatformStatusPanel({ organizationId }: { organizationId: string }) {
  const { data, isLoading, isError } = usePlatformHealth(organizationId);

  // Platform disabled / unavailable → render nothing (graceful, flag-aware).
  if (isError || (!isLoading && !data)) return null;

  if (isLoading || !data) {
    return <Skeleton className="h-32 w-full rounded-xl" />;
  }

  const verdict = readinessVerdict(data);

  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <VerdictLine verdict={verdict} />
          <InfoButton feature="engineering-intelligence" label="" />
        </div>

        {/* Receipts: the infrastructure facts, deliberately below the meaning they support. */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Receipt
            icon={ShieldCheck}
            label="Knowledge integrity"
            value={data.integrityClean ? "No inconsistencies found" : `${formatNumber(data.integrityErrors)} to reconcile`}
          />
          <Receipt
            icon={Activity}
            label="History chain"
            value={data.chainValid ? "Verified end to end" : "Verification failed"}
          />
          <Receipt
            icon={Database}
            label="Recorded acts"
            value={`${formatNumber(data.ledgerSize)} in the engineering record`}
          />
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Turns the integrity snapshot into an engineering sentence. Note the honest empty case: an organization with
 * an empty ledger is *not yet known*, never "healthy" (L-34 — absence is not health).
 */
function readinessVerdict(data: {
  chainValid: boolean;
  integrityClean: boolean;
  integrityErrors: number;
  ledgerSize: number;
}): Verdict {
  if (data.ledgerSize === 0) {
    return {
      state: "unknown",
      headline: "Your engineering record is empty.",
      consequence:
        "Nothing has been recorded yet, so there is nothing to verify. As you register providers, prompts and agents, each becomes a permanent, checkable engineering act.",
      status: "derived",
      provenance: { basis: "the organization's engineering ledger" },
    };
  }

  if (!data.chainValid || !data.integrityClean) {
    const problems: string[] = [];
    if (!data.chainValid) problems.push("the history chain did not verify");
    if (!data.integrityClean) problems.push(`${formatNumber(data.integrityErrors)} knowledge inconsistencies were found`);
    return {
      state: "risk",
      headline: "Parts of your engineering record could not be verified.",
      consequence: `${capitalize(problems.join(" and "))}. Conclusions drawn from the affected records should be treated as unproven until this is reconciled.`,
      status: "derived",
      provenance: { basis: `an integrity scan of ${formatNumber(data.ledgerSize)} recorded acts` },
    };
  }

  return {
    state: "healthy",
    headline: "Every engineering act on record is verifiable.",
    consequence:
      "The history chain verified end to end and the knowledge projection is consistent — anything this platform tells you can be traced back to a real, unaltered act.",
    status: "derived",
    provenance: { basis: `${formatNumber(data.ledgerSize)} recorded acts` },
  };
}

function capitalize(v: string) {
  return v.charAt(0).toUpperCase() + v.slice(1);
}

function Receipt({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Database;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="flex items-center gap-1.5 text-[11px] uppercase tracking-wide text-muted-foreground">
        <Icon className="h-3 w-3" />
        {label}
      </div>
      <p className="mt-1 text-sm text-foreground">{value}</p>
    </div>
  );
}
