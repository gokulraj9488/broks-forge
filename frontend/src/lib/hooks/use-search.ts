"use client";

import { useQuery } from "@tanstack/react-query";
import { searchApi } from "@/lib/api/search";

const keys = {
  query: (o: string, p: string, q: string, limit: number) =>
    ["organizations", o, "projects", p, "search", q, limit] as const,
};

export function useSearch(
  organizationId: string | undefined,
  projectId: string | undefined,
  q: string,
  limit = 5,
) {
  const trimmed = q.trim();
  return useQuery({
    queryKey: keys.query(organizationId ?? "", projectId ?? "", trimmed, limit),
    queryFn: () => searchApi.query(organizationId as string, projectId as string, trimmed, limit),
    enabled: !!organizationId && !!projectId && trimmed.length >= 2,
  });
}
