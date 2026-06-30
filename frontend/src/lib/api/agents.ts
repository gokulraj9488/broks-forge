import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// Enums (mirror com.broksforge.modules.agent.domain.*)
// ---------------------------------------------------------------------------
export type AgentVisibility = "PRIVATE" | "ORGANIZATION" | "PUBLIC";
export type AgentFramework =
  | "SPRING_AI"
  | "LANGGRAPH"
  | "LANGCHAIN"
  | "CREWAI"
  | "AUTOGEN"
  | "PYDANTIC_AI"
  | "SEMANTIC_KERNEL"
  | "LLAMA_INDEX"
  | "CUSTOM_REST"
  | "OTHER";
export type AgentLanguage =
  | "JAVA"
  | "PYTHON"
  | "NODE"
  | "TYPESCRIPT"
  | "GO"
  | "RUST"
  | "CSHARP"
  | "OTHER";
export type AgentAuthType = "NONE" | "API_KEY" | "BEARER_TOKEN" | "BASIC_AUTH" | "CUSTOM_HEADER";
export type AgentHealthStatus = "UNKNOWN" | "HEALTHY" | "DEGRADED" | "UNHEALTHY";
export type AgentLifecycleStatus = "ACTIVE" | "ARCHIVED";
export type LlmProvider =
  | "OPENAI"
  | "ANTHROPIC"
  | "AZURE_OPENAI"
  | "AWS_BEDROCK"
  | "GOOGLE_VERTEX"
  | "GOOGLE_GEMINI"
  | "COHERE"
  | "MISTRAL"
  | "META_LLAMA"
  | "OLLAMA"
  | "HUGGINGFACE"
  | "CUSTOM"
  | "OTHER";
export type DeploymentEnvironment = "DEVELOPMENT" | "STAGING" | "PRODUCTION";
export type HealthCheckType = "MANUAL" | "SCHEDULED";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface AgentCapabilities {
  streaming: boolean;
  memory: boolean;
  rag: boolean;
  toolCalling: boolean;
  structuredOutput: boolean;
  reasoning: boolean;
  multiAgent: boolean;
  customMetadata?: Record<string, unknown>;
}

export interface AgentResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  slug: string;
  description: string | null;
  ownerId: string;
  visibility: AgentVisibility;
  framework: AgentFramework;
  language: AgentLanguage;
  endpointUrl: string;
  authType: AgentAuthType;
  currentActiveVersionId: string | null;
  healthStatus: AgentHealthStatus;
  lastHealthCheckAt: string | null;
  status: AgentLifecycleStatus;
  capabilities: AgentCapabilities;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AgentSummaryResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  slug: string;
  description: string | null;
  framework: AgentFramework;
  language: AgentLanguage;
  visibility: AgentVisibility;
  status: AgentLifecycleStatus;
  healthStatus: AgentHealthStatus;
  lastHealthCheckAt: string | null;
  currentActiveVersionId: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AgentVersionResponse {
  id: string;
  agentId: string;
  versionNumber: string;
  sequence: number;
  model: string;
  provider: LlmProvider;
  frameworkVersion: string | null;
  gitCommitSha: string | null;
  promptVersion: string | null;
  environment: DeploymentEnvironment;
  releaseNotes: string | null;
  deploymentTimestamp: string;
  active: boolean;
  rollbackReady: boolean;
  createdAt: string;
}

export interface AgentCredentialResponse {
  id: string;
  agentId: string;
  authType: AgentAuthType;
  username: string | null;
  headerName: string | null;
  secretHint: string | null;
  keyVersion: number;
  active: boolean;
  createdAt: string;
}

export interface AgentHealthCheckResponse {
  id: string;
  agentId: string;
  versionId: string | null;
  checkType: HealthCheckType;
  status: AgentHealthStatus;
  success: boolean;
  httpStatus: number | null;
  latencyMs: number | null;
  checkedAt: string;
  failureReason: string | null;
}

export interface AgentHealthSummaryResponse {
  agentId: string;
  currentStatus: AgentHealthStatus;
  lastCheckedAt: string | null;
  availabilityPercent: number | null;
  windowDays: number;
  totalChecks: number;
  successfulChecks: number;
  recent: AgentHealthCheckResponse[];
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface RegisterAgentPayload {
  name: string;
  slug?: string;
  description?: string;
  visibility: AgentVisibility;
  framework: AgentFramework;
  language: AgentLanguage;
  endpointUrl: string;
  authType: AgentAuthType;
  capabilities?: AgentCapabilities;
  tags?: string[];
}

export type UpdateAgentPayload = Partial<RegisterAgentPayload>;

export interface RegisterAgentVersionPayload {
  versionNumber: string;
  model: string;
  provider: LlmProvider;
  frameworkVersion?: string;
  gitCommitSha?: string;
  promptVersion?: string;
  environment: DeploymentEnvironment;
  releaseNotes?: string;
  rollbackReady?: boolean;
  activate?: boolean;
}

export interface SetAgentCredentialPayload {
  authType: AgentAuthType;
  secret?: string;
  username?: string;
  headerName?: string;
}

export interface AgentFilterParams extends PageParams {
  q?: string;
  framework?: AgentFramework;
  language?: AgentLanguage;
  visibility?: AgentVisibility;
  status?: AgentLifecycleStatus;
  healthStatus?: AgentHealthStatus;
  tag?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/agents`;
}

export const agentsApi = {
  list: (organizationId: string, projectId: string, params: AgentFilterParams = {}) =>
    apiClient
      .get<PageResponse<AgentSummaryResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .get<AgentResponse>(`${base(organizationId, projectId)}/${agentId}`)
      .then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: RegisterAgentPayload) =>
    apiClient
      .post<AgentResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  update: (organizationId: string, projectId: string, agentId: string, payload: UpdateAgentPayload) =>
    apiClient
      .patch<AgentResponse>(`${base(organizationId, projectId)}/${agentId}`, payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, agentId: string) =>
    apiClient.delete<void>(`${base(organizationId, projectId)}/${agentId}`).then((r) => r.data),

  archive: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .post<AgentResponse>(`${base(organizationId, projectId)}/${agentId}/archive`)
      .then((r) => r.data),

  unarchive: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .post<AgentResponse>(`${base(organizationId, projectId)}/${agentId}/unarchive`)
      .then((r) => r.data),

  listVersions: (organizationId: string, projectId: string, agentId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<AgentVersionResponse>>(`${base(organizationId, projectId)}/${agentId}/versions`, { params })
      .then((r) => r.data),

  registerVersion: (
    organizationId: string,
    projectId: string,
    agentId: string,
    payload: RegisterAgentVersionPayload,
  ) =>
    apiClient
      .post<AgentVersionResponse>(`${base(organizationId, projectId)}/${agentId}/versions`, payload)
      .then((r) => r.data),

  activateVersion: (organizationId: string, projectId: string, agentId: string, versionId: string) =>
    apiClient
      .post<AgentVersionResponse>(
        `${base(organizationId, projectId)}/${agentId}/versions/${versionId}/activate`,
      )
      .then((r) => r.data),

  rollbackVersion: (organizationId: string, projectId: string, agentId: string, versionId: string) =>
    apiClient
      .post<AgentVersionResponse>(
        `${base(organizationId, projectId)}/${agentId}/versions/${versionId}/rollback`,
      )
      .then((r) => r.data),

  listCredentials: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .get<AgentCredentialResponse[]>(`${base(organizationId, projectId)}/${agentId}/credentials`)
      .then((r) => r.data),

  setCredential: (
    organizationId: string,
    projectId: string,
    agentId: string,
    payload: SetAgentCredentialPayload,
  ) =>
    apiClient
      .post<AgentCredentialResponse>(`${base(organizationId, projectId)}/${agentId}/credentials`, payload)
      .then((r) => r.data),

  deleteCredential: (
    organizationId: string,
    projectId: string,
    agentId: string,
    credentialId: string,
  ) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${agentId}/credentials/${credentialId}`)
      .then((r) => r.data),

  runHealthCheck: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .post<AgentHealthCheckResponse>(`${base(organizationId, projectId)}/${agentId}/health-check`)
      .then((r) => r.data),

  getHealth: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .get<AgentHealthSummaryResponse>(`${base(organizationId, projectId)}/${agentId}/health`)
      .then((r) => r.data),

  getHealthHistory: (
    organizationId: string,
    projectId: string,
    agentId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<AgentHealthCheckResponse>>(
        `${base(organizationId, projectId)}/${agentId}/health/history`,
        { params },
      )
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const FRAMEWORK_OPTIONS: { value: AgentFramework; label: string }[] = [
  { value: "SPRING_AI", label: "Spring AI" },
  { value: "LANGGRAPH", label: "LangGraph" },
  { value: "LANGCHAIN", label: "LangChain" },
  { value: "CREWAI", label: "CrewAI" },
  { value: "AUTOGEN", label: "AutoGen" },
  { value: "PYDANTIC_AI", label: "PydanticAI" },
  { value: "SEMANTIC_KERNEL", label: "Semantic Kernel" },
  { value: "LLAMA_INDEX", label: "LlamaIndex" },
  { value: "CUSTOM_REST", label: "Custom REST" },
  { value: "OTHER", label: "Other" },
];

export const LANGUAGE_OPTIONS: { value: AgentLanguage; label: string }[] = [
  { value: "JAVA", label: "Java" },
  { value: "PYTHON", label: "Python" },
  { value: "NODE", label: "Node" },
  { value: "TYPESCRIPT", label: "TypeScript" },
  { value: "GO", label: "Go" },
  { value: "RUST", label: "Rust" },
  { value: "CSHARP", label: "C#" },
  { value: "OTHER", label: "Other" },
];

export const VISIBILITY_OPTIONS: { value: AgentVisibility; label: string }[] = [
  { value: "PRIVATE", label: "Private" },
  { value: "ORGANIZATION", label: "Organization" },
  { value: "PUBLIC", label: "Public" },
];

export const AUTH_TYPE_OPTIONS: { value: AgentAuthType; label: string }[] = [
  { value: "NONE", label: "None" },
  { value: "API_KEY", label: "API Key" },
  { value: "BEARER_TOKEN", label: "Bearer Token" },
  { value: "BASIC_AUTH", label: "Basic Auth" },
  { value: "CUSTOM_HEADER", label: "Custom Header" },
];

export const PROVIDER_OPTIONS: { value: LlmProvider; label: string }[] = [
  { value: "OPENAI", label: "OpenAI" },
  { value: "ANTHROPIC", label: "Anthropic" },
  { value: "AZURE_OPENAI", label: "Azure OpenAI" },
  { value: "AWS_BEDROCK", label: "AWS Bedrock" },
  { value: "GOOGLE_VERTEX", label: "Google Vertex" },
  { value: "GOOGLE_GEMINI", label: "Google Gemini" },
  { value: "COHERE", label: "Cohere" },
  { value: "MISTRAL", label: "Mistral" },
  { value: "META_LLAMA", label: "Meta Llama" },
  { value: "OLLAMA", label: "Ollama" },
  { value: "HUGGINGFACE", label: "Hugging Face" },
  { value: "CUSTOM", label: "Custom" },
  { value: "OTHER", label: "Other" },
];

export const ENVIRONMENT_OPTIONS: { value: DeploymentEnvironment; label: string }[] = [
  { value: "DEVELOPMENT", label: "Development" },
  { value: "STAGING", label: "Staging" },
  { value: "PRODUCTION", label: "Production" },
];

export const HEALTH_STATUS_OPTIONS: { value: AgentHealthStatus; label: string }[] = [
  { value: "UNKNOWN", label: "Unknown" },
  { value: "HEALTHY", label: "Healthy" },
  { value: "DEGRADED", label: "Degraded" },
  { value: "UNHEALTHY", label: "Unhealthy" },
];
