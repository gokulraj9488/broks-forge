"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/lib/stores/auth-store";
import { getLastActivity, touchActivity } from "@/lib/session-activity";

/**
 * Idle-session timeout in minutes. Build-time configurable via
 * NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES; 0 (or negative) disables the timeout.
 */
export const IDLE_TIMEOUT_MINUTES = Number(process.env.NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES ?? 30);

/** How often (ms) mouse/key activity is written and idleness is re-checked. */
const ACTIVITY_THROTTLE_MS = 5_000;
const CHECK_INTERVAL_MS = 30_000;

/**
 * Signs the user out after a configurable period of inactivity. Activity =
 * mouse movement, keyboard input, touch, scroll, route navigation, or any API
 * request (bumped by the axios request interceptor). The last-activity clock
 * lives in localStorage, so activity in any tab keeps all tabs alive.
 *
 * Mounted once inside AuthGuard, so it only ever runs on authenticated views.
 */
export function useIdleTimeout(enabled: boolean) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  // Route navigation counts as activity.
  useEffect(() => {
    if (enabled) touchActivity();
  }, [enabled, pathname]);

  useEffect(() => {
    if (!enabled || IDLE_TIMEOUT_MINUTES <= 0) return;
    const timeoutMs = IDLE_TIMEOUT_MINUTES * 60_000;

    touchActivity();

    let lastWrite = 0;
    const onActivity = () => {
      const now = Date.now();
      if (now - lastWrite < ACTIVITY_THROTTLE_MS) return;
      lastWrite = now;
      touchActivity();
    };

    let expired = false;
    const expire = () => {
      if (expired) return;
      expired = true;
      const { refreshToken, clearAuth } = useAuthStore.getState();
      // Best-effort server-side revocation of this session's refresh token.
      if (refreshToken) void authApi.logout(refreshToken).catch(() => undefined);
      clearAuth();
      queryClient.clear();
      router.replace("/login?reason=session-expired");
    };

    const check = () => {
      if (Date.now() - getLastActivity() >= timeoutMs) expire();
    };

    const events: (keyof WindowEventMap)[] = [
      "mousemove",
      "mousedown",
      "keydown",
      "scroll",
      "touchstart",
    ];
    events.forEach((event) => window.addEventListener(event, onActivity, { passive: true }));

    // Re-check immediately when a backgrounded tab becomes visible again.
    const onVisibility = () => {
      if (document.visibilityState === "visible") check();
    };
    document.addEventListener("visibilitychange", onVisibility);

    const interval = window.setInterval(check, CHECK_INTERVAL_MS);

    return () => {
      events.forEach((event) => window.removeEventListener(event, onActivity));
      document.removeEventListener("visibilitychange", onVisibility);
      window.clearInterval(interval);
    };
  }, [enabled, queryClient, router]);
}
