// ---------------------------------------------------------------------------
// API types — mirror the backend DTOs (com.broksforge.*.web.dto)
// ---------------------------------------------------------------------------

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DEACTIVATED";
export type OrganizationRole = "OWNER" | "ADMIN" | "MEMBER";
export type OrganizationStatus = "ACTIVE" | "ARCHIVED";
export type ProjectStatus = "ACTIVE" | "ARCHIVED";

export interface UserResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  fullName: string;
  status: UserStatus;
  emailVerified: boolean;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: UserResponse;
}

export interface OrganizationResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: OrganizationStatus;
  ownerId: string;
  currentUserRole: OrganizationRole;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface OrganizationMemberResponse {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  role: OrganizationRole;
  joinedAt: string;
}

export interface ProjectResponse {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  description: string | null;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ApiKeyResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  keyPrefix: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revoked: boolean;
  revokedAt: string | null;
  createdAt: string;
}

export interface CreatedApiKeyResponse {
  plaintextKey: string;
  apiKey: ApiKeyResponse;
}

export interface MessageResponse {
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
}

export interface FieldValidationError {
  field: string;
  rejectedValue: unknown;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  errors?: FieldValidationError[];
}
