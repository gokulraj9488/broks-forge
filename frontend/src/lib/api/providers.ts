import { apiClient } from "@/lib/api/client";
import type {
  AgentAuthType,
  AgentCapabilities,
  AgentHealthStatus,
  HealthProbeStrategy,
  LlmProvider,
} from "@/lib/api/agents";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// DTOs (mirror com.broksforge.modules.provider.web.dto.*)
// ---------------------------------------------------------------------------
export interface ProviderResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  type: LlmProvider;
  baseUrl: string;
  authType: AgentAuthType;
  apiKeyHint: string | null;
  apiKeyConfigured: boolean;
  defaultHeaders: Record<string, unknown>;
  defaultModel: string | null;
  supportedModels: string[];
  capabilities: AgentCapabilities;
  rateLimits: Record<string, unknown>;
  pricingMetadata: Record<string, unknown>;
  healthStatus: AgentHealthStatus;
  lastHealthCheckAt: string | null;
  enabled: boolean;
  lastUsedAt: string | null;
  modelCount: number;
  linkedAgentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProviderPayload {
  name: string;
  type: LlmProvider;
  baseUrl: string;
  authType?: AgentAuthType;
  apiKey?: string;
  defaultHeaders?: Record<string, unknown>;
  defaultModel?: string;
  supportedModels?: string[];
  capabilities?: AgentCapabilities;
  rateLimits?: Record<string, unknown>;
  pricingMetadata?: Record<string, unknown>;
}

export type UpdateProviderPayload = Partial<CreateProviderPayload>;

export interface EmbeddingModelsResponse {
  supported: boolean;
  models: string[];
  message: string | null;
}

export interface ChatModelsResponse {
  supported: boolean;
  models: string[];
  message: string | null;
}

export interface ProviderConnectionTestResponse {
  success: boolean;
  httpStatus: number | null;
  latencyMs: number;
  message: string;
  probeStrategy: HealthProbeStrategy | null;
  probeUrl: string | null;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/providers`;
}

export const providersApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<ProviderResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, providerId: string) =>
    apiClient.get<ProviderResponse>(`${base(organizationId, projectId)}/${providerId}`).then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: CreateProviderPayload) =>
    apiClient.post<ProviderResponse>(base(organizationId, projectId), payload).then((r) => r.data),

  update: (organizationId: string, projectId: string, providerId: string, payload: UpdateProviderPayload) =>
    apiClient
      .patch<ProviderResponse>(`${base(organizationId, projectId)}/${providerId}`, payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, providerId: string) =>
    apiClient.delete<void>(`${base(organizationId, projectId)}/${providerId}`).then((r) => r.data),

  duplicate: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .post<ProviderResponse>(`${base(organizationId, projectId)}/${providerId}/duplicate`)
      .then((r) => r.data),

  enable: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .post<ProviderResponse>(`${base(organizationId, projectId)}/${providerId}/enable`)
      .then((r) => r.data),

  disable: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .post<ProviderResponse>(`${base(organizationId, projectId)}/${providerId}/disable`)
      .then((r) => r.data),

  embeddingModels: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .get<EmbeddingModelsResponse>(`${base(organizationId, projectId)}/${providerId}/embedding-models`)
      .then((r) => r.data),

  chatModels: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .get<ChatModelsResponse>(`${base(organizationId, projectId)}/${providerId}/chat-models`)
      .then((r) => r.data),

  testConnection: (organizationId: string, projectId: string, providerId: string) =>
    apiClient
      .post<ProviderConnectionTestResponse>(`${base(organizationId, projectId)}/${providerId}/test-connection`)
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const PROVIDER_TYPE_OPTIONS: { value: LlmProvider; label: string; defaultBaseUrl: string }[] = [
  {
    value: "OPENAI",
    label: "OpenAI",
    defaultBaseUrl: "https://api.openai.com/v1/chat/completions",
  },
  {
    value: "GROQ",
    label: "Groq",
    defaultBaseUrl: "https://api.groq.com/openai/v1/chat/completions",
  },
  {
    value: "OPENROUTER",
    label: "OpenRouter",
    defaultBaseUrl: "https://openrouter.ai/api/v1/chat/completions",
  },
  {
    value: "GOOGLE_GEMINI",
    label: "Google AI Studio",
    defaultBaseUrl: "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
  },
  {
    value: "ANTHROPIC",
    label: "Anthropic",
    defaultBaseUrl: "https://api.anthropic.com/v1/messages",
  },
  {
    value: "OLLAMA",
    label: "Ollama",
    defaultBaseUrl: "http://localhost:11434/api/chat",
  },
  {
    value: "AZURE_OPENAI",
    label: "Azure OpenAI",
    defaultBaseUrl: "",
  },
  {
    value: "CUSTOM",
    label: "Custom REST",
    defaultBaseUrl: "",
  },
];
