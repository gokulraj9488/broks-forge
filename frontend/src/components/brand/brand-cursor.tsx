"use client";

import { useEffect, useRef } from "react";

/**
 * Brok's Forge branded cursor — a small forge-hammer glyph that replaces the native
 * pointer. Feature-flagged, and automatically inert wherever it would be wrong: touch
 * devices, `prefers-reduced-motion`, and text-entry/resize contexts (which keep the
 * native cursor so typing and textarea-resizing are unaffected).
 *
 * <p>Deliberately imperative rather than React-state-driven: position and hover state
 * are written straight to DOM style properties inside a single {@code requestAnimationFrame}
 * loop, using only {@code transform}/{@code opacity} (GPU-composited, no layout
 * thrash) so it tracks the pointer at a steady 60&nbsp;fps without triggering re-renders.</p>
 *
 * <p>Feature flag: {@code NEXT_PUBLIC_ENABLE_BRAND_CURSOR} (default enabled; set to
 * {@code "false"} to disable without a code change, mirroring
 * {@code NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES}'s pattern in use-idle-timeout.ts).</p>
 */
const FEATURE_ENABLED = process.env.NEXT_PUBLIC_ENABLE_BRAND_CURSOR !== "false";

/** Elements where the native cursor (I-beam, resize grips, etc.) must take precedence. */
const NATIVE_CURSOR_SELECTOR = 'input, textarea, select, [contenteditable="true"]';
const BUTTON_SELECTOR = 'button, a, [role="button"], summary, input[type="submit"], input[type="button"]';
const GLOW_SELECTOR = "[data-cursor-glow]";

/** Smoothing factor for trailing movement — higher = snappier, lower = smoother/laggier. */
const LERP = 0.22;

export function BrandCursor() {
  const dotRef = useRef<HTMLDivElement>(null);
  const sparksRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!FEATURE_ENABLED) return;
    if (typeof window === "undefined") return;
    const isTouch = window.matchMedia("(pointer: coarse)").matches || "ontouchstart" in window;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (isTouch || reducedMotion) return;

    const dot = dotRef.current;
    const sparksHost = sparksRef.current;
    if (!dot || !sparksHost) return;

    document.body.classList.add("brand-cursor-active");

    let targetX = window.innerWidth / 2;
    let targetY = window.innerHeight / 2;
    let x = targetX;
    let y = targetY;
    let hoverMode: "default" | "button" | "glow" | "hidden" = "default";
    let raf = 0;
    let visible = false;

    const onPointerMove = (event: PointerEvent) => {
      targetX = event.clientX;
      targetY = event.clientY;
      if (!visible) {
        visible = true;
        x = targetX;
        y = targetY;
        dot.style.opacity = "1";
      }

      const target = event.target as Element | null;
      const nextMode: typeof hoverMode = target?.closest(NATIVE_CURSOR_SELECTOR)
        ? "hidden"
        : target?.closest(BUTTON_SELECTOR)
          ? "button"
          : target?.closest(GLOW_SELECTOR)
            ? "glow"
            : "default";
      if (nextMode !== hoverMode) {
        hoverMode = nextMode;
        dot.dataset.mode = hoverMode;
      }
    };

    const onPointerLeave = () => {
      visible = false;
      dot.style.opacity = "0";
    };

    const strike = () => {
      if (hoverMode === "hidden") return; // don't strike/spark over text inputs
      dot.dataset.striking = "true";
      window.setTimeout(() => {
        delete dot.dataset.striking;
      }, 110);

      const sparkCount = 5;
      for (let i = 0; i < sparkCount; i++) {
        const angle = (Math.PI * 2 * i) / sparkCount + (Math.random() - 0.5);
        const distance = 10 + Math.random() * 8;
        const spark = document.createElement("span");
        spark.className = "brand-cursor-spark";
        spark.style.setProperty("--dx", `${Math.cos(angle) * distance}px`);
        spark.style.setProperty("--dy", `${Math.sin(angle) * distance}px`);
        sparksHost.appendChild(spark);
        window.setTimeout(() => spark.remove(), 130);
      }
    };

    const tick = () => {
      x += (targetX - x) * LERP;
      y += (targetY - y) * LERP;
      dot.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      sparksHost.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      raf = requestAnimationFrame(tick);
    };

    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("pointerdown", strike, { passive: true });
    window.addEventListener("mouseleave", onPointerLeave);
    raf = requestAnimationFrame(tick);

    return () => {
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerdown", strike);
      window.removeEventListener("mouseleave", onPointerLeave);
      cancelAnimationFrame(raf);
      document.body.classList.remove("brand-cursor-active");
    };
  }, []);

  if (!FEATURE_ENABLED) return null;

  return (
    <>
      {/* Hides the native cursor everywhere the custom one takes over, restoring it for
          text-entry and resize contexts. Scoped by a body class the effect above toggles,
          so a disabled cursor (touch / reduced-motion / flag off) never removes the native one. */}
      <style>{`
        body.brand-cursor-active { cursor: none; }
        body.brand-cursor-active ${NATIVE_CURSOR_SELECTOR} { cursor: text; }

        .brand-cursor-dot {
          position: fixed;
          top: 0;
          left: 0;
          z-index: 2147483647;
          width: 22px;
          height: 22px;
          margin: -11px 0 0 -11px;
          pointer-events: none;
          opacity: 0;
          transition: opacity 150ms ease-out;
          will-change: transform;
        }
        .brand-cursor-dot svg {
          width: 100%;
          height: 100%;
          transition: transform 150ms cubic-bezier(0.2, 0.8, 0.2, 1),
            filter 200ms ease-out;
          transform-origin: 60% 60%;
          filter: drop-shadow(0 1px 2px hsl(var(--foreground) / 0.25));
        }
        .brand-cursor-dot[data-mode="button"] svg {
          transform: rotate(-18deg) scale(1.05);
        }
        .brand-cursor-dot[data-mode="glow"] svg {
          filter: drop-shadow(0 0 6px hsl(var(--primary) / 0.55));
        }
        .brand-cursor-dot[data-mode="hidden"] {
          opacity: 0 !important;
        }
        .brand-cursor-dot[data-striking="true"] svg {
          transform: rotate(-34deg) scale(0.92);
          transition-duration: 60ms;
        }

        .brand-cursor-sparks {
          position: fixed;
          top: 0;
          left: 0;
          z-index: 2147483647;
          pointer-events: none;
          will-change: transform;
        }
        .brand-cursor-spark {
          position: absolute;
          top: -1px;
          left: -1px;
          width: 3px;
          height: 3px;
          border-radius: 50%;
          background: hsl(var(--primary));
          opacity: 1;
          animation: brand-cursor-spark-burst 120ms ease-out forwards;
        }
        @keyframes brand-cursor-spark-burst {
          to {
            transform: translate3d(var(--dx), var(--dy), 0) scale(0.3);
            opacity: 0;
          }
        }

        @media (prefers-reduced-motion: reduce) {
          .brand-cursor-dot, .brand-cursor-dot svg, .brand-cursor-spark {
            transition: none !important;
            animation: none !important;
          }
        }
      `}</style>
      <div ref={dotRef} className="brand-cursor-dot" data-mode="default" aria-hidden="true">
        {/* Minimal forge-hammer glyph: rotated so the head naturally points at the hit-point. */}
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <g transform="rotate(-45 12 12)">
            <rect x="10.5" y="10" width="3" height="11" rx="1.2" fill="hsl(var(--foreground))" />
            <rect
              x="6"
              y="4"
              width="12"
              height="7"
              rx="1.5"
              fill="hsl(var(--primary))"
              stroke="hsl(var(--background))"
              strokeWidth="1"
            />
          </g>
        </svg>
      </div>
      <div ref={sparksRef} className="brand-cursor-sparks" aria-hidden="true" />
    </>
  );
}
