import { apiClient } from "@/lib/api/client";
import type { AuthResponse, MessageResponse } from "@/lib/api/types";

export interface RegisterPayload {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiClient.post<AuthResponse>("/api/v1/auth/register", payload).then((r) => r.data),

  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>("/api/v1/auth/login", { email, password }).then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post<MessageResponse>("/api/v1/auth/logout", { refreshToken }).then((r) => r.data),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/change-password", { currentPassword, newPassword })
      .then((r) => r.data),

  forgotPassword: (email: string) =>
    apiClient.post<MessageResponse>("/api/v1/auth/forgot-password", { email }).then((r) => r.data),

  resetPassword: (token: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/reset-password", { token, newPassword })
      .then((r) => r.data),

  verifyEmail: (token: string) =>
    apiClient.post<MessageResponse>("/api/v1/auth/verify-email", { token }).then((r) => r.data),

  resendVerification: (email: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/resend-verification", { email })
      .then((r) => r.data),
};
