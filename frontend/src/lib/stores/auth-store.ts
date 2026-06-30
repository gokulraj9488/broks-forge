import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthResponse, UserResponse } from "@/lib/api/types";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserResponse | null;
  hydrated: boolean;

  setAuth: (auth: AuthResponse) => void;
  setUser: (user: UserResponse) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  clearAuth: () => void;
  setHydrated: () => void;
}

/**
 * Client-side auth state. Tokens are persisted to localStorage so sessions
 * survive reloads. The axios layer reads/writes tokens here via getState().
 *
 * Note: a production deployment may prefer httpOnly cookies to mitigate XSS;
 * this SPA uses the standard bearer-token-in-storage pattern with a strict CSP.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      hydrated: false,

      setAuth: (auth) =>
        set({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          user: auth.user,
        }),
      setUser: (user) => set({ user }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      clearAuth: () => set({ accessToken: null, refreshToken: null, user: null }),
      setHydrated: () => set({ hydrated: true }),
    }),
    {
      name: "broksforge.auth",
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHydrated();
      },
    },
  ),
);
