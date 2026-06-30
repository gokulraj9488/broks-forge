"use client";

import Link from "next/link";
import { ArrowRight, Building2, Crown, Users } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { RoleBadge } from "@/components/common/badges";
import { PageHeader } from "@/components/layout/page-header";
import { useAuth } from "@/lib/hooks/use-auth";
import { useOrganizations } from "@/lib/hooks/use-organizations";

function StatCard({
  label,
  value,
  icon: Icon,
  loading,
}: {
  label: string;
  value: number | string;
  icon: typeof Building2;
  loading?: boolean;
}) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between p-6">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">{label}</p>
          {loading ? (
            <Skeleton className="h-8 w-12" />
          ) : (
            <p className="text-3xl font-semibold tracking-tight">{value}</p>
          )}
        </div>
        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10">
          <Icon className="h-5 w-5 text-primary" />
        </div>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { data, isLoading } = useOrganizations({ size: 5 });

  const organizations = data?.content ?? [];
  const totalOrgs = data?.totalElements ?? 0;
  const ownedOrgs = organizations.filter((o) => o.currentUserRole === "OWNER").length;
  const totalMembers = organizations.reduce((sum, o) => sum + o.memberCount, 0);

  const greeting = user?.firstName ? `Welcome back, ${user.firstName}` : "Welcome back";

  return (
    <div>
      <PageHeader title={greeting} description="Here's what's happening across your workspace." />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Organizations" value={totalOrgs} icon={Building2} loading={isLoading} />
        <StatCard label="Owned by you" value={ownedOrgs} icon={Crown} loading={isLoading} />
        <StatCard label="Members (top 5 orgs)" value={totalMembers} icon={Users} loading={isLoading} />
      </div>

      <div className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Your organizations</h2>
          <Button asChild variant="ghost" size="sm">
            <Link href="/organizations">
              View all
              <ArrowRight className="ml-1 h-4 w-4" />
            </Link>
          </Button>
        </div>

        {isLoading ? (
          <div className="grid gap-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-20 w-full" />
            ))}
          </div>
        ) : organizations.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="No organizations yet"
            description="Create your first organization to start building."
            action={
              <Button asChild>
                <Link href="/organizations">Create organization</Link>
              </Button>
            }
          />
        ) : (
          <div className="grid gap-3">
            {organizations.map((org) => (
              <Link key={org.id} href={`/organizations/${org.id}`}>
                <Card className="transition-colors hover:border-primary/40">
                  <CardHeader className="flex flex-row items-center justify-between space-y-0">
                    <div className="space-y-1">
                      <CardTitle className="text-base">{org.name}</CardTitle>
                      <p className="text-xs text-muted-foreground">
                        {org.memberCount} member{org.memberCount === 1 ? "" : "s"} · /{org.slug}
                      </p>
                    </div>
                    <RoleBadge role={org.currentUserRole} />
                  </CardHeader>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
