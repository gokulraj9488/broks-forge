"use client";

import { useCallback, useEffect, useRef, useState } from "react";
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

/** How long before expiry to warn the user, and the visible countdown length. */
const WARNING_BEFORE_MS = 60_000;
export const WARNING_SECONDS = WARNING_BEFORE_MS / 1000;

/** Throttle for activity writes; the countdown ticks every second. */
const ACTIVITY_THROTTLE_MS = 5_000;
const TICK_INTERVAL_MS = 1_000;

export interface IdleTimeoutState {
  /** True while the pre-logout warning modal should be shown. */
  warningActive: boolean;
  /** Seconds remaining until automatic logout. */
  secondsLeft: number;
  /** Dismiss the warning and reset the idle timer ("Stay signed in"). */
  stayActive: () => void;
  /** Log out immediately. */
  logoutNow: () => void;
}

/**
 * True idle-session timeout with a pre-logout warning. Activity — mouse, keyboard,
 * touch, scroll, route navigation, or any API request (bumped by the axios
 * interceptor) — resets the timer. Sixty seconds before expiry a warning modal
 * appears with a live countdown; passive activity no longer silently resets the
 * timer during the warning, so the user must explicitly choose to stay signed in.
 * The last-activity clock lives in localStorage, so activity in any tab keeps all
 * tabs alive. Mounted once inside AuthGuard, so it only runs on authenticated views.
 */
export function useIdleTimeout(enabled: boolean): IdleTimeoutState {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const [warningActive, setWarningActive] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(WARNING_SECONDS);
  const warningRef = useRef(false);
  const expiredRef = useRef(false);

  const expire = useCallback(() => {
    if (expiredRef.current) return;
    expiredRef.current = true;
    const { refreshToken, clearAuth } = useAuthStore.getState();
    // Best-effort server-side revocation of this session's refresh token.
    if (refreshToken) void authApi.logout(refreshToken).catch(() => undefined);
    clearAuth();
    queryClient.clear();
    router.replace("/login?reason=session-expired");
  }, [queryClient, router]);

  const stayActive = useCallback(() => {
    touchActivity();
    warningRef.current = false;
    setWarningActive(false);
  }, []);

  // Route navigation counts as activity and clears any pending warning.
  useEffect(() => {
    if (enabled) stayActive();
  }, [enabled, pathname, stayActive]);

  useEffect(() => {
    if (!enabled || IDLE_TIMEOUT_MINUTES <= 0) return;
    const timeoutMs = IDLE_TIMEOUT_MINUTES * 60_000;
    const warnAtMs = Math.max(0, timeoutMs - WARNING_BEFORE_MS);

    expiredRef.current = false;
    warningRef.current = false;
    touchActivity();

    let lastWrite = Date.now();
    const onActivity = () => {
      // During the warning, don't let a stray movement silently reset the timer —
      // the user must click "Stay signed in".
      if (warningRef.current) return;
      const now = Date.now();
      if (now - lastWrite < ACTIVITY_THROTTLE_MS) return;
      lastWrite = now;
      touchActivity();
    };

    const tick = () => {
      const idle = Date.now() - getLastActivity();
      if (idle >= timeoutMs) {
        expire();
        return;
      }
      if (idle >= warnAtMs) {
        warningRef.current = true;
        setWarningActive(true);
        setSecondsLeft(Math.max(0, Math.ceil((timeoutMs - idle) / 1000)));
      } else if (warningRef.current) {
        warningRef.current = false;
        setWarningActive(false);
      }
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
      if (document.visibilityState === "visible") tick();
    };
    document.addEventListener("visibilitychange", onVisibility);

    const interval = window.setInterval(tick, TICK_INTERVAL_MS);

    return () => {
      events.forEach((event) => window.removeEventListener(event, onActivity));
      document.removeEventListener("visibilitychange", onVisibility);
      window.clearInterval(interval);
    };
  }, [enabled, expire]);

  return { warningActive, secondsLeft, stayActive, logoutNow: expire };
}
