"use client";

import { useQuery } from "@tanstack/react-query";
import { advisorApi } from "@/lib/api/advisor";

const keys = {
  project: (o: string, p: string) =>
    ["organizations", o, "projects", p, "advisor", "project"] as const,
  agent: (o: string, p: string, a: string) =>
    ["organizations", o, "projects", p, "advisor", "agent", a] as const,
  prompt: (o: string, p: string, pr: string, v: string) =>
    ["organizations", o, "projects", p, "advisor", "prompt", pr, v] as const,
};

export function useProjectAdvisory(
  organizationId: string | undefined,
  projectId: string | undefined,
) {
  return useQuery({
    queryKey: keys.project(organizationId ?? "", projectId ?? ""),
    queryFn: () => advisorApi.adviseProject(organizationId as string, projectId as string),
    enabled: !!organizationId && !!projectId,
  });
}

export function useAgentAdvisory(
  organizationId: string | undefined,
  projectId: string | undefined,
  agentId: string | undefined,
) {
  return useQuery({
    queryKey: keys.agent(organizationId ?? "", projectId ?? "", agentId ?? ""),
    queryFn: () =>
      advisorApi.adviseAgent(organizationId as string, projectId as string, agentId as string),
    enabled: !!organizationId && !!projectId && !!agentId,
  });
}

export function usePromptAdvisory(
  organizationId: string | undefined,
  projectId: string | undefined,
  promptId: string | undefined,
  versionId?: string,
) {
  return useQuery({
    queryKey: keys.prompt(organizationId ?? "", projectId ?? "", promptId ?? "", versionId ?? ""),
    queryFn: () =>
      advisorApi.advisePrompt(
        organizationId as string,
        projectId as string,
        promptId as string,
        versionId,
      ),
    enabled: !!organizationId && !!projectId && !!promptId,
  });
}
