import { APIRequestContext, expect } from "@playwright/test";

/**
 * Backend API helpers used to SEED state quickly for UI tests (register a user, create an org /
 * project / agent) instead of driving every precondition through the browser. This keeps each spec
 * focused on the behaviour it actually asserts. The API contract is stable and covered separately by
 * the Postman collection and the backend integration tests.
 */
export const API_URL = process.env.API_URL ?? "http://localhost:8080";
export const PASSWORD = "StrongPass!2026";

export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@example.com`;
}

export interface SeededUser {
  email: string;
  password: string;
  token: string;
}

export async function registerViaApi(
  request: APIRequestContext,
  email: string = uniqueEmail(),
  password: string = PASSWORD,
): Promise<SeededUser> {
  const res = await request.post(`${API_URL}/api/v1/auth/register`, {
    data: { email, password, firstName: "E2E", lastName: "User" },
  });
  expect(res.status(), "register").toBe(201);
  const body = await res.json();
  return { email, password, token: body.accessToken };
}

function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function apiCreateOrganization(
  request: APIRequestContext,
  token: string,
  name: string,
): Promise<{ id: string; slug: string }> {
  const res = await request.post(`${API_URL}/api/v1/organizations`, {
    headers: auth(token),
    data: { name },
  });
  expect(res.status(), "create org").toBe(201);
  return res.json();
}

export async function apiCreateProject(
  request: APIRequestContext,
  token: string,
  orgId: string,
  name: string,
): Promise<{ id: string; slug: string }> {
  const res = await request.post(`${API_URL}/api/v1/organizations/${orgId}/projects`, {
    headers: auth(token),
    data: { name },
  });
  expect(res.status(), "create project").toBe(201);
  return res.json();
}

export async function apiRegisterAgent(
  request: APIRequestContext,
  token: string,
  orgId: string,
  projectId: string,
  name: string,
  authType: string = "NONE",
): Promise<{ id: string; slug: string }> {
  const res = await request.post(
    `${API_URL}/api/v1/organizations/${orgId}/projects/${projectId}/agents`,
    {
      headers: auth(token),
      data: {
        name,
        visibility: "PRIVATE",
        framework: "CUSTOM_REST",
        language: "PYTHON",
        endpointUrl: "https://api.groq.com/openai/v1/chat/completions",
        authType,
        capabilities: {
          streaming: true,
          toolCalling: false,
          structuredOutput: false,
          reasoning: false,
          memory: false,
          rag: false,
          multiAgent: false,
        },
      },
    },
  );
  expect(res.status(), "register agent").toBe(201);
  return res.json();
}

/** A ready-to-use project scope (user + org + project) for tests that need an authenticated area. */
export async function seedProjectScope(request: APIRequestContext) {
  const user = await registerViaApi(request);
  const org = await apiCreateOrganization(request, user.token, "E2E Org");
  const project = await apiCreateProject(request, user.token, org.id, "E2E Project");
  return { user, org, project };
}
