"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { platformApi, type RegistryQuery } from "@/lib/api/platform";

const keys = {
  list: (o: string, q: RegistryQuery) => ["organizations", o, "platform", "registry", q] as const,
  types: (o: string) => ["organizations", o, "platform", "registry", "types"] as const,
};

/** Server-backed registry listing. Keeps the previous page visible while the next loads (no flicker). */
export function useRegistry(organizationId: string | undefined, query: RegistryQuery) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", query),
    queryFn: () => platformApi.registry(organizationId as string, query),
    enabled: !!organizationId,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useRegistryTypes(organizationId: string | undefined) {
  return useQuery({
    queryKey: keys.types(organizationId ?? ""),
    queryFn: () => platformApi.registryTypes(organizationId as string),
    enabled: !!organizationId,
    retry: false,
  });
}
