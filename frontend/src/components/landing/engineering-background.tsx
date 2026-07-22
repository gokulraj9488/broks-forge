"use client";

import { useEffect, useRef } from "react";

/**
 * A single, reusable ambient backdrop for the landing page: a faint grid, a
 * handful of orthogonal "pipeline" paths with a small pulse traveling along
 * each, a few attached nodes, and soft radial glows between sections. Fixed
 * to the viewport (not the document) so it's O(1) regardless of page length,
 * costs one paint layer, and never affects layout — content lays out exactly
 * as it did without this component mounted.
 *
 * All motion is opacity/transform only (compositor-friendly), scroll
 * parallax is a single rAF-batched listener mutating refs directly (no
 * re-renders), and everything is skipped outright under prefers-reduced-motion.
 */
const PATHS = [
  { d: "M100,150 L400,150 L400,350 L700,350", tone: "chart-1", duration: 13 },
  { d: "M1200,100 L1200,300 L900,300 L900,500", tone: "chart-1", duration: 16 },
  { d: "M200,600 L200,800 L550,800", tone: "success", duration: 14 },
  { d: "M1000,700 L1300,700 L1300,850", tone: "chart-1", duration: 18 },
  { d: "M50,450 L350,450 L350,250", tone: "chart-3", duration: 15 },
  { d: "M750,50 L750,220 L1050,220", tone: "chart-1", duration: 17 },
];

const NODES = [
  { x: 400, y: 150, tone: "chart-1", delay: 0 },
  { x: 700, y: 350, tone: "chart-1", delay: 1.2 },
  { x: 1200, y: 100, tone: "chart-1", delay: 2.1 },
  { x: 900, y: 500, tone: "success", delay: 0.6 },
  { x: 200, y: 600, tone: "success", delay: 1.8 },
  { x: 550, y: 800, tone: "chart-1", delay: 2.6 },
  { x: 1300, y: 850, tone: "chart-1", delay: 0.9 },
  { x: 350, y: 250, tone: "chart-3", delay: 1.5 },
  { x: 1050, y: 220, tone: "chart-1", delay: 2.3 },
];

export function EngineeringBackground() {
  const rootRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const glowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion) return;

    // Scroll parallax: each layer drifts at a different, tiny fraction of
    // scroll speed so they read as separate depths without ever competing
    // with content for attention.
    let ticking = false;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const y = window.scrollY;
        if (gridRef.current) gridRef.current.style.transform = `translate3d(0, ${y * 0.015}px, 0)`;
        if (svgRef.current) svgRef.current.style.transform = `translate3d(0, ${y * 0.03}px, 0)`;
        if (glowRef.current) glowRef.current.style.transform = `translate3d(0, ${y * 0.05}px, 0)`;
        ticking = false;
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });

    // Section-enter brighten: a brief, whole-backdrop opacity lift (<1s) each
    // time a new section crosses into view — a cheap, honest stand-in for
    // "the nearby network brightens" given one shared, viewport-fixed backdrop
    // rather than per-section canvases.
    const sections = document.querySelectorAll("main > section");
    let boostTimeout: ReturnType<typeof setTimeout> | null = null;
    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((e) => e.isIntersecting)) return;
        rootRef.current?.classList.add("eng-bg-boost");
        if (boostTimeout) clearTimeout(boostTimeout);
        boostTimeout = setTimeout(() => rootRef.current?.classList.remove("eng-bg-boost"), 700);
      },
      { threshold: 0.15 },
    );
    sections.forEach((s) => observer.observe(s));

    return () => {
      window.removeEventListener("scroll", onScroll);
      observer.disconnect();
      if (boostTimeout) clearTimeout(boostTimeout);
    };
  }, []);

  return (
    <div
      ref={rootRef}
      aria-hidden="true"
      className="eng-bg-root pointer-events-none fixed inset-0 -z-10 overflow-hidden opacity-80 transition-opacity duration-300 [&.eng-bg-boost]:opacity-100"
    >
      {/* Solid page background — lives here (not on the page wrapper) so this
          component can sit behind normal content via z-index alone; nothing
          upstream paints an opaque layer that would otherwise hide it. */}
      <div className="absolute inset-0 bg-background" />

      {/* Layer 1 — faint engineering grid */}
      <div
        ref={gridRef}
        className="eng-bg-grid absolute inset-[-10%]"
        style={{
          backgroundImage:
            "linear-gradient(hsl(var(--border)) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--border)) 1px, transparent 1px)",
          backgroundSize: "56px 56px",
        }}
      />

      {/* Layer 4 — soft radial illumination between sections */}
      <div
        ref={glowRef}
        className="absolute inset-[-10%] opacity-[0.05]"
        style={{
          backgroundImage:
            "radial-gradient(38rem 26rem at 15% 12%, hsl(var(--chart-1)), transparent 65%)," +
            "radial-gradient(34rem 24rem at 85% 42%, hsl(var(--success)), transparent 65%)," +
            "radial-gradient(36rem 26rem at 20% 72%, hsl(var(--chart-3)), transparent 65%)," +
            "radial-gradient(38rem 28rem at 88% 95%, hsl(var(--chart-1)), transparent 65%)",
        }}
      />

      {/* Layers 2 & 3 — connection paths + attached nodes, with a small pulse
          traveling each path (offset-path keeps this transform-driven, not a
          geometry/canvas animation). */}
      <svg
        ref={svgRef}
        className="absolute inset-0 h-full w-full opacity-90"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
      >
        {PATHS.map((p, i) => (
          <path
            key={i}
            d={p.d}
            fill="none"
            stroke={`hsl(var(--${p.tone}))`}
            strokeOpacity={0.12}
            strokeWidth={1}
            vectorEffect="non-scaling-stroke"
          />
        ))}
        {PATHS.map((p, i) => (
          <circle
            key={i}
            r={2}
            className="eng-bg-dot"
            fill={`hsl(var(--${p.tone}))`}
            style={{ offsetPath: `path("${p.d}")`, animationDuration: `${p.duration}s`, animationDelay: `${i * 1.4}s` }}
          />
        ))}
        {NODES.map((n, i) => (
          <circle
            key={i}
            cx={n.x}
            cy={n.y}
            r={3}
            className="eng-bg-node"
            fill={`hsl(var(--${n.tone}))`}
            style={{ animationDelay: `${n.delay}s` }}
          />
        ))}
      </svg>
    </div>
  );
}
