import { apiClient } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";

export interface UpdateProfilePayload {
  firstName?: string;
  lastName?: string;
}

export const usersApi = {
  me: () => apiClient.get<UserResponse>("/api/v1/users/me").then((r) => r.data),

  updateProfile: (payload: UpdateProfilePayload) =>
    apiClient.patch<UserResponse>("/api/v1/users/me", payload).then((r) => r.data),
};
