"use client";

import { useQuery } from "@tanstack/react-query";
import {
  knowledgeApi,
  type KnowledgeNodeFilterParams,
} from "@/lib/api/knowledge";

const keys = {
  nodes: (params: KnowledgeNodeFilterParams) => ["knowledge", "nodes", params] as const,
  node: (nodeKey: string) => ["knowledge", "nodes", nodeKey] as const,
  graph: () => ["knowledge", "graph"] as const,
};

export function useKnowledgeNodes(params: KnowledgeNodeFilterParams = {}) {
  return useQuery({
    queryKey: keys.nodes(params),
    queryFn: () => knowledgeApi.listNodes(params),
  });
}

export function useKnowledgeNode(nodeKey: string | undefined, enabled = true) {
  return useQuery({
    queryKey: keys.node(nodeKey ?? ""),
    queryFn: () => knowledgeApi.getNode(nodeKey as string),
    enabled: enabled && !!nodeKey,
  });
}

export function useKnowledgeGraph() {
  return useQuery({
    queryKey: keys.graph(),
    queryFn: () => knowledgeApi.graph(),
  });
}
