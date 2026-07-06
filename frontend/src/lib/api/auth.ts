import { apiClient } from "@/lib/api/client";
import type { AuthResponse, MessageResponse } from "@/lib/api/types";

export interface RegisterPayload {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface PasswordChangeTicket {
  ticket: string;
  expiresAt: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiClient.post<AuthResponse>("/api/v1/auth/register", payload).then((r) => r.data),

  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>("/api/v1/auth/login", { email, password }).then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post<MessageResponse>("/api/v1/auth/logout", { refreshToken }).then((r) => r.data),

  // OTP password change (ADR 0017), all authenticated. Step 1: verify the
  // current password and e-mail a 6-digit code.
  requestPasswordChangeOtp: (currentPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/password-change/request", { currentPassword })
      .then((r) => r.data),

  // Step 2: verify the code; returns a single-use ticket for the final step.
  verifyPasswordChangeOtp: (code: string) =>
    apiClient
      .post<PasswordChangeTicket>("/api/v1/auth/password-change/verify", { code })
      .then((r) => r.data),

  // Step 3: set the new password with the ticket; revokes every session.
  completePasswordChange: (ticket: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/password-change/complete", { ticket, newPassword })
      .then((r) => r.data),

  // Legacy emailed-link password change (kept for compatibility; the OTP flow
  // above is the default UX — see ADR 0017).
  changePassword: (currentPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/change-password", { currentPassword })
      .then((r) => r.data),

  confirmPasswordChange: (token: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>("/api/v1/auth/confirm-password-change", { token, newPassword })
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
