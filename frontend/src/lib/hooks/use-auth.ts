"use client";

import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { authApi, type RegisterPayload } from "@/lib/api/auth";
import { usersApi, type UpdateProfilePayload } from "@/lib/api/users";
import { useAuthStore } from "@/lib/stores/auth-store";

export const ME_QUERY_KEY = ["me"] as const;

/** Combined auth state derived from the store and a background /me refresh. */
export function useAuth() {
  const { accessToken, user: storedUser, hydrated, setUser } = useAuthStore();

  const query = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: usersApi.me,
    enabled: hydrated && !!accessToken,
    staleTime: 60_000,
    retry: false,
  });

  useEffect(() => {
    if (query.data) setUser(query.data);
  }, [query.data, setUser]);

  return {
    user: query.data ?? storedUser,
    isAuthenticated: !!accessToken,
    hydrated,
    isLoading: !hydrated || (!!accessToken && query.isLoading),
  };
}

export function useLogin() {
  const queryClient = useQueryClient();
  const setAuth = useAuthStore((s) => s.setAuth);
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.login(email, password),
    onSuccess: (data) => {
      setAuth(data);
      queryClient.setQueryData(ME_QUERY_KEY, data.user);
    },
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  const setAuth = useAuthStore((s) => s.setAuth);
  return useMutation({
    mutationFn: (payload: RegisterPayload) => authApi.register(payload),
    onSuccess: (data) => {
      setAuth(data);
      queryClient.setQueryData(ME_QUERY_KEY, data.user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const { refreshToken, clearAuth } = useAuthStore.getState();
  return useMutation({
    mutationFn: async () => {
      if (refreshToken) {
        await authApi.logout(refreshToken).catch(() => undefined);
      }
    },
    onSettled: () => {
      clearAuth();
      queryClient.clear();
    },
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);
  return useMutation({
    mutationFn: (payload: UpdateProfilePayload) => usersApi.updateProfile(payload),
    onSuccess: (user) => {
      setUser(user);
      queryClient.setQueryData(ME_QUERY_KEY, user);
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: ({
      currentPassword,
      newPassword,
    }: {
      currentPassword: string;
      newPassword: string;
    }) => authApi.changePassword(currentPassword, newPassword),
  });
}
